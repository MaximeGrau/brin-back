package com.maximegrau.brin.service.youtube;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

/**
 * Appels à la YouTube Data API v3. Chaque appel coûte 1 unité de quota (10 000 par jour) :
 * on n'utilise que les endpoints à 1 unité, jamais {@code search.list} (100 unités).
 */
@Component
public class YouTubeClient {

    /** Maximum autorisé par l'API pour {@code maxResults} et pour le nombre d'ID de {@code videos.list}. */
    public static final int MAX_PAGE_SIZE = 50;

    private final RestClient http;
    private final String apiKey;

    public YouTubeClient(RestClient.Builder builder, YouTubeProperties properties) {
        this.apiKey = properties.apiKey();
        this.http = builder
                .baseUrl(properties.baseUrl())
                .defaultStatusHandler(status -> status == HttpStatus.FORBIDDEN, (request, response) -> {
                    var body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
                    if (body.contains("quotaExceeded")) {
                        throw new YouTubeQuotaExceededException("Quota YouTube épuisé");
                    }
                    throw new IllegalStateException("YouTube a refusé la requête (403) : " + body);
                })
                .build();
    }

    /** Chaîne correspondant à un handle (ex. {@code @ScienceEtonnante}). */
    public Optional<YouTubeApi.Channel> findChannelByHandle(String handle) {
        var response = http.get()
                .uri(uri -> uri.path("/channels")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("forHandle", handle)
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(YouTubeApi.ChannelList.class);
        return response == null || response.items() == null
                ? Optional.empty()
                : response.items().stream().findFirst();
    }

    /** Une page de la playlist, des vidéos les plus récentes aux plus anciennes. */
    public YouTubeApi.PlaylistItemList listPlaylistItems(String playlistId, String pageToken) {
        return http.get()
                .uri(uri -> {
                    uri.path("/playlistItems")
                            .queryParam("part", "contentDetails")
                            .queryParam("playlistId", playlistId)
                            .queryParam("maxResults", MAX_PAGE_SIZE)
                            .queryParam("key", apiKey);
                    if (pageToken != null) {
                        uri.queryParam("pageToken", pageToken);
                    }
                    return uri.build();
                })
                .retrieve()
                .body(YouTubeApi.PlaylistItemList.class);
    }

    /** Détail de 50 vidéos au plus. Les vidéos supprimées ou privées sont absentes de la réponse. */
    public List<YouTubeApi.Video> getVideos(List<String> videoIds) {
        if (videoIds.size() > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("50 vidéos au plus par appel, reçu " + videoIds.size());
        }
        var response = http.get()
                .uri(uri -> uri.path("/videos")
                        .queryParam("part", "snippet,contentDetails,status")
                        .queryParam("id", String.join(",", videoIds))
                        .queryParam("key", apiKey)
                        .build())
                .retrieve()
                .body(YouTubeApi.VideoList.class);
        return response == null || response.items() == null ? List.of() : response.items();
    }
}
