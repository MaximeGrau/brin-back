-- Import YouTube : playlist "uploads" et suivi de l'import par créateur, description des vidéos.

alter table creator
    add column uploads_playlist_id varchar(64),
    add column enabled             boolean not null default true,
    add column last_imported_at    timestamptz;

create unique index uk_creator_source_handle on creator (source, lower(handle));

alter table video
    add column description varchar(5000);
