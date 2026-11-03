package com.maximegrau.brin.service.youtube;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code brin.youtube.*} */
@ConfigurationProperties("brin.youtube")
public record YouTubeProperties(
        /** Clé de la YouTube Data API v3 (Google Cloud Console). Vide = import désactivé. */
        String apiKey,
        @DefaultValue("https://www.googleapis.com/youtube/v3") String baseUrl) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
