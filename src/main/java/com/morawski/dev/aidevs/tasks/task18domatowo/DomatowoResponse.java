package com.morawski.dev.aidevs.tasks.task18domatowo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} action: the HTTP status and the raw body string.
 *
 * <p>Like {@code ReactorResponse}, the body is kept raw — intermediate actions return a non-zero Hub
 * {@code code} (which {@code HubClient.submit} would treat as fatal), so we inspect status/body here.
 */
record DomatowoResponse(int status, String body) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Any {@code {FLG:...}} found in the body — returned once the helicopter is called correctly. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }

    /** The Hub {@code code} field (0 = final success; non-zero = intermediate/normal mid-loop state). */
    int code() {
        return node().path("code").asInt(-1);
    }

    /** The Hub {@code message} field (human-readable status / error text), or empty. */
    String message() {
        return node().path("message").asText("");
    }

    private JsonNode node() {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }
}
