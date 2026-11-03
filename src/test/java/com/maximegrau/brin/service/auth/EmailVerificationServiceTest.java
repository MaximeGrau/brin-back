package com.maximegrau.brin.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.maximegrau.brin.model.user.EmailVerification;
import com.maximegrau.brin.model.user.EmailVerificationRepository;
import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

class EmailVerificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    private final UserRepository users = mock(UserRepository.class);
    private final EmailVerificationRepository verifications = mock(EmailVerificationRepository.class);
    private final VerificationMailer mailer = mock(VerificationMailer.class);
    private final AuthProperties properties = new AuthProperties("x", Duration.ofMinutes(15), Duration.ofDays(60),
            "http://localhost:8080", "Brin <test@brin.app>", List.of("brin://"),
            new AuthProperties.Verification(Duration.ofMinutes(15), 3, Duration.ofSeconds(60)), Map.of());
    private final EmailVerificationService service = new EmailVerificationService(users, verifications, mailer,
            mock(TransactionTemplate.class), properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Lea@Example.com", "hash", "Léa");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void preparerStockeLeHashDuCodeEtPasLeCode() {
        when(verifications.findById(USER_ID)).thenReturn(Optional.empty());
        when(verifications.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var code = service.prepare(user);

        assertThat(code).matches("\\d{6}");
        var captor = org.mockito.ArgumentCaptor.forClass(EmailVerification.class);
        verify(verifications).save(captor.capture());
        assertThat(captor.getValue().getCodeHash()).isEqualTo(SecretTokens.sha256(code)).isNotEqualTo(code);
        assertThat(captor.getValue().getEmail()).isEqualTo("lea@example.com");
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void leBonCodeValideLAdresse() {
        var verification = pending("123456", NOW.plusSeconds(60));

        var profile = service.confirm(USER_ID, "123456");

        assertThat(profile.emailVerified()).isTrue();
        verify(verifications).delete(verification);
    }

    @Test
    void unMauvaisCodeEstCompte() {
        var verification = pending("123456", NOW.plusSeconds(60));

        assertThatThrownBy(() -> service.confirm(USER_ID, "000000"))
                .isInstanceOf(InvalidVerificationCodeException.class)
                .hasMessageContaining("encore 2 essais");
        assertThat(verification.getAttempts()).isEqualTo(1);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void auDelaDuNombreDEssaisMemeLeBonCodeEstRefuse() {
        var verification = pending("123456", NOW.plusSeconds(60));
        for (int i = 0; i < 3; i++) {
            verification.countAttempt();
        }

        assertThatThrownBy(() -> service.confirm(USER_ID, "123456"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void unCodeExpireEstRefuse() {
        pending("123456", NOW);

        assertThatThrownBy(() -> service.confirm(USER_ID, "123456")).hasMessageContaining("expiré");
    }

    @Test
    void unCodeEnvoyeAUneAncienneAdresseNeValidePasLaNouvelle() {
        pending("123456", NOW.plusSeconds(60));
        user.changeEmail("autre@example.com");

        assertThatThrownBy(() -> service.confirm(USER_ID, "123456")).hasMessageContaining("Aucun code en cours");
        verify(verifications, never()).delete(any());
    }

    private EmailVerification pending(String code, Instant expiresAt) {
        var verification = new EmailVerification(USER_ID);
        verification.renew(user.getEmail(), SecretTokens.sha256(code), NOW.minusSeconds(60), expiresAt);
        when(verifications.findById(USER_ID)).thenReturn(Optional.of(verification));
        return verification;
    }
}
