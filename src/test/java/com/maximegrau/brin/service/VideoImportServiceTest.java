package com.maximegrau.brin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.CreatorRepository;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.service.youtube.YouTubeApi;
import com.maximegrau.brin.service.youtube.YouTubeClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class VideoImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T02:00:00Z");

    private final YouTubeClient youTube = mock(YouTubeClient.class);
    private final CreatorRepository creators = mock(CreatorRepository.class);
    private final VideoRepository videos = mock(VideoRepository.class);
    private final ImportProperties properties =
            new ImportProperties("-", false, List.of(), Duration.ofSeconds(180), 4, List.of());
    private final VideoImportService service = new VideoImportService(youTube, new VideoFilter(properties),
            creators, videos, new TransactionTemplate(mock(PlatformTransactionManager.class)), properties,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void insereLesNouveauxShortsEtArreteLaPaginationALaPeriodeDejaImportee() {
        var creator = creator(Instant.parse("2026-10-10T00:00:00Z"));
        // Page 1 : deux vidéos récentes ; page 2 : une récente puis une antérieure au dernier import → arrêt.
        when(youTube.listPlaylistItems("UU1", null)).thenReturn(page("P2",
                item("court", "2026-10-20T10:00:00Z"), item("connu", "2026-10-19T10:00:00Z")));
        when(youTube.listPlaylistItems("UU1", "P2")).thenReturn(page("P3",
                item("long", "2026-10-15T10:00:00Z"), item("ancien", "2026-10-01T10:00:00Z")));
        when(videos.findExistingExternalIds(eq(VideoSource.YOUTUBE), anyCollection())).thenReturn(Set.of("connu"));
        when(youTube.getVideos(List.of("court", "long"))).thenReturn(List.of(
                VideoFilterTest.video("PT50S", "public", true, "none", "fr"),
                withId(VideoFilterTest.video("PT12M", "public", true, "none", "fr"), "long")));
        when(creators.getReferenceById(creator.getId())).thenReturn(creator);

        var report = service.importCreator(creator, NOW);

        assertThat(report.scanned()).isEqualTo(3);           // court, connu, long ("ancien" non lu)
        assertThat(report.alreadyKnown()).isEqualTo(1);
        assertThat(report.rejected()).containsEntry(RejectionReason.TOO_LONG, 1L);
        assertThat(report.inserted()).isEqualTo(1);
        assertThat(report.quotaUnits()).isEqualTo(3);        // 2 pages de playlist + 1 videos.list

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Video>> saved = ArgumentCaptor.forClass(List.class);
        verify(videos).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(v -> {
            assertThat(v.getDurationSeconds()).isEqualTo(50);
            assertThat(v.getCreator()).isSameAs(creator);
        });
        assertThat(creator.getLastImportedAt()).isEqualTo(NOW);
    }

    @Test
    void premierImportLimiteAuNombreDePagesConfigure() {
        var creator = creator(null);
        when(youTube.listPlaylistItems(eq("UU1"), any())).thenReturn(page("SUITE", item("v", "2026-10-20T10:00:00Z")));
        when(videos.findExistingExternalIds(any(), anyCollection())).thenReturn(Set.of("v"));
        when(creators.getReferenceById(creator.getId())).thenReturn(creator);

        var report = service.importCreator(creator, NOW);

        assertThat(report.quotaUnits()).isEqualTo(4);        // maxPagesPerCreator, aucune vidéo nouvelle à détailler
        verify(youTube, org.mockito.Mockito.never()).getVideos(any());
    }

    @Test
    void creeLesCreateursConfiguresInconnus() {
        var withChannels = new ImportProperties("-", false, List.of("@nouveau"), Duration.ofSeconds(180), 4, List.of());
        var syncing = new VideoImportService(youTube, new VideoFilter(withChannels), creators, videos,
                new TransactionTemplate(mock(PlatformTransactionManager.class)), withChannels, Clock.systemUTC());
        when(youTube.findChannelByHandle("@nouveau")).thenReturn(java.util.Optional.of(new YouTubeApi.Channel("UC9",
                new YouTubeApi.ChannelSnippet("Nouveau", "@nouveau"),
                new YouTubeApi.ChannelContentDetails(new YouTubeApi.RelatedPlaylists("UU9")))));

        syncing.syncConfiguredCreators();

        var created = ArgumentCaptor.forClass(Creator.class);
        verify(creators).save(created.capture());
        assertThat(created.getValue().getUploadsPlaylistId()).isEqualTo("UU9");
        assertThat(created.getValue().getExternalId()).isEqualTo("UC9");
    }

    private static Creator creator(Instant lastImportedAt) {
        var creator = new Creator(VideoSource.YOUTUBE, "UC1", "Créateur", "@createur", null, "UU1");
        ReflectionTestUtils.setField(creator, "id", UUID.randomUUID());
        if (lastImportedAt != null) {
            creator.markImported(lastImportedAt);
        }
        return creator;
    }

    private static YouTubeApi.PlaylistItemList page(String next, YouTubeApi.PlaylistItem... items) {
        return new YouTubeApi.PlaylistItemList(next, List.of(items));
    }

    private static YouTubeApi.PlaylistItem item(String id, String publishedAt) {
        return new YouTubeApi.PlaylistItem(new YouTubeApi.PlaylistItemContentDetails(id, Instant.parse(publishedAt)));
    }

    private static YouTubeApi.Video withId(YouTubeApi.Video video, String id) {
        return new YouTubeApi.Video(id, video.snippet(), video.contentDetails(), video.status());
    }
}
