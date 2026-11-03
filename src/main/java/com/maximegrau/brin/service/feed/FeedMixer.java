package com.maximegrau.brin.service.feed;

import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.MixedFeedCursor;
import com.maximegrau.brin.model.video.Video;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Compose une page du feed en mélangeant les catégories selon leurs parts, dans un ordre aléatoire, sans deux
 * vidéos du même créateur à la suite.
 *
 * <ul>
 *   <li>Catégorie de chaque emplacement : tirage au hasard pondéré par les parts (en moyenne, une catégorie à 25 %
 *   occupe un emplacement sur quatre).</li>
 *   <li>Dans une catégorie : ordre aléatoire de la session ({@link com.maximegrau.brin.model.video.ShuffleOrder}).</li>
 *   <li>Créateur : si la prochaine vidéo d'une catégorie a le même créateur que la précédente, elle est mise de côté
 *   (servie plus tard, en priorité) et on regarde un peu plus loin, puis dans les autres catégories. On ne
 *   répète un créateur que s'il n'existe aucune autre possibilité.</li>
 * </ul>
 * Une catégorie sans vidéo restante est retirée et sa part redistribuée.
 */
public final class FeedMixer {

    /** Vidéos mises de côté au plus par catégorie : au-delà, on accepte de répéter le créateur. */
    static final int MAX_DEFERRED_PER_LANE = 5;

    public interface LaneLoader {

        /** Vidéos de la catégorie dans l'ordre mélangé de {@code seed}, après {@code afterId} (nul = début). */
        List<Video> load(Category category, String seed, UUID afterId, int limit);

        /** Vidéos toujours ACCEPTED parmi ces IDs (ordre quelconque). */
        List<Video> loadByIds(Collection<UUID> ids);
    }

    public record Page(List<Video> videos, MixedFeedCursor nextCursor) {
    }

    private FeedMixer() {
    }

    public static Page mix(Map<Category, Double> shares, MixedFeedCursor cursor, int limit, LaneLoader loader,
                           RandomGenerator random) {
        var deferredIds = cursor.lanes().values().stream().flatMap(lane -> lane.deferred().stream()).toList();
        Map<UUID, Video> deferredById = deferredIds.isEmpty() ? Map.of()
                : loader.loadByIds(deferredIds).stream().collect(Collectors.toMap(Video::getId, Function.identity()));

        var lanes = new EnumMap<Category, Lane>(Category.class);
        for (var category : Category.values()) {
            var saved = cursor.lane(category);
            if (!saved.exhausted() && shares.getOrDefault(category, 0.0) > 0) {
                var lane = new Lane(category, cursor.seed(), saved.afterId(), loader, limit);
                saved.deferred().stream().map(deferredById::get).filter(Objects::nonNull).forEach(lane.deferred::add);
                lanes.put(category, lane);
            }
        }

        var active = EnumSet.noneOf(Category.class);
        active.addAll(lanes.keySet());
        var picked = new ArrayList<Video>(limit);
        var lastCreator = cursor.lastCreatorId();
        while (picked.size() < limit && !active.isEmpty()) {
            Video chosen = null;
            Lane fallback = null;
            for (var category : drawOrder(active, shares, random)) {
                var lane = lanes.get(category);
                chosen = lane.takeOtherThan(lastCreator);
                if (chosen != null) {
                    break;
                }
                if (lane.isEmpty()) {
                    active.remove(category);
                } else if (fallback == null) {
                    fallback = lane;   // n'a que ce créateur à proposer pour l'instant
                }
            }
            if (chosen == null && fallback != null) {
                chosen = fallback.takeAny();   // aucune autre possibilité : on répète le créateur
            }
            if (chosen != null) {
                picked.add(chosen);
                lastCreator = creatorId(chosen);
            }
        }

        var next = new EnumMap<Category, MixedFeedCursor.Lane>(Category.class);
        var remaining = false;
        for (var category : Category.values()) {
            var lane = lanes.get(category);
            if (lane == null) {
                if (cursor.lanes().containsKey(category)) {
                    next.put(category, cursor.lane(category));   // catégorie hors de ce feed : inchangée
                }
            } else if (lane.isEmpty()) {
                next.put(category, new MixedFeedCursor.Lane(null, List.of(), true));
            } else {
                remaining = true;
                next.put(category, new MixedFeedCursor.Lane(lane.afterId,
                        lane.deferred.stream().map(Video::getId).toList(), false));
            }
        }
        return new Page(picked, remaining ? new MixedFeedCursor(cursor.seed(), lastCreator, next) : null);
    }

    /** Catégories actives dans un ordre tiré au hasard, chacune avec une probabilité proportionnelle à sa part. */
    static List<Category> drawOrder(Collection<Category> active, Map<Category, Double> shares, RandomGenerator random) {
        var remaining = new ArrayList<>(active);
        var order = new ArrayList<Category>(remaining.size());
        while (!remaining.isEmpty()) {
            var total = remaining.stream().mapToDouble(shares::get).sum();
            var target = random.nextDouble() * total;
            var index = 0;
            for (double cumulated = shares.get(remaining.getFirst()); cumulated <= target && index < remaining.size() - 1; ) {
                cumulated += shares.get(remaining.get(++index));
            }
            order.add(remaining.remove(index));
        }
        return order;
    }

    private static UUID creatorId(Video video) {
        return video.getCreator().getId();
    }

    /** File d'une catégorie : vidéos mises de côté d'abord, puis la suite de l'ordre mélangé, chargée à la demande. */
    private static final class Lane {

        private final Category category;
        private final String seed;
        private final LaneLoader loader;
        private final int batchSize;
        private final List<Video> deferred = new ArrayList<>();
        private final ArrayDeque<Video> buffer = new ArrayDeque<>();
        /** Dernière vidéo sortie de l'ordre mélangé (servie ou mise de côté). */
        private UUID afterId;
        private boolean fullyLoaded;

        Lane(Category category, String seed, UUID afterId, LaneLoader loader, int batchSize) {
            this.category = category;
            this.seed = seed;
            this.afterId = afterId;
            this.loader = loader;
            this.batchSize = batchSize;
        }

        /** Prochaine vidéo d'un autre créateur que {@code creator} (nul = n'importe lequel), ou nul s'il n'y en a pas. */
        Video takeOtherThan(UUID creator) {
            for (var iterator = deferred.iterator(); iterator.hasNext(); ) {
                var video = iterator.next();
                if (creator == null || !creator.equals(creatorId(video))) {
                    iterator.remove();
                    return video;
                }
            }
            while (true) {
                var head = peek();
                if (head == null) {
                    return null;
                }
                var sameCreator = creator != null && creator.equals(creatorId(head));
                if (sameCreator && deferred.size() >= MAX_DEFERRED_PER_LANE) {
                    return null;
                }
                pull();
                if (!sameCreator) {
                    return head;
                }
                deferred.add(head);
            }
        }

        Video takeAny() {
            if (!deferred.isEmpty()) {
                return deferred.removeFirst();
            }
            var head = peek();
            if (head != null) {
                pull();
            }
            return head;
        }

        boolean isEmpty() {
            return deferred.isEmpty() && buffer.isEmpty() && fullyLoaded;
        }

        private Video peek() {
            if (buffer.isEmpty() && !fullyLoaded) {
                var loaded = loader.load(category, seed, afterId, batchSize);
                buffer.addAll(loaded);
                fullyLoaded = loaded.size() < batchSize;
            }
            return buffer.peek();
        }

        private void pull() {
            afterId = buffer.poll().getId();
        }
    }
}
