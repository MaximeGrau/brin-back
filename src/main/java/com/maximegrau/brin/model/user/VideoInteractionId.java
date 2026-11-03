package com.maximegrau.brin.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public record VideoInteractionId(
        @Column(name = "user_id") UUID userId,
        @Column(name = "video_id") UUID videoId) {
}
