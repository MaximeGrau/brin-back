-- Swipe rapide (vidéo passée en moins d'une demi-seconde) : signal négatif pour la catégorie de la vidéo.

-- L'affinité peut désormais descendre sous 0 ; le plancher est appliqué par l'application (brin.feed.min-affinity).
alter table user_category_affinity
    drop constraint user_category_affinity_score_check;

-- Nombre de swipes rapides de l'utilisateur sur la vidéo : seul le premier compte.
alter table video_interaction
    add column skip_count integer not null default 0 check (skip_count >= 0);
