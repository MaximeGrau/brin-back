package com.maximegrau.brin.model.user;

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
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** Compte externe (Google, TikTok, Facebook) lié à un utilisateur Brin. */
@Entity
@Table(name = "user_identity")
public class UserIdentity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    /** Identifiant de l'utilisateur chez le fournisseur. */
    @Column(nullable = false)
    private String subject;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UserIdentity() {
    }

    UserIdentity(User user, AuthProvider provider, String subject) {
        this.user = user;
        this.provider = provider;
        this.subject = subject;
    }

    public User getUser() {
        return user;
    }

    public AuthProvider getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }
}
