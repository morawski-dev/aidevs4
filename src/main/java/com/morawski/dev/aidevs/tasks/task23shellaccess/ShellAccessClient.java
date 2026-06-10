package com.morawski.dev.aidevs.tasks.task23shellaccess;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.config.ShellAccessProperties;
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
 * Non-throwing client for the task23 shellaccess dialog. The only channel is {@code POST /verify} on
 * {@code hub.ag3nts.org} with {@code answer:{cmd:...}}: the server runs the command and returns its
 * stdout, and — once the printed JSON is correct — the same reply carries {@code {FLG:...}}. So a
 * single method covers both exploration and submission, and we reuse {@code hubRestClient}.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing — stderr, error text, and the flag are all exactly the
 * output the agent needs. Retryable back-pressure (429/503) is absorbed here with a bounded backoff so
 * the agent's view stays clean. Pattern follows {@code ShellClient}/{@code ZmailClient}.
 */
@Component
class ShellAccessClient {

    private static final Logger log = LoggerFactory.getLogger(ShellAccessClient.class);
    private static final String TASK = "shellaccess";

    private final RestClient http;
    private final String apiKey;
    private final ShellAccessProperties props;

    ShellAccessClient(RestClient hubRestClient, HubProperties hub, ShellAccessProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.props = props;
    }

    /**
     * Run one shell command on the server via {@code /verify}; returns the raw reply (does not throw).
     * When the command's stdout is the correct answer JSON, the reply body contains the {@code {FLG:...}}.
     */
    ShellAccessResponse run(String cmd) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("cmd", cmd));
        var resp = withRetries("/verify", body);
        log.info("shellaccess cmd={} -> HTTP {}\n  body: {}", cmd, resp.status(), resp.body());
        return resp;
    }

    /** Absorb deliberately-retryable statuses (429/503) with a short bounded backoff. */
    private ShellAccessResponse withRetries(String uri, Object body) {
        int maxRetries = Math.max(0, props.maxRetries());
        long pauseMs = Math.max(0, props.retryPauseMs());
        ShellAccessResponse resp = exchange(uri, body);
        for (int attempt = 1; attempt <= maxRetries && resp.retryable(); attempt++) {
            long wait = pauseMs * attempt; // linear backoff
            log.warn("{} returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    uri, resp.status(), wait, attempt, maxRetries);
            sleep(wait);
            resp = exchange(uri, body);
        }
        return resp;
    }

    private ShellAccessResponse exchange(String uri, Object body) {
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
                    return new ShellAccessResponse(status, raw);
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
            throw new IllegalStateException("Interrupted while backing off on a retryable shellaccess response", e);
        }
    }
}
