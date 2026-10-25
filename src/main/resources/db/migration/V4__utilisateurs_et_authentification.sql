-- Comptes utilisateurs : e-mail + mot de passe et/ou fournisseurs externes (Google, TikTok, Facebook),
-- validation de l'adresse par code, jetons de rafraîchissement, catégories préférées.

create table app_user
(
    id                uuid primary key,
    -- Absente après une connexion TikTok (pas d'adresse fournie) tant qu'elle n'est pas saisie.
    email             varchar(320),
    email_verified_at timestamptz,
    password_hash     varchar(100),
    display_name      varchar(100),
    created_at        timestamptz not null,
    updated_at        timestamptz not null
);

create unique index uk_app_user_email on app_user (lower(email));

create table user_identity
(
    id         uuid primary key,
    user_id    uuid         not null references app_user (id) on delete cascade,
    provider   varchar(16)  not null,
    -- Identifiant de l'utilisateur chez le fournisseur ("sub" Google, "open_id" TikTok, ID Facebook).
    subject    varchar(255) not null,
    created_at timestamptz  not null,
    constraint uk_user_identity_provider_subject unique (provider, subject)
);

create index idx_user_identity_user on user_identity (user_id);

create table user_preferred_category
(
    user_id  uuid        not null references app_user (id) on delete cascade,
    category varchar(32) not null,
    primary key (user_id, category)
);

-- Un seul code en cours par utilisateur ; seul le hash SHA-256 du code est stocké.
create table email_verification
(
    user_id    uuid primary key references app_user (id) on delete cascade,
    email      varchar(320) not null,
    code_hash  varchar(64)  not null,
    expires_at timestamptz  not null,
    attempts   integer      not null default 0,
    sent_at    timestamptz  not null
);

-- Jetons de rafraîchissement (hash SHA-256), à usage unique : remplacés à chaque rafraîchissement.
create table refresh_token
(
    id         uuid primary key,
    user_id    uuid        not null references app_user (id) on delete cascade,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    constraint uk_refresh_token_hash unique (token_hash)
);

create index idx_refresh_token_user on refresh_token (user_id);

-- Ticket à usage unique et de courte durée remis à l'app à la fin d'une connexion OAuth (dans le lien profond),
-- échangé ensuite contre des jetons : les jetons eux-mêmes ne transitent jamais par une URL.
create table login_ticket
(
    ticket_hash varchar(64) primary key,
    user_id     uuid        not null references app_user (id) on delete cascade,
    expires_at  timestamptz not null
);

-- Les interactions créées avec l'utilisateur fixe de dev n'ont plus de propriétaire.
delete from video_interaction where user_id not in (select id from app_user);

alter table video_interaction
    add constraint fk_video_interaction_user foreign key (user_id) references app_user (id) on delete cascade;
