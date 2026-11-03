package com.maximegrau.brin.service;

import com.maximegrau.brin.service.youtube.YouTubeProperties;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenchement de l'import. Ne contient aucune logique : tout est dans {@link VideoImportService}.
 *
 * <p>Les tâches {@code @Scheduled} tournent sur le thread unique du {@link TaskScheduler} de Spring Boot :
 * deux imports ne peuvent pas se chevaucher dans une même instance. Avec plusieurs instances,
 * il faudra un verrou partagé (ShedLock).
 */
@Component
public class VideoImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(VideoImportScheduler.class);

    private final VideoImportService importService;
    private final ImportProperties importProperties;
    private final YouTubeProperties youTubeProperties;
    private final TaskScheduler taskScheduler;

    public VideoImportScheduler(VideoImportService importService, ImportProperties importProperties,
                                YouTubeProperties youTubeProperties, TaskScheduler taskScheduler) {
        this.importService = importService;
        this.importProperties = importProperties;
        this.youTubeProperties = youTubeProperties;
        this.taskScheduler = taskScheduler;
    }

    /** Import quotidien ({@code brin.import.cron}, 4 h du matin par défaut). */
    @Scheduled(cron = "${brin.import.cron}", zone = "Europe/Paris")
    public void scheduledImport() {
        runImport();
    }

    /** Import au démarrage, poussé sur le thread du scheduler pour ne pas bloquer et ne pas chevaucher le cron. */
    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        if (importProperties.runOnStartup()) {
            taskScheduler.schedule(this::runImport, Instant.now());
        }
    }

    private void runImport() {
        if (!youTubeProperties.hasApiKey()) {
            log.warn("YOUTUBE_API_KEY absente : import YouTube ignoré");
            return;
        }
        log.info("Import YouTube : début");
        var reports = importService.importAll();
        log.info("Import YouTube : fin, {} créateur(s), {} vidéo(s) insérée(s)", reports.size(),
                reports.stream().mapToInt(CreatorImportReport::inserted).sum());
    }
}
