package com.maximegrau.brin.service.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.service.auth.AuthProperties;
import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Google (OpenID Connect). Identifiants : Google Cloud Console → API et services → Identifiants →
 * ID client OAuth de type "Application Web", URI de redirection {public-base-url}/api/v1/auth/oauth/google/callback.
 */
@Component
public class GoogleOAuthClient implements OAuthClient {

    static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    static final String USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final RestClient http;
    private final AuthProperties.OAuthClientRegistration registration;

    public GoogleOAuthClient(RestClient.Builder builder, AuthProperties properties) {
        this.http = builder.build();
        this.registration = properties.oauth(AuthProvider.GOOGLE);
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.GOOGLE;
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
                "scope", "openid email profile",
                "prompt", "select_account",
                "state", state));
    }

    @Override
    public OAuthProfile fetchProfile(String code, String redirectUri) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", registration.clientId());
        form.add("client_secret", registration.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");
        var token = http.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Token.class);
        if (token == null || token.accessToken() == null) {
            throw new OAuthException("Google n'a pas renvoyé de jeton d'accès");
        }
        var info = http.get().uri(USERINFO_URL)
                .headers(headers -> headers.setBearerAuth(token.accessToken()))
                .retrieve()
                .body(UserInfo.class);
        if (info == null || info.sub() == null) {
            throw new OAuthException("Google n'a pas renvoyé l'identité de l'utilisateur");
        }
        return new OAuthProfile(info.sub(), info.email(), Boolean.TRUE.equals(info.emailVerified()), info.name());
    }

    record Token(@JsonProperty("access_token") String accessToken) {
    }

    record UserInfo(String sub, String email, @JsonProperty("email_verified") Boolean emailVerified, String name) {
    }
}
