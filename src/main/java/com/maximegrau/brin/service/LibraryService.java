package com.maximegrau.brin.service;

import com.maximegrau.brin.model.user.LibraryCursor;
import com.maximegrau.brin.model.user.VideoInteraction;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.FeedEntry;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bibliothèque (shorts enregistrés) et compteurs du profil. */
@Service
@Transactional(readOnly = true)
public class LibraryService {

    /** Short enregistré, avec la date d'enregistrement. */
    public record SavedEntry(FeedEntry entry, Instant savedAt) {
    }

    /** @param nextCursor nul s'il n'y a pas de page suivante */
    public record SavedSlice(List<SavedEntry> entries, LibraryCursor nextCursor) {
    }

    public record UserStats(long learned, long saved, long published) {
    }

    private final VideoInteractionRepository interactions;
    private final VideoRepository videos;

    public LibraryService(VideoInteractionRepository interactions, VideoRepository videos) {
        this.interactions = interactions;
        this.videos = videos;
    }

    /** @param after nul = première page, sinon le curseur renvoyé par la page précédente */
    public SavedSlice getSaved(UUID userId, LibraryCursor after, int limit) {
        var found = after == null
                ? interactions.findSaved(userId, Limit.of(limit + 1))
                : interactions.findSavedAfter(userId, after.savedAt(), after.videoId(), Limit.of(limit + 1));
        var hasNext = found.size() > limit;
        var page = hasNext ? found.subList(0, limit) : found;
        // Une vidéo devenue indisponible (refusée après coup) disparaît de la liste sans casser la pagination.
        Map<UUID, Video> byId = page.isEmpty() ? Map.of()
                : videos.findWithCreator(page.stream().map(VideoInteraction::getVideoId).toList(), VideoStatus.ACCEPTED)
                        .stream().collect(Collectors.toMap(Video::getId, Function.identity()));
        var entries = page.stream()
                .filter(i -> byId.containsKey(i.getVideoId()))
                .map(i -> new SavedEntry(new FeedEntry(byId.get(i.getVideoId()), i.isLearned(), true), i.getSavedAt()))
                .toList();
        var last = page.isEmpty() ? null : page.getLast();
        return new SavedSlice(entries, hasNext ? new LibraryCursor(last.getSavedAt(), last.getVideoId()) : null);
    }

    public UserStats stats(UUID userId) {
        return new UserStats(interactions.countByIdUserIdAndLearnedTrue(userId),
                interactions.countByIdUserIdAndSavedTrue(userId),
                videos.countPublishedUploads(userId));
    }
}
