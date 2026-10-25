package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.model.video.Category;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Profil de l'utilisateur connecté : adresse e-mail et catégories préférées. */
@Service
public class AccountService {

    private final UserRepository users;
    private final EmailVerificationService verification;
    private final VerificationMailer mailer;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public AccountService(UserRepository users, EmailVerificationService verification, VerificationMailer mailer,
                          TransactionTemplate transaction, Clock clock) {
        this.users = users;
        this.verification = verification;
        this.mailer = mailer;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserProfile profile(UUID userId) {
        return UserProfile.of(load(userId));
    }

    /** Nouvelle adresse (ou première, après TikTok) : un code y est envoyé. */
    public UserProfile updateEmail(UUID userId, String email) {
        var normalized = User.normalizeEmail(email);
        var result = transaction.execute(status -> {
            var user = load(userId);
            if (normalized.equals(user.getEmail())) {
                return new EmailChange(UserProfile.of(user), null);
            }
            if (users.existsByEmail(normalized)) {
                throw AuthExceptions.emailAlreadyUsed();
            }
            user.changeEmail(normalized);
            var code = verification.prepare(user);
            return new EmailChange(UserProfile.of(user), code);
        });
        if (result.code() != null) {
            mailer.sendQuietly(normalized, result.code());
        }
        return result.profile();
    }

    /** Liste vide permise : toutes les catégories à parts égales au départ. */
    @Transactional
    public UserProfile updatePreferredCategories(UUID userId, Set<Category> categories) {
        var user = load(userId);
        user.replacePreferredCategories(categories, clock.instant());
        return UserProfile.of(user);
    }

    private User load(UUID userId) {
        return users.findById(userId).orElseThrow(AuthExceptions::unknownUser);
    }

    private record EmailChange(UserProfile profile, String code) {
    }
}
