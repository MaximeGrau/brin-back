package com.maximegrau.brin.service.upload;

import com.maximegrau.brin.model.user.InteractionCounts;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.CreatorRepository;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoIds;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.service.VideoNotFoundException;
import com.maximegrau.brin.service.auth.AuthExceptions;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vidéos envoyées directement sur Brin. Le serveur vérifie lui-même le fichier (format vertical, durée, taille),
 * le stocke, puis la vidéo attend l'analyse IA (PENDING) avant de pouvoir entrer dans le feed des autres.
 * Ouvert à tous les utilisateurs : objectif, un catalogue propre à Brin, lu dans son propre lecteur plein écran.
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UserRepository users;
    private final CreatorRepository creators;
    private final VideoRepository videos;
    private final VideoInteractionRepository interactions;
    private final VideoStorage storage;
    private final UploadValidator validator;
    private final TransactionTemplate transaction;
    private final UploadProperties properties;
    private final Clock clock;

    public UploadService(UserRepository users, CreatorRepository creators, VideoRepository videos,
                         VideoInteractionRepository interactions, VideoStorage storage,
                         UploadValidator validator, TransactionTemplate transaction, UploadProperties properties, Clock clock) {
        this.users = users;
        this.creators = creators;
        this.videos = videos;
        this.interactions = interactions;
        this.storage = storage;
        this.validator = validator;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    public UploadView submit(UUID userId, MultipartFile file, MultipartFile thumbnail, String title, String description) {
        var user = users.findById(userId).orElseThrow(AuthExceptions::unknownUser);
        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vérifie ton adresse e-mail avant de publier");
        }
        if (videos.countPendingUploads(userId) >= properties.maxPending()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Tu as déjà " + properties.maxPending() + " vidéos en analyse : attends leur résultat");
        }
        var contentType = validator.checkVideoFile(file);
        var thumbnailType = validator.checkThumbnail(thumbnail);

        var stored = new ArrayList<String>();
        Path temp = null;
        try {
            temp = Files.createTempFile("brin-upload-", ".video");
            file.transferTo(temp);
            var info = validator.checkContent(temp);

            var fileId = UUID.randomUUID();
            var videoKey = "videos/" + fileId + (contentType.equals("video/quicktime") ? ".mov" : ".mp4");
            storage.store(videoKey, temp);
            stored.add(videoKey);
            String thumbnailKey = null;
            if (thumbnailType != null) {
                thumbnailKey = "thumbnails/" + fileId + (thumbnailType.equals("image/png") ? ".png" : ".jpg");
                var thumbnailTemp = Files.createTempFile("brin-thumbnail-", ".img");
                thumbnail.transferTo(thumbnailTemp);
                storage.store(thumbnailKey, thumbnailTemp);
                stored.add(thumbnailKey);
            }

            var finalThumbnailKey = thumbnailKey;
            var video = transaction.execute(status -> {
                var creator = creatorFor(user);
                return videos.save(Video.uploaded(creator, title.strip(), blankToNull(description),
                        (int) Math.round(info.durationSeconds()), videoKey, finalThumbnailKey, contentType,
                        file.getSize(), info.width(), info.height(), clock.instant()));
            });
            log.info("Vidéo envoyée par {} : « {} » ({} s, {}×{}), en attente d'analyse", userId, title,
                    Math.round(info.durationSeconds()), info.width(), info.height());
            return new UploadView(video, 0, 0);
        } catch (IOException e) {
            stored.forEach(this::deleteQuietly);
            throw new UncheckedIOException("Réception de la vidéo impossible", e);
        } catch (RuntimeException e) {
            stored.forEach(this::deleteQuietly);
            throw e;
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException e) {
                    log.debug("Fichier temporaire {} non supprimé", temp, e);
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UploadView> list(UUID userId) {
        var uploads = videos.findUploadsByUser(userId);
        if (uploads.isEmpty()) {
            return List.of();
        }
        Map<UUID, InteractionCounts> counts = interactions.countByVideoIdIn(uploads.stream().map(Video::getId).toList())
                .stream().collect(Collectors.toMap(InteractionCounts::videoId, Function.identity()));
        return uploads.stream()
                .map(video -> {
                    var c = counts.get(video.getId());
                    return new UploadView(video, c == null ? 0 : c.learned(), c == null ? 0 : c.saved());
                })
                .toList();
    }

    /** Supprime la vidéo (et les réactions reçues), puis ses fichiers. */
    public void delete(UUID userId, String uploadId) {
        var video = transaction.execute(status -> {
            var found = VideoIds.parse(uploadId).flatMap(videos::findById)
                    .filter(v -> v.isUpload() && userId.equals(v.getCreator().getUserId()))
                    .orElseThrow(() -> new VideoNotFoundException(uploadId));
            videos.delete(found);
            return found;
        });
        deleteQuietly(video.getFileKey());
        if (video.getThumbnailKey() != null) {
            deleteQuietly(video.getThumbnailKey());
        }
    }

    private Creator creatorFor(User user) {
        var name = user.getDisplayName() != null ? user.getDisplayName() : "Créateur Brin";
        var creator = creators.findByUserId(user.getId())
                .orElseGet(() -> creators.save(Creator.forUser(user.getId(), name)));
        creator.rename(name);
        return creator;
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Fichier {} non supprimé : {}", key, e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
