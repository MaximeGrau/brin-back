package com.maximegrau.brin.model.video;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VideoRepository extends JpaRepository<Video, UUID> {

    /**
     * IDs des vidéos ACCEPTED d'une catégorie dans l'ordre aléatoire de la graine ({@link ShuffleOrder}),
     * depuis le début de cet ordre.
     */
    @Query(value = """
            select v.id from video v
            where v.status = 'ACCEPTED' and v.category = :category
            order by md5(:seed || cast(v.id as text)), v.id
            limit :limit
            """, nativeQuery = true)
    List<UUID> findShuffledIds(@Param("category") String category, @Param("seed") String seed,
                               @Param("limit") int limit);

    /** Suite de {@link #findShuffledIds} : les vidéos situées strictement après {@code (afterKey, afterId)}. */
    @Query(value = """
            select v.id from video v
            where v.status = 'ACCEPTED' and v.category = :category
              and (md5(:seed || cast(v.id as text)), v.id) > (:afterKey, :afterId)
            order by md5(:seed || cast(v.id as text)), v.id
            limit :limit
            """, nativeQuery = true)
    List<UUID> findShuffledIdsAfter(@Param("category") String category, @Param("seed") String seed,
                                    @Param("afterKey") String afterKey, @Param("afterId") UUID afterId,
                                    @Param("limit") int limit);

    /** Vidéos ACCEPTED parmi ces IDs, créateur chargé dans la même requête (ordre quelconque). */
    @Query("select v from Video v join fetch v.creator where v.id in :ids and v.status = :status")
    List<Video> findWithCreator(@Param("ids") Collection<UUID> ids, @Param("status") VideoStatus status);

    /** Vidéos envoyées sur Brin par un utilisateur (tous statuts), les plus récentes d'abord. */
    @Query("""
            select v from Video v join fetch v.creator c
            where c.userId = :userId and v.source = com.maximegrau.brin.model.video.VideoSource.BRIN
            order by v.createdAt desc
            """)
    List<Video> findUploadsByUser(@Param("userId") UUID userId);

    @Query("""
            select count(v) from Video v
            where v.creator.userId = :userId and v.status = com.maximegrau.brin.model.video.VideoStatus.PENDING
            """)
    long countPendingUploads(@Param("userId") UUID userId);

    @Query("""
            select count(v) from Video v
            where v.creator.userId = :userId and v.status = com.maximegrau.brin.model.video.VideoStatus.ACCEPTED
            """)
    long countPublishedUploads(@Param("userId") UUID userId);

    /** Parmi {@code externalIds}, ceux déjà en base : ils ne sont ni redemandés à YouTube ni reclassés. */
    @Query("select v.externalId from Video v where v.source = :source and v.externalId in :externalIds")
    Set<String> findExistingExternalIds(@Param("source") VideoSource source,
                                        @Param("externalIds") Collection<String> externalIds);

    /**
     * Prochaines vidéos à classer : d'abord celles envoyées sur Brin (leur auteur attend sur son tableau de bord),
     * puis les plus anciennes ; celles qui ont trop échoué sont ignorées.
     */
    @Query("""
            select v from Video v join fetch v.creator
            where v.status = :status and v.classificationAttempts < :maxAttempts
            order by case when v.source = com.maximegrau.brin.model.video.VideoSource.BRIN then 0 else 1 end,
                     v.createdAt, v.id
            """)
    List<Video> findToClassify(@Param("status") VideoStatus status,
                               @Param("maxAttempts") int maxAttempts,
                               Limit limit);
}
