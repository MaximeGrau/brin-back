package com.maximegrau.brin.service;

import com.maximegrau.brin.service.youtube.YouTubeApi;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Filtres déterministes appliqués avant toute insertion : seules les vidéos qui passent
 * arrivent en base (en PENDING) et coûteront un appel IA.
 */
@Component
public class VideoFilter {

    private final ImportProperties properties;

    public VideoFilter(ImportProperties properties) {
        this.properties = properties;
    }

    /** @return la raison du rejet, vide si la vidéo est retenue */
    public Optional<RejectionReason> reject(YouTubeApi.Video video) {
        if (video.status() == null || !"public".equals(video.status().privacyStatus())) {
            return Optional.of(RejectionReason.NOT_PUBLIC);
        }
        // Le lecteur intégré de l'app ne peut pas lire une vidéo non intégrable.
        if (!video.status().embeddable()) {
            return Optional.of(RejectionReason.NOT_EMBEDDABLE);
        }
        if (video.snippet() == null || !"none".equals(video.snippet().liveBroadcastContent())) {
            return Optional.of(RejectionReason.LIVE);
        }
        var duration = duration(video);
        if (duration.isEmpty() || duration.get().isZero()) {
            return Optional.of(RejectionReason.UNKNOWN_DURATION);
        }
        if (duration.get().compareTo(properties.maxDuration()) > 0) {
            return Optional.of(RejectionReason.TOO_LONG);
        }
        if (!languageAccepted(video.snippet())) {
            return Optional.of(RejectionReason.LANGUAGE);
        }
        return Optional.empty();
    }

    public static Optional<Duration> duration(YouTubeApi.Video video) {
        if (video.contentDetails() == null || video.contentDetails().duration() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.parse(video.contentDetails().duration()));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private boolean languageAccepted(YouTubeApi.VideoSnippet snippet) {
        if (properties.languages().isEmpty()) {
            return true;
        }
        var language = snippet.defaultAudioLanguage() != null ? snippet.defaultAudioLanguage() : snippet.defaultLanguage();
        if (language == null) {
            return true;
        }
        var normalized = language.toLowerCase(Locale.ROOT);
        return properties.languages().stream().anyMatch(accepted -> normalized.startsWith(accepted.toLowerCase(Locale.ROOT)));
    }
}
