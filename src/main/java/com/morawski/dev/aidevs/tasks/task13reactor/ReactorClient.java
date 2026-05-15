package com.morawski.dev.aidevs.tasks.task13reactor;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.config.ReactorProperties;
import com.morawski.dev.aidevs.hub.dto.AnswerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Hub I/O for the reactor robot: sends one {@code {command}} per {@code POST /verify} and returns
 * the raw status + body so the caller can read the board state.
 *
 * <p>Like {@code RailwayClient}/{@code ElectricityClient}, the call is non-throwing: a non-zero Hub
 * {@code code} (robot not yet at the goal) is a normal mid-loop state we want to read, not a fatal
 * exception. Deliberately retryable statuses (429 rate limit / 503 overload) are retried with a
 * short bounded linear backoff. Every call is logged; the flag (once present) is extracted by the
 * caller via {@link ReactorResponse#flag()}.
 */
@Component
class ReactorClient {

    private static final Logger log = LoggerFactory.getLogger(ReactorClient.class);
    private static final String TASK = "reactor";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private final RestClient http;
    private final String apiKey;
    private final ReactorProperties props;

    ReactorClient(RestClient hubRestClient, HubProperties hub, ReactorProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Send one command and return the raw response, after any rate-limit/overload retries. */
    ReactorResponse command(Command c) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("command", c.toApi()));

        int attempt = 0;
        while (true) {
            ReactorResponse resp = exchange(body);
            log.info("reactor command={} -> HTTP {}  body: {}", c.toApi(), resp.status(), resp.body());

            if (isRetryable(resp.status()) && attempt < props.maxRetries()) {
                long wait = props.backoffMs() * (attempt + 1L);
                sleep(wait, "%d retry (attempt %d/%d)".formatted(resp.status(), attempt + 1, props.maxRetries()));
                attempt++;
                continue;
            }
            return resp;
        }
    }

    private static boolean isRetryable(int status) {
        return status == HTTP_TOO_MANY_REQUESTS || status == HTTP_SERVICE_UNAVAILABLE;
    }

    private ReactorResponse exchange(AnswerRequest body) {
        return http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling, so a non-2xx
                // (or Hub code!=0 inside a 200) doesn't throw — we inspect status/body ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read reactor response body", e);
                        raw = "";
                    }
                    return new ReactorResponse(status, raw);
                });
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
