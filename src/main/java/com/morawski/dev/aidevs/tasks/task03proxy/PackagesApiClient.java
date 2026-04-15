package com.morawski.dev.aidevs.tasks.task03proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.HubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Client for the external packages API: POST /api/packages (raw JSON, apikey in body).
 * Mirrors the FindHimApiClient pattern: log the raw response, parse defensively.
 */
@Component
class PackagesApiClient {

    private static final Logger log = LoggerFactory.getLogger(PackagesApiClient.class);

    private final RestClient http;
    private final String apiKey;
    private final ObjectMapper mapper;

    PackagesApiClient(RestClient hubRestClient, HubProperties hub, ObjectMapper mapper) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.mapper = mapper;
    }

    /** Returns the raw status/location info for a package (the LLM interprets it). */
    String check(String packageid) {
        var body = Map.of("apikey", apiKey, "action", "check", "packageid", packageid);
        var raw = http.post()
                .uri("/api/packages")
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("check({}) -> {}", packageid, raw);
        return raw;
    }

    /** Redirects a package; returns the confirmation code to relay to the operator. */
    String redirect(String packageid, String destination, String code) {
        var body = Map.of(
                "apikey", apiKey,
                "action", "redirect",
                "packageid", packageid,
                "destination", destination,
                "code", code);
        var raw = http.post()
                .uri("/api/packages")
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("redirect(id={}, dest={}) -> {}", packageid, destination, raw);
        return extractConfirmation(raw);
    }

    private String extractConfirmation(String raw) {
        try {
            var root = mapper.readTree(raw);
            for (var field : java.util.List.of("confirmation", "message", "data")) {
                if (root.has(field) && !root.get(field).isNull()) {
                    return root.get(field).asText();
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse confirmation from redirect response, returning raw", e);
        }
        return raw;
    }
}
