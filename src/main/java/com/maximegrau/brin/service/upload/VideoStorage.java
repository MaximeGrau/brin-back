package com.maximegrau.brin.service.upload;

import java.nio.file.Path;
import org.springframework.core.io.Resource;

/** Stockage des fichiers envoyés sur Brin (vidéos et couvertures), adressés par une clé. */
public interface VideoStorage {

    /** Range le fichier sous {@code key} (le fichier source peut être déplacé). */
    void store(String key, Path source);

    Resource load(String key);

    /** Sans effet si la clé n'existe pas. */
    void delete(String key);
}
