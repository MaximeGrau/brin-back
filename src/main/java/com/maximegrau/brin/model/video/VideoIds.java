package com.maximegrau.brin.model.video;

import java.util.Optional;
import java.util.UUID;

/** Les ID de vidéo exposés par l'API sont des UUID sous forme de chaîne. */
public final class VideoIds {

    private VideoIds() {
    }

    /** Un ID mal formé est traité comme une vidéo inexistante (404) par les appelants. */
    public static Optional<UUID> parse(String videoId) {
        try {
            return Optional.of(UUID.fromString(videoId));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
