package com.morawski.dev.aidevs.tasks.task18domatowo;

import com.morawski.dev.aidevs.config.DomatowoProperties;
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
import java.util.Map;

/**
 * Hub I/O for the {@code domatowo} operation: sends one {@code {action}} per {@code POST /verify} and
 * returns the raw status + body so the caller can read the map / unit state / logs.
 *
 * <p>Like {@code ReactorClient}/{@code ElectricityClient}, the call is non-throwing: a non-zero Hub
 * {@code code} (operation still in progress) is a normal mid-loop state we want to read, not a fatal
 * exception. Deliberately retryable statuses (429 rate limit / 503 overload) are retried with a short
 * bounded linear backoff. Every call is logged; the flag (once present) is extracted by the caller via
 * {@link DomatowoResponse#flag()}.
 */
@Component
class DomatowoClient {

    private static final Logger log = LoggerFactory.getLogger(DomatowoClient.class);
    private static final String TASK = "domatowo";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private final RestClient http;
    private final String apiKey;
    private final DomatowoProperties props;

    DomatowoClient(RestClient hubRestClient, HubProperties hub, DomatowoProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Send one action and return the raw response, after any rate-limit/overload retries. */
    DomatowoResponse send(Map<String, Object> answer) {
        var body = new AnswerRequest(apiKey, TASK, answer);

        int attempt = 0;
        while (true) {
            DomatowoResponse resp = exchange(body);
            log.info("domatowo action={} -> HTTP {}  body: {}", answer.get("action"), resp.status(), resp.body());

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

    private DomatowoResponse exchange(AnswerRequest body) {
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
                        log.warn("Failed to read domatowo response body", e);
                        raw = "";
                    }
                    return new DomatowoResponse(status, raw);
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
