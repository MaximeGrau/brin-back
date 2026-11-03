package com.maximegrau.brin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.Reliability;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.model.video.VideoStatus;
import com.maximegrau.brin.service.ai.MetadataClassification;
import com.maximegrau.brin.service.ai.UploadedVideoClassifier;
import com.maximegrau.brin.service.ai.MetadataClassifier;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ClassificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-21T20:00:00Z");

    private final VideoRepository videos = mock(VideoRepository.class);
    private final MetadataClassifier classifier = mock(MetadataClassifier.class);
    private final UploadedVideoClassifier uploadClassifier = mock(UploadedVideoClassifier.class);
    private final ClassificationService service = new ClassificationService(videos, List.of(classifier, uploadClassifier),
            new TransactionTemplate(mock(PlatformTransactionManager.class)),
            new ClassificationProperties(true, 10, 6.0, 3), Clock.fixed(NOW, ZoneOffset.UTC));

    @BeforeEach
    void setUp() {
        for (var c : List.of(classifier, uploadClassifier)) {
            when(c.supports(any())).thenCallRealMethod();
            when(c.version()).thenCallRealMethod();
        }
    }

    @Test
    void accepteUneVideoEducativeBienNotee() {
        var video = pending("Pourquoi le ciel est bleu");
        givenBatch(video);
        when(classifier.classify(video)).thenReturn(
                result(true, 8.26, Reliability.FIABLE, "La lumière bleue est plus diffusée par l'air."));

        assertThat(service.classifyPending()).isEqualTo(1);

        assertThat(video.getStatus()).isEqualTo(VideoStatus.ACCEPTED);
        assertThat(video.getScore()).isEqualByComparingTo(new BigDecimal("8.3"));
        assertThat(video.getCategory()).isEqualTo(Category.SCIENCES);
        assertThat(video.getKeyTakeaway()).isEqualTo("La lumière bleue est plus diffusée par l'air.");
        assertThat(video.getClassifierVersion()).isEqualTo(MetadataClassifier.VERSION);
        assertThat(video.getClassifiedAt()).isEqualTo(NOW);
    }

    @Test
    void rejetteSelonLaRegle() {
        assertThat(service.decide(result(false, 8, Reliability.FIABLE, ""))).startsWith("Non éducatif");
        assertThat(service.decide(result(true, 9, Reliability.DOUTEUX, "x"))).startsWith("Fiabilité douteuse");
        assertThat(service.decide(result(true, 5.9, Reliability.FIABLE, "x"))).startsWith("Score 5.9 < 6.0");
        assertThat(service.decide(result(true, 6.0, Reliability.A_VERIFIER, "x"))).isNull();
    }

    @Test
    void uneVideoRejeteeGardeSonScoreMaisPasDeARetenir() {
        var video = pending("Les coulisses d'un tournage");
        givenBatch(video);
        when(classifier.classify(video)).thenReturn(result(false, 3, Reliability.FIABLE, "Anecdote."));

        service.classifyPending();

        assertThat(video.getStatus()).isEqualTo(VideoStatus.REJECTED);
        assertThat(video.getScore()).isEqualByComparingTo("3.0");
        assertThat(video.getKeyTakeaway()).isNull();
        assertThat(video.getRejectionReason()).startsWith("Non éducatif");
    }

    @Test
    void erreurPassagereArreteLeLotSansPenaliserLaVideo() {
        var first = pending("Première");
        var second = pending("Seconde");
        givenBatch(first, second);
        when(classifier.classify(first)).thenThrow(new RuntimeException("Failed to generate content",
                new RuntimeException("503 . This model is currently experiencing high demand.")));

        assertThat(service.classifyPending()).isZero();

        assertThat(first.getStatus()).isEqualTo(VideoStatus.PENDING);
        assertThat(first.getClassificationAttempts()).isZero();
        verify(classifier, never()).classify(second);
    }

    @Test
    void erreurDefinitiveCompteLaTentativeEtPasseALaSuivante() {
        var first = pending("Première");
        var second = pending("Seconde");
        givenBatch(first, second);
        when(classifier.classify(first)).thenThrow(new RuntimeException("Failed to generate content",
                new RuntimeException("400 . Invalid argument")));
        when(classifier.classify(second)).thenReturn(result(true, 7, Reliability.FIABLE, "x"));

        assertThat(service.classifyPending()).isEqualTo(1);

        assertThat(first.getClassificationAttempts()).isEqualTo(1);
        assertThat(first.getLastClassificationError()).contains("400 . Invalid argument");
        assertThat(second.getStatus()).isEqualTo(VideoStatus.ACCEPTED);
    }

    @Test
    void uneVideoEnvoyeeEstRegardeeParLIA() {
        var upload = uploaded("Comment vole un avion");
        givenBatch(upload);
        when(uploadClassifier.classify(upload)).thenReturn(result(true, 8, Reliability.FIABLE, "La portance."));

        service.classifyPending();

        verify(classifier, never()).classify(upload);
        assertThat(upload.getStatus()).isEqualTo(VideoStatus.ACCEPTED);
        assertThat(upload.getClassifierVersion()).isEqualTo(UploadedVideoClassifier.VERSION);
    }

    @Test
    void uneVideoEnvoyeeQuOnNeParvientPasAAnalyserFinitRefuseePourNePasResterEnAnalyse() {
        var upload = uploaded("Vidéo bloquée");
        givenBatch(upload);
        when(uploadClassifier.classify(upload)).thenThrow(new RuntimeException("Failed to generate content",
                new RuntimeException("400 . Blocked by safety filters")));

        for (int i = 0; i < 3; i++) {
            service.classifyPending();
        }

        assertThat(upload.getStatus()).isEqualTo(VideoStatus.REJECTED);
        assertThat(upload.getRejectionReason()).contains("Analyse de la vidéo impossible");
    }

    private static Video uploaded(String title) {
        var creator = Creator.forUser(UUID.randomUUID(), "Léa");
        var video = Video.uploaded(creator, title, null, 42, "videos/x.mp4", null, "video/mp4", 1_000_000L,
                1080, 1920, NOW);
        ReflectionTestUtils.setField(video, "id", UUID.randomUUID());
        return video;
    }

    private void givenBatch(Video... batch) {
        when(videos.findToClassify(eq(VideoStatus.PENDING), eq(3), any())).thenReturn(List.of(batch));
        for (var video : batch) {
            when(videos.findById(video.getId())).thenReturn(Optional.of(video));
        }
    }

    private static MetadataClassification result(boolean educational, double score, Reliability reliability,
                                                 String keyTakeaway) {
        return new MetadataClassification(educational, Category.SCIENCES, score, reliability, keyTakeaway, "Raison.");
    }

    private static Video pending(String title) {
        var creator = new Creator(VideoSource.YOUTUBE, "UC1", "Créateur", "@createur", null, "UU1");
        var video = new Video(VideoSource.YOUTUBE, UUID.randomUUID().toString(), title, null, creator, 50, NOW);
        ReflectionTestUtils.setField(video, "id", UUID.randomUUID());
        return video;
    }
}
