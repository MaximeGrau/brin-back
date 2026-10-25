package com.maximegrau.brin.model.user;

import java.util.UUID;

/** Nombre d'utilisateurs ayant marqué une vidéo "J'ai appris" et l'ayant enregistrée. */
public record InteractionCounts(UUID videoId, long learned, long saved) {
}
