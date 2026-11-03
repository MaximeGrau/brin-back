-- Créateurs, vidéos classées par l'IA et interactions utilisateur ("J'ai appris" / "Enregistrer").

create table creator
(
    id          uuid primary key,
    source      varchar(16)  not null,
    external_id varchar(128) not null,
    name        varchar(255) not null,
    handle      varchar(255),
    channel_url varchar(512),
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint uk_creator_source_external_id unique (source, external_id)
);

create table video
(
    id               uuid primary key,
    source           varchar(16)  not null,
    external_id      varchar(128) not null,
    title            varchar(255) not null,
    creator_id       uuid         not null references creator (id),
    duration_seconds integer check (duration_seconds >= 0),
    published_at     timestamptz,
    status           varchar(16)  not null,
    category         varchar(32),
    score            numeric(3, 1) check (score between 0 and 10),
    reliability      varchar(16),
    key_takeaway     varchar(255),
    classified_at    timestamptz,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    -- Cache de classification : une vidéo n'est importée et classée qu'une fois.
    constraint uk_video_source_external_id unique (source, external_id),
    -- Une vidéo du feed a forcément été classée.
    constraint ck_video_accepted_classified check (
        status <> 'ACCEPTED' or (category is not null and score is not null and reliability is not null))
);

create index idx_video_creator on video (creator_id);
-- Feed "Pour toi" et feed par catégorie (pagination par clé sur score desc, id desc).
create index idx_video_feed on video (status, score desc, id desc);
create index idx_video_feed_category on video (status, category, score desc, id desc);

-- user_id sans clé étrangère tant que la table des utilisateurs n'existe pas (authentification à venir).
create table video_interaction
(
    user_id    uuid    not null,
    video_id   uuid    not null references video (id) on delete cascade,
    learned    boolean not null default false,
    learned_at timestamptz,
    saved      boolean not null default false,
    saved_at   timestamptz,
    primary key (user_id, video_id)
);

create index idx_video_interaction_video on video_interaction (video_id);
