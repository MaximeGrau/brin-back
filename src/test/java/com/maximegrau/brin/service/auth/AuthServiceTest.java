package com.maximegrau.brin.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.model.user.LoginTicket;
import com.maximegrau.brin.model.user.LoginTicketRepository;
import com.maximegrau.brin.model.user.RefreshToken;
import com.maximegrau.brin.model.user.RefreshTokenRepository;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import com.maximegrau.brin.service.auth.oauth.OAuthClient;
import com.maximegrau.brin.service.auth.oauth.OAuthProfile;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final LoginTicketRepository tickets = mock(LoginTicketRepository.class);
    private final EmailVerificationService verification = mock(EmailVerificationService.class);
    private final VerificationMailer mailer = mock(VerificationMailer.class);
    private final JwtTokens jwt = mock(JwtTokens.class);
    private final OAuthClient google = mock(OAuthClient.class);
    private final TransactionTemplate transaction = mock(TransactionTemplate.class);
    private final AuthProperties properties = new AuthProperties("x", Duration.ofMinutes(15), Duration.ofDays(60),
            "https://api.brin.test", "Brin <test@brin.app>", List.of("brin://", "exp://"),
            new AuthProperties.Verification(Duration.ofMinutes(15), 5, Duration.ofSeconds(60)), Map.of());

    private AuthService service;
    private OAuthLoginService oauth;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(google.provider()).thenReturn(AuthProvider.GOOGLE);
        when(google.isConfigured()).thenReturn(true);
        when(transaction.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(mock(TransactionStatus.class)));
        when(users.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(jwt.accessToken(any())).thenReturn("jwt");
        service = new AuthService(users, refreshTokens, tickets, verification, mailer,
                NoOpPasswordEncoder.getInstance(), jwt, transaction, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        oauth = oauthWith(google);
    }

    @Test
    void linscriptionEnvoieLeCodeApresLaTransaction() {
        when(verification.prepare(any())).thenReturn("123456");

        var session = service.register(" Lea@Example.com ", "motdepasse", "Léa");

        assertThat(session.user().email()).isEqualTo("lea@example.com");
        assertThat(session.user().emailVerified()).isFalse();
        assertThat(session.refreshToken()).isNotBlank();
        verify(mailer).sendQuietly("lea@example.com", "123456");
    }

    @Test
    void linscriptionRefuseUneAdresseDejaUtilisee() {
        when(users.existsByEmail("lea@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("lea@example.com", "motdepasse", null))
                .hasMessageContaining("déjà utilisée");
        verify(mailer, never()).sendQuietly(anyString(), anyString());
    }

    @Test
    void laConnexionRefuseUnMauvaisMotDePasse() {
        when(users.findByEmail("lea@example.com")).thenReturn(Optional.of(withId(new User("lea@example.com", "bon", null))));

        assertThatThrownBy(() -> service.login("lea@example.com", "mauvais")).hasMessageContaining("incorrect");
        assertThat(service.login("Lea@example.com", "bon").accessToken()).isEqualTo("jwt");
    }

    @Test
    void leRafraichissementConsommeLeJeton() {
        var user = withId(new User("lea@example.com", "bon", null));
        var stored = new RefreshToken(user.getId(), SecretTokens.sha256("ancien"), NOW.plusSeconds(60));
        when(refreshTokens.findByTokenHash(SecretTokens.sha256("ancien"))).thenReturn(Optional.of(stored));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        var session = service.refresh("ancien");

        verify(refreshTokens).delete(stored);
        assertThat(session.refreshToken()).isNotEqualTo("ancien");
    }

    @Test
    void refuseUnLienDeRetourNonAutorise() {
        assertThatThrownBy(() -> oauth.authorizationRedirect(AuthProvider.GOOGLE, "https://pirate.example/auth"))
                .hasMessageContaining("non autorisé");
    }

    @Test
    void fournisseurNonConfigureRenvoieVersLAppAvecUneErreur() {
        var uri = oauth.authorizationRedirect(AuthProvider.TIKTOK, "brin://auth");

        assertThat(uri.toString()).isEqualTo("brin://auth?error=unavailable");
    }

    @Test
    void laConnexionGoogleCreeUnCompteVerifieEtRenvoieUnTicket() {
        callbackFor(new OAuthProfile("google-sub", "Lea@gmail.com", true, "Léa"));
        when(users.findByIdentity(AuthProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
        when(users.findByEmail("lea@gmail.com")).thenReturn(Optional.empty());

        var uri = oauth.handleCallback(AuthProvider.GOOGLE, "state", "code", null);

        var ticket = query(uri, "ticket");
        var savedTicket = org.mockito.ArgumentCaptor.forClass(LoginTicket.class);
        verify(tickets).save(savedTicket.capture());
        assertThat(ReflectionTestUtils.getField(savedTicket.getValue(), "ticketHash")).isEqualTo(SecretTokens.sha256(ticket));
        var savedUser = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(savedUser.capture());
        assertThat(savedUser.getValue().isEmailVerified()).isTrue();
        assertThat(savedUser.getValue().getDisplayName()).isEqualTo("Léa");
        assertThat(savedUser.getValue().getIdentities()).singleElement()
                .satisfies(identity -> assertThat(identity.getSubject()).isEqualTo("google-sub"));
    }

    @Test
    void googleReprendUnCompteDontLAdresseNEtaitPasVerifiee() {
        callbackFor(new OAuthProfile("google-sub", "lea@gmail.com", true, "Léa"));
        var squatter = withId(new User("lea@gmail.com", "mdp-de-quelqu-un-d-autre", null));
        when(users.findByIdentity(AuthProvider.GOOGLE, "google-sub")).thenReturn(Optional.empty());
        when(users.findByEmail("lea@gmail.com")).thenReturn(Optional.of(squatter));

        oauth.handleCallback(AuthProvider.GOOGLE, "state", "code", null);

        assertThat(squatter.hasPassword()).isFalse();
        assertThat(squatter.isEmailVerified()).isTrue();
        verify(refreshTokens).deleteByUserId(squatter.getId());
    }

    @Test
    void laConnexionTikTokCreeUnCompteSansAdresse() {
        var tiktok = mock(OAuthClient.class);
        when(tiktok.provider()).thenReturn(AuthProvider.TIKTOK);
        oauth = oauthWith(tiktok);
        when(jwt.parseState("state")).thenReturn(new JwtTokens.OAuthState(AuthProvider.TIKTOK, "brin://auth"));
        when(tiktok.fetchProfile("code", "https://api.brin.test/api/v1/auth/oauth/tiktok/callback"))
                .thenReturn(new OAuthProfile("open-id", null, false, "lea.tiktok"));
        when(users.findByIdentity(AuthProvider.TIKTOK, "open-id")).thenReturn(Optional.empty());

        oauth.handleCallback(AuthProvider.TIKTOK, "state", "code", null);

        verify(users).save(org.mockito.ArgumentMatchers.<User>argThat(u -> u.getEmail() == null && !u.isEmailVerified()));
    }

    @Test
    void facebookEnregistreLAdresseNonVerifieeEtEnvoieUnCode() {
        var facebook = providerClient(AuthProvider.FACEBOOK);
        when(facebook.fetchProfile(eq("code"), any())).thenReturn(new OAuthProfile("fb-id", "Lea@example.com", false, "Léa"));
        when(users.findByIdentity(AuthProvider.FACEBOOK, "fb-id")).thenReturn(Optional.empty());
        when(verification.prepare(any())).thenReturn("123456");

        oauth.handleCallback(AuthProvider.FACEBOOK, "state", "code", null);

        verify(users).save(org.mockito.ArgumentMatchers.<User>argThat(u ->
                "lea@example.com".equals(u.getEmail()) && !u.isEmailVerified()));
        verify(mailer).sendQuietly("lea@example.com", "123456");
        // Adresse non garantie : jamais utilisée pour retrouver (et reprendre) un compte existant.
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void facebookAvecUneAdresseDejaPriseCreeUnCompteSansAdresse() {
        var facebook = providerClient(AuthProvider.FACEBOOK);
        when(facebook.fetchProfile(eq("code"), any())).thenReturn(new OAuthProfile("fb-id", "lea@example.com", false, "Léa"));
        when(users.findByIdentity(AuthProvider.FACEBOOK, "fb-id")).thenReturn(Optional.empty());
        when(users.existsByEmail("lea@example.com")).thenReturn(true);

        oauth.handleCallback(AuthProvider.FACEBOOK, "state", "code", null);

        verify(users).save(org.mockito.ArgumentMatchers.<User>argThat(u -> u.getEmail() == null));
        verify(mailer, never()).sendQuietly(anyString(), anyString());
    }

    @Test
    void annulationSurLaPageDuFournisseur() {
        when(jwt.parseState("state")).thenReturn(new JwtTokens.OAuthState(AuthProvider.GOOGLE, "brin://auth"));

        var uri = oauth.handleCallback(AuthProvider.GOOGLE, "state", null, "access_denied");

        assertThat(uri.toString()).isEqualTo("brin://auth?error=cancelled");
    }

    @Test
    void unTicketNeSertQuUneFois() {
        var user = withId(new User("lea@example.com", null, null));
        var stored = new LoginTicket(SecretTokens.sha256("t"), user.getId(), NOW.plusSeconds(60));
        when(tickets.findById(SecretTokens.sha256("t"))).thenReturn(Optional.of(stored));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.exchangeTicket("t").accessToken()).isEqualTo("jwt");
        verify(tickets).delete(stored);
    }

    private OAuthClient providerClient(AuthProvider provider) {
        var client = mock(OAuthClient.class);
        when(client.provider()).thenReturn(provider);
        oauth = oauthWith(client);
        when(jwt.parseState("state")).thenReturn(new JwtTokens.OAuthState(provider, "brin://auth"));
        return client;
    }

    private OAuthLoginService oauthWith(OAuthClient client) {
        return new OAuthLoginService(users, refreshTokens, tickets, verification, mailer, jwt, List.of(client),
                transaction, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void callbackFor(OAuthProfile profile) {
        when(jwt.parseState("state")).thenReturn(new JwtTokens.OAuthState(AuthProvider.GOOGLE, "exp://192.168.1.27:8081/--/auth"));
        when(google.fetchProfile(eq("code"), eq("https://api.brin.test/api/v1/auth/oauth/google/callback")))
                .thenReturn(profile);
    }

    private static String query(URI uri, String name) {
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
    }

    private static User withId(User user) {
        if (user.getId() == null) {
            ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        }
        return user;
    }
}
