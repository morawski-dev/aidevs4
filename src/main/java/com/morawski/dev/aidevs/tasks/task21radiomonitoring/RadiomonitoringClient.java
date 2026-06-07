package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.config.RadiomonitoringProperties;
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
 * Non-throwing client for the task21 radiomonitoring API. The whole API is the single endpoint
 * {@code POST /verify} with {@code task="radiomonitoring"} and an {@code answer:{action, ...}} body —
 * the {@code action} field selects {@code start|listen|transmit}.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing: every {@code listen} chunk and the {@code transmit}
 * feedback carry a non-zero {@code code} and are data the driver must read. Retryable back-pressure
 * (429/503) is absorbed with a bounded linear backoff. Pattern follows {@code OkoClient}.
 */
@Component
class RadiomonitoringClient {

    private static final Logger log = LoggerFactory.getLogger(RadiomonitoringClient.class);
    private static final String TASK = "radiomonitoring";

    private final RestClient http;
    private final String apiKey;
    private final RadiomonitoringProperties props;

    RadiomonitoringClient(RestClient hubRestClient, HubProperties hub, RadiomonitoringProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Prepare the monitoring session and the material pool. */
    RadiomonitoringResponse start() {
        return call(Map.of("action", "start"));
    }

    /** Pull the next captured chunk (text transcription, base64 attachment, or a terminal signal). */
    RadiomonitoringResponse listen() {
        return call(Map.of("action", "listen"));
    }

    /** Send the final report. {@code fields} carries cityName/cityArea/warehousesCount/phoneNumber. */
    RadiomonitoringResponse transmit(Map<String, Object> fields) {
        var answer = new LinkedHashMap<String, Object>();
        answer.put("action", "transmit");
        answer.putAll(fields);
        return call(answer);
    }

    /** Send one {@code answer:{action, ...}} object to {@code /verify} and return the raw response (does not throw). */
    private RadiomonitoringResponse call(Map<String, Object> answer) {
        var body = new AnswerRequest(apiKey, TASK, new LinkedHashMap<>(answer));
        var resp = withRetries(body);
        log.info("radiomonitoring action={} -> HTTP {}\n  body: {}", answer.get("action"), resp.status(), truncate(resp.body()));
        return resp;
    }

    /** Absorb deliberately-retryable statuses (429/503) with a short bounded linear backoff. */
    private RadiomonitoringResponse withRetries(AnswerRequest body) {
        int maxRetries = Math.max(0, props.maxRetries());
        long pauseMs = Math.max(0, props.retryPauseMs());
        RadiomonitoringResponse resp = exchange(body);
        for (int attempt = 1; attempt <= maxRetries && resp.retryable(); attempt++) {
            long wait = pauseMs * attempt;
            log.warn("/verify returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    resp.status(), wait, attempt, maxRetries);
            sleep(wait);
            resp = exchange(body);
        }
        return resp;
    }

    private RadiomonitoringResponse exchange(AnswerRequest body) {
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
                        log.warn("Failed to read radiomonitoring /verify response body", e);
                        raw = "";
                    }
                    return new RadiomonitoringResponse(status, raw);
                });
    }

    /** A base64 {@code attachment} can be hundreds of KB — keep the log readable by truncating the body. */
    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        int max = 800;
        return body.length() <= max ? body : body.substring(0, max) + "...[+" + (body.length() - max) + " chars]";
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
