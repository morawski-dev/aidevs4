package com.morawski.dev.aidevs.tasks.task19filesystem;

import com.morawski.dev.aidevs.config.FilesystemProperties;
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

/**
 * Non-throwing client for the filesystem {@code POST /verify} dialog. The {@code answer} field is an
 * {@code Object} ({@code AnswerRequest.answer}), so the same call carries either a single
 * {@code {action, ...}} map or a <b>batch</b> — an array of action maps the API runs sequentially.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing — per-action results, validation messages from
 * {@code done}, rate-limit and 503 are exactly the feedback the driver needs. Retryable back-pressure
 * (429/503) is absorbed with a bounded linear backoff. Pattern follows {@code OkoClient}.
 */
@Component
class FsClient {

    private static final Logger log = LoggerFactory.getLogger(FsClient.class);
    private static final String TASK = "filesystem";

    private final RestClient http;
    private final String apiKey;
    private final FilesystemProperties props;

    FsClient(RestClient hubRestClient, HubProperties hub, FilesystemProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Send one action map or a list of action maps to {@code /verify} and return the raw response. */
    FsResponse call(Object answer) {
        var body = new AnswerRequest(apiKey, TASK, answer);
        var resp = withRetries(body);
        log.info("filesystem -> HTTP {}\n  body: {}", resp.status(), resp.body());
        return resp;
    }

    private FsResponse withRetries(AnswerRequest body) {
        int maxRetries = Math.max(0, props.maxRetries());
        long pauseMs = Math.max(0, props.retryPauseMs());
        FsResponse resp = exchange(body);
        for (int attempt = 1; attempt <= maxRetries && resp.retryable(); attempt++) {
            long wait = pauseMs * attempt;
            log.warn("/verify returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    resp.status(), wait, attempt, maxRetries);
            sleep(wait);
            resp = exchange(body);
        }
        return resp;
    }

    private FsResponse exchange(AnswerRequest body) {
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
                        log.warn("Failed to read filesystem /verify response body", e);
                        raw = "";
                    }
                    return new FsResponse(status, raw);
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
