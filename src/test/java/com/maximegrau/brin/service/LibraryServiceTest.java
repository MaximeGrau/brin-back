package com.maximegrau.brin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.user.LibraryCursor;
import com.maximegrau.brin.model.user.VideoInteraction;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.model.video.VideoStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.util.ReflectionTestUtils;

class LibraryServiceTest {

    private static final UUID USER = UUID.randomUUID();

    private final VideoInteractionRepository interactions = mock(VideoInteractionRepository.class);
    private final VideoRepository videos = mock(VideoRepository.class);
    private final LibraryService service = new LibraryService(interactions, videos);

    @Test
    void renvoieLesEnregistrementsDuPlusRecentAuPlusAncienAvecUnCurseur() {
        var v1 = video();
        var v2 = video();
        var v3 = video();
        var s1 = saved(v1, "2026-10-22T10:00:00.123456Z", true);
        var s2 = saved(v2, "2026-10-21T10:00:00Z", false);
        var s3 = saved(v3, "2026-10-20T10:00:00Z", false);
        when(interactions.findSaved(USER, Limit.of(3))).thenReturn(List.of(s1, s2, s3));
        when(videos.findWithCreator(anyCollection(), eq(VideoStatus.ACCEPTED))).thenReturn(List.of(v2, v1));

        var slice = service.getSaved(USER, null, 2);

        assertThat(slice.entries()).extracting(e -> e.entry().video()).containsExactly(v1, v2);
        assertThat(slice.entries().getFirst().entry().learned()).isTrue();
        assertThat(slice.entries()).allSatisfy(e -> assertThat(e.entry().saved()).isTrue());
        // La date garde ses microsecondes après un aller-retour chez le client.
        assertThat(LibraryCursor.decode(slice.nextCursor().encode()))
                .isEqualTo(new LibraryCursor(Instant.parse("2026-10-21T10:00:00Z"), v2.getId()));
    }

    @Test
    void uneVideoDevenueIndisponibleDisparaitSansCasserLaPagination() {
        var v1 = video();
        var gone = video();
        when(interactions.findSaved(eq(USER), any()))
                .thenReturn(List.of(saved(v1, "2026-10-22T10:00:00Z", false), saved(gone, "2026-10-21T10:00:00Z", false)));
        when(videos.findWithCreator(anyCollection(), eq(VideoStatus.ACCEPTED))).thenReturn(List.of(v1));

        var slice = service.getSaved(USER, null, 10);

        assertThat(slice.entries()).extracting(e -> e.entry().video()).containsExactly(v1);
        assertThat(slice.nextCursor()).isNull();
    }

    private static VideoInteraction saved(Video video, String savedAt, boolean learned) {
        var interaction = new VideoInteraction(USER, video.getId());
        interaction.save(Instant.parse(savedAt));
        if (learned) {
            interaction.markLearned(Instant.parse(savedAt));
        }
        return interaction;
    }

    private static Video video() {
        var creator = new Creator(VideoSource.YOUTUBE, "UC1", "Créateur", "@createur", null, "UU1");
        var video = new Video(VideoSource.YOUTUBE, UUID.randomUUID().toString(), "Titre", null, creator, 47, Instant.now());
        ReflectionTestUtils.setField(video, "id", UUID.randomUUID());
        return video;
    }
}
