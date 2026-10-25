package com.maximegrau.brin.controller;

import com.maximegrau.brin.api.AuthApi;
import com.maximegrau.brin.api.model.AuthProvider;
import com.maximegrau.brin.api.model.AuthTokens;
import com.maximegrau.brin.api.model.LoginRequest;
import com.maximegrau.brin.api.model.RefreshRequest;
import com.maximegrau.brin.api.model.RegisterRequest;
import com.maximegrau.brin.api.model.TicketExchangeRequest;
import com.maximegrau.brin.service.auth.AuthService;
import com.maximegrau.brin.service.auth.OAuthLoginService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/auth/** : inscription, connexion (mot de passe ou OAuth), rafraîchissement et déconnexion. */
@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;
    private final OAuthLoginService oauthLoginService;
    private final AccountMapper mapper;

    public AuthController(AuthService authService, OAuthLoginService oauthLoginService, AccountMapper mapper) {
        this.authService = authService;
        this.oauthLoginService = oauthLoginService;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<AuthTokens> register(RegisterRequest request) {
        var session = authService.register(request.getEmail(), request.getPassword(), request.getDisplayName());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toTokens(session));
    }

    @Override
    public ResponseEntity<AuthTokens> login(LoginRequest request) {
        return ResponseEntity.ok(mapper.toTokens(authService.login(request.getEmail(), request.getPassword())));
    }

    @Override
    public ResponseEntity<AuthTokens> refreshTokens(RefreshRequest request) {
        return ResponseEntity.ok(mapper.toTokens(authService.refresh(request.getRefreshToken())));
    }

    @Override
    public ResponseEntity<Void> logout(RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> oauthAuthorize(AuthProvider provider, String redirectUri) {
        return redirect(oauthLoginService.authorizationRedirect(mapper.toDomain(provider), redirectUri));
    }

    @Override
    public ResponseEntity<Void> oauthCallback(AuthProvider provider, String state, String code, String error) {
        return redirect(oauthLoginService.handleCallback(mapper.toDomain(provider), state, code, error));
    }

    @Override
    public ResponseEntity<AuthTokens> oauthExchange(TicketExchangeRequest request) {
        return ResponseEntity.ok(mapper.toTokens(authService.exchangeTicket(request.getTicket())));
    }

    private static ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}
