-- Classification IA (étape 1, métadonnées) : raison du rejet, version du prompt, suivi des échecs.

alter table video
    add column rejection_reason          varchar(500),
    add column classifier_version        varchar(32),
    add column classification_attempts   integer not null default 0,
    add column last_classification_error varchar(1000);

-- File d'attente de la classification (VideoRepository.findToClassify).
create index idx_video_to_classify on video (created_at, id) where status = 'PENDING';
