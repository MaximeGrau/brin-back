package com.maximegrau.brin.service;

import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.CreatorRepository;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.service.youtube.YouTubeApi;
import com.maximegrau.brin.service.youtube.YouTubeClient;
import com.maximegrau.brin.service.youtube.YouTubeQuotaExceededException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/**
 * Alimente la base depuis YouTube, sans IA : vidéos récentes des créateurs suivis,
 * filtrées par {@link VideoFilter}, insérées en PENDING pour la classification.
 *
 * <p>Les appels HTTP se font hors transaction ; seule l'écriture du résultat d'un créateur
 * est transactionnelle (une erreur sur un créateur n'annule pas les autres).
 */
@Service
public class VideoImportService {

    private static final Logger log = LoggerFactory.getLogger(VideoImportService.class);

    private final YouTubeClient youTube;
    private final VideoFilter filter;
    private final CreatorRepository creators;
    private final VideoRepository videos;
    private final TransactionTemplate transaction;
    private final ImportProperties properties;
    private final Clock clock;

    public VideoImportService(YouTubeClient youTube, VideoFilter filter, CreatorRepository creators,
                              VideoRepository videos, TransactionTemplate transaction,
                              ImportProperties properties, Clock clock) {
        this.youTube = youTube;
        this.filter = filter;
        this.creators = creators;
        this.videos = videos;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /** Import complet : synchronise la liste des créateurs puis importe chacun d'eux. */
    public List<CreatorImportReport> importAll() {
        var startedAt = clock.instant();
        var reports = new ArrayList<CreatorImportReport>();
        try {
            syncConfiguredCreators();
            for (var creator : creators.findBySourceAndEnabledTrue(VideoSource.YOUTUBE)) {
                try {
                    reports.add(importCreator(creator, startedAt));
                } catch (RestClientException e) {
                    log.warn("Import de {} en échec, on passe au suivant", creator.getHandle(), e);
                }
            }
        } catch (YouTubeQuotaExceededException e) {
            log.warn("Quota YouTube épuisé : import interrompu, reprise au prochain passage");
        }
        reports.forEach(r -> log.info("Import {} : {} lues, {} déjà connues, {} écartées {}, {} insérées ({} unités)",
                r.handle(), r.scanned(), r.alreadyKnown(), r.rejected().values().stream().mapToLong(Long::longValue).sum(),
                r.rejected(), r.inserted(), r.quotaUnits()));
        return reports;
    }

    /** Crée en base les créateurs de {@code brin.import.channels} pas encore connus (1 unité chacun). */
    void syncConfiguredCreators() {
        for (var handle : properties.channels()) {
            if (creators.findBySourceAndHandleIgnoreCase(VideoSource.YOUTUBE, handle).isPresent()) {
                continue;
            }
            youTube.findChannelByHandle(handle).ifPresentOrElse(
                    channel -> {
                        creators.save(new Creator(VideoSource.YOUTUBE, channel.id(), channel.snippet().title(), handle,
                                "https://www.youtube.com/" + handle, channel.contentDetails().relatedPlaylists().uploads()));
                        log.info("Nouveau créateur suivi : {} ({})", handle, channel.snippet().title());
                    },
                    () -> log.warn("Aucune chaîne YouTube pour le handle {}", handle));
        }
    }

    CreatorImportReport importCreator(Creator creator, Instant startedAt) {
        var quotaUnits = 0;

        // 1. Vidéos publiées depuis le dernier import (la playlist est triée de la plus récente à la plus ancienne).
        var since = creator.getLastImportedAt();
        var ids = new LinkedHashSet<String>();
        String pageToken = null;
        var pages = 0;
        var reachedKnownPeriod = false;
        do {
            var page = youTube.listPlaylistItems(creator.getUploadsPlaylistId(), pageToken);
            quotaUnits++;
            pages++;
            for (var item : page.items()) {
                var publishedAt = item.contentDetails().videoPublishedAt();
                if (since != null && publishedAt != null && publishedAt.isBefore(since)) {
                    reachedKnownPeriod = true;
                    break;
                }
                ids.add(item.contentDetails().videoId());
            }
            pageToken = page.nextPageToken();
        } while (!reachedKnownPeriod && pageToken != null && pages < properties.maxPagesPerCreator());

        // 2. On ne redemande pas à YouTube les vidéos déjà en base.
        var known = ids.isEmpty() ? Set.<String>of() : videos.findExistingExternalIds(VideoSource.YOUTUBE, ids);
        var toFetch = ids.stream().filter(id -> !known.contains(id)).toList();

        // 3. Détail par lots de 50 puis filtrage.
        var accepted = new ArrayList<YouTubeApi.Video>();
        var rejected = new EnumMap<RejectionReason, Long>(RejectionReason.class);
        for (var batch : partition(toFetch, YouTubeClient.MAX_PAGE_SIZE)) {
            var details = youTube.getVideos(batch);
            quotaUnits++;
            for (var video : details) {
                filter.reject(video).ifPresentOrElse(
                        reason -> rejected.merge(reason, 1L, Long::sum),
                        () -> accepted.add(video));
            }
        }

        // 4. Écriture : vidéos en PENDING + date d'import du créateur, dans une seule transaction.
        transaction.executeWithoutResult(status -> {
            var managedCreator = creators.getReferenceById(creator.getId());
            videos.saveAll(accepted.stream().map(v -> toVideo(v, managedCreator)).toList());
            managedCreator.markImported(startedAt);
        });

        return new CreatorImportReport(creator.getHandle(), ids.size(), known.size(), Map.copyOf(rejected),
                accepted.size(), quotaUnits);
    }

    private static Video toVideo(YouTubeApi.Video video, Creator creator) {
        var duration = VideoFilter.duration(video).orElseThrow();
        return new Video(VideoSource.YOUTUBE, video.id(), video.snippet().title(), video.snippet().description(),
                creator, (int) duration.toSeconds(), video.snippet().publishedAt());
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        var chunks = new ArrayList<List<T>>();
        for (var i = 0; i < list.size(); i += size) {
            chunks.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return chunks;
    }
}
