package com.maximegrau.brin.service.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Code erroné : rendue en 400, sans annuler la transaction (la tentative doit rester comptée). */
public class InvalidVerificationCodeException extends ResponseStatusException {

    public InvalidVerificationCodeException(int remainingAttempts) {
        super(HttpStatus.BAD_REQUEST, remainingAttempts > 0
                ? "Code incorrect (encore %d essai%s)".formatted(remainingAttempts, remainingAttempts > 1 ? "s" : "")
                : "Code incorrect : demandez un nouveau code");
    }
}
