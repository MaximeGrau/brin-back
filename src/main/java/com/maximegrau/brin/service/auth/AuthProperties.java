package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.AuthProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code brin.auth.*} */
@ConfigurationProperties("brin.auth")
public record AuthProperties(
        /** Clé HMAC de signature des JWT, en base64, 32 octets au moins. */
        String jwtSecret,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("60d") Duration refreshTokenTtl,
        /** URL publique du backend (HTTPS en prod), pour l'URL de retour OAuth déclarée chez les fournisseurs. */
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue("Brin <no-reply@brin.app>") String mailFrom,
        /** Préfixes autorisés pour le lien profond de retour vers l'app (ex. brin://). */
        @DefaultValue("brin://") List<String> allowedRedirectUris,
        @DefaultValue Verification verification,
        Map<AuthProvider, OAuthClientRegistration> oauth) {

    public AuthProperties {
        oauth = oauth == null ? Map.of() : Map.copyOf(oauth);
    }

    public OAuthClientRegistration oauth(AuthProvider provider) {
        return oauth.getOrDefault(provider, new OAuthClientRegistration(null, null));
    }

    public record Verification(
            @DefaultValue("15m") Duration codeTtl,
            /** Codes erronés tolérés avant de devoir en redemander un. */
            @DefaultValue("5") int maxAttempts,
            /** Délai minimal entre deux envois de code. */
            @DefaultValue("60s") Duration resendCooldown) {
    }

    /** Identifiants de l'app déclarée chez le fournisseur ("client key" pour TikTok, "app ID" pour Facebook). */
    public record OAuthClientRegistration(String clientId, String clientSecret) {

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }
    }
}
