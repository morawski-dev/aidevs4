package com.morawski.dev.aidevs.tasks.task11evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Unzips {@code sensors.zip} (downloaded as a {@code byte[]}) into {@link SensorReading}s. Only {@code .json}
 * entries are parsed — the archive may also hold a directory entry or a stray readme. Parsing is done via
 * {@link ObjectMapper#readTree} and field-name lookups so it tolerates field ordering and any extra fields;
 * the {@code fileId} is the entry name stripped of its directory and {@code .json} suffix, which is exactly
 * the bare numeric id the Centrala accepts in the {@code recheck} array.
 */
final class SensorZip {

    private SensorZip() {
    }

    static List<SensorReading> read(byte[] zip, ObjectMapper mapper) {
        var out = new ArrayList<SensorReading>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase().endsWith(".json")) {
                    continue;
                }
                var node = mapper.readTree(zis.readAllBytes());
                out.add(toReading(fileId(entry.getName()), node));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read sensors zip", e);
        }
        return out;
    }

    /** Entry name → bare id: drop any directory prefix and the {@code .json} suffix. */
    private static String fileId(String entryName) {
        var slash = Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\'));
        var name = slash >= 0 ? entryName.substring(slash + 1) : entryName;
        var dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static SensorReading toReading(String fileId, JsonNode n) {
        return new SensorReading(
                fileId,
                n.path("sensor_type").asText(""),
                n.path("temperature_K").asDouble(0),
                n.path("pressure_bar").asDouble(0),
                n.path("water_level_meters").asDouble(0),
                n.path("voltage_supply_v").asDouble(0),
                n.path("humidity_percent").asDouble(0),
                n.path("operator_notes").asText(""));
    }
}
