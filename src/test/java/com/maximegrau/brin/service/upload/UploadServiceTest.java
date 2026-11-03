package com.maximegrau.brin.service.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.CreatorRepository;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.service.VideoNotFoundException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;
import org.springframework.web.server.ResponseStatusException;

class UploadServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UploadProperties PROPERTIES = new UploadProperties(Path.of("unused"), DataSize.ofMegabytes(100),
            Duration.ofSeconds(3), Duration.ofSeconds(180), 1.25, 360, List.of("video/mp4", "video/quicktime"),
            DataSize.ofMegabytes(2), 5, Duration.ofHours(6));

    private final UserRepository users = mock(UserRepository.class);
    private final CreatorRepository creators = mock(CreatorRepository.class);
    private final VideoRepository videos = mock(VideoRepository.class);
    private final VideoStorage storage = mock(VideoStorage.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final UploadValidator validator = new UploadValidator(PROPERTIES);
    private final UploadService service = new UploadService(users, creators, videos,
            mock(VideoInteractionRepository.class), storage, validator, transaction, PROPERTIES,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private User user;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        user = new User("lea@example.com", "hash", "Léa");
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.markEmailVerified(NOW);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(transaction.execute(any())).thenAnswer(i ->
                i.<TransactionCallback<Object>>getArgument(0).doInTransaction(mock(TransactionStatus.class)));
    }

    @Test
    void lesCriteresDUnShort() {
        assertThatCode(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(45, 1080, 1920))).doesNotThrowAnyException();
        assertThatCode(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(180.3, 1080, 1350))).doesNotThrowAnyException();

        assertThatThrownBy(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(45, 1920, 1080)))
                .hasMessageContaining("verticale");
        assertThatThrownBy(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(45, 1080, 1080)))
                .hasMessageContaining("verticale");
        assertThatThrownBy(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(200, 1080, 1920)))
                .hasMessageContaining("3 min 00 au plus").hasMessageContaining("3 min 20");
        assertThatThrownBy(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(1.5, 1080, 1920)))
                .hasMessageContaining("trop courte");
        assertThatThrownBy(() -> validator.checkRules(new Mp4Inspector.VideoFileInfo(30, 240, 426)))
                .hasMessageContaining("Résolution");
    }

    @Test
    void ilFautUneAdresseVerifieePourPublier() {
        ReflectionTestUtils.setField(user, "emailVerifiedAt", null);

        assertThatThrownBy(() -> service.submit(user.getId(), video("a.mp4", "video/mp4"), null, "Titre", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void tropDeVideosEnAnalyseEnMemeTemps() {
        when(videos.countPendingUploads(user.getId())).thenReturn(5L);

        assertThatThrownBy(() -> service.submit(user.getId(), video("a.mp4", "video/mp4"), null, "Titre", null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void refuseUnFormatQuiNEstPasUneVideoEtNeStockeRien() {
        assertThatThrownBy(() -> service.submit(user.getId(), video("photo.jpg", "image/jpeg"), null, "Titre", null))
                .hasMessageContaining("MP4 ou MOV");
        assertThatThrownBy(() -> service.submit(user.getId(), video("faux.mp4", "video/mp4"), null, "Titre", null))
                .hasMessageContaining("illisible");
        verify(storage, never()).store(any(), any());
    }

    @Test
    void seulLAuteurPeutSupprimerSaVideo() {
        var video = Video.uploaded(Creator.forUser(user.getId(), "Léa"), "Titre", null, 30, "videos/a.mp4",
                "thumbnails/a.jpg", "video/mp4", 10L, 1080, 1920, NOW);
        ReflectionTestUtils.setField(video, "id", UUID.randomUUID());
        when(videos.findById(video.getId())).thenReturn(Optional.of(video));

        assertThatThrownBy(() -> service.delete(UUID.randomUUID(), video.getId().toString()))
                .isInstanceOf(VideoNotFoundException.class);
        verify(storage, never()).delete(any());

        service.delete(user.getId(), video.getId().toString());

        verify(videos).delete(video);
        verify(storage).delete("videos/a.mp4");
        verify(storage).delete("thumbnails/a.jpg");
    }

    private static MockMultipartFile video(String name, String type) {
        return new MockMultipartFile("file", name, type, "pas vraiment une vidéo".getBytes());
    }
}
