package com.morawski.dev.aidevs.tasks.task12firmware;

import com.morawski.dev.aidevs.config.FirmwareProperties;
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
 * Non-throwing client for the task12 firmware dialog. Both the shell API ({@code POST /api/shell})
 * and answer submission ({@code POST /verify}) live on {@code hub.ag3nts.org}, so we reuse
 * {@code hubRestClient}.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing — the API's "error" replies (rate limit, ban, 503,
 * non-zero shell exit) are exactly the output the agent needs. Retryable back-pressure (429/503) is
 * absorbed here with a bounded backoff so the agent's view stays clean; a ban is returned verbatim
 * so the model can decide to wait or stop. Pattern follows {@code ZmailClient}/{@code RailwayClient}.
 */
@Component
class ShellClient {

    private static final Logger log = LoggerFactory.getLogger(ShellClient.class);
    private static final String TASK = "firmware";

    private final RestClient http;
    private final String apiKey;
    private final FirmwareProperties props;

    ShellClient(RestClient hubRestClient, HubProperties hub, FirmwareProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /** Run one shell command on the VM; returns the raw API output (does not throw). */
    FirmwareResponse shell(String cmd) {
        var body = Map.<String, Object>of("apikey", apiKey, "cmd", cmd);
        var resp = withRetries("/api/shell", body);
        log.info("shell cmd={} -> HTTP {}\n  body: {}", cmd, resp.status(), resp.body());
        return resp;
    }

    /** Submit the answer ({@code {confirmation: ...}}) to {@code /verify}; returns feedback or flag. */
    FirmwareResponse verify(Map<String, Object> answer) {
        var body = new AnswerRequest(apiKey, TASK, answer);
        var resp = withRetries("/verify", body);
        log.info("firmware /verify answer={} -> HTTP {}\n  body: {}", answer, resp.status(), resp.body());
        return resp;
    }

    /** Absorb deliberately-retryable statuses (429/503) with a short bounded backoff. */
    private FirmwareResponse withRetries(String uri, Object body) {
        int maxRetries = Math.max(0, props.maxRetries());
        long pauseMs = Math.max(0, props.retryPauseMs());
        FirmwareResponse resp = exchange(uri, body);
        for (int attempt = 1; attempt <= maxRetries && resp.retryable(); attempt++) {
            long wait = pauseMs * attempt; // linear backoff; the API's own ban window is honoured by the agent
            log.warn("{} returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    uri, resp.status(), wait, attempt, maxRetries);
            sleep(wait);
            resp = exchange(uri, body);
        }
        return resp;
    }

    private FirmwareResponse exchange(String uri, Object body) {
        return http.post()
                .uri(uri)
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
                        log.warn("Failed to read response body from {}", uri, e);
                        raw = "";
                    }
                    return new FirmwareResponse(status, raw);
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
            throw new IllegalStateException("Interrupted while backing off on a retryable shell response", e);
        }
    }
}
