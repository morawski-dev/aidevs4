package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Routing category for one decoded attachment, plus the pure classifier that decides it. This is the
 * cost gate of the task: we sniff the bytes locally (free) and only {@link #IMAGE}/{@link #AUDIO}
 * payloads ever reach a model — {@link #JSON_OR_TEXT} is read locally and {@link #NOISE} is dropped,
 * so large base64 binaries don't get billed as LLM tokens.
 *
 * <p>{@link #classify} trusts the <em>magic bytes</em> over the declared {@code meta} MIME (the brief
 * hints the stream may be mislabelled), falling back to the MIME hint and finally a UTF-8 readability
 * test. Unit-tested in {@code RadioRouterTest}.
 */
enum MediaKind {
    /** A plain text transcription (handled before classification — kept for completeness). */
    TEXT,
    /** Raster image (PNG/JPEG/GIF/BMP/WEBP) — OCR via a vision model. */
    IMAGE,
    /** JSON or UTF-8 text — decoded and read locally, no model. */
    JSON_OR_TEXT,
    /** Audio (MP3/WAV/OGG) — transcribe via an audio-capable model (rare; voice is usually pre-transcribed). */
    AUDIO,
    /** Unrecognised / unreadable binary — dropped (never sent to a model). */
    NOISE;

    /** Classify a decoded attachment from its declared MIME and a magic-byte sniff (magic wins). */
    static MediaKind classify(String meta, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return NOISE;
        }
        // 1. Magic bytes (most reliable — the declared meta may be wrong).
        MediaKind byMagic = byMagicBytes(bytes);
        if (byMagic != null) {
            return byMagic;
        }
        // 2. Declared MIME hint.
        String mime = meta == null ? "" : meta.trim().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) {
            return IMAGE;
        }
        if (mime.startsWith("audio/")) {
            return AUDIO;
        }
        if (mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") || mime.contains("csv")) {
            return JSON_OR_TEXT;
        }
        // 3. Last resort: is it readable UTF-8 text?
        return isProbablyText(bytes) ? JSON_OR_TEXT : NOISE;
    }

    private static MediaKind byMagicBytes(byte[] b) {
        if (startsWith(b, 0x89, 0x50, 0x4E, 0x47)            // PNG
                || startsWith(b, 0xFF, 0xD8, 0xFF)           // JPEG
                || startsWith(b, 0x47, 0x49, 0x46, 0x38)     // GIF8
                || startsWith(b, 0x42, 0x4D)) {              // BMP
            return IMAGE;
        }
        if (startsWith(b, 0x49, 0x44, 0x33)                  // MP3 with ID3 tag
                || startsWith(b, 0xFF, 0xFB)                 // MP3 frame sync
                || startsWith(b, 0xFF, 0xF3)
                || startsWith(b, 0xFF, 0xF2)
                || startsWith(b, 0x4F, 0x67, 0x67, 0x53)) {  // OggS
            return AUDIO;
        }
        // RIFF container is shared: ....WEBP = image, ....WAVE = audio.
        if (startsWith(b, 0x52, 0x49, 0x46, 0x46) && b.length >= 12) {
            if (matchesAt(b, 8, 0x57, 0x45, 0x42, 0x50)) {   // WEBP
                return IMAGE;
            }
            if (matchesAt(b, 8, 0x57, 0x41, 0x56, 0x45)) {   // WAVE
                return AUDIO;
            }
        }
        return null;
    }

    /** True if the bytes decode cleanly as UTF-8 and look like text (no control bytes besides whitespace). */
    static boolean isProbablyText(byte[] bytes) {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String decoded;
        try {
            decoded = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return false;
        }
        for (int i = 0; i < decoded.length(); i++) {
            char c = decoded.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                return false; // binary control byte → not text
            }
        }
        return true;
    }

    private static boolean startsWith(byte[] b, int... prefix) {
        return matchesAt(b, 0, prefix);
    }

    private static boolean matchesAt(byte[] b, int offset, int... expected) {
        if (b.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((b[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
