package com.maximegrau.brin.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code brin.classification.*} (la fréquence est lue directement par {@link ClassificationScheduler}). */
@ConfigurationProperties("brin.classification")
public record ClassificationProperties(
        @DefaultValue("true") boolean enabled,
        /** Vidéos classées par passage (un appel IA chacune, en séquence). */
        @DefaultValue("10") int batchSize,
        /** Score minimal pour entrer dans le feed. */
        @DefaultValue("6.0") double minScore,
        /** Au-delà, une vidéo en échec n'est plus retentée (à reprendre à la main). */
        @DefaultValue("3") int maxAttempts) {
}
