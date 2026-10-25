package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.MediaApi;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.service.upload.VideoStorage;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * /api/v1/media/{kind}/{id} : fichiers des vidéos envoyées sur Brin, protégés par un lien signé ({@link MediaUrls}).
 * Spring MVC gère l'en-tête Range pour un {@link Resource} (réponses 206) : le lecteur peut avancer dans la vidéo
 * sans tout télécharger.
 */
@RestController
public class MediaController implements MediaApi {

    private final MediaUrls mediaUrls;
    private final VideoRepository videos;
    private final VideoStorage storage;

    public MediaController(MediaUrls mediaUrls, VideoRepository videos, VideoStorage storage) {
        this.mediaUrls = mediaUrls;
        this.videos = videos;
        this.storage = storage;
    }

    @Override
    public ResponseEntity<Resource> getMedia(String kind, String videoId, Long exp, String sig) {
        if (!mediaUrls.isValid(kind, videoId, exp, sig)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Lien expiré ou invalide");
        }
        var video = videos.findById(UUID.fromString(videoId)).filter(v -> v.isUpload())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable"));
        var isVideo = MediaUrls.VIDEO.equals(kind);
        var key = isVideo ? video.getFileKey() : video.getThumbnailKey();
        var resource = key == null ? null : storage.load(key);
        if (resource == null || !resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
        }
        var contentType = isVideo ? Objects.requireNonNullElse(video.getContentType(), "video/mp4")
                : key.endsWith(".png") ? "image/png" : "image/jpeg";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(resource);
    }
}
