package com.morawski.dev.aidevs.tasks.task20foodwarehouse;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} call in the foodwarehouse task: the HTTP status and the raw
 * body string. We read both ourselves (no exception on a non-2xx or a non-zero Hub {@code code})
 * because every intermediate tool reply ({@code help}, {@code database}, {@code orders},
 * {@code signatureGenerator}, {@code done}'s "missing" list) carries a non-zero {@code code} and is
 * exactly the data/feedback the driver needs to read. Pattern follows {@code OkoResponse}.
 */
record FoodWarehouseResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body (returned by {@code done} on success). */
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
