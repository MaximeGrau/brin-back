package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.AuthProvider;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * JWT signés par le backend (HS256) : jetons d'accès et paramètre {@code state} des connexions OAuth.
 * L'audience les distingue, pour qu'un {@code state} ne soit jamais accepté comme jeton d'accès (et inversement).
 */
@Component
public class JwtTokens {

    public static final String ISSUER = "brin";
    public static final String API_AUDIENCE = "brin-api";
    private static final String STATE_AUDIENCE = "brin-oauth-state";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final JwtEncoder encoder;
    private final JwtDecoder stateDecoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokens(JwtEncoder encoder, SecretKey jwtKey, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        var decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(validatorFor(STATE_AUDIENCE));
        this.stateDecoder = decoder;
    }

    /** Validation des jetons d'accès, utilisée aussi par le décodeur du resource server. */
    public static OAuth2TokenValidator<Jwt> validatorFor(String audience) {
        return JwtValidators.createDefaultWithValidators(
                new JwtClaimValidator<String>(JwtClaimNames.ISS, ISSUER::equals),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience)));
    }

    public String accessToken(UUID userId) {
        var now = clock.instant();
        return encode(JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(API_AUDIENCE))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .build());
    }

    public String oauthState(AuthProvider provider, String appRedirectUri) {
        var now = clock.instant();
        return encode(JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(STATE_AUDIENCE))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(STATE_TTL))
                .claim("provider", provider.name())
                .claim("redirect", appRedirectUri)
                .build());
    }

    /** @throws InvalidOAuthStateException state absent, falsifié ou expiré */
    public OAuthState parseState(String state) {
        try {
            var jwt = stateDecoder.decode(state);
            return new OAuthState(AuthProvider.valueOf(jwt.getClaimAsString("provider")), jwt.getClaimAsString("redirect"));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidOAuthStateException();
        }
    }

    private String encode(JwtClaimsSet claims) {
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public record OAuthState(AuthProvider provider, String appRedirectUri) {
    }
}
