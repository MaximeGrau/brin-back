package com.maximegrau.brin.service.upload;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/** {@code brin.upload.*} : critères des vidéos envoyées sur Brin (calqués sur les Shorts YouTube) et stockage. */
@ConfigurationProperties("brin.upload")
public record UploadProperties(
        /** Dossier des fichiers envoyés (stockage local ; à remplacer par un stockage objet en prod). */
        @DefaultValue("./data/uploads") Path storageDir,
        @DefaultValue("100MB") DataSize maxSize,
        @DefaultValue("3s") Duration minDuration,
        /** Comme les Shorts YouTube : 3 minutes au plus. */
        @DefaultValue("180s") Duration maxDuration,
        /** Hauteur / largeur minimale à l'affichage : 1,25 accepte du 4:5 au 9:16, refuse le carré et le paysage. */
        @DefaultValue("1.25") double minAspectRatio,
        @DefaultValue("360") int minShortSide,
        @DefaultValue({"video/mp4", "video/quicktime"}) List<String> acceptedTypes,
        @DefaultValue("2MB") DataSize maxThumbnailSize,
        /** Vidéos en analyse en même temps au plus par utilisateur (anti-spam). */
        @DefaultValue("5") int maxPending,
        /** Durée de validité des liens signés vers les fichiers. */
        @DefaultValue("6h") Duration mediaUrlTtl) {
}
