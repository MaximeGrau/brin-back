package com.maximegrau.brin.service.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.maximegrau.brin.model.user.AuthProvider;
import com.maximegrau.brin.service.auth.AuthProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/** URL appelées et désérialisation des réponses des fournisseurs. */
class OAuthClientsTest {

    private static final String CALLBACK = "https://api.brin.test/api/v1/auth/oauth/x/callback";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AuthProperties properties = new AuthProperties("x", Duration.ofMinutes(15), Duration.ofDays(60),
            "https://api.brin.test", "Brin <test@brin.app>", List.of("brin://"),
            new AuthProperties.Verification(Duration.ofMinutes(15), 5, Duration.ofSeconds(60)),
            Map.of(AuthProvider.GOOGLE, new AuthProperties.OAuthClientRegistration("g-id", "g-secret"),
                    AuthProvider.TIKTOK, new AuthProperties.OAuthClientRegistration("tt-key", "tt-secret"),
                    AuthProvider.FACEBOOK, new AuthProperties.OAuthClientRegistration("fb-id", "fb-secret")));

    @Test
    void googleConstruitLUrlDeConnexion() {
        var uri = new GoogleOAuthClient(builder, properties).authorizationUri("st.at-e", CALLBACK);

        var params = UriComponentsBuilder.fromUri(uri).build(true).getQueryParams();
        assertThat(uri.toString()).startsWith(GoogleOAuthClient.AUTHORIZE_URL + "?");
        assertThat(params.getFirst("client_id")).isEqualTo("g-id");
        assertThat(params.getFirst("redirect_uri")).isEqualTo("https%3A%2F%2Fapi.brin.test%2Fapi%2Fv1%2Fauth%2Foauth%2Fx%2Fcallback");
        assertThat(params.getFirst("scope")).isEqualTo("openid%20email%20profile");
        assertThat(params.getFirst("state")).isEqualTo("st.at-e");
    }

    @Test
    void googleEchangeLeCodeEtLitLIdentite() {
        server.expect(requestTo(GoogleOAuthClient.TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of("code", "c0de", "client_secret", "g-secret",
                        "redirect_uri", CALLBACK, "grant_type", "authorization_code")))
                .andRespond(withSuccess("""
                        {"access_token": "ya29.x", "expires_in": 3599, "scope": "openid email profile",
                         "token_type": "Bearer", "id_token": "eyJ..."}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(GoogleOAuthClient.USERINFO_URL))
                .andExpect(header("Authorization", "Bearer ya29.x"))
                .andRespond(withSuccess("""
                        {"sub": "1100", "name": "Léa Martin", "email": "lea@gmail.com", "email_verified": true,
                         "picture": "https://lh3.googleusercontent.com/a/x"}
                        """, MediaType.APPLICATION_JSON));

        var profile = new GoogleOAuthClient(builder, properties).fetchProfile("c0de", CALLBACK);

        assertThat(profile).isEqualTo(new OAuthProfile("1100", "lea@gmail.com", true, "Léa Martin"));
        server.verify();
    }

    @Test
    void tiktokUtiliseOpenIdEtNeDonnePasDAdresse() {
        server.expect(requestTo(TikTokOAuthClient.TOKEN_URL))
                .andExpect(content().formDataContains(Map.of("client_key", "tt-key", "code", "c0de")))
                .andRespond(withSuccess("""
                        {"access_token": "act.x", "expires_in": 86400, "open_id": "oid-42",
                         "refresh_expires_in": 31536000, "refresh_token": "rft.x", "scope": "user.info.basic",
                         "token_type": "Bearer"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://open.tiktokapis.com/v2/user/info/")))
                .andExpect(queryParam("fields", "open_id,display_name"))
                .andRespond(withSuccess("""
                        {"data": {"user": {"open_id": "oid-42", "display_name": "lea.tiktok"}},
                         "error": {"code": "ok", "message": "", "log_id": "2026"}}
                        """, MediaType.APPLICATION_JSON));

        var profile = new TikTokOAuthClient(builder, properties).fetchProfile("c0de", CALLBACK);

        assertThat(profile).isEqualTo(new OAuthProfile("oid-42", null, false, "lea.tiktok"));
    }

    @Test
    void tiktokSignaleUneErreurRenvoyeeEn200() {
        server.expect(requestTo(TikTokOAuthClient.TOKEN_URL))
                .andRespond(withSuccess("""
                        {"error": "invalid_grant", "error_description": "Authorization code is expired.", "log_id": "x"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new TikTokOAuthClient(builder, properties).fetchProfile("c0de", CALLBACK))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void facebookSigneLAppelDuProfilEtNeGarantitPasLAdresse() {
        var client = new FacebookOAuthClient(builder, properties);
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(FacebookOAuthClient.TOKEN_URL)))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("client_id", "fb-id"))
                .andExpect(queryParam("client_secret", "fb-secret"))
                .andExpect(queryParam("code", "c0de"))
                .andRespond(withSuccess("""
                        {"access_token": "EAAG.x", "token_type": "bearer", "expires_in": 5183944}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(FacebookOAuthClient.ME_URL)))
                .andExpect(queryParam("fields", "id%2Cname%2Cemail"))
                .andExpect(queryParam("access_token", "EAAG.x"))
                .andExpect(queryParam("appsecret_proof", client.appSecretProof("EAAG.x")))
                .andRespond(withSuccess("""
                        {"id": "10229", "name": "Léa Martin", "email": "lea@example.com"}
                        """, MediaType.APPLICATION_JSON));

        var profile = client.fetchProfile("c0de", CALLBACK);

        assertThat(profile).isEqualTo(new OAuthProfile("10229", "lea@example.com", false, "Léa Martin"));
        server.verify();
    }

    @Test
    void facebookConstruitLUrlDeConnexion() {
        var uri = new FacebookOAuthClient(builder, properties).authorizationUri("st", CALLBACK);

        var params = UriComponentsBuilder.fromUri(uri).build(true).getQueryParams();
        assertThat(uri.toString()).startsWith("https://www.facebook.com/v23.0/dialog/oauth?");
        assertThat(params.getFirst("client_id")).isEqualTo("fb-id");
        assertThat(params.getFirst("scope")).isEqualTo("public_profile%2Cemail");
    }
}
