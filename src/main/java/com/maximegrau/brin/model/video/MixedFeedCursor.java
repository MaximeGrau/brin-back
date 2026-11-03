package com.maximegrau.brin.model.video;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Position dans le feed : graine de l'ordre aléatoire de la session ({@link ShuffleOrder}), créateur de la dernière
 * vidéo servie (pour ne pas enchaîner deux vidéos du même créateur d'une page à l'autre) et, par catégorie, la
 * dernière vidéo lue dans l'ordre mélangé, les vidéos mises de côté et l'épuisement.
 *
 * <p>Exposé en Base64 URL-safe de {@code graine|créateur|CATEGORIE,position,différées;…}
 * (position : {@code -} rien lu, {@code x} épuisée, sinon l'ID ; différées : IDs séparés par {@code +}).
 */
public record MixedFeedCursor(String seed, UUID lastCreatorId, Map<Category, Lane> lanes) implements PageCursor {

    /**
     * @param afterId   dernière vidéo lue dans l'ordre mélangé de la catégorie, nulle si aucune
     * @param deferred  vidéos lues mais mises de côté (même créateur que la précédente), à servir en priorité
     * @param exhausted plus aucune vidéo à servir dans cette catégorie
     */
    public record Lane(UUID afterId, List<UUID> deferred, boolean exhausted) {

        public static final Lane EMPTY = new Lane(null, List.of(), false);

        public Lane {
            deferred = List.copyOf(deferred);
        }
    }

    public MixedFeedCursor {
        lanes = lanes.isEmpty() ? Map.of() : new EnumMap<>(lanes);
    }

    public static MixedFeedCursor start(String seed) {
        return new MixedFeedCursor(seed, null, Map.of());
    }

    public Lane lane(Category category) {
        return lanes.getOrDefault(category, Lane.EMPTY);
    }

    @Override
    public String encode() {
        var joiner = new StringJoiner(";");
        lanes.forEach((category, lane) -> {
            var position = lane.exhausted() ? "x" : lane.afterId() == null ? "-" : lane.afterId().toString();
            var deferred = new StringJoiner("+");
            lane.deferred().forEach(id -> deferred.add(id.toString()));
            joiner.add(category.name() + "," + position + "," + deferred);
        });
        var raw = seed + "|" + (lastCreatorId == null ? "-" : lastCreatorId) + "|" + joiner;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws InvalidCursorException si le curseur n'a pas été produit par {@link #encode()} */
    public static MixedFeedCursor decode(String cursor) {
        try {
            var raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var parts = raw.split("\\|", -1);
            if (parts.length != 3 || !parts[0].matches("[0-9a-f]{1,32}")) {
                throw new InvalidCursorException(cursor);
            }
            var lanes = new EnumMap<Category, Lane>(Category.class);
            for (var entry : parts[2].isEmpty() ? new String[0] : parts[2].split(";")) {
                var fields = entry.split(",", -1);
                var deferred = new ArrayList<UUID>();
                for (var id : fields[2].isEmpty() ? new String[0] : fields[2].split("\\+")) {
                    deferred.add(UUID.fromString(id));
                }
                var lane = switch (fields[1]) {
                    case "x" -> new Lane(null, List.of(), true);
                    case "-" -> new Lane(null, deferred, false);
                    default -> new Lane(UUID.fromString(fields[1]), deferred, false);
                };
                lanes.put(Category.valueOf(fields[0]), lane);
            }
            return new MixedFeedCursor(parts[0], parts[1].equals("-") ? null : UUID.fromString(parts[1]), lanes);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new InvalidCursorException(cursor);
        }
    }
}
