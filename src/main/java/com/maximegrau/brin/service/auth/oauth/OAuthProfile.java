package com.maximegrau.brin.service.auth.oauth;

/**
 * Identité renvoyée par un fournisseur.
 *
 * @param subject       identifiant stable de l'utilisateur chez le fournisseur
 * @param email         nulle si le fournisseur n'en donne pas (TikTok, Facebook selon le compte)
 * @param emailVerified le fournisseur garantit que l'utilisateur possède cette adresse
 */
public record OAuthProfile(String subject, String email, boolean emailVerified, String displayName) {
}
