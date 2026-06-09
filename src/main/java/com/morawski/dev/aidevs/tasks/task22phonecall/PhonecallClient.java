package com.morawski.dev.aidevs.tasks.task22phonecall;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.config.PhonecallProperties;
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
 * Non-throwing client for the task22 phonecall dialog. The whole conversation is {@code POST /verify}
 * with {@code task="phonecall"}: one {@code answer:{action:"start"}} to open the session, then one
 * {@code answer:{audio:"<base64 MP3>"}} per turn.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here reads
 * the raw status + body without throwing: each mid-conversation reply carries a non-zero {@code code}
 * and is the operator's turn we must read. Retryable back-pressure (429/503) is absorbed with a bounded
 * linear backoff. The base64 audio is never logged in full (only its length) — bodies are huge and noisy.
 * Pattern follows {@code FoodWarehouseClient}.
 */
@Component
class PhonecallClient {

    private static final Logger log = LoggerFactory.getLogger(PhonecallClient.class);
    private static final String TASK = "phonecall";
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_PAUSE_MS = 2000;

    private final RestClient http;
    private final String apiKey;

    PhonecallClient(RestClient hubRestClient, HubProperties hub, PhonecallProperties props) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Open a fresh conversation. After this, every turn must be sent via {@link #audio(String)}. */
    PhonecallResponse start() {
        return call(Map.of("action", "start"), "start");
    }

    /** Send one spoken turn as a base64-encoded MP3 recording. */
    PhonecallResponse audio(String base64Mp3) {
        return call(Map.of("audio", base64Mp3), "audio(%d b64 chars)".formatted(base64Mp3.length()));
    }

    private PhonecallResponse call(Map<String, Object> answer, String label) {
        var body = new AnswerRequest(apiKey, TASK, answer);
        var resp = withRetries(body);
        log.info("phonecall {} -> HTTP {}\n  body: {}", label, resp.status(), resp.body());
        return resp;
    }

    /** Absorb deliberately-retryable statuses (429/503) with a short bounded linear backoff. */
    private PhonecallResponse withRetries(AnswerRequest body) {
        PhonecallResponse resp = exchange(body);
        for (int attempt = 1; attempt <= MAX_RETRIES && resp.retryable(); attempt++) {
            long wait = RETRY_PAUSE_MS * attempt;
            log.warn("/verify returned HTTP {} (retryable) — waiting {}ms then retry {}/{}",
                    resp.status(), wait, attempt, MAX_RETRIES);
            sleep(wait);
            resp = exchange(body);
        }
        return resp;
    }

    private PhonecallResponse exchange(AnswerRequest body) {
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
                        log.warn("Failed to read phonecall /verify response body", e);
                        raw = "";
                    }
                    return new PhonecallResponse(status, raw);
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
