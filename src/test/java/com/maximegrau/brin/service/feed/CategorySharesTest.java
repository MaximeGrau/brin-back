package com.maximegrau.brin.service.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.maximegrau.brin.model.video.Category;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CategorySharesTest {

    private static final FeedProperties PROPERTIES = new FeedProperties(3.0, 1.0, 0.15, 0.6, 0.1, 3, 0.2, -2.0);

    @Test
    void sansPreferenceToutesLesCategoriesOntLaMemePart() {
        var shares = CategoryShares.compute(Set.of(), Map.of(), PROPERTIES);

        assertThat(shares.values()).allSatisfy(share -> assertThat(share).isCloseTo(1.0 / 8, within(1e-9)));
    }

    @Test
    void lesCategoriesChoisiesPassentDevantSansEffacerLesAutres() {
        var shares = CategoryShares.compute(Set.of(Category.SCIENCES, Category.HISTOIRE), Map.of(), PROPERTIES);

        // Poids 3+3+6×1 = 12 → choisie : 0,85×3/12 + 0,15/8 ; non choisie : 0,85×1/12 + 0,15/8
        assertThat(shares.get(Category.SCIENCES)).isCloseTo(0.23125, within(1e-9));
        assertThat(shares.get(Category.ARTS)).isCloseTo(0.0895833, within(1e-6));
        assertThat(shares.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void laffiniteFaitMonterUneCategorieNonChoisie() {
        var before = CategoryShares.compute(Set.of(Category.SCIENCES), Map.of(), PROPERTIES);
        var after = CategoryShares.compute(Set.of(Category.SCIENCES), Map.of(Category.ARTS, 1.2), PROPERTIES);

        assertThat(after.get(Category.ARTS)).isGreaterThan(before.get(Category.ARTS));
        assertThat(after.get(Category.SCIENCES)).isLessThan(before.get(Category.SCIENCES));
    }

    @Test
    void lesSwipesRapidesFontBaisserUneCategorieSansLaFaireDisparaitre() {
        var before = CategoryShares.compute(Set.of(), Map.of(), PROPERTIES);
        var after = CategoryShares.compute(Set.of(), Map.of(Category.ECONOMIE, -2.0), PROPERTIES);

        assertThat(after.get(Category.ECONOMIE)).isLessThan(before.get(Category.ECONOMIE))
                .isCloseTo(0.15 / 8, within(1e-9));   // poids 0 : il ne reste que la part d'exploration
        assertThat(after.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void toutSwiperRameneAPartsEgales() {
        var all = new java.util.EnumMap<Category, Double>(Category.class);
        for (var category : Category.values()) {
            all.put(category, -2.0);
        }

        var shares = CategoryShares.compute(Set.of(), all, PROPERTIES);

        assertThat(shares.values()).allSatisfy(share -> assertThat(share).isCloseTo(1.0 / 8, within(1e-9)));
    }

    @Test
    void chaqueCategorieGardeUnePartMinimale() {
        var shares = CategoryShares.compute(Set.of(Category.SCIENCES), Map.of(Category.SCIENCES, 1_000.0), PROPERTIES);

        assertThat(shares.get(Category.LANGUES)).isGreaterThanOrEqualTo(0.15 / 8);
    }
}
