package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.LoginTicketRepository;
import com.maximegrau.brin.model.user.RefreshToken;
import com.maximegrau.brin.model.user.RefreshTokenRepository;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import java.time.Clock;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sessions : inscription et connexion par e-mail + mot de passe, rafraîchissement, déconnexion, et échange du ticket
 * remis à l'app à la fin d'une connexion OAuth ({@link OAuthLoginService}).
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final LoginTicketRepository tickets;
    private final EmailVerificationService verification;
    private final VerificationMailer mailer;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokens jwt;
    private final TransactionTemplate transaction;
    private final AuthProperties properties;
    private final Clock clock;
    /** Comparé quand l'e-mail est inconnu, pour que la réponse prenne le même temps que pour un compte existant. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, LoginTicketRepository tickets,
                       EmailVerificationService verification, VerificationMailer mailer,
                       PasswordEncoder passwordEncoder, JwtTokens jwt, TransactionTemplate transaction, AuthProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tickets = tickets;
        this.verification = verification;
        this.mailer = mailer;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("mot-de-passe-factice");
    }

    /** Crée le compte et envoie le code de validation (un échec d'envoi n'empêche pas l'inscription). */
    public AuthSession register(String email, String password, String displayName) {
        var normalized = User.normalizeEmail(email);
        var result = transaction.execute(status -> {
            if (users.existsByEmail(normalized)) {
                throw AuthExceptions.emailAlreadyUsed();
            }
            var user = users.save(new User(normalized, passwordEncoder.encode(password), blankToNull(displayName)));
            var code = verification.prepare(user);
            return new Registration(openSession(user), code);
        });
        mailer.sendQuietly(normalized, result.code());
        return result.session();
    }

    @Transactional
    public AuthSession login(String email, String password) {
        var user = users.findByEmail(User.normalizeEmail(email)).filter(User::hasPassword);
        var hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        if (!passwordEncoder.matches(password, hash) || user.isEmpty()) {
            throw AuthExceptions.badCredentials();
        }
        return openSession(user.get());
    }

    /** Rotation : le jeton présenté est consommé et remplacé. */
    @Transactional
    public AuthSession refresh(String refreshToken) {
        var stored = refreshTokens.findByTokenHash(SecretTokens.sha256(refreshToken))
                .orElseThrow(AuthExceptions::sessionExpired);
        refreshTokens.delete(stored);
        if (stored.isExpired(clock.instant())) {
            throw AuthExceptions.sessionExpired();
        }
        var user = users.findById(stored.getUserId()).orElseThrow(AuthExceptions::sessionExpired);
        return openSession(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(SecretTokens.sha256(refreshToken)).ifPresent(refreshTokens::delete);
    }

    @Transactional
    public AuthSession exchangeTicket(String ticket) {
        var stored = tickets.findById(SecretTokens.sha256(ticket)).orElseThrow(AuthExceptions::sessionExpired);
        tickets.delete(stored);
        if (stored.isExpired(clock.instant())) {
            throw AuthExceptions.sessionExpired();
        }
        return openSession(users.findById(stored.getUserId()).orElseThrow(AuthExceptions::sessionExpired));
    }

    private AuthSession openSession(User user) {
        var refreshToken = SecretTokens.generate();
        refreshTokens.save(new RefreshToken(user.getId(), SecretTokens.sha256(refreshToken),
                clock.instant().plus(properties.refreshTokenTtl())));
        return new AuthSession(jwt.accessToken(user.getId()), refreshToken,
                properties.accessTokenTtl().toSeconds(), UserProfile.of(user));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private record Registration(AuthSession session, String code) {
    }
}
