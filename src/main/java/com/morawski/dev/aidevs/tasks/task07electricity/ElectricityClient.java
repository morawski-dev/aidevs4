package com.morawski.dev.aidevs.tasks.task07electricity;

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
 * Hub I/O for the electricity puzzle:
 * <ul>
 *   <li>downloads the current board PNG ({@code GET /data/{apikey}/electricity.png}, with optional
 *       {@code ?reset=1}); {@code HubClient.downloadData} can't add the query param, so the URI is
 *       built here.</li>
 *   <li>downloads the fixed target schematic ({@code GET /i/solved_electricity.png}).</li>
 *   <li>rotates a tile ({@code POST /verify} with {@code answer={"rotate":"AxB"}}).</li>
 * </ul>
 *
 * <p>Like {@code RailwayClient}, the rotate call is non-throwing: a non-zero Hub {@code code}
 * (board not yet solved) is a normal mid-loop state we want to read, not a fatal exception. Every
 * call is logged, and the flag (once present) is extracted by the caller via {@link EleResponse}.
 */
@Component
class ElectricityClient {

    private static final Logger log = LoggerFactory.getLogger(ElectricityClient.class);
    private static final String TASK = "electricity";

    private final RestClient http;
    private final String apiKey;

    ElectricityClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Download the current board PNG; {@code reset=true} appends {@code ?reset=1} to randomise the board. */
    byte[] downloadBoard(boolean reset) {
        log.info("GET /data/.../electricity.png{}", reset ? "?reset=1" : "");
        return http.get()
                .uri(b -> {
                    b.path("/data/{apiKey}/electricity.png");
                    if (reset) {
                        b.queryParam("reset", "1");
                    }
                    return b.build(apiKey);
                })
                .retrieve()
                .body(byte[].class);
    }

    /** Download the fixed target schematic (the solved board). */
    byte[] downloadSolved() {
        log.info("GET /i/solved_electricity.png");
        return http.get()
                .uri("/i/solved_electricity.png")
                .retrieve()
                .body(byte[].class);
    }

    /** Rotate one tile 90° clockwise ({@code "AxB"}); one request = one rotation. Does not throw. */
    EleResponse rotate(String cell) {
        var body = new AnswerRequest(apiKey, TASK, Map.of("rotate", cell));
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
                        log.warn("Failed to read electricity response body", e);
                        raw = "";
                    }
                    return new EleResponse(status, raw);
                });
        log.info("rotate {} -> HTTP {}  body: {}", cell, resp.status(), resp.body());
        return resp;
    }
}
