package com.maximegrau.brin.model.video;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Créateur (chaîne YouTube, compte Instagram/TikTok) d'une ou plusieurs vidéos. */
@Entity
@Table(name = "creator")
public class Creator {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VideoSource source;

    /** Identifiant sur la plateforme d'origine (ID de chaîne YouTube). */
    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    private String name;

    private String handle;

    @Column(name = "channel_url")
    private String channelUrl;

    /** Playlist "uploads" de la chaîne (UU…) : toutes ses vidéos, lisibles à 1 unité de quota / 50 vidéos. */
    @Column(name = "uploads_playlist_id")
    private String uploadsPlaylistId;

    /** Un créateur désactivé n'est plus importé (ses vidéos déjà en base restent). */
    @Column(nullable = false)
    private boolean enabled = true;

    /** Utilisateur Brin derrière ce créateur (vidéos envoyées sur Brin), nul pour une chaîne YouTube. */
    @Column(name = "user_id")
    private UUID userId;

    /** Début du dernier import réussi : l'import suivant s'arrête aux vidéos publiées avant. */
    @Column(name = "last_imported_at")
    private Instant lastImportedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Creator() {
    }

    public Creator(VideoSource source, String externalId, String name, String handle, String channelUrl,
                   String uploadsPlaylistId) {
        this.source = source;
        this.externalId = externalId;
        this.name = name;
        this.handle = handle;
        this.channelUrl = channelUrl;
        this.uploadsPlaylistId = uploadsPlaylistId;
    }

    /** Créateur des vidéos envoyées sur Brin par un utilisateur. */
    public static Creator forUser(UUID userId, String name) {
        var creator = new Creator(VideoSource.BRIN, userId.toString(), name, null, null, null);
        creator.userId = userId;
        return creator;
    }

    /** Nom à jour à chaque publication (l'utilisateur a pu en changer). */
    public void rename(String name) {
        this.name = name;
    }

    public UUID getUserId() {
        return userId;
    }

    public void markImported(Instant importStartedAt) {
        this.lastImportedAt = importStartedAt;
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

    public String getName() {
        return name;
    }

    public String getHandle() {
        return handle;
    }

    public String getChannelUrl() {
        return channelUrl;
    }

    public String getUploadsPlaylistId() {
        return uploadsPlaylistId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastImportedAt() {
        return lastImportedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
