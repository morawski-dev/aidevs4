package com.morawski.dev.aidevs.tasks.task02findhim;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.HubProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
class FindHimApiClient {

    private static final Logger log = LoggerFactory.getLogger(FindHimApiClient.class);

    private final RestClient http;
    private final String apiKey;
    private final ObjectMapper mapper;

    FindHimApiClient(RestClient hubRestClient, HubProperties hub, ObjectMapper mapper) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
        this.mapper = mapper;
    }

    List<Coordinate> locations(String name, String surname) {
        var body = Map.of("apikey", apiKey, "name", name, "surname", surname);
        var raw = http.post()
                .uri("/api/location")
                .body(body)
                .retrieve()
                .body(String.class);

        log.info("Location response for {} {}: {}", name, surname, raw);
        return parseCoordinates(raw);
    }

    int accessLevel(String name, String surname, int birthYear) {
        var body = Map.of("apikey", apiKey, "name", name, "surname", surname, "birthYear", birthYear);
        var raw = http.post()
                .uri("/api/accesslevel")
                .body(body)
                .retrieve()
                .body(String.class);

        log.info("AccessLevel response for {} {}: {}", name, surname, raw);
        return parseAccessLevel(raw);
    }

    private List<Coordinate> parseCoordinates(String raw) {
        try {
            var root = mapper.readTree(raw);

            // direct array
            if (root.isArray()) {
                return mapper.convertValue(root, new TypeReference<>() {});
            }

            // Hub-style wrapper: {"code":0, "message":"...", "data":[...]} or {"locations":[...]}
            for (var field : List.of("data", "locations", "coordinates", "result")) {
                if (root.has(field) && root.get(field).isArray()) {
                    return mapper.convertValue(root.get(field), new TypeReference<>() {});
                }
            }

            // fallback: try first array field found
            var iter = root.fields();
            while (iter.hasNext()) {
                var entry = iter.next();
                if (entry.getValue().isArray()) {
                    log.warn("Using fallback field '{}' for coordinates", entry.getKey());
                    return mapper.convertValue(entry.getValue(), new TypeReference<>() {});
                }
            }

            log.error("Cannot parse coordinates from: {}", raw);
            return List.of();
        } catch (Exception e) {
            log.error("Failed to parse location response: {}", raw, e);
            return List.of();
        }
    }

    private int parseAccessLevel(String raw) {
        try {
            var root = mapper.readTree(raw);

            // direct number
            if (root.isNumber()) {
                return root.intValue();
            }

            // look for accessLevel / access_level / level fields
            for (var field : List.of("accessLevel", "access_level", "level", "access")) {
                if (root.has(field)) {
                    JsonNode node = root.get(field);
                    if (node.isNumber()) return node.intValue();
                    if (node.isTextual()) return Integer.parseInt(node.asText());
                }
            }

            // Hub wrapper: check message field for a numeric value
            if (root.has("message")) {
                String msg = root.get("message").asText();
                try {
                    return Integer.parseInt(msg.strip());
                } catch (NumberFormatException ignored) {
                    // message is not a plain number
                }
            }

            log.error("Cannot parse accessLevel from: {}", raw);
            return -1;
        } catch (Exception e) {
            log.error("Failed to parse accessLevel response: {}", raw, e);
            return -1;
        }
    }
}
