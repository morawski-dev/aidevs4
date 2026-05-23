package com.morawski.dev.aidevs.tasks.task16okoeditor;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} call in the okoeditor task: the HTTP status and the raw body
 * string. We read both ourselves (no exception on a non-2xx or a non-zero Hub {@code code}) because
 * the API's "error" replies — wrong action, wrong field, validation messages, rate limit, 503 — carry
 * exactly the feedback the agent needs to read and react to. Pattern follows {@code FirmwareResponse}.
 */
record OkoResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }

    /** Statuses the API may use for transient back-pressure: 429 (rate limit) and 503 (overload). */
    boolean retryable() {
        return status == 429 || status == 503;
    }
}
