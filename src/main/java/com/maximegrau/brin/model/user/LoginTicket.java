package com.maximegrau.brin.model.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Ticket remis à l'app à la fin d'une connexion OAuth, échangé une seule fois contre des jetons. */
@Entity
@Table(name = "login_ticket")
public class LoginTicket {

    @Id
    @Column(name = "ticket_hash")
    private String ticketHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected LoginTicket() {
    }

    public LoginTicket(String ticketHash, UUID userId, Instant expiresAt) {
        this.ticketHash = ticketHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public UUID getUserId() {
        return userId;
    }
}
