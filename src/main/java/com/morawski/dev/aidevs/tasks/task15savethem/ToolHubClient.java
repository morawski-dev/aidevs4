package com.morawski.dev.aidevs.tasks.task15savethem;

import com.morawski.dev.aidevs.config.HubProperties;
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
 * Hub I/O for the {@code savethem} recon: the tool-search endpoint and any tool it surfaces. Every
 * tool — including {@code /api/toolsearch} itself — takes {@code {apikey, query}} and answers in
 * JSON, so a single {@link #call(String, String)} covers them all. Returned tool URLs are
 * Hub-relative (e.g. {@code /api/maps}); the shared {@code hubRestClient} resolves them against the
 * Hub base url, and an absolute URL would also work.
 *
 * <p>Like {@code ElectricityClient}, calls are non-throwing: a tool error (a negative {@code code}
 * inside a 200, e.g. "Unknown vehicle") is information we want to read and feed back to the agent,
 * not an exception. Every request/response is logged — this log is our recon trail.
 */
@Component
class ToolHubClient {

    private static final Logger log = LoggerFactory.getLogger(ToolHubClient.class);

    private final RestClient http;
    private final String apiKey;

    ToolHubClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Discover tools matching {@code query} (returns the raw {@code /api/toolsearch} JSON, top-3). */
    String searchTools(String query) {
        return call("/api/toolsearch", query);
    }

    /** Call any tool by its URL with a free-text {@code query}; returns the raw JSON body. */
    String call(String toolUrl, String query) {
        var body = Map.of("apikey", apiKey, "query", query == null ? "" : query);
        var raw = http.post()
                .uri(toolUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() skips default error handling so a non-2xx (or a tool's negative code in a
                // 200) doesn't throw — we hand the raw body back to the agent / parser to interpret.
                .exchange((request, response) -> {
                    try (var in = response.getBody()) {
                        return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read body from {}", toolUrl, e);
                        return "";
                    }
                });
        log.info("POST {} query='{}' -> {}", toolUrl, query, raw);
        return raw;
    }
}
