package com.maximegrau.brin.model.user;

import com.maximegrau.brin.model.video.Category;

/** Affinité apprise d'un utilisateur pour une catégorie (table {@code user_category_affinity}). */
public record CategoryAffinity(Category category, double score) {
}
