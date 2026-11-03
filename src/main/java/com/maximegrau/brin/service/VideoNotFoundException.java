package com.maximegrau.brin.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Rendue en 404 au format Problem Details. */
public class VideoNotFoundException extends ResponseStatusException {

    public VideoNotFoundException(String videoId) {
        super(HttpStatus.NOT_FOUND, "Vidéo introuvable : " + videoId);
    }
}
