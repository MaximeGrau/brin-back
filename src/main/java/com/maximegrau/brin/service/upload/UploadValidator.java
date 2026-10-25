package com.maximegrau.brin.service.upload;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Critères d'une vidéo envoyée sur Brin ({@code brin.upload.*}), calqués sur les Shorts YouTube. */
@Component
public class UploadValidator {

    private static final List<String> THUMBNAIL_TYPES = List.of("image/jpeg", "image/png");

    private final UploadProperties properties;

    public UploadValidator(UploadProperties properties) {
        this.properties = properties;
    }

    /** Critères du fichier lui-même (avant toute lecture du contenu). @return le type MIME retenu */
    public String checkVideoFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("Aucune vidéo reçue");
        }
        if (file.getSize() > properties.maxSize().toBytes()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Vidéo trop lourde : " + properties.maxSize().toMegabytes() + " Mo au plus");
        }
        var type = normalizedVideoType(file);
        if (type == null) {
            throw badRequest("Format non pris en charge : envoie une vidéo MP4 ou MOV");
        }
        return type;
    }

    /** @return le type MIME de la couverture, nul si aucune n'est fournie */
    public String checkThumbnail(MultipartFile thumbnail) {
        if (thumbnail == null || thumbnail.isEmpty()) {
            return null;
        }
        var type = thumbnail.getContentType() == null ? "" : thumbnail.getContentType().toLowerCase(Locale.ROOT);
        if (!THUMBNAIL_TYPES.contains(type) || thumbnail.getSize() > properties.maxThumbnailSize().toBytes()) {
            throw badRequest("Couverture invalide : JPEG ou PNG de "
                    + properties.maxThumbnailSize().toMegabytes() + " Mo au plus");
        }
        return type;
    }

    /** Lit la vidéo reçue et vérifie qu'il s'agit bien d'un short. */
    public Mp4Inspector.VideoFileInfo checkContent(Path file) {
        Mp4Inspector.VideoFileInfo info;
        try {
            info = Mp4Inspector.inspect(file);
        } catch (InvalidVideoException e) {
            throw badRequest("Fichier vidéo illisible (" + e.getMessage() + ")");
        }
        checkRules(info);
        return info;
    }

    /** Vertical, 3 minutes au plus. */
    void checkRules(Mp4Inspector.VideoFileInfo info) {
        var seconds = info.durationSeconds();
        if (seconds < properties.minDuration().toSeconds()) {
            throw badRequest("Vidéo trop courte : " + properties.minDuration().toSeconds() + " s au moins");
        }
        if (seconds > properties.maxDuration().toSeconds() + 0.5) {
            throw badRequest("Vidéo trop longue : " + formatDuration(properties.maxDuration().toSeconds())
                    + " au plus (elle dure " + formatDuration(Math.round(seconds)) + ")");
        }
        if ((double) info.height() / info.width() < properties.minAspectRatio()) {
            throw badRequest("La vidéo doit être verticale, au format 9:16 comme un short (elle est en "
                    + info.width() + "×" + info.height() + ")");
        }
        if (Math.min(info.width(), info.height()) < properties.minShortSide()) {
            throw badRequest("Résolution trop faible : " + properties.minShortSide() + " pixels de large au moins");
        }
    }

    /** Type MIME annoncé, ou déduit de l'extension quand l'app envoie un type générique. */
    private String normalizedVideoType(MultipartFile file) {
        var declared = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (properties.acceptedTypes().contains(declared)) {
            return declared;
        }
        var name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".mp4") || name.endsWith(".m4v")) {
            return "video/mp4";
        }
        return name.endsWith(".mov") ? "video/quicktime" : null;
    }

    private static String formatDuration(long seconds) {
        return seconds < 60 ? seconds + " s" : "%d min %02d".formatted(seconds / 60, seconds % 60);
    }

    private static ResponseStatusException badRequest(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }
}
