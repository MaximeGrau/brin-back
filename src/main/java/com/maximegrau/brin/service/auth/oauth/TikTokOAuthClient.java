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
 * TikTok Login Kit (web). Identifiants : developers.tiktok.com → app avec le produit "Login Kit", scope
 * {@code user.info.basic}, URI de redirection {public-base-url}/api/v1/auth/oauth/tiktok/callback.
 * TikTok ne fournit pas l'adresse e-mail : l'app la demande ensuite.
 */
@Component
public class TikTokOAuthClient implements OAuthClient {

    static final String AUTHORIZE_URL = "https://www.tiktok.com/v2/auth/authorize/";
    static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    static final String USERINFO_URL = "https://open.tiktokapis.com/v2/user/info/?fields=open_id,display_name";

    private final RestClient http;
    private final AuthProperties.OAuthClientRegistration registration;

    public TikTokOAuthClient(RestClient.Builder builder, AuthProperties properties) {
        this.http = builder.build();
        this.registration = properties.oauth(AuthProvider.TIKTOK);
    }

    @Override
    public AuthProvider provider() {
        return AuthProvider.TIKTOK;
    }

    @Override
    public boolean isConfigured() {
        return registration.isConfigured();
    }

    @Override
    public URI authorizationUri(String state, String redirectUri) {
        return OAuthClient.withQuery(AUTHORIZE_URL, Map.of(
                "client_key", registration.clientId(),
                "redirect_uri", redirectUri,
                "response_type", "code",
                "scope", "user.info.basic",
                "state", state));
    }

    @Override
    public OAuthProfile fetchProfile(String code, String redirectUri) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("client_key", registration.clientId());
        form.add("client_secret", registration.clientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        // TikTok répond 200 même en cas d'erreur, avec "error" et "error_description".
        var token = http.post().uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(Token.class);
        if (token == null || token.accessToken() == null || token.openId() == null) {
            throw new OAuthException("TikTok a refusé le code : " + (token == null ? "réponse vide" : token.errorDescription()));
        }
        var info = http.get().uri(USERINFO_URL)
                .headers(headers -> headers.setBearerAuth(token.accessToken()))
                .retrieve()
                .body(UserInfoResponse.class);
        var displayName = info == null || info.data() == null || info.data().user() == null
                ? null : info.data().user().displayName();
        return new OAuthProfile(token.openId(), null, false, displayName);
    }

    record Token(@JsonProperty("access_token") String accessToken,
                 @JsonProperty("open_id") String openId,
                 @JsonProperty("error_description") String errorDescription) {
    }

    record UserInfoResponse(Data data) {

        record Data(User user) {
        }

        record User(@JsonProperty("open_id") String openId, @JsonProperty("display_name") String displayName) {
        }
    }
}
