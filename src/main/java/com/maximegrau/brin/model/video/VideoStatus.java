package com.maximegrau.brin.model.video;

/** Résultat de la classification IA : seules les vidéos ACCEPTED apparaissent dans le feed. */
public enum VideoStatus {
    /** Importée, pas encore classée. */
    PENDING,
    ACCEPTED,
    REJECTED
}
