package com.maximegrau.brin.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Rendue en 400 : connexion OAuth expirée ou lien forgé. */
public class InvalidOAuthStateException extends ResponseStatusException {

    public InvalidOAuthStateException() {
        super(HttpStatus.BAD_REQUEST, "Connexion expirée ou invalide, recommencez depuis l'app");
    }
}
