package com.morawski.dev.aidevs.tasks.task05railway;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.config.RailwayProperties;
import com.morawski.dev.aidevs.hub.dto.AnswerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resilient client for the railway {@code POST /verify} dialog. Unlike {@code HubClient.submit}
 * (which throws on HTTP 503 and on a non-zero Hub {@code code}), this one reads the full
 * status + headers + body, retries 503 with exponential backoff, and holds the next call until
 * the rate-limit reset window passes — both being deliberate parts of the task, not failures.
 *
 * <p>Every call (action, status, all headers, raw body) is logged: at restrictive rate limits
 * with random 503s, good logging is the only way to debug. The rate-limit header names are not
 * documented, so the parser is best-effort across common conventions ({@code Retry-After},
 * {@code X-RateLimit-*}, {@code RateLimit-*}).
 */
@Component
class RailwayClient {

    private static final Logger log = LoggerFactory.getLogger(RailwayClient.class);
    private static final String TASK = "railway";
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;

    private final RestClient http;
    private final String apiKey;
    private final RailwayProperties props;

    /** Earliest wall-clock time (ms) the next request may be sent, derived from rate-limit headers. */
    private long nextAllowedAtMs = 0L;

    RailwayClient(RestClient hubRestClient, HubProperties hub, RailwayProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Send one {@code {action, ...params}} and return the raw response, after all waiting/retries. */
    RailwayResponse call(Map<String, Object> action) {
        waitForRateLimitWindow();
        var body = new AnswerRequest(apiKey, TASK, action);

        int attempt = 0;
        while (true) {
            RailwayResponse resp = exchange(body);
            logResponse(action, resp);

            // 429 (rate limit) and 503 (simulated overload) are both deliberate and retryable:
            // wait — 429 until the limit resets, 503 with exponential backoff — and resend.
            if (isRetryable(resp.status()) && attempt < props.maxRetries()) {
                long wait = retryWaitMs(resp, attempt);
                sleep(wait, "%d retry (attempt %d/%d)".formatted(resp.status(), attempt + 1, props.maxRetries()));
                attempt++;
                continue;
            }
            updateRateLimitWindow(resp.headers());
            return resp;
        }
    }

    private static boolean isRetryable(int status) {
        return status == HTTP_TOO_MANY_REQUESTS || status == HTTP_SERVICE_UNAVAILABLE;
    }

    private long retryWaitMs(RailwayResponse resp, int attempt) {
        if (resp.status() == HTTP_TOO_MANY_REQUESTS) {
            long reset = resetWaitMs(resp.headers());
            long base = reset > 0 ? reset : props.baseBackoffMs();
            return base + props.rateLimitSafetyMarginMs();
        }
        return backoffMs(resp, attempt); // 503
    }

    private RailwayResponse exchange(AnswerRequest body) {
        return http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without applying default error handling,
                // so 4xx/5xx don't throw — we inspect status/headers ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    HttpHeaders headers = response.getHeaders();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read railway response body", e);
                        raw = "";
                    }
                    return new RailwayResponse(status, headers, raw);
                });
    }

    private void logResponse(Map<String, Object> action, RailwayResponse resp) {
        log.info("railway action={} -> HTTP {}\n  headers: {}\n  body: {}",
                action, resp.status(), resp.headers(), resp.body());
    }

    // --- rate limiting -------------------------------------------------------

    private void waitForRateLimitWindow() {
        long wait = nextAllowedAtMs - System.currentTimeMillis();
        if (wait > 0) {
            sleep(wait, "rate-limit window");
        }
    }

    private void updateRateLimitWindow(HttpHeaders headers) {
        Long remaining = firstLong(headers, "X-RateLimit-Remaining", "RateLimit-Remaining", "X-Rate-Limit-Remaining");
        if (remaining != null && remaining > 0) {
            nextAllowedAtMs = 0L;
            return;
        }
        long wait = resetWaitMs(headers);
        if (wait > 0) {
            long total = wait + props.rateLimitSafetyMarginMs();
            nextAllowedAtMs = System.currentTimeMillis() + total;
            log.info("Rate limit exhausted (remaining={}) — holding next call for {} ms", remaining, total);
        } else {
            nextAllowedAtMs = 0L;
        }
    }

    /** Milliseconds to wait for the limit to reset, from {@code Retry-After} or a {@code *-Reset} header. */
    private long resetWaitMs(HttpHeaders headers) {
        Long retryAfter = firstLong(headers, "Retry-After");
        if (retryAfter != null) {
            return retryAfter * 1000L;
        }
        Long reset = firstLong(headers, "X-RateLimit-Reset", "RateLimit-Reset", "X-Rate-Limit-Reset");
        if (reset == null) {
            return 0L;
        }
        long now = System.currentTimeMillis();
        if (reset > 100_000_000_000L) {
            return Math.max(0L, reset - now);           // epoch millis
        }
        if (reset > 1_000_000_000L) {
            return Math.max(0L, reset * 1000L - now);   // epoch seconds
        }
        return reset * 1000L;                           // seconds until reset
    }

    private long backoffMs(RailwayResponse resp, int attempt) {
        Long retryAfter = firstLong(resp.headers(), "Retry-After");
        if (retryAfter != null) {
            return retryAfter * 1000L;
        }
        double exp = props.baseBackoffMs() * Math.pow(2, attempt);
        long capped = (long) Math.min(exp, props.maxBackoffMs());
        long jitter = (long) (capped * 0.2 * ThreadLocalRandom.current().nextDouble());
        return capped + jitter;
    }

    private static Long firstLong(HttpHeaders headers, String... names) {
        for (String name : names) {
            String value = headers.getFirst(name);
            if (value != null) {
                try {
                    return Long.parseLong(value.trim());
                } catch (NumberFormatException ignored) {
                    // header present but not a plain number (e.g. Retry-After as an HTTP date) — skip
                }
            }
        }
        return null;
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
