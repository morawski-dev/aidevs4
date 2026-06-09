package com.morawski.dev.aidevs.tasks.task22phonecall;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} call in the phonecall dialog: the HTTP status and the raw body
 * string. We read both ourselves (no exception on a non-2xx or a non-zero Hub {@code code}) because
 * every mid-conversation reply (the operator's spoken/written turn, a hint, a "burned" signal) carries
 * a non-zero {@code code} and is exactly the data the state machine must read. Pattern follows
 * {@code FoodWarehouseResponse}/{@code EleResponse}.
 */
record PhonecallResponse(int status, String body) {

    /** Any {@code {FLG:...}} found in the body — returned by the Hub once the road is successfully unblocked. */
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
