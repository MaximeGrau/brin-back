package com.maximegrau.brin.service.feed;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code brin.feed.*} : réglages de la répartition des catégories dans le feed "Pour toi".
 * Poids d'une catégorie = poids de base (choisie ou non) + affinité apprise ; voir {@link CategoryShares}.
 */
@ConfigurationProperties("brin.feed")
public record FeedProperties(
        /** Poids de base d'une catégorie choisie par l'utilisateur. */
        @DefaultValue("3.0") double selectedWeight,
        /** Poids de base d'une catégorie non choisie (ou de toutes si aucune n'est choisie). */
        @DefaultValue("1.0") double baseWeight,
        /** Part du feed répartie à parts égales entre toutes les catégories, quoi qu'il arrive (0 à 1). */
        @DefaultValue("0.15") double exploration,
        /** Affinité ajoutée à la catégorie d'une vidéo marquée "J'ai appris" (retirée si on décoche). */
        @DefaultValue("0.6") double learnedBoost,
        /** Affinité ajoutée à chaque boucle d'une vidéo (signal faible). */
        @DefaultValue("0.1") double loopBoost,
        /** Boucles prises en compte au plus par vidéo et par utilisateur (au-delà, plus d'effet). */
        @DefaultValue("3") int maxCountedLoops,
        /** Affinité retirée quand une vidéo est passée presque aussitôt (une fois par vidéo). */
        @DefaultValue("0.2") double skipPenalty,
        /**
         * Plancher de l'affinité. Le poids d'une catégorie ne descend pas sous 0 et la part d'exploration
         * lui garde de toute façon une place minimale.
         */
        @DefaultValue("-2.0") double minAffinity) {
}
