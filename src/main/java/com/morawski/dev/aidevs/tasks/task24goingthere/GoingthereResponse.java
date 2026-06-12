package com.morawski.dev.aidevs.tasks.task24goingthere;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/** Raw result of one HTTP call to the {@code goingthere} endpoints: the status and the raw body. */
record GoingthereResponse(int status, String body) {

    /** Any {@code {FLG:...}} found in the body — returned once the rocket reaches Grudziądz. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }

    /**
     * Whether the call should simply be retried. The OKO jamming makes the API return random errors
     * even for valid requests (429 rate-limit, and assorted 5xx — 500/502/503/504 observed in recon),
     * and a status of 0 means we failed to even read the response. All are transient ⇒ re-request.
     */
    boolean retryable() {
        return status == 429 || status >= 500 || status == 0;
    }
}
