package com.maximegrau.brin.model.video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Vidéo importée, avec le résultat de sa classification IA (cache par {@code source + externalId}).
 * Les champs de classification sont nuls tant que la vidéo est {@link VideoStatus#PENDING}.
 */
@Entity
@Table(name = "video")
public class Video {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoSource source;

    /** Identifiant sur la plateforme d'origine (ID YouTube pour le lecteur intégré). */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String title;

    /** Description d'origine (5 000 caractères max sur YouTube) : matière première de la classification IA. */
    @Column(length = 5000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private Creator creator;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoStatus status = VideoStatus.PENDING;

    @Enumerated(EnumType.STRING)
    private Category category;

    /** Score culture 0–10, une décimale. */
    @Column(precision = 3, scale = 1)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    private Reliability reliability;

    /** La phrase "À retenir". */
    @Column(name = "key_takeaway")
    private String keyTakeaway;

    @Column(name = "classified_at")
    private Instant classifiedAt;

    /** Pourquoi l'IA n'a pas retenu la vidéo (REJECTED). */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    /** Version du prompt qui a produit la classification : permet de reclasser après un changement de prompt. */
    @Column(name = "classifier_version", length = 32)
    private String classifierVersion;

    /** Appels IA en échec : au-delà d'un seuil, la vidéo n'est plus retentée. */
    @Column(name = "classification_attempts", nullable = false)
    private int classificationAttempts;

    @Column(name = "last_classification_error", length = 1000)
    private String lastClassificationError;

    /** Vidéo envoyée sur Brin : clé du fichier dans le stockage ({@code VideoStorage}). */
    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "thumbnail_key")
    private String thumbnailKey;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    /** Dimensions à l'affichage (rotation appliquée). */
    private Integer width;

    private Integer height;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Video() {
    }

    public Video(VideoSource source, String externalId, String title, String description, Creator creator,
                 Integer durationSeconds, Instant publishedAt) {
        this.source = source;
        this.externalId = externalId;
        this.title = title;
        this.description = description;
        this.creator = creator;
        this.durationSeconds = durationSeconds;
        this.publishedAt = publishedAt;
    }

    /** Vidéo envoyée sur Brin par un utilisateur, en attente d'analyse IA. */
    public static Video uploaded(Creator creator, String title, String description, int durationSeconds,
                                 String fileKey, String thumbnailKey, String contentType, long fileSize,
                                 int width, int height, Instant now) {
        var video = new Video(VideoSource.BRIN, UUID.randomUUID().toString(), title, description, creator,
                durationSeconds, now);
        video.fileKey = fileKey;
        video.thumbnailKey = thumbnailKey;
        video.contentType = contentType;
        video.fileSize = fileSize;
        video.width = width;
        video.height = height;
        return video;
    }

    public boolean isUpload() {
        return source == VideoSource.BRIN;
    }

    /** Retenue par l'IA : la vidéo entre dans le feed. */
    public void accept(Category category, BigDecimal score, Reliability reliability, String keyTakeaway,
                       String classifierVersion, Instant now) {
        applyClassification(VideoStatus.ACCEPTED, category, score, reliability, classifierVersion, now);
        this.keyTakeaway = keyTakeaway;
        this.rejectionReason = null;
    }

    /** Écartée par l'IA : conservée en base (cache, calibration), jamais affichée. */
    public void reject(String reason, Category category, BigDecimal score, Reliability reliability,
                       String classifierVersion, Instant now) {
        applyClassification(VideoStatus.REJECTED, category, score, reliability, classifierVersion, now);
        this.keyTakeaway = null;
        this.rejectionReason = reason;
    }

    public void classificationFailed(String error) {
        this.classificationAttempts++;
        this.lastClassificationError = error;
    }

    private void applyClassification(VideoStatus status, Category category, BigDecimal score, Reliability reliability,
                                     String classifierVersion, Instant now) {
        this.status = status;
        this.category = category;
        this.score = score;
        this.reliability = reliability;
        this.classifierVersion = classifierVersion;
        this.classifiedAt = now;
        this.lastClassificationError = null;
    }

    public String getFileKey() {
        return fileKey;
    }

    public String getThumbnailKey() {
        return thumbnailKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public UUID getId() {
        return id;
    }

    public VideoSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Creator getCreator() {
        return creator;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public VideoStatus getStatus() {
        return status;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getScore() {
        return score;
    }

    public Reliability getReliability() {
        return reliability;
    }

    public String getKeyTakeaway() {
        return keyTakeaway;
    }

    public Instant getClassifiedAt() {
        return classifiedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getClassifierVersion() {
        return classifierVersion;
    }

    public int getClassificationAttempts() {
        return classificationAttempts;
    }

    public String getLastClassificationError() {
        return lastClassificationError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
