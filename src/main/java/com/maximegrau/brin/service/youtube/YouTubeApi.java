package com.maximegrau.brin.service.youtube;

import java.time.Instant;
import java.util.List;

/**
 * Sous-ensemble des réponses de la YouTube Data API v3 utilisé par l'import
 * (les champs non déclarés sont ignorés à la désérialisation).
 */
public final class YouTubeApi {

    private YouTubeApi() {
    }

    // channels.list?part=snippet,contentDetails

    public record ChannelList(List<Channel> items) {
    }

    public record Channel(String id, ChannelSnippet snippet, ChannelContentDetails contentDetails) {
    }

    public record ChannelSnippet(String title, String customUrl) {
    }

    public record ChannelContentDetails(RelatedPlaylists relatedPlaylists) {
    }

    public record RelatedPlaylists(String uploads) {
    }

    // playlistItems.list?part=contentDetails

    public record PlaylistItemList(String nextPageToken, List<PlaylistItem> items) {
    }

    public record PlaylistItem(PlaylistItemContentDetails contentDetails) {
    }

    public record PlaylistItemContentDetails(String videoId, Instant videoPublishedAt) {
    }

    // videos.list?part=snippet,contentDetails,status

    public record VideoList(List<Video> items) {
    }

    public record Video(String id, VideoSnippet snippet, VideoContentDetails contentDetails, VideoStatus status) {
    }

    public record VideoSnippet(String title, String description, Instant publishedAt, String channelId,
                               String defaultAudioLanguage, String defaultLanguage,
                               /** none, live ou upcoming */ String liveBroadcastContent) {
    }

    /** @param duration durée ISO 8601, ex. {@code PT1M5S} */
    public record VideoContentDetails(String duration) {
    }

    public record VideoStatus(String privacyStatus, boolean embeddable) {
    }
}
