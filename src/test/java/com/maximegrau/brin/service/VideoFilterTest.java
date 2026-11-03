package com.maximegrau.brin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.maximegrau.brin.service.youtube.YouTubeApi;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VideoFilterTest {

    private final VideoFilter filter = new VideoFilter(
            new ImportProperties("-", false, List.of(), Duration.ofSeconds(180), 4, List.of("fr")));

    @Test
    void garderUnShortPublicIntegrableEnFrancais() {
        assertThat(filter.reject(video("PT58S", "public", true, "none", "fr-FR"))).isEmpty();
    }

    @Test
    void garderUneVideoSansLangueDeclaree() {
        assertThat(filter.reject(video("PT58S", "public", true, "none", null))).isEmpty();
    }

    @Test
    void ecarterSelonLaRaison() {
        assertThat(filter.reject(video("PT3M1S", "public", true, "none", "fr"))).contains(RejectionReason.TOO_LONG);
        assertThat(filter.reject(video("PT58S", "unlisted", true, "none", "fr"))).contains(RejectionReason.NOT_PUBLIC);
        assertThat(filter.reject(video("PT58S", "public", false, "none", "fr"))).contains(RejectionReason.NOT_EMBEDDABLE);
        assertThat(filter.reject(video("P0D", "public", true, "live", "fr"))).contains(RejectionReason.LIVE);
        assertThat(filter.reject(video("P0D", "public", true, "none", "fr"))).contains(RejectionReason.UNKNOWN_DURATION);
        assertThat(filter.reject(video("PT58S", "public", true, "none", "en"))).contains(RejectionReason.LANGUAGE);
    }

    static YouTubeApi.Video video(String duration, String privacy, boolean embeddable, String live, String language) {
        return new YouTubeApi.Video("abc",
                new YouTubeApi.VideoSnippet("Titre", "Description", Instant.parse("2026-10-01T10:00:00Z"), "UC1",
                        language, null, live),
                new YouTubeApi.VideoContentDetails(duration),
                new YouTubeApi.VideoStatus(privacy, embeddable));
    }
}
