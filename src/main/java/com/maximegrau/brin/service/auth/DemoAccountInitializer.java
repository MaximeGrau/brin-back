package com.maximegrau.brin.service.auth;

import com.maximegrau.brin.model.user.User;
import com.maximegrau.brin.model.user.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Environnement de test uniquement : garantit au démarrage un compte de démo utilisable sans passer par
 * l'inscription (adresse déjà vérifiée, mot de passe remis à celui de la configuration). Survit aux remises à zéro
 * de la base ({@code docker compose down --volumes}).
 */
@Component
@Profile("test")
public class DemoAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String email;
    private final String password;
    private final String displayName;

    public DemoAccountInitializer(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
                                  @Value("${brin.auth.demo-account.email:}") String email,
                                  @Value("${brin.auth.demo-account.password:}") String password,
                                  @Value("${brin.auth.demo-account.display-name:Démo}") String displayName) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.email = email;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) {
            return;
        }
        var user = users.findByEmail(User.normalizeEmail(email))
                .orElseGet(() -> users.save(new User(email, passwordEncoder.encode(password), displayName)));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            user.changePassword(passwordEncoder.encode(password));
        }
        user.markEmailVerified(clock.instant());
        log.info("Compte de démo prêt : {}", user.getEmail());
    }
}
