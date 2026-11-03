package com.maximegrau.brin.service.auth.oauth;

/** Réponse inattendue d'un fournisseur OAuth (jeton ou identité absents). */
public class OAuthException extends RuntimeException {

    public OAuthException(String message) {
        super(message);
    }
}
