package com.morawski.dev.aidevs.tasks.task15savethem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the agent's {@link Findings} into a concrete world for the planner. The bulky, error-prone
 * data is fetched and parsed straight from the tools' JSON here (never transcribed by the LLM): the
 * map grid from the maps tool, and each vehicle's fuel/food consumption from the vehicles tool.
 */
@Service
class SaveThemService {

    private static final Logger log = LoggerFactory.getLogger(SaveThemService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ToolHubClient hub;

    SaveThemService(ToolHubClient hub) {
        this.hub = hub;
    }

    /** A planning world: the terrain grid and the travel modes with their resource costs. */
    record World(Grid grid, List<Mode> modes) {
    }

    World build(Findings findings) {
        Grid grid = fetchGrid(findings.mapsToolUrl(), findings.destinationCity());
        List<Mode> modes = fetchModes(findings.vehiclesToolUrl(), findings.vehicleNames());
        if (modes.isEmpty()) {
            throw new IllegalStateException("No usable travel modes resolved from " + findings.vehicleNames());
        }
        log.info("World built: {}x{} grid, start=({},{}) goal=({},{}), modes={}",
                grid.rows(), grid.cols(), grid.startRow(), grid.startCol(),
                grid.goalRow(), grid.goalCol(), modes);
        return new World(grid, modes);
    }

    private Grid fetchGrid(String mapsToolUrl, String city) {
        String body = hub.call(mapsToolUrl, city);
        JsonNode root = read(body, "map response for " + city);
        JsonNode map = root.path("map");
        if (!map.isArray() || map.isEmpty()) {
            throw new IllegalStateException("Maps tool returned no 'map' grid for '" + city + "': " + body);
        }
        var rows = new ArrayList<List<String>>();
        for (JsonNode row : map) {
            var cells = new ArrayList<String>();
            for (JsonNode cell : row) {
                cells.add(cell.asText());
            }
            rows.add(cells);
        }
        return Grid.fromRows(rows);
    }

    private List<Mode> fetchModes(String vehiclesToolUrl, List<String> names) {
        var modes = new ArrayList<Mode>();
        for (String name : names) {
            String body = hub.call(vehiclesToolUrl, name);
            JsonNode root;
            try {
                root = MAPPER.readTree(body);
            } catch (Exception e) {
                log.warn("Skipping vehicle '{}' — unparseable response: {}", name, body);
                continue;
            }
            JsonNode consumption = root.path("consumption");
            if (consumption.isMissingNode() || !consumption.has("fuel") || !consumption.has("food")) {
                log.warn("Skipping vehicle '{}' — no consumption in response: {}", name, body);
                continue;
            }
            String resolvedName = root.path("name").asText(name);
            double fuel = consumption.path("fuel").asDouble();
            double food = consumption.path("food").asDouble();
            modes.add(new Mode(resolvedName.toLowerCase(), fuel, food));
        }
        return modes;
    }

    private JsonNode read(String body, String what) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Unparseable " + what + ": " + body, e);
        }
    }
}
