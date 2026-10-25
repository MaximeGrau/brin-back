package com.maximegrau.brin.service;

import com.maximegrau.brin.model.user.CategoryAffinityRepository;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.model.user.VideoInteraction;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.FeedEntry;
import com.maximegrau.brin.model.video.FeedSlice;
import com.maximegrau.brin.model.video.MixedFeedCursor;
import com.maximegrau.brin.model.video.ShuffleOrder;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.model.video.VideoStatus;
import com.maximegrau.brin.service.auth.AuthExceptions;
import com.maximegrau.brin.service.feed.CategoryShares;
import com.maximegrau.brin.service.feed.FeedMixer;
import com.maximegrau.brin.service.feed.FeedProperties;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lecture du feed : vidéos ACCEPTED, dans un ordre aléatoire propre à chaque session (nouvel ordre à chaque
 * ouverture du feed), sans deux vidéos du même créateur à la suite ({@link FeedMixer}).
 * Uniquement des données déjà en base, jamais d'appel IA ici.
 * <ul>
 *   <li>"Pour toi" : catégories mélangées selon les parts de l'utilisateur ({@link CategoryShares}) ;</li>
 *   <li>une catégorie : uniquement celle-ci.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class FeedService {

    private final VideoRepository videos;
    private final VideoInteractionRepository interactions;
    private final UserRepository users;
    private final CategoryAffinityRepository affinities;
    private final FeedProperties properties;

    public FeedService(VideoRepository videos, VideoInteractionRepository interactions, UserRepository users,
                       CategoryAffinityRepository affinities, FeedProperties properties) {
        this.videos = videos;
        this.interactions = interactions;
        this.users = users;
        this.affinities = affinities;
        this.properties = properties;
    }

    /** @param after nul = première page (nouvel ordre aléatoire), sinon le curseur renvoyé par la page précédente */
    public FeedSlice getPersonalFeed(UUID userId, MixedFeedCursor after, int limit) {
        return mix(userId, categoryShares(userId), after, limit);
    }

    /** @param after nul = première page (nouvel ordre aléatoire), sinon le curseur renvoyé par la page précédente */
    public FeedSlice getCategoryFeed(UUID userId, Category category, MixedFeedCursor after, int limit) {
        return mix(userId, Map.of(category, 1.0), after, limit);
    }

    /** Part de chaque catégorie dans le feed "Pour toi" de l'utilisateur. */
    private Map<Category, Double> categoryShares(UUID userId) {
        var user = users.findById(userId).orElseThrow(AuthExceptions::unknownUser);
        return CategoryShares.compute(user.getPreferredCategories(), affinities.findByUser(userId), properties);
    }

    private FeedSlice mix(UUID userId, Map<Category, Double> shares, MixedFeedCursor after, int limit) {
        var random = ThreadLocalRandom.current();
        var cursor = after != null ? after : MixedFeedCursor.start(ShuffleOrder.newSeed(random));
        var page = FeedMixer.mix(shares, cursor, limit, new RepositoryLaneLoader(), random);
        return new FeedSlice(withInteractions(userId, page.videos()), page.nextCursor());
    }

    private final class RepositoryLaneLoader implements FeedMixer.LaneLoader {

        @Override
        public List<Video> load(Category category, String seed, UUID afterId, int limit) {
            var ids = afterId == null
                    ? videos.findShuffledIds(category.name(), seed, limit)
                    : videos.findShuffledIdsAfter(category.name(), seed, ShuffleOrder.key(seed, afterId), afterId, limit);
            var byId = loadByIds(ids).stream().collect(Collectors.toMap(Video::getId, Function.identity()));
            return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
        }

        @Override
        public List<Video> loadByIds(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : videos.findWithCreator(ids, VideoStatus.ACCEPTED);
        }
    }

    /** "J'ai appris" / "Enregistrer" de l'utilisateur pour les vidéos de la page, en une requête. */
    private List<FeedEntry> withInteractions(UUID userId, List<Video> page) {
        Map<UUID, VideoInteraction> byVideo = page.isEmpty() ? Map.of()
                : interactions.findByIdUserIdAndIdVideoIdIn(userId, page.stream().map(Video::getId).toList()).stream()
                        .collect(Collectors.toMap(VideoInteraction::getVideoId, Function.identity()));
        return page.stream()
                .map(video -> {
                    var interaction = byVideo.get(video.getId());
                    return new FeedEntry(video,
                            interaction != null && interaction.isLearned(),
                            interaction != null && interaction.isSaved());
                })
                .toList();
    }
}
