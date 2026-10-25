package com.maximegrau.brin.service;

import com.maximegrau.brin.model.user.CategoryAffinityRepository;
import com.maximegrau.brin.model.user.VideoInteraction;
import com.maximegrau.brin.model.user.VideoInteractionId;
import com.maximegrau.brin.model.user.VideoInteractionRepository;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.model.video.VideoIds;
import com.maximegrau.brin.model.video.VideoRepository;
import com.maximegrau.brin.service.feed.FeedProperties;
import java.time.Clock;
import java.util.UUID;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "J'ai appris", "Enregistrer" et boucles d'un utilisateur sur une vidéo.
 * "J'ai appris" et les boucles font monter l'affinité pour la catégorie de la vidéo, donc sa part dans le feed :
 * nettement pour "J'ai appris" (retiré si on décoche, pour qu'un aller-retour ne fasse rien gagner), un peu pour
 * chacune des premières boucles. Un swipe en moins d'une demi-seconde la fait baisser un peu (une fois par vidéo).
 */
@Service
@Transactional
public class InteractionService {

    private final VideoRepository videos;
    private final VideoInteractionRepository interactions;
    private final CategoryAffinityRepository affinities;
    private final FeedProperties properties;
    private final Clock clock;

    public InteractionService(VideoRepository videos, VideoInteractionRepository interactions,
                              CategoryAffinityRepository affinities, FeedProperties properties, Clock clock) {
        this.videos = videos;
        this.interactions = interactions;
        this.affinities = affinities;
        this.properties = properties;
        this.clock = clock;
    }

    public void markLearned(UUID userId, String videoId) {
        update(userId, videoId, (video, interaction) -> {
            if (!interaction.isLearned()) {
                interaction.markLearned(clock.instant());
                boost(userId, video, properties.learnedBoost());
            }
        });
    }

    public void unmarkLearned(UUID userId, String videoId) {
        update(userId, videoId, (video, interaction) -> {
            if (interaction.isLearned()) {
                interaction.unmarkLearned();
                boost(userId, video, -properties.learnedBoost());
            }
        });
    }

    public void save(UUID userId, String videoId) {
        update(userId, videoId, (video, interaction) -> interaction.save(clock.instant()));
    }

    public void unsave(UUID userId, String videoId) {
        update(userId, videoId, (video, interaction) -> interaction.unsave());
    }

    public void recordLoop(UUID userId, String videoId) {
        update(userId, videoId, (video, interaction) -> {
            if (interaction.countLoop() <= properties.maxCountedLoops()) {
                boost(userId, video, properties.loopBoost());
            }
        });
    }

    /** Vidéo passée presque aussitôt : la catégorie baisse un peu, une seule fois par vidéo. */
    public void recordSkip(UUID userId, String videoId) {
        update(userId, videoId, (video, interaction) -> {
            if (interaction.countSkip() == 1) {
                boost(userId, video, -properties.skipPenalty());
            }
        });
    }

    private void update(UUID userId, String videoId, BiConsumer<Video, VideoInteraction> change) {
        var video = VideoIds.parse(videoId).flatMap(videos::findById)
                .orElseThrow(() -> new VideoNotFoundException(videoId));
        var id = new VideoInteractionId(userId, video.getId());
        var interaction = interactions.findById(id).orElseGet(() -> new VideoInteraction(userId, video.getId()));
        change.accept(video, interaction);
        if (interaction.isEmpty()) {
            interactions.findById(id).ifPresent(interactions::delete);
        } else {
            interactions.save(interaction);
        }
    }

    private void boost(UUID userId, Video video, double delta) {
        if (video.getCategory() != null) {
            affinities.add(userId, video.getCategory(), delta, properties.minAffinity(), clock.instant());
        }
    }
}
