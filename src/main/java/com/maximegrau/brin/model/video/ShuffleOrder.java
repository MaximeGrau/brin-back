package com.maximegrau.brin.model.video;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * Ordre aléatoire mais stable d'une session de feed : les vidéos sont triées sur {@code md5(graine || id)}.
 * Une nouvelle graine à chaque ouverture du feed donne un nouvel ordre ; la même graine (gardée dans le curseur)
 * permet de paginer sans doublon. Doit rester identique à l'expression SQL de {@link VideoRepository}.
 */
public final class ShuffleOrder {

    private ShuffleOrder() {
    }

    public static String newSeed(RandomGenerator random) {
        return HexFormat.of().toHexDigits(random.nextLong());
    }

    /** Équivalent Java de {@code md5(:seed || cast(v.id as text))} en PostgreSQL. */
    public static String key(String seed, UUID videoId) {
        try {
            var digest = MessageDigest.getInstance("MD5").digest((seed + videoId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
