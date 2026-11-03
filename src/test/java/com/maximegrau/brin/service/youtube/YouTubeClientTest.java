package com.maximegrau.brin.service.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Vérifie les URL appelées et la désérialisation de réponses YouTube réelles (champs inconnus compris). */
class YouTubeClientTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final YouTubeClient client = new YouTubeClient(builder, new YouTubeProperties("cle", "https://yt.test/v3"));

    @Test
    void trouveUneChaineParHandle() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://yt.test/v3/channels")))
                .andExpect(queryParam("forHandle", "@ScienceEtonnante"))
                .andExpect(queryParam("key", "cle"))
                .andRespond(withSuccess("""
                        {"kind": "youtube#channelListResponse", "items": [{
                          "id": "UCaNlbnghtwlsGF-KzAFThqA",
                          "snippet": {"title": "ScienceEtonnante", "customUrl": "@scienceetonnante", "country": "FR"},
                          "contentDetails": {"relatedPlaylists": {"likes": "", "uploads": "UUaNlbnghtwlsGF-KzAFThqA"}}
                        }]}
                        """, MediaType.APPLICATION_JSON));

        var channel = client.findChannelByHandle("@ScienceEtonnante").orElseThrow();

        assertThat(channel.contentDetails().relatedPlaylists().uploads()).isEqualTo("UUaNlbnghtwlsGF-KzAFThqA");
        assertThat(channel.snippet().title()).isEqualTo("ScienceEtonnante");
    }

    @Test
    void litUnePageDePlaylistEtLeDetailDesVideos() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://yt.test/v3/playlistItems")))
                .andExpect(queryParam("pageToken", "P2"))
                .andRespond(withSuccess("""
                        {"nextPageToken": "P3", "pageInfo": {"totalResults": 120},
                         "items": [{"kind": "youtube#playlistItem",
                                    "contentDetails": {"videoId": "v1", "videoPublishedAt": "2026-10-01T10:00:00Z"}}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://yt.test/v3/videos")))
                .andExpect(queryParam("id", "v1"))
                .andRespond(withSuccess("""
                        {"items": [{"id": "v1",
                          "snippet": {"title": "Pourquoi le ciel est bleu", "description": "…",
                                      "publishedAt": "2026-10-01T10:00:00Z", "channelId": "UC1",
                                      "defaultAudioLanguage": "fr", "liveBroadcastContent": "none",
                                      "thumbnails": {"default": {"url": "https://i.ytimg.com/x.jpg"}}},
                          "contentDetails": {"duration": "PT47S", "dimension": "2d", "definition": "hd"},
                          "status": {"privacyStatus": "public", "embeddable": true, "license": "youtube"}}]}
                        """, MediaType.APPLICATION_JSON));

        var page = client.listPlaylistItems("UU1", "P2");
        var videos = client.getVideos(List.of("v1"));

        assertThat(page.nextPageToken()).isEqualTo("P3");
        assertThat(page.items().getFirst().contentDetails().videoPublishedAt())
                .isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(videos.getFirst().contentDetails().duration()).isEqualTo("PT47S");
        assertThat(videos.getFirst().status().embeddable()).isTrue();
        server.verify();
    }

    @Test
    void signaleLeQuotaEpuise() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://yt.test/v3/videos")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"code": 403, "errors": [{"reason": "quotaExceeded"}]}}
                        """));

        assertThatThrownBy(() -> client.getVideos(List.of("v1"))).isInstanceOf(YouTubeQuotaExceededException.class);
    }
}
