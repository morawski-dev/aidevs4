package com.morawski.dev.aidevs.tasks.task08failure;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code /verify} call in the failure task: HTTP status and raw body string.
 * Deliberately thin — during the iterative loop the Hub's body carries the verdict (flag, which
 * subsystem the technicians couldn't analyse, or a token-limit rejection), all read defensively
 * rather than through a typed contract.
 */
record FailureResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
