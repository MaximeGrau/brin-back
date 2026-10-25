-- Feed personnalisé : étape "catégories" de l'inscription, affinité apprise par catégorie, boucles par vidéo.

-- Choisir zéro catégorie est permis : on retient que l'étape a été faite.
alter table app_user
    add column categories_chosen_at timestamptz;

update app_user
set categories_chosen_at = now()
where id in (select user_id from user_preferred_category);

-- Affinité apprise de l'utilisateur pour une catégorie ("J'ai appris", boucles), ajoutée au poids de base
-- (catégorie choisie ou non) pour répartir le feed. Ligne absente = 0.
create table user_category_affinity
(
    user_id    uuid          not null references app_user (id) on delete cascade,
    category   varchar(32)   not null,
    score      numeric(8, 3) not null default 0 check (score >= 0),
    updated_at timestamptz   not null,
    primary key (user_id, category)
);

-- Nombre de fois où la vidéo a rebouclé pour l'utilisateur : seules les premières boucles comptent.
alter table video_interaction
    add column loop_count integer not null default 0 check (loop_count >= 0);
