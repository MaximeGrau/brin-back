package com.maximegrau.brin.service;

/** Raison pour laquelle une vidéo n'est pas insérée en base (filtre avant IA). */
public enum RejectionReason {
    NOT_PUBLIC,
    NOT_EMBEDDABLE,
    LIVE,
    UNKNOWN_DURATION,
    TOO_LONG,
    LANGUAGE
}
