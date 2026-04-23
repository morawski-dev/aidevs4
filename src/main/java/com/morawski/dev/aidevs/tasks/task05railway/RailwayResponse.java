package com.morawski.dev.aidevs.tasks.task05railway;

import com.morawski.dev.aidevs.hub.FlagExtractor;
import org.springframework.http.HttpHeaders;

import java.util.Optional;

/**
 * Raw result of one {@code /verify} call in the railway task: the HTTP status, response headers
 * (kept for rate-limit/503 handling) and the raw body string.
 */
record RailwayResponse(int status, HttpHeaders headers, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
