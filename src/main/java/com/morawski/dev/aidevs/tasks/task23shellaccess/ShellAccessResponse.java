package com.morawski.dev.aidevs.tasks.task23shellaccess;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} carrying a shell command ({@code answer:{cmd:...}}): the HTTP
 * status and the raw body string. We read both ourselves (no exception on a non-2xx) because the
 * server's replies — the command's stdout/stderr, a rate limit, a 503, or the {@code {FLG:...}} once
 * the printed JSON is correct — are exactly the output the agent needs to read and react to.
 */
record ShellAccessResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }

    /** Statuses the Hub uses for transient back-pressure: 429 (rate limit) and 503 (overload). */
    boolean retryable() {
        return status == 429 || status == 503;
    }
}
