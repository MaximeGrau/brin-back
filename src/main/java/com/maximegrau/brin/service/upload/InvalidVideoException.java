package com.maximegrau.brin.service.upload;

/** Fichier envoyé qui n'est pas une vidéo MP4 / MOV lisible. */
public class InvalidVideoException extends RuntimeException {

    public InvalidVideoException(String message) {
        super(message);
    }
}
