package com.maximegrau.brin.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.maximegrau.brin.service.auth.AuthProperties;
import com.maximegrau.brin.service.auth.JwtTokens;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * API sans état : chaque requête porte un JWT émis par le backend ({@code Authorization: Bearer …}).
 * Publiques : l'authentification, la liste des catégories, les critères d'envoi, les fichiers (liens signés)
 * et le health check.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        // 401 rendue par ApiExceptionHandler, au format Problem Details comme le reste de l'API.
        AuthenticationEntryPoint entryPoint = (request, response, e) -> resolver.resolveException(request, response, null, e);
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(WebConfig.API_BASE_PATH + "/auth/**", WebConfig.API_BASE_PATH + "/categories",
                                WebConfig.API_BASE_PATH + "/uploads/rules",
                                // Lien signé vérifié par MediaController (le lecteur vidéo n'envoie pas de jeton).
                                WebConfig.API_BASE_PATH + "/media/**",
                                "/actuator/health/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(withDefaults())
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                .build();
    }

    @Bean
    SecretKey jwtKey(AuthProperties properties) {
        if (properties.jwtSecret() == null || properties.jwtSecret().isBlank()) {
            throw new IllegalStateException("brin.auth.jwt-secret (JWT_SECRET) est obligatoire");
        }
        var bytes = Base64.getDecoder().decode(properties.jwtSecret());
        if (bytes.length < 32) {
            throw new IllegalStateException("brin.auth.jwt-secret doit faire au moins 32 octets (openssl rand -base64 32)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    /** Décodeur des jetons d'accès (resource server) : signature, expiration, émetteur et audience. */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        var decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtTokens.validatorFor(JwtTokens.API_AUDIENCE));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
