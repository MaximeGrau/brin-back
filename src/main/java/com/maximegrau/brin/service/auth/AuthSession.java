package com.maximegrau.brin.service.auth;

/** Jetons remis à l'app à la connexion ou au rafraîchissement. */
public record AuthSession(String accessToken, String refreshToken, long expiresInSeconds, UserProfile user) {
}
