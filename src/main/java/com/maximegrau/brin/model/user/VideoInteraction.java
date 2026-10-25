package com.maximegrau.brin.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** "J'ai appris" / "Enregistrer" d'un utilisateur sur une vidéo (une ligne par couple utilisateur-vidéo). */
@Entity
@Table(name = "video_interaction")
public class VideoInteraction {

    @EmbeddedId
    private VideoInteractionId id;

    @Column(nullable = false)
    private boolean learned;

    @Column(name = "learned_at")
    private Instant learnedAt;

    @Column(nullable = false)
    private boolean saved;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Column(name = "loop_count", nullable = false)
    private int loopCount;

    @Column(name = "skip_count", nullable = false)
    private int skipCount;

    protected VideoInteraction() {
    }

    public VideoInteraction(UUID userId, UUID videoId) {
        this.id = new VideoInteractionId(userId, videoId);
    }

    public void markLearned(Instant now) {
        if (!learned) {
            learned = true;
            learnedAt = now;
        }
    }

    public void unmarkLearned() {
        learned = false;
        learnedAt = null;
    }

    public void save(Instant now) {
        if (!saved) {
            saved = true;
            savedAt = now;
        }
    }

    public void unsave() {
        saved = false;
        savedAt = null;
    }

    /** @return le nombre de boucles, celle-ci comprise */
    public int countLoop() {
        return ++loopCount;
    }

    /** @return le nombre de swipes rapides, celui-ci compris */
    public int countSkip() {
        return ++skipCount;
    }

    /** Plus aucune interaction : la ligne peut être supprimée (boucles et swipes comptés doivent être conservés). */
    public boolean isEmpty() {
        return !learned && !saved && loopCount == 0 && skipCount == 0;
    }

    public VideoInteractionId getId() {
        return id;
    }

    public UUID getVideoId() {
        return id.videoId();
    }

    public boolean isLearned() {
        return learned;
    }

    public Instant getLearnedAt() {
        return learnedAt;
    }

    public boolean isSaved() {
        return saved;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public int getSkipCount() {
        return skipCount;
    }
}
