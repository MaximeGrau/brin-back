package com.maximegrau.brin.service.auth.oauth;

import com.maximegrau.brin.model.user.AuthProvider;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

/** Connexion via un fournisseur externe, flux "authorization code" avec secret côté serveur. */
public interface OAuthClient {

    AuthProvider provider();

    /** Identifiants de l'app renseignés (sinon le fournisseur est désactivé). */
    boolean isConfigured();

    /** Page de connexion du fournisseur, qui reviendra sur {@code redirectUri} avec {@code code} et {@code state}. */
    URI authorizationUri(String state, String redirectUri);

    /**
     * Échange le code contre un jeton puis lit l'identité de l'utilisateur.
     *
     * @param redirectUri la même que pour {@link #authorizationUri} (exigé par les fournisseurs)
     * @throws OAuthException réponse inattendue du fournisseur
     */
    OAuthProfile fetchProfile(String code, String redirectUri);

    /** Ajoute des paramètres à une URL, chaque valeur étant encodée strictement. */
    static URI withQuery(String base, Map<String, String> params) {
        var builder = UriComponentsBuilder.fromUriString(base);
        var variables = new LinkedHashMap<String, String>();
        params.forEach((name, value) -> {
            var variable = "v" + variables.size();
            builder.queryParam(name, "{" + variable + "}");
            variables.put(variable, value);
        });
        return builder.encode().buildAndExpand(variables).toUri();
    }
}
