package com.maximegrau.brin.service.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.Reliability;

/**
 * Réponse structurée attendue de l'IA. Spring AI en déduit un schéma JSON envoyé à Gemini
 * (sortie structurée native) ; les descriptions ci-dessous font partie de ce schéma.
 */
public record MetadataClassification(
        @JsonPropertyDescription("true si un spectateur apprend probablement quelque chose de concret")
        boolean educational,
        @JsonPropertyDescription("Catégorie la plus proche du sujet, même si la vidéo n'est pas éducative")
        Category category,
        @JsonPropertyDescription("Valeur culturelle de 0 à 10, une décimale, selon le barème")
        double score,
        @JsonPropertyDescription("Fiabilité probable du contenu")
        Reliability reliability,
        @JsonPropertyDescription("Ce qu'on retient, une phrase en français de 200 caractères au plus ; vide si non éducatif")
        String keyTakeaway,
        @JsonPropertyDescription("Justification de la décision en une phrase")
        String reason) {
}
