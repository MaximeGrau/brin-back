package com.maximegrau.brin.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Code de validation en cours pour l'adresse d'un utilisateur (un seul à la fois, seul son hash est stocké). */
@Entity
@Table(name = "email_verification")
public class EmailVerification {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    /** Adresse à laquelle le code a été envoyé : le code ne vaut plus rien si l'utilisateur en change. */
    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected EmailVerification() {
    }

    public EmailVerification(UUID userId) {
        this.userId = userId;
    }

    /** Nouveau code : remet à zéro les tentatives. */
    public void renew(String email, String codeHash, Instant now, Instant expiresAt) {
        this.email = email;
        this.codeHash = codeHash;
        this.sentAt = now;
        this.expiresAt = expiresAt;
        this.attempts = 0;
    }

    public void countAttempt() {
        attempts++;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
