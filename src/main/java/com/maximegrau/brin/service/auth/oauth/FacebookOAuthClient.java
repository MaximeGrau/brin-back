package com.maximegrau.brin.service.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.service.auth.AuthProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Facebook Login (Meta), pour tout compte Facebook. Identifiants : developers.facebook.com → app de type
 * "Authentifier et demander des données aux utilisateurs avec Facebook Login" → Facebook Login → Paramètres,
 * URI de redirection OAuth valide {public-base-url}/api/v1/auth/oauth/facebook/callback.
 * L'adresse e-mail n'est renvoyée que si le compte en a une et que l'utilisateur l'accepte ; Meta ne garantit pas
 * qu'elle soit vérifiée, on la fait donc valider par code comme une adresse saisie.
 */
@Component
public class FacebookOAuthClient implements OAuthClient {

    /** Version de la Graph API, supportée environ deux ans après sa sortie : à monter régulièrement. */
    static final String GRAPH_VERSION = "v23.0";
    static final String AUTHORIZE_URL = "https://www.facebook.com/" + GRAPH_VERSION + "/dialog/oauth";
    static final String TOKEN_URL = "https://graph.facebook.com/" + GRAPH_VERSION + "/oauth/access_token";
    static final String ME_URL = "https://graph.facebook.com/" + GRAPH_VERSION + "/me";

    private final RestClient http;
    private final AuthProperties.OAuthClientRegistration registration;

    public FacebookOAuthClient(RestClient.Builder builder, AuthProperties properties) {
        this.http = builder.build();
        this.registration = properties.oauth(AuthProvider.FACEBOOK);
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.FACEBOOK;
    }

    @Override
    public boolean isConfigured() {
        return registration.isConfigured();
    }

    @Override
    public URI authorizationUri(String state, String redirectUri) {
        return OAuthClient.withQuery(AUTHORIZE_URL, Map.of(
                "client_id", registration.clientId(),
                "redirect_uri", redirectUri,
                "response_type", "code",
                "scope", "public_profile,email",
                "state", state));
    }

    @Override
    public OAuthProfile fetchProfile(String code, String redirectUri) {
        var token = http.get()
                .uri(OAuthClient.withQuery(TOKEN_URL, Map.of(
                        "client_id", registration.clientId(),
                        "client_secret", registration.clientSecret(),
                        "redirect_uri", redirectUri,
                        "code", code)))
                .retrieve()
                .body(Token.class);
        if (token == null || token.accessToken() == null) {
            throw new OAuthException("Facebook n'a pas renvoyé de jeton d'accès");
        }
        var me = http.get()
                .uri(OAuthClient.withQuery(ME_URL, Map.of(
                        "fields", "id,name,email",
                        "access_token", token.accessToken(),
                        "appsecret_proof", appSecretProof(token.accessToken()))))
                .retrieve()
                .body(Me.class);
        if (me == null || me.id() == null) {
            throw new OAuthException("Facebook n'a pas renvoyé l'identité de l'utilisateur");
        }
        return new OAuthProfile(me.id(), me.email(), false, me.name());
    }

    /** Preuve que l'appel vient du serveur qui détient le secret de l'app (recommandé par Meta pour les appels serveur). */
    String appSecretProof(String accessToken) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(registration.clientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(accessToken.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    record Token(@JsonProperty("access_token") String accessToken) {
    }

    record Me(String id, String name, String email) {
    }
}
