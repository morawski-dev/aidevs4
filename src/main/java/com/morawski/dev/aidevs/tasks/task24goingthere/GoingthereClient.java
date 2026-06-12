package com.morawski.dev.aidevs.tasks.task24goingthere;

import com.morawski.dev.aidevs.config.GoingthereProperties;
import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.hub.dto.AnswerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Hub I/O for the {@code goingthere} rocket game. Four interactions, all non-throwing (a non-2xx or a
 * Hub {@code code != 0} is a normal mid-game state to read, not a fatal exception) and all wrapped in a
 * short bounded linear backoff for the deliberately-retryable statuses (429 rate limit / 503 overload):
 *
 * <ul>
 *   <li>{@link #command(Command)} — {@code POST /verify} a move/start command.</li>
 *   <li>{@link #scan()} — {@code GET /api/frequencyScanner?key=...} to check for a radar lock.</li>
 *   <li>{@link #disarm(String, String)} — {@code POST /api/frequencyScanner} to neutralise a lock.</li>
 *   <li>{@link #message()} — {@code POST /api/getmessage} for the forward radio hint.</li>
 * </ul>
 *
 * <p><b>Param trap:</b> the GET scanner authenticates with the query param {@code key}, while every POST
 * uses the body field {@code apikey}.
 */
@Component
class GoingthereClient {

    private static final Logger log = LoggerFactory.getLogger(GoingthereClient.class);
    private static final String TASK = "goingthere";

    private final RestClient http;
    private final String apiKey;
    private final GoingthereProperties props;

    GoingthereClient(RestClient hubRestClient, HubProperties hub, GoingthereProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Send one move/start command to {@code /verify}. */
    GoingthereResponse command(Command c) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("command", c.toApi()));
        return withRetries("command " + c.toApi(), () -> http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> toResponse(response)));
    }

    /** Check the frequency scanner for a radar lock at the current position. */
    GoingthereResponse scan() {
        return withRetries("scan", () -> http.get()
                .uri(b -> b.path("/api/frequencyScanner").queryParam("key", apiKey).build())
                .exchange((request, response) -> toResponse(response)));
    }

    /** Neutralise a detected radar lock with the disarm hash. */
    GoingthereResponse disarm(String frequency, String disarmHash) {
        long freq;
        try {
            freq = (long) Double.parseDouble(frequency.trim()); // server wants a plain integer
        } catch (NumberFormatException e) {
            freq = 0;
        }
        // Build the JSON ourselves so the exact bytes are unambiguous (frequency as a bare integer).
        String json = "{\"apikey\":\"%s\",\"frequency\":%d,\"disarmHash\":\"%s\"}".formatted(apiKey, freq, disarmHash);
        log.info("disarm payload: {}", json);
        return withRetries("disarm", () -> http.post()
                .uri("/api/frequencyScanner")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange((request, response) -> toResponse(response)));
    }

    /** Request the forward radio hint about the rock in the next column. */
    GoingthereResponse message() {
        var body = Map.of("apikey", apiKey);
        return withRetries("getmessage", () -> http.post()
                .uri("/api/getmessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> toResponse(response)));
    }

    /** Run {@code call}, retrying retryable (429/503) statuses with bounded linear backoff. */
    private GoingthereResponse withRetries(String label, Supplier<GoingthereResponse> call) {
        int attempt = 0;
        while (true) {
            GoingthereResponse resp = call.get();
            log.info("{} -> HTTP {}  body: {}", label, resp.status(), resp.body());
            if (resp.retryable() && attempt < props.maxRetries()) {
                long wait = props.backoffMs() * (attempt + 1L);
                sleep(wait, "%d retry (attempt %d/%d)".formatted(resp.status(), attempt + 1, props.maxRetries()));
                attempt++;
                continue;
            }
            return resp;
        }
    }

    /**
     * Read the raw status + body without applying default error handling, so a non-2xx (or a Hub
     * {@code code != 0} inside a 200) doesn't throw — we inspect status/body ourselves.
     */
    private static GoingthereResponse toResponse(ClientHttpResponse response) {
        int status = 0;
        String raw = "";
        try (var in = response.getBody()) {
            status = response.getStatusCode().value();
            raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read goingthere response body", e);
        }
        return new GoingthereResponse(status, raw);
    }

    private void sleep(long ms, String reason) {
        if (ms <= 0) {
            return;
        }
        log.info("Sleeping {} ms ({})", ms, reason);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting (" + reason + ")", e);
        }
    }
}
