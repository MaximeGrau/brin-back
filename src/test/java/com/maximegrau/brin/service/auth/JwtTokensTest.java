package com.maximegrau.brin.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.maximegrau.brin.config.SecurityConfig;
import com.maximegrau.brin.model.user.AuthProvider;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokensTest {

    private final AuthProperties properties = new AuthProperties(
            "ZGV2LXVuaXF1ZW1lbnQtY2xlLWRlLXNpZ25hdHVyZS1kZXMtand0LWJyaW4tMzJvY3RldHMtbWluaW11bQ==",
            Duration.ofMinutes(15), Duration.ofDays(60), "http://localhost:8080", "Brin <test@brin.app>",
            List.of("brin://"), new AuthProperties.Verification(Duration.ofMinutes(15), 5, Duration.ofSeconds(60)), Map.of());
    private final SecurityConfig config = new SecurityConfig();
    private final SecretKey key = invoke("jwtKey", properties);
    private final JwtTokens tokens = new JwtTokens(invoke("jwtEncoder", key), key, properties, Clock.systemUTC());
    private final JwtDecoder apiDecoder = invoke("jwtDecoder", key);

    @Test
    void leJetonDAccesPorteLIdentifiantDeLUtilisateur() {
        var userId = UUID.randomUUID();

        assertThat(apiDecoder.decode(tokens.accessToken(userId)).getSubject()).isEqualTo(userId.toString());
    }

    @Test
    void unStateNEstPasAccepteCommeJetonDAcces() {
        var state = tokens.oauthState(AuthProvider.GOOGLE, "brin://auth");

        assertThat(tokens.parseState(state)).isEqualTo(new JwtTokens.OAuthState(AuthProvider.GOOGLE, "brin://auth"));
        assertThatThrownBy(() -> apiDecoder.decode(state)).isInstanceOf(JwtException.class);
    }

    @Test
    void unJetonDAccesNEstPasAccepteCommeState() {
        var accessToken = tokens.accessToken(UUID.randomUUID());

        assertThatThrownBy(() -> tokens.parseState(accessToken)).isInstanceOf(InvalidOAuthStateException.class);
        assertThatThrownBy(() -> tokens.parseState("nimportequoi")).isInstanceOf(InvalidOAuthStateException.class);
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(String method, Object arg) {
        return (T) ReflectionTestUtils.invokeMethod(config, method, arg);
    }
}
