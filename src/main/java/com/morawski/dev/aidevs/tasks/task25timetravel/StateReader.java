package com.morawski.dev.aidevs.tasks.task25timetravel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser odpowiedzi {@code getConfig}/{@code timeTravel} → {@link DeviceState}. Kształt JSON potwierdzony
 * reconem (config zagnieżdżony pod {@code config}, podpowiedź pod {@code needConfig}), ale parser jest
 * tolerancyjny: przeszukuje całe drzewo i bierze pierwsze pole pasujące (bez wielkości liter) do
 * kandydata. Liczby bywają tekstem (np. {@code "100%"}) — wyłuskujemy z nich pierwszą liczbę.
 *
 * <p>Czysta logika — testowana w {@code StateReaderTest}.
 */
final class StateReader {

    private static final Logger log = LoggerFactory.getLogger(StateReader.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern FIRST_INT = Pattern.compile("-?\\d+");

    private StateReader() {
    }

    static DeviceState parse(String body) {
        JsonNode root;
        try {
            root = MAPPER.readTree(body == null ? "" : body);
        } catch (Exception e) {
            log.debug("getConfig body is not JSON, exposing raw only: {}", body);
            root = MAPPER.nullNode();
        }

        return new DeviceState(
                findInt(root, "internalMode", "internal_mode", "phase"),
                findInt(root, "fluxDensity", "flux_density", "flux"),
                findInt(root, "day"),
                findInt(root, "month"),
                findInt(root, "year"),
                findText(root, "syncRatio", "sync_ratio", "sync"),
                findText(root, "stabilization", "stab"),
                findText(root, "needConfig", "stabilizationHint", "stabilization_hint", "hint", "podpowiedz"),
                findText(root, "mode", "deviceMode", "powerMode"),
                findText(root, "condition", "state", "stan"),
                findText(root, "batteryStatus", "battery", "bateria"),
                findBool(root, "PTA", "PT-A", "ptA", "pta"),
                findBool(root, "PTB", "PT-B", "ptB", "ptb"),
                findInt(root, "PWR", "pwr"),
                findText(root, "currentDate", "current_date"),
                findInt(root, "code"),
                findText(root, "message", "msg", "info"),
                body);
    }

    // --- defensive tree search ----------------------------------------------

    private static Integer findInt(JsonNode root, String... candidates) {
        JsonNode node = find(root, candidates);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        Matcher m = FIRST_INT.matcher(node.asText(""));
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private static String findText(JsonNode root, String... candidates) {
        JsonNode node = find(root, candidates);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = node.isValueNode() ? node.asText() : node.toString();
        return text.isBlank() ? null : text.trim();
    }

    private static Boolean findBool(JsonNode root, String... candidates) {
        JsonNode node = find(root, candidates);
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        String text = node.asText("").trim().toLowerCase();
        if (text.equals("true") || text.equals("on") || text.equals("1")) {
            return true;
        }
        if (text.equals("false") || text.equals("off") || text.equals("0")) {
            return false;
        }
        return null;
    }

    /** Depth-first search for the first field whose name matches any candidate (case-insensitive). */
    private static JsonNode find(JsonNode node, String... candidates) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                for (String candidate : candidates) {
                    if (entry.getKey().equalsIgnoreCase(candidate)) {
                        return entry.getValue();
                    }
                }
            }
            for (JsonNode child : node) {
                JsonNode hit = find(child, candidates);
                if (hit != null) {
                    return hit;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode hit = find(child, candidates);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
