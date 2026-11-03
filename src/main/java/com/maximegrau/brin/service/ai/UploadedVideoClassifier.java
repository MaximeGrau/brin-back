package com.maximegrau.brin.service.ai;

import com.google.genai.Client;
import com.google.genai.types.DeleteFileConfig;
import com.google.genai.types.FileState;
import com.google.genai.types.GetFileConfig;
import com.google.genai.types.UploadFileConfig;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.service.upload.VideoStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

/**
 * Classe une vidéo envoyée sur Brin en la faisant regarder par Gemini (image et son), modération comprise.
 *
 * <p>Le fichier passe par l'API Files de Gemini (pas de limite de taille d'une requête en ligne) : envoi, attente
 * du traitement par Google ({@code ACTIVE}), appel du modèle avec l'URI du fichier, puis suppression du fichier
 * chez Google (sinon effacé au bout de 48 h). Appel bloquant : uniquement en tâche de fond.
 */
@Component
public class UploadedVideoClassifier implements VideoClassifier {

    public static final String VERSION = "upload-v1";

    private static final Logger log = LoggerFactory.getLogger(UploadedVideoClassifier.class);
    private static final Duration PROCESSING_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    private final ChatClient chat;
    private final Client genai;
    private final VideoStorage storage;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public UploadedVideoClassifier(ChatClient.Builder builder, Client genai, VideoStorage storage,
                                   @Value("classpath:prompts/classification-upload-v1-system.st") Resource systemPrompt,
                                   @Value("classpath:prompts/classification-upload-v1-user.st") Resource userPrompt) {
        this.chat = builder.build();
        this.genai = genai;
        this.storage = storage;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public boolean supports(Video video) {
        return video.isUpload();
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public MetadataClassification classify(Video video) {
        var mimeType = Objects.requireNonNullElse(video.getContentType(), "video/mp4");
        String fileName;
        try (var in = storage.load(video.getFileKey()).getInputStream()) {
            var uploaded = genai.files.upload(in, video.getFileSize(), UploadFileConfig.builder()
                    .mimeType(mimeType)
                    .displayName("brin-" + video.getId())
                    .build());
            fileName = uploaded.name().orElseThrow(() -> new IllegalStateException("Gemini n'a pas renvoyé de nom de fichier"));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de " + video.getFileKey() + " impossible", e);
        }
        try {
            var uri = waitUntilActive(fileName);
            var media = Media.builder().mimeType(MimeType.valueOf(mimeType)).data(URI.create(uri)).build();
            return chat.prompt()
                    .options(VideoClassifier.options())
                    .system(systemPrompt)
                    .user(u -> u.text(userPrompt)
                            .param("creator", video.getCreator().getName())
                            .param("title", video.getTitle())
                            .param("duration", Objects.requireNonNullElse(video.getDurationSeconds(), 0))
                            .param("description", VideoClassifier.promptDescription(video))
                            .media(media))
                    .call()
                    .entity(MetadataClassification.class, spec -> spec.useProviderStructuredOutput());
        } finally {
            try {
                genai.files.delete(fileName, DeleteFileConfig.builder().build());
            } catch (RuntimeException e) {
                log.debug("Fichier Gemini {} non supprimé (effacé automatiquement sous 48 h)", fileName, e);
            }
        }
    }

    /** Google traite la vidéo (quelques secondes) avant qu'elle soit utilisable par le modèle. */
    private String waitUntilActive(String fileName) {
        var deadline = Instant.now().plus(PROCESSING_TIMEOUT);
        while (true) {
            var file = genai.files.get(fileName, GetFileConfig.builder().build());
            var state = file.state().map(FileState::knownEnum).orElse(FileState.Known.STATE_UNSPECIFIED);
            if (state == FileState.Known.ACTIVE) {
                return file.uri().orElseThrow(() -> new IllegalStateException("Fichier Gemini sans URI"));
            }
            if (state == FileState.Known.FAILED) {
                throw new IllegalStateException("Gemini n'a pas pu lire la vidéo : "
                        + file.error().map(Object::toString).orElse("raison inconnue"));
            }
            if (Instant.now().isAfter(deadline)) {
                throw new IllegalStateException("Vidéo toujours en traitement chez Gemini après " + PROCESSING_TIMEOUT);
            }
            try {
                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Attente interrompue", e);
            }
        }
    }
}
