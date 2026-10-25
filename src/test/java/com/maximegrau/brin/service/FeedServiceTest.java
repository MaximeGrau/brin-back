package com.maximegrau.brin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.user.CategoryAffinityRepository;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.model.user.VideoInteraction;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.FeedEntry;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.model.video.VideoStatus;
import com.maximegrau.brin.service.feed.FeedProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FeedServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final VideoRepository videos = mock(VideoRepository.class);
    private final VideoInteractionRepository interactions = mock(VideoInteractionRepository.class);
    private final FeedService service = new FeedService(videos, interactions, mock(UserRepository.class),
            mock(CategoryAffinityRepository.class), new FeedProperties(3.0, 1.0, 0.15, 0.6, 0.1, 3, 0.2, -2.0));

    @Test
    void lesVideosSuiventLOrdreMelangeRenvoyeParLaBaseAvecLesInteractions() {
        var v1 = video();
        var v2 = video();
        when(videos.findShuffledIds(eq("SCIENCES"), anyString(), anyInt())).thenReturn(List.of(v2.getId(), v1.getId()));
        when(videos.findWithCreator(anyCollection(), eq(VideoStatus.ACCEPTED))).thenReturn(List.of(v1, v2));
        var saved = new VideoInteraction(USER, v2.getId());
        saved.save(Instant.now());
        when(interactions.findByIdUserIdAndIdVideoIdIn(eq(USER), anyCollection())).thenReturn(List.of(saved));

        var slice = service.getCategoryFeed(USER, Category.SCIENCES, null, 10);

        // v1 et v2 ont des créateurs différents : l'ordre de la base est gardé.
        assertThat(slice.entries()).containsExactly(new FeedEntry(v2, false, true), new FeedEntry(v1, false, false));
        assertThat(slice.nextCursor()).isNull();
    }

    @Test
    void lesPagesSuivantesRepartentDeLaDerniereVideoLue() {
        var v1 = video();
        when(videos.findShuffledIds(eq("SCIENCES"), anyString(), eq(1))).thenReturn(List.of(v1.getId()));
        when(videos.findWithCreator(anyCollection(), any())).thenReturn(List.of(v1));

        var first = service.getCategoryFeed(USER, Category.SCIENCES, null, 1);

        var cursor = (com.maximegrau.brin.model.video.MixedFeedCursor) first.nextCursor();
        assertThat(cursor.lane(Category.SCIENCES).afterId()).isEqualTo(v1.getId());
        assertThat(cursor.lastCreatorId()).isEqualTo(v1.getCreator().getId());
    }

    private static Video video() {
        var creator = new Creator(VideoSource.YOUTUBE, UUID.randomUUID().toString(), "Créateur", "@createur", null, "UU1");
        ReflectionTestUtils.setField(creator, "id", UUID.randomUUID());
        var video = new Video(VideoSource.YOUTUBE, UUID.randomUUID().toString(), "Titre", null, creator, 47, Instant.now());
        ReflectionTestUtils.setField(video, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(video, "status", VideoStatus.ACCEPTED);
        ReflectionTestUtils.setField(video, "category", Category.SCIENCES);
        return video;
    }
}
