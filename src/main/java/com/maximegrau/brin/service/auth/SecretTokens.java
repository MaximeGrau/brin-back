package com.maximegrau.brin.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Génération et hachage des secrets aléatoires (jetons de rafraîchissement, tickets, codes de validation). */
final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecretTokens() {
    }

    /** 256 bits aléatoires en base64url. */
    static String generate() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Code à 6 chiffres, zéros de tête compris. */
    static String sixDigitCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    /** SHA-256 en hexadécimal : seule forme stockée en base. */
    static String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparaison en temps constant de deux hash. */
    static boolean matches(String value, String expectedHash) {
        return MessageDigest.isEqual(sha256(value).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
