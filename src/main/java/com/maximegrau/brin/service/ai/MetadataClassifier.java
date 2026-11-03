package com.maximegrau.brin.service.ai;

import com.maximegrau.brin.model.video.Video;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Étape 1 de l'entonnoir : classe une vidéo YouTube à partir de ses seules métadonnées (créateur, titre, description).
 */
@Component
public class MetadataClassifier implements VideoClassifier {

    public static final String VERSION = "metadata-v1";

    private final ChatClient chat;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public MetadataClassifier(ChatClient.Builder builder,
                              @Value("classpath:prompts/classification-metadata-v1-system.st") Resource systemPrompt,
                              @Value("classpath:prompts/classification-metadata-v1-user.st") Resource userPrompt) {
        this.chat = builder.build();
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    @Override
    public boolean supports(Video video) {
        return !video.isUpload();
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public MetadataClassification classify(Video video) {
        return chat.prompt()
                .options(VideoClassifier.options())
                .system(systemPrompt)
                .user(u -> u.text(userPrompt)
                        .param("creator", video.getCreator().getName())
                        .param("title", video.getTitle())
                        .param("duration", Objects.requireNonNullElse(video.getDurationSeconds(), 0))
                        .param("description", VideoClassifier.promptDescription(video)))
                .call()
                .entity(MetadataClassification.class, spec -> spec.useProviderStructuredOutput());
    }
}
