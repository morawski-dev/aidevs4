package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/**
 * Raw result of one {@code POST /verify} call in the radiomonitoring task: the HTTP status and the
 * raw body string. We read both ourselves (no exception on a non-2xx or a non-zero Hub {@code code})
 * because every intermediate reply — {@code start} acknowledgement, each captured {@code listen}
 * chunk, validation feedback on {@code transmit} — carries a non-zero {@code code} and is exactly the
 * data/feedback the driver must read. Pattern follows {@code OkoResponse}/{@code FoodWarehouseResponse}.
 */
record RadiomonitoringResponse(int status, String body) {

    /** Any {@code {FLG:...}} found anywhere in the body (returned by {@code transmit} on success). */
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
