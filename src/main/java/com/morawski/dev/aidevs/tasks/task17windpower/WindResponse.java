package com.morawski.dev.aidevs.tasks.task17windpower;

import com.fasterxml.jackson.databind.JsonNode;
import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} windpower action: the HTTP status and the raw body string.
 * Like {@code EleResponse}, the client is non-throwing — a non-zero Hub {@code code} (e.g. "report
 * not ready yet") is a normal mid-flow state we read, not a fatal exception.
 */
record WindResponse(int status, String body) {

    /** Any {@code {FLG:...}} found in the body — returned by the Hub once {@code done} validates the schedule. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }

    /** The body parsed as a JSON tree (a missing node if the body isn't valid JSON). */
    JsonNode json() {
        return Json.parse(body);
    }
}
