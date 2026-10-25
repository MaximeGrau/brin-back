package com.maximegrau.brin.model.user;

import com.maximegrau.brin.model.video.Category;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Affinités par catégorie, en SQL direct : l'incrément est un upsert atomique (pas de lecture puis écriture,
 * deux "J'ai appris" simultanés s'additionnent correctement).
 */
@Repository
public class CategoryAffinityRepository {

    private final JdbcClient jdbc;

    public CategoryAffinityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Catégories sans ligne absentes de la map (affinité 0). */
    public Map<Category, Double> findByUser(UUID userId) {
        var affinities = new EnumMap<Category, Double>(Category.class);
        jdbc.sql("select category, score from user_category_affinity where user_id = :userId")
                .param("userId", userId)
                .query((rs, rowNum) -> new CategoryAffinity(Category.valueOf(rs.getString("category")), rs.getDouble("score")))
                .list()
                .forEach(a -> affinities.put(a.category(), a.score()));
        return affinities;
    }

    /** Ajoute {@code delta} (négatif pour retirer) à l'affinité, sans descendre sous {@code floor}. */
    public void add(UUID userId, Category category, double delta, double floor, Instant now) {
        jdbc.sql("""
                        insert into user_category_affinity (user_id, category, score, updated_at)
                        values (:userId, :category, greatest(:floor, :delta), :now)
                        on conflict (user_id, category)
                        do update set score = greatest(:floor, user_category_affinity.score + :delta), updated_at = :now
                        """)
                .param("userId", userId)
                .param("category", category.name())
                .param("delta", delta)
                .param("floor", floor)
                .param("now", Timestamp.from(now))
                .update();
    }
}
