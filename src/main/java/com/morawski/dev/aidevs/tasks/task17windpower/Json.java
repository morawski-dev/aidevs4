package com.morawski.dev.aidevs.tasks.task17windpower;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.Locale;

/**
 * Tolerant JSON navigation for the windpower reports. The exact report shape (field names, units,
 * whether a report is nested) is unknown until the live {@code help}/recon pass, so instead of a
 * rigid DTO we search a parsed tree for the first value whose key <em>contains</em> any of a set of
 * candidate substrings (case-insensitive). This lets the parser survive small naming differences;
 * the candidate lists are the single place to adjust once recon confirms the real keys.
 */
final class Json {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    /** Parse a body to a tree; returns a missing node (never throws) on null/blank/invalid input. */
    static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return MissingNode.getInstance();
        }
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return MissingNode.getInstance();
        }
    }

    /** First numeric value (or numeric-looking text) under a key matching any candidate, searched recursively. */
    static Double findNumber(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            for (var it = node.fields(); it.hasNext(); ) {
                var e = it.next();
                if (keyMatches(e.getKey(), keys)) {
                    if (e.getValue().isNumber()) {
                        return e.getValue().asDouble();
                    }
                    if (e.getValue().isTextual()) {
                        Double d = tryParse(e.getValue().asText());
                        if (d != null) {
                            return d;
                        }
                    }
                }
            }
            for (var it = node.fields(); it.hasNext(); ) {
                Double r = findNumber(it.next().getValue(), keys);
                if (r != null) {
                    return r;
                }
            }
        } else if (node.isArray()) {
            for (var child : node) {
                Double r = findNumber(child, keys);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /** First scalar text under a key matching any candidate, searched recursively. */
    static String findText(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            for (var it = node.fields(); it.hasNext(); ) {
                var e = it.next();
                if (keyMatches(e.getKey(), keys) && e.getValue().isValueNode() && !e.getValue().isNull()) {
                    return e.getValue().asText();
                }
            }
            for (var it = node.fields(); it.hasNext(); ) {
                String r = findText(it.next().getValue(), keys);
                if (r != null) {
                    return r;
                }
            }
        } else if (node.isArray()) {
            for (var child : node) {
                String r = findText(child, keys);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Find the array of forecast slots: prefer an array whose key matches a candidate; otherwise fall
     * back to the first array of objects found anywhere in the tree (the weather report's hourly list).
     */
    static JsonNode findObjectArray(JsonNode node, String... keys) {
        JsonNode keyed = findArrayByKey(node, keys);
        return keyed != null ? keyed : firstObjectArray(node);
    }

    private static JsonNode findArrayByKey(JsonNode node, String[] keys) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            for (var it = node.fields(); it.hasNext(); ) {
                var e = it.next();
                if (keyMatches(e.getKey(), keys) && e.getValue().isArray() && e.getValue().size() > 0) {
                    return e.getValue();
                }
            }
            for (var it = node.fields(); it.hasNext(); ) {
                JsonNode r = findArrayByKey(it.next().getValue(), keys);
                if (r != null) {
                    return r;
                }
            }
        } else if (node.isArray()) {
            for (var child : node) {
                JsonNode r = findArrayByKey(child, keys);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static JsonNode firstObjectArray(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            if (node.size() > 0 && node.get(0).isObject()) {
                return node;
            }
            for (var child : node) {
                JsonNode r = firstObjectArray(child);
                if (r != null) {
                    return r;
                }
            }
        } else if (node.isObject()) {
            for (var it = node.fields(); it.hasNext(); ) {
                JsonNode r = firstObjectArray(it.next().getValue());
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private static boolean keyMatches(String key, String[] keys) {
        String k = key.toLowerCase(Locale.ROOT);
        for (String candidate : keys) {
            if (k.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Double tryParse(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
