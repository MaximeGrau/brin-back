package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.EmailVerification;
import com.maximegrau.brin.model.user.EmailVerificationRepository;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Validation de l'adresse e-mail par un code à 6 chiffres envoyé par e-mail.
 * Seul le hash du code est stocké ; le nombre d'essais et la fréquence d'envoi sont limités.
 */
@Service
public class EmailVerificationService {

    private final UserRepository users;
    private final EmailVerificationRepository verifications;
    private final VerificationMailer mailer;
    private final TransactionTemplate transaction;
    private final AuthProperties properties;
    private final Clock clock;

    public EmailVerificationService(UserRepository users, EmailVerificationRepository verifications,
                                    VerificationMailer mailer, TransactionTemplate transaction,
                                    AuthProperties properties, Clock clock) {
        this.users = users;
        this.verifications = verifications;
        this.mailer = mailer;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Crée (ou remplace) le code de l'utilisateur, dans la transaction de l'appelant.
     * L'appelant l'envoie avec {@link VerificationMailer} une fois la transaction validée.
     *
     * @return le code en clair
     */
    @Transactional
    public String prepare(User user) {
        var code = SecretTokens.sixDigitCode();
        var now = clock.instant();
        var verification = verifications.findById(user.getId()).orElseGet(() -> new EmailVerification(user.getId()));
        verification.renew(user.getEmail(), SecretTokens.sha256(code), now, now.plus(properties.verification().codeTtl()));
        verifications.save(verification);
        return code;
    }

    /** Renvoie un nouveau code (bouton "Renvoyer le code"). */
    public void resend(UUID userId) {
        var pending = transaction.execute(status -> {
            var user = users.findById(userId).orElseThrow(AuthExceptions::unknownUser);
            if (user.getEmail() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune adresse e-mail à vérifier");
            }
            if (user.isEmailVerified()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Adresse e-mail déjà vérifiée");
            }
            verifications.findById(userId)
                    .filter(v -> v.getSentAt().plus(properties.verification().resendCooldown()).isAfter(clock.instant()))
                    .ifPresent(v -> {
                        throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Patientez un peu avant de demander un nouveau code");
                    });
            return new PendingCode(user.getEmail(), prepare(user));
        });
        try {
            mailer.send(pending.email(), pending.code());
        } catch (MailException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Envoi de l'e-mail impossible pour le moment", e);
        }
    }

    /** Vérifie le code saisi ; un code erroné est compté même si la requête échoue. */
    @Transactional(noRollbackFor = InvalidVerificationCodeException.class)
    public UserProfile confirm(UUID userId, String code) {
        var user = users.findById(userId).orElseThrow(AuthExceptions::unknownUser);
        if (user.isEmailVerified()) {
            return UserProfile.of(user);
        }
        var verification = verifications.findById(userId)
                .filter(v -> v.getEmail().equals(user.getEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucun code en cours : demandez un nouveau code"));
        var maxAttempts = properties.verification().maxAttempts();
        if (verification.getAttempts() >= maxAttempts) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Trop d'essais : demandez un nouveau code");
        }
        if (!clock.instant().isBefore(verification.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Code expiré : demandez un nouveau code");
        }
        if (!SecretTokens.matches(code, verification.getCodeHash())) {
            verification.countAttempt();
            throw new InvalidVerificationCodeException(maxAttempts - verification.getAttempts());
        }
        user.markEmailVerified(clock.instant());
        verifications.delete(verification);
        return UserProfile.of(user);
    }

    private record PendingCode(String email, String code) {
    }
}
