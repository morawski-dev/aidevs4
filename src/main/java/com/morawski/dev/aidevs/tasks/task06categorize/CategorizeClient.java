package com.morawski.dev.aidevs.tasks.task06categorize;

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
 * Non-throwing client for the categorize {@code POST /verify} dialog. Unlike {@code HubClient.submit}
 * (which throws {@code HubException} on a non-zero Hub {@code code}), this one reads the full
 * status + body and returns them as-is: during the prompt-engineering loop the Hub deliberately
 * replies with errors (which item was misclassified, budget state) that we want to <em>read</em>,
 * not treat as fatal.
 *
 * <p>The {@code answer} is a single {@code {"prompt": "..."}} object. Sending the literal word
 * {@code reset} resets the token counter on the Hub side. Every call (status + body) is logged.
 */
@Component
class CategorizeClient {

    static final String RESET = "reset";

    private static final Logger log = LoggerFactory.getLogger(CategorizeClient.class);
    private static final String TASK = "categorize";

    private final RestClient http;
    private final String apiKey;

    CategorizeClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Reset the Hub-side token/attempt counter before a fresh attempt. */
    CategorizeResponse reset() {
        log.info("categorize: resetting token counter");
        return submitPrompt(RESET);
    }

    /** Send one classifier prompt (or {@code reset}) and return the raw response without throwing. */
    CategorizeResponse submitPrompt(String prompt) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("prompt", prompt));
        var resp = http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling,
                // so a non-2xx (or Hub code!=0 inside a 200) doesn't throw — we inspect it ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read categorize response body", e);
                        raw = "";
                    }
                    return new CategorizeResponse(status, raw);
                });
        log.info("categorize prompt ({} chars) -> HTTP {}\n  body: {}",
                prompt.length(), resp.status(), resp.body());
        return resp;
    }
}
