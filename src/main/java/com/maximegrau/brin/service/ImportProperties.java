package com.maximegrau.brin.service;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code brin.import.*} */
@ConfigurationProperties("brin.import")
public record ImportProperties(
        /** Expression cron Spring (6 champs, avec les secondes) ; "-" désactive l'import planifié. */
        @DefaultValue("0 0 4 * * *") String cron,
        /** Lance aussi un import juste après le démarrage (utile quand la base repart de zéro). */
        @DefaultValue("false") boolean runOnStartup,
        /** Handles YouTube des créateurs à importer (liste éditoriale). */
        @DefaultValue List<String> channels,
        /** Au-delà, la vidéo n'est pas un short. */
        @DefaultValue("180s") Duration maxDuration,
        /** Pages de 50 vidéos lues au plus par créateur et par import (limite le quota au premier import). */
        @DefaultValue("4") int maxPagesPerCreator,
        /** Préfixes de langue acceptés (ex. "fr") ; vide = toutes. Une vidéo sans langue déclarée est acceptée. */
        @DefaultValue List<String> languages) {
}
