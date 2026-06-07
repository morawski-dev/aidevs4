package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic tests for the cost gate {@link MediaKind#classify}. The router's LLM branches are
 * exercised live, not here — these tests only pin the magic-byte sniffing that keeps binaries out of
 * the model.
 */
class RadioRouterTest {

    private static byte[] bytes(int... values) {
        byte[] b = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            b[i] = (byte) values[i];
        }
        return b;
    }

    /** A magic prefix followed by some filler so length checks pass. */
    private static byte[] withFiller(byte[] magic, int fillerLen) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(magic);
        out.writeBytes(new byte[fillerLen]);
        return out.toByteArray();
    }

    private static byte[] riff(String formType) {
        var out = new ByteArrayOutputStream();
        out.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(new byte[]{0, 0, 0, 0});                 // chunk size
        out.writeBytes(formType.getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    @Test
    void detectsImagesByMagicBytes() {
        assertThat(MediaKind.classify(null, withFiller(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), 16)))
                .isEqualTo(MediaKind.IMAGE); // PNG
        assertThat(MediaKind.classify(null, withFiller(bytes(0xFF, 0xD8, 0xFF), 16))).isEqualTo(MediaKind.IMAGE); // JPEG
        assertThat(MediaKind.classify(null, "GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEqualTo(MediaKind.IMAGE);
        assertThat(MediaKind.classify(null, "BM......".getBytes(StandardCharsets.US_ASCII))).isEqualTo(MediaKind.IMAGE); // BMP
        assertThat(MediaKind.classify(null, riff("WEBP"))).isEqualTo(MediaKind.IMAGE);
    }

    @Test
    void detectsAudioByMagicBytes() {
        assertThat(MediaKind.classify(null, withFiller("ID3".getBytes(StandardCharsets.US_ASCII), 16)))
                .isEqualTo(MediaKind.AUDIO); // MP3 w/ ID3
        assertThat(MediaKind.classify(null, withFiller(bytes(0xFF, 0xFB), 16))).isEqualTo(MediaKind.AUDIO); // MP3 frame
        assertThat(MediaKind.classify(null, withFiller("OggS".getBytes(StandardCharsets.US_ASCII), 16)))
                .isEqualTo(MediaKind.AUDIO);
        assertThat(MediaKind.classify(null, riff("WAVE"))).isEqualTo(MediaKind.AUDIO);
    }

    @Test
    void treatsUtf8AndJsonAsTextLocally() {
        assertThat(MediaKind.classify(null, "{\"city\":\"Syjon\",\"warehouses\":5}".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(MediaKind.JSON_OR_TEXT);
        assertThat(MediaKind.classify(null, "Kontakt: 123456789, powierzchnia 12.34 km2".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(MediaKind.JSON_OR_TEXT);
    }

    @Test
    void unknownBinaryIsNoise() {
        assertThat(MediaKind.classify("application/octet-stream", bytes(0x00, 0x01, 0x02, 0x7F, 0x80, 0xFE)))
                .isEqualTo(MediaKind.NOISE);
        assertThat(MediaKind.classify(null, new byte[0])).isEqualTo(MediaKind.NOISE);
    }

    @Test
    void magicBytesWinOverMisleadingMeta() {
        // bytes are clearly a PNG, but meta lies and says audio — magic wins.
        byte[] png = withFiller(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), 16);
        assertThat(MediaKind.classify("audio/mpeg", png)).isEqualTo(MediaKind.IMAGE);
    }

    @Test
    void metaHintUsedWhenNoMagicMatch() {
        // text bytes with no image magic, but meta declares an image → trust the hint.
        byte[] textBytes = "plain content".getBytes(StandardCharsets.UTF_8);
        assertThat(MediaKind.classify("image/png", textBytes)).isEqualTo(MediaKind.IMAGE);
    }
}
