package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.config.WebConfig;
import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.model.user.LoginTicket;
import com.maximegrau.brin.model.user.LoginTicketRepository;
import com.maximegrau.brin.model.user.RefreshTokenRepository;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.service.auth.oauth.OAuthClient;
import com.maximegrau.brin.service.auth.oauth.OAuthProfile;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * Connexion par un fournisseur externe (Google, TikTok, Facebook), identique pour les trois : l'app ouvre
 * {@code /auth/oauth/{provider}/authorize} dans un navigateur → page de connexion du fournisseur → retour sur
 * {@code /callback} → le backend échange le code (appel HTTP hors transaction), crée ou retrouve le compte et renvoie
 * l'app sur son lien profond avec un ticket à usage unique → l'app échange le ticket contre des jetons
 * ({@link AuthService#exchangeTicket}).
 */
@Service
public class OAuthLoginService {

    private static final Logger log = LoggerFactory.getLogger(OAuthLoginService.class);
    private static final Duration TICKET_TTL = Duration.ofMinutes(2);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final LoginTicketRepository tickets;
    private final EmailVerificationService verification;
    private final VerificationMailer mailer;
    private final JwtTokens jwt;
    private final Map<AuthProvider, OAuthClient> oauthClients = new EnumMap<>(AuthProvider.class);
    private final TransactionTemplate transaction;
    private final AuthProperties properties;
    private final Clock clock;

    public OAuthLoginService(UserRepository users, RefreshTokenRepository refreshTokens, LoginTicketRepository tickets,
                             EmailVerificationService verification, VerificationMailer mailer, JwtTokens jwt,
                             List<OAuthClient> oauthClients, TransactionTemplate transaction,
                             AuthProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.tickets = tickets;
        this.verification = verification;
        this.mailer = mailer;
        this.jwt = jwt;
        oauthClients.forEach(client -> this.oauthClients.put(client.provider(), client));
        this.transaction = transaction;
        this.properties = properties;
        this.clock = clock;
    }

    /** URL de la page de connexion du fournisseur, ou retour direct vers l'app s'il n'est pas configuré. */
    public URI authorizationRedirect(AuthProvider provider, String appRedirectUri) {
        if (appRedirectUri == null || properties.allowedRedirectUris().stream().noneMatch(appRedirectUri::startsWith)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lien de retour vers l'app non autorisé : " + appRedirectUri);
        }
        var client = oauthClients.get(provider);
        if (client == null || !client.isConfigured()) {
            log.warn("Connexion {} demandée mais non configurée (brin.auth.oauth.{})", provider, key(provider));
            return toApp(appRedirectUri, "error", "unavailable");
        }
        return client.authorizationUri(jwt.oauthState(provider, appRedirectUri), callbackUri(provider));
    }

    /** Retour du fournisseur : renvoie l'app sur son lien profond avec un ticket ou une erreur. */
    public URI handleCallback(AuthProvider provider, String state, String code, String error) {
        var oauthState = jwt.parseState(state);
        if (oauthState.provider() != provider) {
            throw new InvalidOAuthStateException();
        }
        var app = oauthState.appRedirectUri();
        if (error != null || code == null) {
            // access_denied : l'utilisateur a annulé sur la page du fournisseur.
            return toApp(app, "error", "access_denied".equals(error) ? "cancelled" : "failed");
        }
        OAuthProfile profile;
        try {
            profile = oauthClients.get(provider).fetchProfile(code, callbackUri(provider));   // hors transaction
        } catch (RuntimeException e) {
            log.warn("Connexion {} : échange du code impossible : {}", provider, e.getMessage());
            return toApp(app, "error", "failed");
        }
        var login = transaction.execute(status -> {
            var result = findOrCreate(provider, profile);
            var value = SecretTokens.generate();
            tickets.save(new LoginTicket(SecretTokens.sha256(value), result.user().getId(), clock.instant().plus(TICKET_TTL)));
            return new OAuthLogin(value, result.user().getEmail(), result.pendingCode());
        });
        if (login.pendingCode() != null) {
            mailer.sendQuietly(login.email(), login.pendingCode());
        }
        return toApp(app, "ticket", login.ticket());
    }

    /**
     * Compte déjà lié à ce fournisseur, sinon compte existant avec la même adresse si le fournisseur la garantit
     * (Google), sinon nouveau compte. Une adresse non garantie (Facebook) n'est jamais utilisée pour retrouver un
     * compte : elle est enregistrée non vérifiée sur le nouveau compte (si elle est libre) et un code y est envoyé.
     * TikTok ne donne pas d'adresse : l'app la demandera.
     */
    private NewOrExisting findOrCreate(AuthProvider provider, OAuthProfile profile) {
        var linked = users.findByIdentity(provider, profile.subject());
        if (linked.isPresent()) {
            linked.get().fillDisplayNameIfMissing(profile.displayName());
            return new NewOrExisting(linked.get(), null);
        }
        var now = clock.instant();
        var verifiedEmail = profile.emailVerified() ? User.normalizeEmail(profile.email()) : null;
        if (verifiedEmail != null) {
            var sameEmail = users.findByEmail(verifiedEmail);
            if (sameEmail.isPresent()) {
                var user = sameEmail.get();
                if (user.confirmEmailOwnership(now)) {
                    refreshTokens.deleteByUserId(user.getId());
                }
                user.link(provider, profile.subject());
                user.fillDisplayNameIfMissing(profile.displayName());
                return new NewOrExisting(user, null);
            }
        }
        var unverifiedEmail = verifiedEmail == null ? User.normalizeEmail(profile.email()) : null;
        if (unverifiedEmail != null && users.existsByEmail(unverifiedEmail)) {
            unverifiedEmail = null;   // déjà prise : l'utilisateur en saisira une autre
        }
        var user = new User(verifiedEmail != null ? verifiedEmail : unverifiedEmail, null, null);
        user.fillDisplayNameIfMissing(profile.displayName());
        if (verifiedEmail != null) {
            user.markEmailVerified(now);
        }
        user.link(provider, profile.subject());
        var saved = users.save(user);
        return new NewOrExisting(saved, unverifiedEmail != null ? verification.prepare(saved) : null);
    }

    /** URL de retour déclarée chez le fournisseur. */
    private String callbackUri(AuthProvider provider) {
        return properties.publicBaseUrl() + WebConfig.API_BASE_PATH + "/auth/oauth/" + key(provider) + "/callback";
    }

    private static URI toApp(String appRedirectUri, String param, String value) {
        return OAuthClient.withQuery(appRedirectUri, Map.of(param, value));
    }

    private static String key(AuthProvider provider) {
        return provider.name().toLowerCase(Locale.ROOT);
    }

    /** @param pendingCode code de validation à envoyer une fois la transaction validée, nul s'il n'y en a pas */
    private record NewOrExisting(User user, String pendingCode) {
    }

    private record OAuthLogin(String ticket, String email, String pendingCode) {
    }
}
