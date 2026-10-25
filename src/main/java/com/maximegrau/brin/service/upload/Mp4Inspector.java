package com.maximegrau.brin.service.upload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lit la durée et les dimensions d'un fichier MP4 / MOV (format ISO BMFF / QuickTime) sans le décoder :
 * boîtes {@code moov/mvhd} (durée) et {@code moov/trak/tkhd} (dimensions et matrice de rotation de la piste vidéo).
 * Les dimensions renvoyées sont celles de l'affichage : une vidéo de téléphone stockée en 1920×1080 avec une
 * rotation de 90° est verticale (1080×1920).
 */
public final class Mp4Inspector {

    public record VideoFileInfo(double durationSeconds, int width, int height) {
    }

    private record Box(String type, long contentStart, long end) {
    }

    private Mp4Inspector() {
    }

    /** @throws InvalidVideoException fichier qui n'est pas une vidéo MP4 / MOV lisible */
    public static VideoFileInfo inspect(Path file) {
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            var moov = find(children(channel, 0, channel.size()), "moov")
                    .orElseThrow(() -> new InvalidVideoException("pas de boîte moov"));
            var moovChildren = children(channel, moov.contentStart(), moov.end());
            var mvhd = find(moovChildren, "mvhd").orElseThrow(() -> new InvalidVideoException("pas de boîte mvhd"));
            var duration = readDuration(channel, mvhd);
            for (var trak : moovChildren.stream().filter(b -> b.type().equals("trak")).toList()) {
                var tkhd = find(children(channel, trak.contentStart(), trak.end()), "tkhd");
                if (tkhd.isPresent()) {
                    var size = readDisplaySize(channel, tkhd.get());
                    if (size[0] > 0 && size[1] > 0) {   // les pistes audio ont des dimensions nulles
                        return new VideoFileInfo(duration, size[0], size[1]);
                    }
                }
            }
            throw new InvalidVideoException("aucune piste vidéo");
        } catch (IOException e) {
            throw new InvalidVideoException("lecture impossible : " + e.getMessage());
        }
    }

    private static double readDuration(FileChannel channel, Box mvhd) throws IOException {
        var version = read(channel, mvhd.contentStart(), 1).get() & 0xFF;
        if (version == 1) {
            var b = read(channel, mvhd.contentStart() + 20, 12);
            long timescale = Integer.toUnsignedLong(b.getInt());
            long duration = b.getLong();
            return timescale == 0 ? 0 : (double) duration / timescale;
        }
        var b = read(channel, mvhd.contentStart() + 12, 8);
        long timescale = Integer.toUnsignedLong(b.getInt());
        long duration = Integer.toUnsignedLong(b.getInt());
        return timescale == 0 ? 0 : (double) duration / timescale;
    }

    /** {largeur, hauteur} à l'affichage, rotation de la matrice appliquée. */
    private static int[] readDisplaySize(FileChannel channel, Box tkhd) throws IOException {
        var version = read(channel, tkhd.contentStart(), 1).get() & 0xFF;
        var matrixOffset = version == 1 ? 52 : 40;
        var b = read(channel, tkhd.contentStart() + matrixOffset, 44);
        var a = b.getInt(0);
        var d = b.getInt(16);
        var width = b.getInt(36) >>> 16;    // 16.16 en virgule fixe
        var height = b.getInt(40) >>> 16;
        var rotated = a == 0 && d == 0;     // rotation de 90° ou 270°
        return rotated ? new int[] {height, width} : new int[] {width, height};
    }

    private static List<Box> children(FileChannel channel, long start, long end) throws IOException {
        var boxes = new ArrayList<Box>();
        var position = start;
        while (position + 8 <= end) {
            var header = read(channel, position, 8);
            long size = Integer.toUnsignedLong(header.getInt());
            var type = new String(new byte[] {header.get(), header.get(), header.get(), header.get()},
                    StandardCharsets.ISO_8859_1);
            var headerSize = 8;
            if (size == 1) {
                size = read(channel, position + 8, 8).getLong();
                headerSize = 16;
            } else if (size == 0) {
                size = end - position;   // jusqu'à la fin du conteneur
            }
            if (size < headerSize || position + size > end) {
                throw new InvalidVideoException("boîte " + type + " mal formée");
            }
            boxes.add(new Box(type, position + headerSize, position + size));
            position += size;
        }
        return boxes;
    }

    private static Optional<Box> find(List<Box> boxes, String type) {
        return boxes.stream().filter(b -> b.type().equals(type)).findFirst();
    }

    private static ByteBuffer read(FileChannel channel, long position, int length) throws IOException {
        if (position + length > channel.size()) {
            throw new InvalidVideoException("fichier tronqué");
        }
        var buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) {
                throw new InvalidVideoException("fichier tronqué");
            }
        }
        return buffer.flip();
    }
}
