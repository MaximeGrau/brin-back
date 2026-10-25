package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.model.CategoryInfo;
import com.maximegrau.brin.api.model.FeedItem;
import com.maximegrau.brin.api.model.FeedPage;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.FeedEntry;
import com.maximegrau.brin.model.video.FeedSlice;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoSource;
import java.net.URI;
import org.springframework.stereotype.Component;

/**
 * Conversion domaine → DTO générés depuis le contrat OpenAPI.
 * Les enums portent les mêmes noms des deux côtés, d'où les noms qualifiés pour ceux de l'API.
 */
@Component
public class FeedMapper {

    private final MediaUrls mediaUrls;

    public FeedMapper(MediaUrls mediaUrls) {
        this.mediaUrls = mediaUrls;
    }

    public FeedPage toPage(FeedSlice slice) {
        return new FeedPage(slice.entries().stream().map(this::toItem).toList())
                .nextCursor(slice.nextCursor() == null ? null : slice.nextCursor().encode());
    }

    public FeedItem toItem(FeedEntry entry) {
        Video video = entry.video();
        return new FeedItem(
                video.getId().toString(),
                com.maximegrau.brin.api.model.VideoSource.valueOf(video.getSource().name()),
                video.getExternalId(),
                video.getTitle(),
                toCreator(video.getCreator()),
                toApi(video.getCategory()),
                video.getScore().floatValue(),
                com.maximegrau.brin.api.model.Reliability.valueOf(video.getReliability().name()),
                entry.learned(),
                entry.saved())
                .keyTakeaway(video.getKeyTakeaway())
                .durationSeconds(video.getDurationSeconds())
                .playbackUrl(video.isUpload() ? mediaUrls.video(video) : null)
                .posterUrl(posterUrl(video));
    }

    /** Vignette : celle de YouTube (URL publique fournie par YouTube pour chaque vidéo) ou la couverture envoyée. */
    private URI posterUrl(Video video) {
        if (video.isUpload()) {
            return mediaUrls.thumbnail(video);
        }
        return video.getSource() == VideoSource.YOUTUBE
                ? URI.create("https://i.ytimg.com/vi/" + video.getExternalId() + "/hqdefault.jpg")
                : null;
    }

    public CategoryInfo toCategoryInfo(Category category) {
        return new CategoryInfo(toApi(category), category.label());
    }

    public Category toDomain(com.maximegrau.brin.api.model.Category category) {
        return category == null ? null : Category.valueOf(category.name());
    }

    private com.maximegrau.brin.api.model.Category toApi(Category category) {
        return com.maximegrau.brin.api.model.Category.valueOf(category.name());
    }

    private com.maximegrau.brin.api.model.Creator toCreator(Creator creator) {
        return new com.maximegrau.brin.api.model.Creator(creator.getName())
                .handle(creator.getHandle())
                .channelUrl(creator.getChannelUrl() == null ? null : URI.create(creator.getChannelUrl()));
    }
}
