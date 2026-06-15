package com.morawski.dev.aidevs.tasks.task18domatowo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builders for the {@code answer} payloads of the {@code domatowo} API. Each factory returns the
 * {@code answer} object ({@code {"action": ...}}) sent inside {@code AnswerRequest}.
 *
 * <p>The exact parameter names for {@code move}/{@code inspect}/{@code unload}/{@code getLogs} are
 * confirmed against the live {@code help} contract during recon and adjusted here in one place.
 */
final class Action {

    private Action() {
    }

    static Map<String, Object> help() {
        return Map.of("action", "help");
    }

    /** Full 11×11 map. */
    static Map<String, Object> getMap() {
        return Map.of("action", "getMap");
    }

    /** Map preview limited to the given terrain symbols. */
    static Map<String, Object> getMap(List<String> symbols) {
        var m = new LinkedHashMap<String, Object>();
        m.put("action", "getMap");
        m.put("symbols", symbols);
        return m;
    }

    /** Accumulated inspection results. */
    static Map<String, Object> getLogs() {
        return Map.of("action", "getLogs");
    }

    static Map<String, Object> createScout() {
        return Map.of("action", "create", "type", "scout");
    }

    static Map<String, Object> createTransporter(int passengers) {
        return Map.of("action", "create", "type", "transporter", "passengers", passengers);
    }

    static Map<String, Object> callHelicopter(String destination) {
        return Map.of("action", "callHelicopter", "destination", destination);
    }
}
