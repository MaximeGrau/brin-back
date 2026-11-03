package com.maximegrau.brin.service.ai;

import com.maximegrau.brin.model.video.Video;
import java.util.Objects;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;

/**
 * Classe une vidéo grâce à l'IA. Une implémentation par type de vidéo : {@code ClassificationService} prend la
 * première qui l'accepte ({@link #supports}). Appel bloquant au modèle : uniquement en tâche de fond.
 */
public interface VideoClassifier {

    boolean supports(Video video);

    /** Version du prompt, stockée avec la classification : à incrémenter à chaque modification du prompt. */
    String version();

    MetadataClassification classify(Video video);

    /** Options communes : décisions stables d'un appel à l'autre, peu de raisonnement (moins de tokens). */
    static GoogleGenAiChatOptions.Builder options() {
        return GoogleGenAiChatOptions.builder()
                .temperature(0.2)
                .thinkingLevel(GoogleGenAiThinkingLevel.LOW);
    }

    /** Description envoyée au modèle : tronquée à 1 500 caractères, "(aucune)" si vide. */
    static String promptDescription(Video video) {
        var description = Objects.requireNonNullElse(video.getDescription(), "").strip();
        if (description.length() > 1500) {
            description = description.substring(0, 1500) + "…";
        }
        return description.isEmpty() ? "(aucune)" : description;
    }
}
