package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.model.Upload;
import com.maximegrau.brin.api.model.UploadRules;
import com.maximegrau.brin.api.model.UploadStatus;
import com.maximegrau.brin.service.upload.UploadProperties;
import com.maximegrau.brin.service.upload.UploadView;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** Conversion domaine → DTO générés pour les vidéos envoyées sur Brin. */
@Component
public class UploadMapper {

    private final MediaUrls mediaUrls;

    public UploadMapper(MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    public Upload toUpload(UploadView view) {
        var video = view.video();
        var status = switch (video.getStatus()) {
            case PENDING -> UploadStatus.ANALYSING;
            case ACCEPTED -> UploadStatus.PUBLISHED;
            case REJECTED -> UploadStatus.REJECTED;
        };
        return new Upload(video.getId().toString(), video.getTitle(), status, video.getDurationSeconds(),
                video.getCreatedAt().atOffset(ZoneOffset.UTC), mediaUrls.video(video),
                Math.toIntExact(view.learnedCount()), Math.toIntExact(view.savedCount()))
                .description(video.getDescription())
                .rejectionReason(video.getRejectionReason())
                .category(video.getCategory() == null ? null
                        : com.maximegrau.brin.api.model.Category.valueOf(video.getCategory().name()))
                .score(video.getScore() == null ? null : video.getScore().floatValue())
                .keyTakeaway(video.getKeyTakeaway())
                .posterUrl(mediaUrls.thumbnail(video));
    }

    public UploadRules toRules(UploadProperties properties) {
        return new UploadRules(properties.maxSize().toBytes(),
                Math.toIntExact(properties.minDuration().toSeconds()),
                Math.toIntExact(properties.maxDuration().toSeconds()),
                properties.minAspectRatio(),
                properties.minShortSide(),
                properties.acceptedTypes(),
                properties.maxPending());
    }
}
