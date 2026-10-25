package com.maximegrau.brin.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.user.CategoryAffinityRepository;
import com.maximegrau.brin.model.user.VideoInteraction;
import com.maximegrau.brin.model.user.VideoInteractionId;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.service.feed.FeedProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InteractionServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final VideoRepository videos = mock(VideoRepository.class);
    private final VideoInteractionRepository interactions = mock(VideoInteractionRepository.class);
    private final CategoryAffinityRepository affinities = mock(CategoryAffinityRepository.class);
    private final InteractionService service = new InteractionService(videos, interactions, affinities,
            new FeedProperties(3.0, 1.0, 0.15, 0.6, 0.1, 3, 0.2, -2.0), Clock.fixed(NOW, ZoneOffset.UTC));

    private Video video;
    private VideoInteraction stored;

    @BeforeEach
    void setUp() {
        var creator = new Creator(VideoSource.YOUTUBE, "UC1", "Créateur", "@createur", null, "UU1");
        video = new Video(VideoSource.YOUTUBE, "yt1", "Titre", null, creator, 47, NOW);
        ReflectionTestUtils.setField(video, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(video, "category", Category.HISTOIRE);
        when(videos.findById(video.getId())).thenReturn(Optional.of(video));
        // La "base" : ce qui a été sauvegardé est relu à l'appel suivant.
        when(interactions.findById(any(VideoInteractionId.class))).thenAnswer(i -> Optional.ofNullable(stored));
        when(interactions.save(any())).thenAnswer(i -> stored = i.getArgument(0));
        doAnswer(i -> stored = null).when(interactions).delete(any());
    }

    @Test
    void jaiAppriseFaitMonterLaCategorieUneSeuleFois() {
        service.markLearned(USER, video.getId().toString());
        service.markLearned(USER, video.getId().toString());

        verify(affinities, times(1)).add(USER, Category.HISTOIRE, 0.6, -2.0, NOW);
    }

    @Test
    void decocherRetireCeQueJaiAppriseAvaitDonne() {
        service.markLearned(USER, video.getId().toString());
        service.unmarkLearned(USER, video.getId().toString());
        service.unmarkLearned(USER, video.getId().toString());

        verify(affinities).add(USER, Category.HISTOIRE, 0.6, -2.0, NOW);
        verify(affinities, times(1)).add(USER, Category.HISTOIRE, -0.6, -2.0, NOW);
        verify(interactions).delete(any());
    }

    @Test
    void seulesLesPremieresBouclesComptent() {
        for (int i = 0; i < 5; i++) {
            service.recordLoop(USER, video.getId().toString());
        }

        verify(affinities, times(3)).add(USER, Category.HISTOIRE, 0.1, -2.0, NOW);
    }

    @Test
    void unSwipeRapideFaitBaisserLaCategorieUneSeuleFoisParVideo() {
        service.recordSkip(USER, video.getId().toString());
        service.recordSkip(USER, video.getId().toString());

        verify(affinities, times(1)).add(USER, Category.HISTOIRE, -0.2, -2.0, NOW);
    }

    @Test
    void enregistrerNeChangePasLeFeed() {
        service.save(USER, video.getId().toString());

        verify(affinities, never()).add(any(), any(), anyDouble(), anyDouble(), any());
    }

    @Test
    void videoInconnue() {
        assertThatThrownBy(() -> service.markLearned(USER, "pas-un-uuid")).isInstanceOf(VideoNotFoundException.class);
        assertThatThrownBy(() -> service.recordLoop(USER, UUID.randomUUID().toString()))
                .isInstanceOf(VideoNotFoundException.class);
        verify(affinities, never()).add(eq(USER), any(), anyDouble(), anyDouble(), any());
    }
}
