package com.maximegrau.brin.model.video;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Rendue en 400 au format Problem Details. */
public class InvalidCursorException extends ResponseStatusException {

    public InvalidCursorException(String cursor) {
        super(HttpStatus.BAD_REQUEST, "Curseur invalide : " + cursor);
    }
}
