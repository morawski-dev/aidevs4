package com.morawski.dev.aidevs.tasks.task06categorize;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code /verify} call in the categorize task: HTTP status and raw body string.
 * Kept deliberately thin — the body carries the Hub's verdict (flag, which item was misclassified,
 * or a budget message), all of which we read defensively rather than via a typed contract.
 */
record CategorizeResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
