package com.maximegrau.brin.service.youtube;

/** Quota journalier de la YouTube Data API épuisé (remis à zéro à minuit, heure du Pacifique). */
public class YouTubeQuotaExceededException extends RuntimeException {

    public YouTubeQuotaExceededException(String message) {
        super(message);
    }
}
