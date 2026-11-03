package com.maximegrau.brin.service.feed;

import static org.assertj.core.api.Assertions.assertThat;

import com.maximegrau.brin.model.video.Category;
import com.maximegrau.brin.model.video.Creator;
import com.maximegrau.brin.model.video.MixedFeedCursor;
import com.maximegrau.brin.model.video.ShuffleOrder;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoSource;
import com.maximegrau.brin.model.video.VideoStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FeedMixerTest {

    private static final FeedProperties PROPERTIES = new FeedProperties(3.0, 1.0, 0.15, 0.6, 0.1, 3, 0.2, -2.0);

    /** Catalogue simulé, rangé comme en base : les vidéos d'un même créateur à la suite. */
    private final Map<Category, List<Video>> catalog = new EnumMap<>(Category.class);
    private final Random random = new Random(42);
    /** Identifiants déterministes : l'ordre mélangé (md5 de la graine et de l'ID) est le même à chaque exécution. */
    private int nextId;
    private final FeedMixer.LaneLoader loader = new FakeLoader();

    @Test
    void laRepartitionSuitLesPartsEnMoyenne() {
        fillCatalog(100, 5);
        var shares = CategoryShares.compute(Set.of(Category.SCIENCES, Category.HISTOIRE), Map.of(), PROPERTIES);

        var served = readPages(shares, "a1", 40, 10);

        var counts = served.stream().collect(Collectors.groupingBy(Video::getCategory, Collectors.counting()));
        // 400 vidéos : ≈ 92 par catégorie choisie (23,1 %), ≈ 36 par catégorie non choisie (9 %).
        assertThat(counts.get(Category.SCIENCES)).isBetween(75L, 110L);
        assertThat(counts.get(Category.HISTOIRE)).isBetween(75L, 110L);
        assertThat(counts.get(Category.ARTS)).isBetween(24L, 50L);
        assertThat(counts.keySet()).containsExactlyInAnyOrder(Category.values());
    }

    @Test
    void jamaisDeuxVideosDuMemeCreateurALaSuiteQuandOnPeutLEviter() {
        fillCatalog(30, 3);

        var served = readPages(CategoryShares.compute(Set.of(), Map.of(), PROPERTIES), "b2", 30, 10);

        assertThat(served).hasSize(240);
        // Tout à la fin, il peut ne rester qu'un créateur : la répétition devient alors inévitable.
        for (int i = 1; i < 200; i++) {
            assertThat(creator(served.get(i))).as("position %d", i).isNotEqualTo(creator(served.get(i - 1)));
        }
    }

    @Test
    void uneSeuleCategorieDominéeParUnCreateurResteEspaceeTantQuePossible() {
        // Sciences : 12 vidéos de A puis 4 de B (rangées ainsi en base), feed limité à cette catégorie.
        var a = creatorEntity();
        var b = creatorEntity();
        var list = new ArrayList<Video>();
        for (int i = 0; i < 12; i++) list.add(video(Category.SCIENCES, a));
        for (int i = 0; i < 4; i++) list.add(video(Category.SCIENCES, b));
        catalog.put(Category.SCIENCES, list);

        var served = readPages(Map.of(Category.SCIENCES, 1.0), "c3", 10, 3);

        assertThat(new HashSet<>(served)).containsExactlyInAnyOrderElementsOf(list);   // rien de perdu, pas de doublon
        // Les 4 vidéos de B coupent les répétitions de A dès qu'elles sont accessibles.
        var firstEight = served.subList(0, 8).stream().map(this::creator).toList();
        assertThat(firstEight).filteredOn(b.getId()::equals).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void lOrdreEstAleatoireEtChangeAvecLaGraine() {
        fillCatalog(50, 5);
        var shares = Map.of(Category.ARTS, 1.0);

        var first = readPages(shares, "d4", 1, 20);
        var second = readPages(shares, "e5", 1, 20);

        assertThat(first).isNotEqualTo(catalog.get(Category.ARTS).subList(0, 20));   // pas l'ordre de la base
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aucuneVideoPerdueNiServieDeuxFoisSurTouteLaLecture() {
        fillCatalog(23, 4);

        var served = readPages(CategoryShares.compute(Set.of(Category.ARTS), Map.of(), PROPERTIES), "f6", 100, 7);

        assertThat(served).hasSize(8 * 23);
        assertThat(new HashSet<>(served)).hasSize(8 * 23);
    }

    @Test
    void uneCategorieVideLaisseSaPlaceEtLaFinRenvoieUnCurseurNul() {
        fillCatalog(2, 2);
        catalog.put(Category.LANGUES, List.of());
        var shares = CategoryShares.compute(Set.of(Category.LANGUES), Map.of(), PROPERTIES);

        var page = FeedMixer.mix(shares, MixedFeedCursor.start("a7"), 50, loader, random);

        assertThat(page.videos()).hasSize(14).noneMatch(v -> v.getCategory() == Category.LANGUES);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void leCreateurDeLaDernierePageEstEviteEnDebutDePageSuivante() {
        fillCatalog(20, 2);
        var shares = CategoryShares.compute(Set.of(), Map.of(), PROPERTIES);
        var first = FeedMixer.mix(shares, MixedFeedCursor.start("b8"), 5, loader, random);

        var second = FeedMixer.mix(shares, MixedFeedCursor.decode(first.nextCursor().encode()), 5, loader, random);

        assertThat(creator(second.videos().getFirst())).isNotEqualTo(creator(first.videos().getLast()));
    }

    private List<Video> readPages(Map<Category, Double> shares, String seed, int pages, int pageSize) {
        var served = new ArrayList<Video>();
        var cursor = MixedFeedCursor.start(seed);
        for (int i = 0; i < pages && cursor != null; i++) {
            var page = FeedMixer.mix(shares, MixedFeedCursor.decode(cursor.encode()), pageSize, loader, random);
            served.addAll(page.videos());
            cursor = page.nextCursor();
        }
        return served;
    }

    /** {@code perCategory} vidéos par catégorie, réparties entre {@code creators} créateurs, à la suite par créateur. */
    private void fillCatalog(int perCategory, int creators) {
        for (var category : Category.values()) {
            var list = new ArrayList<Video>();
            for (int c = 0; c < creators; c++) {
                var creator = creatorEntity();
                for (int i = c * perCategory / creators; i < (c + 1) * perCategory / creators; i++) {
                    list.add(video(category, creator));
                }
            }
            catalog.put(category, list);
        }
    }

    private UUID deterministicId() {
        return UUID.nameUUIDFromBytes(("id-" + nextId++).getBytes(StandardCharsets.UTF_8));
    }

    private UUID creator(Video video) {
        return video.getCreator().getId();
    }

    private Creator creatorEntity() {
        var creator = new Creator(VideoSource.YOUTUBE, UUID.randomUUID().toString(), "Créateur", "@c", null, "UU");
        ReflectionTestUtils.setField(creator, "id", deterministicId());
        return creator;
    }

    private Video video(Category category, Creator creator) {
        var video = new Video(VideoSource.YOUTUBE, UUID.randomUUID().toString(), "Titre", null, creator, 47, Instant.now());
        ReflectionTestUtils.setField(video, "id", deterministicId());
        ReflectionTestUtils.setField(video, "status", VideoStatus.ACCEPTED);
        ReflectionTestUtils.setField(video, "category", category);
        ReflectionTestUtils.setField(video, "score", new BigDecimal("8.0"));
        return video;
    }

    /** Reproduit l'ordre SQL : md5(graine || id), puis id. */
    private final class FakeLoader implements FeedMixer.LaneLoader {

        @Override
        public List<Video> load(Category category, String seed, UUID afterId, int limit) {
            Comparator<Video> order = Comparator.comparing((Video v) -> ShuffleOrder.key(seed, v.getId()))
                    .thenComparing(Video::getId);
            var sorted = catalog.getOrDefault(category, List.of()).stream().sorted(order).toList();
            var start = 0;
            if (afterId != null) {
                var after = sorted.stream().filter(v -> v.getId().equals(afterId)).findFirst().orElseThrow();
                start = sorted.indexOf(after) + 1;
            }
            return sorted.subList(start, Math.min(sorted.size(), start + limit));
        }

        @Override
        public List<Video> loadByIds(Collection<UUID> ids) {
            return catalog.values().stream().flatMap(List::stream).filter(v -> ids.contains(v.getId())).toList();
        }
    }
}
