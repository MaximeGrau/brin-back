package com.maximegrau.brin.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Envoi du code de validation d'adresse (en dev : Mailpit, http://localhost:8025). Toujours hors transaction. */
@Component
public class VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(VerificationMailer.class);

    private final JavaMailSender sender;
    private final AuthProperties properties;

    public VerificationMailer(JavaMailSender sender, AuthProperties properties) {
        this.sender = sender;
        this.properties = properties;
    }

    /** @throws MailException serveur SMTP injoignable ou adresse refusée */
    public void send(String to, String code) {
        var message = new SimpleMailMessage();
        message.setFrom(properties.mailFrom());
        message.setTo(to);
        message.setSubject(code + " est ton code Brin");
        message.setText("""
                Bonjour,

                Voici ton code pour valider ton adresse e-mail sur Brin :

                    %s

                Il est valable %d minutes. Si tu n'as pas créé de compte Brin, ignore simplement ce message.
                """.formatted(code, properties.verification().codeTtl().toMinutes()));
        sender.send(message);
    }

    /** Envoi qui ne fait pas échouer l'opération en cours : l'utilisateur pourra redemander un code. */
    public void sendQuietly(String to, String code) {
        try {
            send(to, code);
        } catch (MailException e) {
            log.warn("Envoi du code de validation à {} impossible : {}", to, e.getMessage());
        }
    }
}
