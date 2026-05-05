package com.morawski.dev.aidevs.tasks.task10drone;

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
import java.util.List;
import java.util.Map;

/**
 * Hub I/O for the drone task:
 * <ul>
 *   <li>downloads the terrain map PNG ({@code GET /data/{apikey}/drone.png}),</li>
 *   <li>downloads the drone API documentation ({@code GET /dane/drone.html} — note the {@code /dane/}
 *       path and that it carries no apikey),</li>
 *   <li>submits a flight program ({@code POST /verify} with {@code answer={"instructions":[...]}}).</li>
 * </ul>
 *
 * <p>Like {@code ElectricityClient}/{@code RailwayClient}, the submit call is non-throwing: an
 * intermediate, non-zero Hub {@code code} (the drone missed / a bad instruction) is exactly the
 * feedback the planner needs to read, not a fatal exception. Every call is logged; the flag (once
 * present) is extracted by the caller via {@link DroneResponse}.
 */
@Component
class DroneClient {

    private static final Logger log = LoggerFactory.getLogger(DroneClient.class);
    private static final String TASK = "drone";

    private final RestClient http;
    private final String apiKey;

    DroneClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Download the terrain map PNG (grid of sectors; the dam's water colour is intensified). */
    byte[] downloadMap() {
        log.info("GET /data/.../drone.png");
        return http.get()
                .uri("/data/{apiKey}/drone.png", apiKey)
                .retrieve()
                .body(byte[].class);
    }

    /** Download the drone API documentation (HTML); fed to the planner so it builds valid instructions. */
    String downloadDocs() {
        log.info("GET /dane/drone.html");
        return http.get()
                .uri("/dane/drone.html")
                .retrieve()
                .body(String.class);
    }

    /** Submit a flight program ({@code answer.instructions}); does not throw on a non-zero Hub code. */
    DroneResponse submit(List<String> instructions) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("instructions", instructions));
        var resp = http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling, so a non-2xx
                // (or Hub code!=0 inside a 200) doesn't throw — we inspect status/body ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read drone response body", e);
                        raw = "";
                    }
                    return new DroneResponse(status, raw);
                });
        log.info("submit instructions={} -> HTTP {}  body: {}", instructions, resp.status(), resp.body());
        return resp;
    }
}
