package com.morawski.dev.aidevs.tasks.task02findhim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Geocodes power-plant cities to accurate coordinates via OpenStreetMap Nominatim.
 * LLM geocoding proved too imprecise for small Polish towns (Chełmno was off by ~30 km),
 * which is fatal here since we look for sub-kilometer proximity.
 */
@Component
class NominatimGeocoder {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocoder.class);

    private final RestClient http;
    private final ObjectMapper mapper;

    NominatimGeocoder(ObjectMapper mapper) {
        this.mapper = mapper;
        this.http = RestClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                // Nominatim requires an identifying User-Agent
                .defaultHeader("User-Agent", "aidevs4-findhim/1.0 (lorem-ipsum@xyz.com)")
                .build();
    }

    List<PowerPlant> geocode(List<PowerPlant> plants) {
        return plants.stream()
                .map(p -> p.hasCoords() ? p : geocodeOne(p))
                .toList();
    }

    private PowerPlant geocodeOne(PowerPlant plant) {
        var raw = http.get()
                .uri(uri -> uri.path("/search")
                        .queryParam("q", plant.name() + ", Polska")
                        .queryParam("format", "json")
                        .queryParam("limit", "1")
                        .queryParam("countrycodes", "pl")
                        .build())
                .retrieve()
                .body(String.class);

        try {
            JsonNode arr = mapper.readTree(raw);
            if (arr.isArray() && !arr.isEmpty()) {
                double lat = arr.get(0).get("lat").asDouble();
                double lng = arr.get(0).get("lon").asDouble();
                log.info("Plant {} ({}) geocoded to lat={}, lng={}", plant.code(), plant.name(), lat, lng);
                return plant.withCoords(lat, lng);
            }
            log.warn("Nominatim returned no result for {}: {}", plant.name(), raw);
        } catch (Exception e) {
            log.error("Failed to geocode {} via Nominatim: {}", plant.name(), raw, e);
        }
        return plant;
    }
}
