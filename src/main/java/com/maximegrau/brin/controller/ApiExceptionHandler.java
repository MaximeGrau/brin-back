package com.maximegrau.brin.controller;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs non gérées nativement par Spring, renvoyées au format Problem Details (schéma Problem du contrat).
 * Les {@code ResponseStatusException} (404, curseur invalide…) le sont déjà grâce à spring.mvc.problemdetails.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /** Paramètre hors des bornes du contrat (ex. limit=0) : les interfaces générées sont validées par @Validated. */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onConstraintViolation(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Vidéo au-delà de spring.servlet.multipart.max-file-size : refusée avant même d'atteindre le contrôleur. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail onUploadTooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Vidéo trop lourde");
    }

    /** Jeton absent, invalide ou expiré (relayé par le point d'entrée de Spring Security, voir SecurityConfig). */
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> onAuthentication(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate", "Bearer")
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentification requise"));
    }
}
