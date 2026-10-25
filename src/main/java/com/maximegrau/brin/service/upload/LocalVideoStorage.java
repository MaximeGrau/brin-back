package com.maximegrau.brin.service.upload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Stockage sur le disque du serveur ({@code brin.upload.storage-dir}). Suffisant en local et pour un seul serveur ;
 * en prod, une implémentation S3 / R2 de {@link VideoStorage} permettra de servir les fichiers par un CDN.
 */
@Component
public class LocalVideoStorage implements VideoStorage {

    private final Path root;

    public LocalVideoStorage(UploadProperties properties) throws IOException {
        this.root = properties.storageDir().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public void store(String key, Path source) {
        try {
            var target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Stockage de " + key + " impossible", e);
        }
    }

    @Override
    public Resource load(String key) {
        return new FileSystemResource(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Suppression de " + key + " impossible", e);
        }
    }

    /** Refuse toute clé qui sortirait du dossier de stockage. */
    private Path resolve(String key) {
        var path = root.resolve(key).normalize();
        if (!key.matches("[a-z0-9/._-]+") || !path.startsWith(root)) {
            throw new IllegalArgumentException("Clé de stockage invalide : " + key);
        }
        return path;
    }
}
