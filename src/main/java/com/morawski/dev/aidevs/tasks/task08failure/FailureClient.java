package com.morawski.dev.aidevs.tasks.task08failure;

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
 * Non-throwing client for the failure {@code POST /verify} dialog. Unlike {@code HubClient.submit}
 * (which throws {@code HubException} on a non-zero Hub {@code code}), this reads the full status +
 * body and returns them as-is: during the condense→submit→read-feedback loop the Hub deliberately
 * replies with precise feedback (which subsystem couldn't be analysed, or a token-limit rejection)
 * that we want to <em>read</em>, not treat as a fatal exception.
 *
 * <p>The {@code answer} is a single {@code {"logs": "...\n..."}} object — Jackson escapes the {@code \n}
 * line separators inside the string. Every call (status + full body) is logged; the technicians'
 * feedback in the body is the whole point.
 */
@Component
class FailureClient {

    private static final Logger log = LoggerFactory.getLogger(FailureClient.class);
    private static final String TASK = "failure";

    private final RestClient http;
    private final String apiKey;

    FailureClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Submit the condensed log and return the raw response without throwing on a non-zero code. */
    FailureResponse submitLogs(String logs) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("logs", logs));
        var resp = http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling, so a non-2xx
                // (or Hub code!=0 inside a 200) doesn't throw — we inspect it ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read failure response body", e);
                        raw = "";
                    }
                    return new FailureResponse(status, raw);
                });
        log.info("failure submit ({} chars logs) -> HTTP {}\n  body: {}",
                logs.length(), resp.status(), resp.body());
        return resp;
    }
}
