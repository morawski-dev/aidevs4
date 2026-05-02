package com.morawski.dev.aidevs.tasks.task09mailbox;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST} to the zmail API or {@code /verify}: the HTTP status and the raw
 * body string. We read both ourselves (no exception on a non-zero Hub {@code code}) because the
 * "error" responses — wrong/missing answer fields — carry the feedback that drives the loop.
 */
record ZmailResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body, regardless of the response shape. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
