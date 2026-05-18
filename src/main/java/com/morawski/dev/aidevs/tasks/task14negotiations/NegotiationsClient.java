package com.morawski.dev.aidevs.tasks.task14negotiations;

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
 * Hub I/O for the negotiations task. Two calls, both {@code POST /verify}:
 * <ul>
 *   <li>{@link #submit(Object)} — register the tools ({@code answer={"tools":[...]}}),</li>
 *   <li>{@link #check()} — poll for the asynchronous result ({@code answer={"action":"check"}}).</li>
 * </ul>
 *
 * <p>Like {@code ElectricityClient}, both are non-throwing: while verification is still pending the Hub
 * returns a non-zero {@code code} (which {@code HubClient.submit} would treat as fatal), so we read the
 * raw status/body ourselves and let the caller look for the flag via {@link NegResponse}.
 */
@Component
class NegotiationsClient {

    private static final Logger log = LoggerFactory.getLogger(NegotiationsClient.class);
    private static final String TASK = "negotiations";

    private final RestClient http;
    private final String apiKey;

    NegotiationsClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Register the tool URLs/descriptions with the Hub. Does not throw. */
    NegResponse submit(Object answer) {
        return post("submit", answer);
    }

    /** Poll for the asynchronous verification result. Does not throw. */
    NegResponse check() {
        return post("check", Map.of("action", "check"));
    }

    private NegResponse post(String label, Object answer) {
        var body = new AnswerRequest(apiKey, TASK, answer);
        var resp = http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling, so a non-2xx
                // (or Hub code!=0 inside a 200 while verification is pending) doesn't throw.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read negotiations response body", e);
                        raw = "";
                    }
                    return new NegResponse(status, raw);
                });
        log.info("{} -> HTTP {}  body: {}", label, resp.status(), resp.body());
        return resp;
    }
}
