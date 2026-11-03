package com.maximegrau.brin.service;

import java.util.UUID;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Utilisateur à l'origine de la requête : sujet du JWT validé par le resource server. */
@Component
public class CurrentUser {

    public UUID id() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return UUID.fromString(token.getToken().getSubject());
        }
        throw new AuthenticationCredentialsNotFoundException("Authentification requise");
    }
}
