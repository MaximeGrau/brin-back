-- Vidéos envoyées directement sur Brin (source BRIN) : fichier stocké par Brin, analysé par l'IA avant publication.

-- Tout utilisateur qui publie devient créateur (un seul par compte).
alter table creator
    add column user_id uuid references app_user (id) on delete cascade;

create unique index uk_creator_user on creator (user_id) where user_id is not null;

alter table video
    add column file_key      varchar(255),
    add column thumbnail_key varchar(255),
    add column content_type  varchar(64),
    add column file_size     bigint check (file_size > 0),
    add column width         integer check (width > 0),
    add column height        integer check (height > 0),
    -- Une vidéo envoyée a forcément son fichier.
    add constraint ck_video_upload_file check (source <> 'BRIN' or file_key is not null);

-- Tableau de bord du créateur : ses vidéos, les plus récentes d'abord.
create index idx_video_creator_created on video (creator_id, created_at desc);
