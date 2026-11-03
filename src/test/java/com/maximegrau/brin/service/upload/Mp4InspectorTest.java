package com.maximegrau.brin.service.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Fichiers MP4 / MOV fabriqués boîte par boîte (structure ISO BMFF réelle, sans données vidéo). */
class Mp4InspectorTest {

    private static final int[] IDENTITY = {0x10000, 0, 0, 0, 0x10000, 0, 0, 0, 0x40000000};
    private static final int[] ROTATE_90 = {0, 0x10000, 0, -0x10000, 0, 0, 0, 0, 0x40000000};

    @TempDir
    Path dir;

    @Test
    void litLaDureeEtLesDimensionsDUnShortVertical() throws IOException {
        var file = write(box("ftyp", ascii("isom")), box("moov", mvhd(0, 600, 27_000), trak(1080, 1920, IDENTITY)),
                box("mdat", new byte[64]));

        var info = Mp4Inspector.inspect(file);

        assertThat(info.durationSeconds()).isEqualTo(45.0);
        assertThat(info.width()).isEqualTo(1080);
        assertThat(info.height()).isEqualTo(1920);
    }

    @Test
    void appliqueLaRotationDUneVideoDeTelephone() throws IOException {
        // iPhone : stockée en 1920×1080 avec une rotation de 90° → affichée verticale.
        var file = write(box("ftyp", ascii("qt  ")), box("moov", mvhd(1, 1000, 12_500), trak(1920, 1080, ROTATE_90)));

        var info = Mp4Inspector.inspect(file);

        assertThat(info.durationSeconds()).isEqualTo(12.5);
        assertThat(info.width()).isEqualTo(1080);
        assertThat(info.height()).isEqualTo(1920);
    }

    @Test
    void ignoreLaPisteAudioEtTrouveMoovEnFinDeFichier() throws IOException {
        var file = write(box("ftyp", ascii("isom")), box("mdat", new byte[128]),
                box("moov", mvhd(0, 1000, 30_000), trak(0, 0, IDENTITY), trak(720, 1280, IDENTITY)));

        var info = Mp4Inspector.inspect(file);

        assertThat(info.width()).isEqualTo(720);
        assertThat(info.height()).isEqualTo(1280);
    }

    @Test
    void refuseCeQuiNEstPasUneVideo() throws IOException {
        var text = Files.writeString(dir.resolve("pas-une-video.mp4"), "bonjour, je ne suis pas une vidéo");
        var noVideoTrack = write(box("ftyp", ascii("isom")), box("moov", mvhd(0, 1000, 5_000), trak(0, 0, IDENTITY)));

        assertThatThrownBy(() -> Mp4Inspector.inspect(text)).isInstanceOf(InvalidVideoException.class);
        assertThatThrownBy(() -> Mp4Inspector.inspect(noVideoTrack)).isInstanceOf(InvalidVideoException.class)
                .hasMessageContaining("aucune piste vidéo");
    }

    private Path write(byte[]... boxes) throws IOException {
        var out = new ByteArrayOutputStream();
        for (var box : boxes) {
            out.write(box);
        }
        return Files.write(Files.createTempFile(dir, "video", ".mp4"), out.toByteArray());
    }

    private static byte[] mvhd(int version, int timescale, long duration) {
        var b = ByteBuffer.allocate(version == 1 ? 112 : 100);
        b.put((byte) version).put(new byte[3]);
        if (version == 1) {
            b.putLong(0).putLong(0).putInt(timescale).putLong(duration);
        } else {
            b.putInt(0).putInt(0).putInt(timescale).putInt((int) duration);
        }
        return box("mvhd", b.array());
    }

    private static byte[] trak(int width, int height, int[] matrix) {
        var b = ByteBuffer.allocate(84);
        b.put((byte) 0).put(new byte[3]);         // version 0, flags
        b.putInt(0).putInt(0).putInt(1).putInt(0).putInt(0);   // création, modification, piste, réservé, durée
        b.put(new byte[8]).putShort((short) 0).putShort((short) 0).putShort((short) 0).putShort((short) 0);
        for (var m : matrix) {
            b.putInt(m);
        }
        b.putInt(width << 16).putInt(height << 16);
        return box("trak", box("tkhd", b.array()));
    }

    private static byte[] box(String type, byte[]... contents) {
        var size = 8;
        for (var c : contents) {
            size += c.length;
        }
        var b = ByteBuffer.allocate(size).putInt(size).put(ascii(type));
        for (var c : contents) {
            b.put(c);
        }
        return b.array();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }
}
