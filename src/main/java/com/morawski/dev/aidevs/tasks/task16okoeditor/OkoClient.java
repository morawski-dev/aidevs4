package com.morawski.dev.aidevs.tasks.task16okoeditor;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.config.OkoEditorProperties;
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
 * Non-throwing client for the okoeditor {@code POST /verify} dialog (the OKO Operations Center
 * "backdoor"). All edits go through {@code /verify} with {@code task="okoeditor"} and an
 * {@code answer:{action, ...params}} body.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing — the API's "error" replies (wrong action, validation
 * messages, non-zero {@code code} on intermediate/list responses, rate limit, 503) are exactly the
 * feedback the agent needs to read and correct. Retryable back-pressure (429/503) is absorbed with a
 * bounded linear backoff so the agent's view stays clean. Pattern follows {@code ShellClient}/
 * {@code RailwayClient}.
 */
@Component
class OkoClient {

    private static final Logger log = LoggerFactory.getLogger(OkoClient.class);
    private static final String TASK = "okoeditor";

    private final RestClient http;
    private final String apiKey;
    private final OkoEditorProperties props;

    OkoClient(RestClient hubRestClient, HubProperties hub, OkoEditorProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Send one {@code {action, ...params}} to {@code /verify} and return the raw response (does not throw). */
    OkoResponse call(Map<String, Object> action) {
        var body = new AnswerRequest(apiKey, TASK, action);
        var resp = withRetries(body);
        log.info("okoeditor action={} -> HTTP {}\n  body: {}", action, resp.status(), resp.body());
        return resp;
    }

    /** Absorb deliberately-retryable statuses (429/503) with a short bounded linear backoff. */
    private OkoResponse withRetries(AnswerRequest body) {
        int maxRetries = Math.max(0, props.maxRetries());
        long pauseMs = Math.max(0, props.retryPauseMs());
        OkoResponse resp = exchange(body);
        for (int attempt = 1; attempt <= maxRetries && resp.retryable(); attempt++) {
            long wait = pauseMs * attempt;
            log.warn("/verify returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    resp.status(), wait, attempt, maxRetries);
            sleep(wait);
            resp = exchange(body);
        }
        return resp;
    }

    private OkoResponse exchange(AnswerRequest body) {
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
                        log.warn("Failed to read okoeditor /verify response body", e);
                        raw = "";
                    }
                    return new OkoResponse(status, raw);
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
