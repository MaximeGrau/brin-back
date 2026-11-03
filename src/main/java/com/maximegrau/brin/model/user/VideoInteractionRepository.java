package com.maximegrau.brin.model.user;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoInteractionRepository extends JpaRepository<VideoInteraction, VideoInteractionId> {

    List<VideoInteraction> findByIdUserIdAndIdVideoIdIn(UUID userId, Collection<UUID> videoIds);

    long countByIdUserIdAndLearnedTrue(UUID userId);

    long countByIdUserIdAndSavedTrue(UUID userId);

    /** Vidéos enregistrées par l'utilisateur, les plus récentes d'abord (première page). */
    @Query("""
            select i from VideoInteraction i
            where i.id.userId = :userId and i.saved = true
            order by i.savedAt desc, i.id.videoId desc
            """)
    List<VideoInteraction> findSaved(@Param("userId") UUID userId, Limit limit);

    /**
     * Suite de {@link #findSaved} en pagination par clé sur {@code (savedAt desc, videoId desc)} : celles situées
     * strictement après {@code (afterSavedAt, afterVideoId)}. Requête séparée : un paramètre date nul
     * ({@code :x is null}) n'a pas de type pour PostgreSQL.
     */
    @Query("""
            select i from VideoInteraction i
            where i.id.userId = :userId and i.saved = true
              and (i.savedAt < :afterSavedAt or (i.savedAt = :afterSavedAt and i.id.videoId < :afterVideoId))
            order by i.savedAt desc, i.id.videoId desc
            """)
    List<VideoInteraction> findSavedAfter(@Param("userId") UUID userId, @Param("afterSavedAt") Instant afterSavedAt,
                                          @Param("afterVideoId") UUID afterVideoId, Limit limit);

    /** "J'ai appris" et "Enregistrer" reçus par chaque vidéo (tableau de bord du créateur). */
    @Query("""
            select new com.maximegrau.brin.model.user.InteractionCounts(i.id.videoId,
                   sum(case when i.learned = true then 1 else 0 end), sum(case when i.saved = true then 1 else 0 end))
            from VideoInteraction i
            where i.id.videoId in :videoIds
            group by i.id.videoId
            """)
    List<InteractionCounts> countByVideoIdIn(@Param("videoIds") Collection<UUID> videoIds);
}
