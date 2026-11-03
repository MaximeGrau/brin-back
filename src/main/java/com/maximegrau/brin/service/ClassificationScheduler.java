package com.maximegrau.brin.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Classification en tâche de fond : toutes les minutes (après la fin du passage précédent),
 * un lot de vidéos PENDING est envoyé à l'IA. Partage le thread unique du scheduler avec l'import :
 * les deux ne tournent jamais en même temps.
 */
@Component
public class ClassificationScheduler {

    private final ClassificationService service;
    private final ClassificationProperties properties;

    public ClassificationScheduler(ClassificationService service, ClassificationProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(initialDelayString = "${brin.classification.initial-delay:30s}",
               fixedDelayString = "${brin.classification.fixed-delay:60s}")
    public void classifyPending() {
        if (properties.enabled()) {
            service.classifyPending();
        }
    }
}
