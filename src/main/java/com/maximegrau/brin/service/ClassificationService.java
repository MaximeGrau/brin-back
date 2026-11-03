package com.maximegrau.brin.service;

import com.maximegrau.brin.model.video.Reliability;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoStatus;
import com.maximegrau.brin.service.ai.MetadataClassification;
import com.maximegrau.brin.service.ai.VideoClassifier;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Passe les vidéos PENDING en ACCEPTED ou REJECTED grâce à l'IA (étape 1 : métadonnées).
 *
 * <p>Chaque appel IA se fait hors transaction ; seule l'écriture du résultat d'une vidéo est transactionnelle.
 * Une erreur sur une vidéo est comptée et la vidéo sera retentée au passage suivant.
 */
@Service
public class ClassificationService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationService.class);
    private static final int MAX_KEY_TAKEAWAY_LENGTH = 255;
    private static final int MAX_REASON_LENGTH = 500;

    private final VideoRepository videos;
    private final List<VideoClassifier> classifiers;
    private final TransactionTemplate transaction;
    private final ClassificationProperties properties;
    private final Clock clock;

    public ClassificationService(VideoRepository videos, List<VideoClassifier> classifiers,
                                 TransactionTemplate transaction, ClassificationProperties properties, Clock clock) {
        this.videos = videos;
        this.classifiers = classifiers;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return le nombre de vidéos classées (acceptées ou rejetées) pendant ce passage */
    public int classifyPending() {
        var batch = videos.findToClassify(VideoStatus.PENDING, properties.maxAttempts(),
                Limit.of(properties.batchSize()));
        int accepted = 0, rejected = 0, failed = 0;
        for (var video : batch) {
            try {
                // Vidéo envoyée sur Brin : l'IA la regarde (modération comprise) ; YouTube : métadonnées seules.
                var classifier = classifierFor(video);
                var result = classifier.classify(video); // hors transaction
                if (save(video, result, classifier.version()) == VideoStatus.ACCEPTED) {
                    accepted++;
                } else {
                    rejected++;
                }
            } catch (RuntimeException e) {
                failed++;
                var error = describe(e);
                log.warn("Classification de {} ({}) en échec : {}", video.getExternalId(), video.getTitle(), error);
                log.debug("Détail de l'échec", e);
                if (isTransient(e)) {
                    // Limite de débit ou modèle surchargé : la vidéo n'y est pour rien, on ne compte pas l'échec.
                    log.warn("API Gemini indisponible pour l'instant : reprise au prochain passage");
                    break;
                }
                transaction.executeWithoutResult(status -> videos.findById(video.getId()).ifPresent(v -> {
                    v.classificationFailed(truncate(error, 1000));
                    // Une vidéo envoyée ne reste pas "en analyse" indéfiniment sur le tableau de bord de son auteur
                    // (souvent : vidéo bloquée par les filtres de sécurité de Gemini).
                    if (v.isUpload() && v.getClassificationAttempts() >= properties.maxAttempts()) {
                        v.reject("Analyse de la vidéo impossible : elle ne peut pas être publiée", null, null, null,
                                classifierFor(v).version(), clock.instant());
                    }
                }));
            }
        }
        if (!batch.isEmpty()) {
            log.info("Classification : {} acceptée(s), {} rejetée(s), {} en échec", accepted, rejected, failed);
        }
        return accepted + rejected;
    }

    private VideoClassifier classifierFor(Video video) {
        return classifiers.stream().filter(c -> c.supports(video)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun classifieur pour une vidéo " + video.getSource()));
    }

    private VideoStatus save(Video video, MetadataClassification result, String version) {
        var decision = decide(result);
        transaction.executeWithoutResult(status -> videos.findById(video.getId()).ifPresent(v -> {
            var score = BigDecimal.valueOf(Math.clamp(result.score(), 0, 10)).setScale(1, RoundingMode.HALF_UP);
            if (decision == null) {
                v.accept(result.category(), score, result.reliability(),
                        truncate(blankToNull(result.keyTakeaway()), MAX_KEY_TAKEAWAY_LENGTH), version, clock.instant());
            } else {
                v.reject(truncate(decision, MAX_REASON_LENGTH), result.category(), score, result.reliability(),
                        version, clock.instant());
            }
        }));
        log.debug("{} « {} » → {} ({}) : {}", video.getExternalId(), video.getTitle(),
                decision == null ? "ACCEPTED" : "REJECTED", result.score(), result.reason());
        return decision == null ? VideoStatus.ACCEPTED : VideoStatus.REJECTED;
    }

    /** @return null si la vidéo est retenue, sinon la raison du rejet */
    String decide(MetadataClassification result) {
        if (!result.educational()) {
            return "Non éducatif : " + result.reason();
        }
        if (result.category() == null || result.reliability() == null) {
            return "Classification incomplète : " + result.reason();
        }
        if (result.reliability() == Reliability.DOUTEUX) {
            return "Fiabilité douteuse : " + result.reason();
        }
        if (result.score() < properties.minScore()) {
            return "Score " + result.score() + " < " + properties.minScore() + " : " + result.reason();
        }
        return null;
    }

    /** Message de l'exception suivi de celui de sa cause racine (souvent la réponse de l'API). */
    private static String describe(Throwable e) {
        var root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root == e ? String.valueOf(e.getMessage()) : e.getMessage() + " ← " + root.getClass().getSimpleName() + " : " + root.getMessage();
    }

    /** 429 (limite de débit) ou 503 (modèle surchargé) : erreurs passagères côté API. */
    private static boolean isTransient(Throwable e) {
        for (var t = e; t != null; t = t.getCause()) {
            var message = t.getMessage();
            if (message != null && (message.startsWith("429") || message.startsWith("503")
                    || message.contains("RESOURCE_EXHAUSTED") || message.contains("UNAVAILABLE"))) {
                return true;
            }
        }
        return false;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
