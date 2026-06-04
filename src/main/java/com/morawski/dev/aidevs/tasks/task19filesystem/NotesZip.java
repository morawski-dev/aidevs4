package com.morawski.dev.aidevs.tasks.task19filesystem;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unzips {@code natan_notes.zip} (downloaded as a {@code byte[]}) into a name → UTF-8 text map. Natan's
 * notes are plain text files ({@code .txt}/{@code .md}); the archive holds no binary entries, so every
 * non-directory entry is read as a UTF-8 string. The file names carry Polish characters (e.g.
 * {@code ogłoszenia.txt}) but the contents are what matter. Pattern follows {@code SensorZip}.
 */
final class NotesZip {

    private NotesZip() {
    }

    /** Entry name → UTF-8 content, insertion-ordered, directory entries skipped. */
    static Map<String, String> read(byte[] zip) {
        var out = new LinkedHashMap<String, String>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                out.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read natan_notes zip", e);
        }
        return out;
    }
}
