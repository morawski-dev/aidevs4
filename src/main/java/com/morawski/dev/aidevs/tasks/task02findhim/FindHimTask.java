package com.morawski.dev.aidevs.tasks.task02findhim;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.common.GeoUtils;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import com.morawski.dev.aidevs.tasks.task01people.Person;
import com.morawski.dev.aidevs.tasks.task01people.SuspectsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class FindHimTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(FindHimTask.class);

    private final HubClient hub;
    private final SuspectsProvider suspectsProvider;
    private final FindHimApiClient api;
    private final NominatimGeocoder geocoder;
    private final ObjectMapper mapper;

    FindHimTask(HubClient hub, SuspectsProvider suspectsProvider,
                FindHimApiClient api, NominatimGeocoder geocoder, ObjectMapper mapper) {
        this.hub = hub;
        this.suspectsProvider = suspectsProvider;
        this.api = api;
        this.geocoder = geocoder;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "findhim";
    }

    @Override
    public Object solve() {
        var plants = loadPlants();
        log.info("Loaded {} power plants", plants.size());
        plants.forEach(p -> log.info("  Plant: code={}, name={}, location={}, hasCoords={}",
                p.code(), p.name(), p.location(), p.hasCoords()));

        plants = geocoder.geocode(plants);

        var suspects = suspectsProvider.get();
        log.info("Suspects from S01E01: {}", suspects.size());

        record Hit(Person person, PowerPlant plant, double distKm) {
        }

        Hit best = null;

        for (var suspect : suspects) {
            var coords = api.locations(suspect.name(), suspect.surname());

            Hit personBest = null;
            for (var coord : coords) {
                for (var plant : plants) {
                    if (!plant.hasCoords()) continue;
                    double dist = GeoUtils.haversineKm(coord.lat(), coord.lng(), plant.lat(), plant.lng());
                    if (personBest == null || dist < personBest.distKm()) {
                        personBest = new Hit(suspect, plant, dist);
                    }
                }
            }

            if (personBest != null) {
                log.info("  {} {} → {} locations, nearest {} at {} km",
                        suspect.name(), suspect.surname(), coords.size(),
                        personBest.plant().code(), personBest.distKm());
                if (best == null || personBest.distKm() < best.distKm()) {
                    best = personBest;
                }
            }
        }

        if (best == null) {
            throw new IllegalStateException("No locations found for any suspect");
        }

        log.info("Winner: {} {} near {} at {} km",
                best.person().name(), best.person().surname(), best.plant().code(), best.distKm());

        int level = api.accessLevel(best.person().name(), best.person().surname(), best.person().born());
        log.info("Access level: {}", level);

        return new FindHimAnswer(best.person().name(), best.person().surname(), level, best.plant().code());
    }

    private List<PowerPlant> loadPlants() {
        var raw = hub.downloadData("findhim_locations.json");
        var json = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
        log.info("findhim_locations.json raw: {}", json);
        try {
            // Structure: { "power_plants": { "<city>": { "code": "PWRxxxxPL", "power": "...", "is_active": ... } } }
            var root = mapper.readTree(json);
            var plantsNode = root.has("power_plants") ? root.get("power_plants") : root;

            var plants = new java.util.ArrayList<PowerPlant>();
            var fields = plantsNode.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String city = entry.getKey();
                var node = entry.getValue();
                String code = node.has("code") ? node.get("code").asText() : null;
                // city name is both the human name and the geocoding target; no coords in source
                plants.add(new PowerPlant(code, city, city, null, null));
            }
            return plants;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse findhim_locations.json: " + json, e);
        }
    }
}
