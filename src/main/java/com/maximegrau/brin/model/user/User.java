package com.maximegrau.brin.model.user;

import com.maximegrau.brin.model.video.Category;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Compte Brin : e-mail + mot de passe et/ou un ou plusieurs fournisseurs externes. */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    /** Toujours en minuscules (voir {@link #normalizeEmail}). */
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Nul pour un compte créé uniquement via un fournisseur externe. */
    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserIdentity> identities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "user_preferred_category", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private Set<Category> preferredCategories = EnumSet.noneOf(Category.class);

    /** L'utilisateur est passé par l'écran des catégories (il peut n'en avoir choisi aucune). */
    @Column(name = "categories_chosen_at")
    private Instant categoriesChosenAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Nouvelle adresse : elle devra être vérifiée. Même adresse : rien ne change. */
    public void changeEmail(String newEmail) {
        var normalized = normalizeEmail(newEmail);
        if (!normalized.equals(email)) {
            email = normalized;
            emailVerifiedAt = null;
        }
    }

    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
        }
    }

    /**
     * Adresse dont un fournisseur (Google) garantit la propriété : elle est vérifiée d'office. Si elle ne l'était pas,
     * le mot de passe éventuel a pu être choisi par quelqu'un d'autre que le propriétaire de l'adresse : on le retire.
     *
     * @return vrai si l'adresse n'était pas vérifiée (les sessions ouvertes avec l'ancien mot de passe sont à révoquer)
     */
    public boolean confirmEmailOwnership(Instant now) {
        if (emailVerifiedAt != null) {
            return false;
        }
        passwordHash = null;
        emailVerifiedAt = now;
        return true;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void link(AuthProvider provider, String subject) {
        identities.add(new UserIdentity(this, provider, subject));
    }

    public void replacePreferredCategories(Collection<Category> categories, Instant now) {
        preferredCategories.clear();
        preferredCategories.addAll(categories);
        if (categoriesChosenAt == null) {
            categoriesChosenAt = now;
        }
    }

    public void fillDisplayNameIfMissing(String name) {
        if (displayName == null && name != null && !name.isBlank()) {
            displayName = name.length() > 100 ? name.substring(0, 100) : name;
        }
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<UserIdentity> getIdentities() {
        return identities;
    }

    public boolean hasChosenCategories() {
        return categoriesChosenAt != null;
    }

    public Set<Category> getPreferredCategories() {
        return preferredCategories;
    }
}
