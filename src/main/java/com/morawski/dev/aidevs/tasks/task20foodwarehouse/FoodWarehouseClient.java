package com.morawski.dev.aidevs.tasks.task20foodwarehouse;

import com.morawski.dev.aidevs.config.FoodWarehouseProperties;
import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.hub.dto.AnswerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Non-throwing client for the task20 foodwarehouse API. The whole API is the single endpoint
 * {@code POST /verify} with {@code task="foodwarehouse"} and an {@code answer:{tool, ...}} body — the
 * {@code tool} field selects the operation.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing: every intermediate reply (help text, database rows,
 * created-order id, generated signature, {@code done}'s "missing" list, validation errors) carries a
 * non-zero {@code code} and is data the driver must read. Retryable back-pressure (429/503) is
 * absorbed with a bounded linear backoff. Pattern follows {@code OkoClient}/{@code RailwayClient}.
 */
@Component
class FoodWarehouseClient {

    private static final Logger log = LoggerFactory.getLogger(FoodWarehouseClient.class);
    private static final String TASK = "foodwarehouse";

    private final RestClient http;
    private final String apiKey;
    private final FoodWarehouseProperties props;

    FoodWarehouseClient(RestClient hubRestClient, HubProperties hub, FoodWarehouseProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    FoodWarehouseResponse help() {
        return call(Map.of("tool", "help"));
    }

    /** Read-only SQLite access (SELECT / SHOW TABLES / SHOW CREATE TABLE / .schema). */
    FoodWarehouseResponse database(String query) {
        return call(Map.of("tool", "database", "query", query));
    }

    /** SHA1 signature for an order, bound to the creating user (login+birthday) and destination. */
    FoodWarehouseResponse generateSignature(String login, String birthday, int destination) {
        return call(Map.of(
                "tool", "signatureGenerator",
                "action", "generate",
                "login", login,
                "birthday", birthday,
                "destination", destination));
    }

    FoodWarehouseResponse ordersGet() {
        return call(Map.of("tool", "orders", "action", "get"));
    }

    /** Create an empty order; the response carries the new order {@code id} used by {@link #ordersAppend}. */
    FoodWarehouseResponse ordersCreate(String title, int creatorId, int destination, String signature) {
        return call(Map.of(
                "tool", "orders",
                "action", "create",
                "title", title,
                "creatorID", creatorId,
                "destination", destination,
                "signature", signature));
    }

    /** Batch-append items to an order ({@code {name: qty, ...}}); an existing item's quantity is increased. */
    FoodWarehouseResponse ordersAppend(String id, Map<String, Integer> items) {
        return call(Map.of("tool", "orders", "action", "append", "id", id, "items", items));
    }

    FoodWarehouseResponse reset() {
        return call(Map.of("tool", "reset"));
    }

    /** Final validation: returns the flag if all city orders are present, correct and well-signed. */
    FoodWarehouseResponse done() {
        return call(Map.of("tool", "done"));
    }

    /** Send one {@code answer:{tool, ...}} object to {@code /verify} and return the raw response (does not throw). */
    private FoodWarehouseResponse call(Map<String, Object> answer) {
        var body = new AnswerRequest(apiKey, TASK, new LinkedHashMap<>(answer));
        var resp = withRetries(body);
        log.info("foodwarehouse tool={} -> HTTP {}\n  body: {}", answer.get("tool"), resp.status(), resp.body());
        return resp;
    }

    /** Absorb deliberately-retryable statuses (429/503) with a short bounded linear backoff. */
    private FoodWarehouseResponse withRetries(AnswerRequest body) {
        int maxRetries = Math.max(0, props.maxRetries());
        long pauseMs = Math.max(0, props.retryPauseMs());
        FoodWarehouseResponse resp = exchange(body);
        for (int attempt = 1; attempt <= maxRetries && resp.retryable(); attempt++) {
            long wait = pauseMs * attempt;
            log.warn("/verify returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    resp.status(), wait, attempt, maxRetries);
            sleep(wait);
            resp = exchange(body);
        }
        return resp;
    }

    private FoodWarehouseResponse exchange(AnswerRequest body) {
        return http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling,
                // so 4xx/5xx don't throw — we inspect status/body ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read foodwarehouse /verify response body", e);
                        raw = "";
                    }
                    return new FoodWarehouseResponse(status, raw);
                });
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off on a retryable /verify response", e);
        }
    }
}
