package com.maximegrau.brin.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Erreurs métier de l'authentification et du compte, rendues au format Problem Details. */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    public static ResponseStatusException emailAlreadyUsed() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Cette adresse e-mail est déjà utilisée");
    }

    public static ResponseStatusException badCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou mot de passe incorrect");
    }

    public static ResponseStatusException sessionExpired() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expirée, reconnectez-vous");
    }

    public static ResponseStatusException unknownUser() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte introuvable");
    }
}
