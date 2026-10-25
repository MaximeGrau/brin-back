package com.maximegrau.brin.service.upload;

import com.maximegrau.brin.model.video.Video;

/** Une vidéo envoyée par l'utilisateur, avec les réactions reçues (tableau de bord). */
public record UploadView(Video video, long learnedCount, long savedCount) {
}
