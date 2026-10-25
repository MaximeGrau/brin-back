package com.maximegrau.brin.controller;

import com.maximegrau.brin.config.WebConfig;
import com.maximegrau.brin.model.video.Video;
import com.maximegrau.brin.service.upload.UploadProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Liens signés vers les fichiers des vidéos envoyées sur Brin ({@code /media/{kind}/{id}?exp=…&sig=…}).
 * Le lecteur vidéo de l'app ne peut pas envoyer le jeton d'accès : le lien porte sa propre autorisation (HMAC),
 * valable {@code brin.upload.media-url-ttl}, comme une URL présignée S3 (même principe si le stockage y passe).
 * L'expiration est arrondie à l'heure : le lien reste identique d'une page à l'autre, le lecteur peut le mettre en cache.
 */
@Component
public class MediaUrls {

    public static final String VIDEO = "video";
    public static final String THUMBNAIL = "thumbnail";

    private final SecretKey key;
    private final UploadProperties properties;
    private final Clock clock;

    public MediaUrls(SecretKey jwtKey, UploadProperties properties, Clock clock) {
        this.key = jwtKey;
        this.properties = properties;
        this.clock = clock;
    }

    public URI video(Video video) {
        return url(VIDEO, video.getId());
    }

    /** Nul si l'auteur n'a pas fourni de couverture. */
    public URI thumbnail(Video video) {
        return video.getThumbnailKey() == null ? null : url(THUMBNAIL, video.getId());
    }

    public boolean isValid(String kind, String videoId, long exp, String signature) {
        if (exp < clock.instant().getEpochSecond()) {
            return false;
        }
        return MessageDigest.isEqual(sign(kind, videoId, exp).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    /** Lien absolu construit sur l'hôte de la requête en cours (l'app le joint comme elle joint l'API). */
    private URI url(String kind, UUID videoId) {
        var exp = clock.instant().truncatedTo(ChronoUnit.HOURS).plus(properties.mediaUrlTtl()).getEpochSecond();
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(WebConfig.API_BASE_PATH + "/media/{kind}/{id}")
                .queryParam("exp", exp)
                .queryParam("sig", sign(kind, videoId.toString(), exp))
                .buildAndExpand(kind, videoId)
                .toUri();
    }

    private String sign(String kind, String videoId, long exp) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            var digest = mac.doFinal(("media:" + kind + ":" + videoId + ":" + exp).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
