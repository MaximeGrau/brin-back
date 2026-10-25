package com.maximegrau.brin.service.feed;

import com.maximegrau.brin.model.video.Category;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Part de chaque catégorie dans le feed "Pour toi" d'un utilisateur.
 *
 * <pre>
 * poids(c) = max(0, (c choisie ? selectedWeight : baseWeight) + affinité(c))      affinité négative : swipes rapides
 * part(c)  = (1 - exploration) × poids(c) / Σ poids  +  exploration / nombre de catégories
 * </pre>
 *
 * Sans catégorie choisie ni affinité, toutes les parts sont égales. Le terme d'exploration garantit à chaque
 * catégorie une part minimale ({@code exploration / 8}), même quand l'utilisateur s'intéresse à une seule.
 */
public final class CategoryShares {

    private CategoryShares() {
    }

    /** @return une part par catégorie, la somme vaut 1 */
    public static Map<Category, Double> compute(Set<Category> preferred, Map<Category, Double> affinities,
                                                FeedProperties properties) {
        var weights = new EnumMap<Category, Double>(Category.class);
        for (var category : Category.values()) {
            var base = preferred.contains(category) ? properties.selectedWeight() : properties.baseWeight();
            weights.put(category, Math.max(0, base + affinities.getOrDefault(category, 0.0)));
        }
        if (weights.values().stream().allMatch(weight -> weight == 0)) {
            // Tout a été swipé : on repart à parts égales.
            weights.replaceAll((category, weight) -> 1.0);
        }
        var total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        var exploration = properties.exploration();
        var shares = new EnumMap<Category, Double>(Category.class);
        weights.forEach((category, weight) ->
                shares.put(category, (1 - exploration) * weight / total + exploration / weights.size()));
        return shares;
    }
}
