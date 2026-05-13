package com.morawski.dev.aidevs.tasks.task12firmware;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST} to the shell API ({@code /api/shell}) or {@code /verify}: the HTTP
 * status and the raw body string. We read both ourselves (no exception on a non-2xx) because the
 * shell API's "error" replies — rate limit, ban, 503, or a non-zero shell exit — carry exactly the
 * output the agent needs to read and react to.
 */
record FirmwareResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }

    /**
     * Statuses the shell API uses deliberately for transient back-pressure: 429 (rate limit) and 503
     * (simulated overload). A ban is <em>not</em> retryable here — it has a fixed duration and the
     * agent must read it and decide to wait/stop, so we surface that body verbatim instead.
     */
    boolean retryable() {
        return status == 429 || status == 503;
    }
}
