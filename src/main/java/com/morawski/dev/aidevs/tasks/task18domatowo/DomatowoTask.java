package com.morawski.dev.aidevs.tasks.task18domatowo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.DomatowoProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S04E03 ({@code domatowo}) — find a partisan hiding in one of the tallest buildings of a bombed 11×11
 * city and call a rescue helicopter to his cell, within a 300-action-point budget.
 *
 * <p>The audio clue ("ukryłem się w jednym z najwyższych bloków") pins the search to the tallest
 * {@code block3} ("Blok 3p") cells. Movement cost is wildly asymmetric — transporters move at 1/field
 * (roads only) while scouts move at 7/field (on foot, orthogonal) — so the winning shape is: for each
 * cluster of tall blocks, ferry a scout by transporter to the adjacent road, dismount for free, then
 * walk the scout cell-by-cell over the cluster, inspecting each. The first inspect whose log isn't
 * "negatywnie" is the partisan; {@code callHelicopter} to that field returns the flag.
 *
 * <p>Perception (the constant map) is split from logic (the route): {@link CityMap} parses the grid
 * and {@link DomatowoPlanner} computes the cluster sweep deterministically — no LLM. The flag arrives
 * inside the {@code callHelicopter} response, so the task is {@link #selfSubmitting() self-submitting}.
 *
 * <p><b>Recon mode</b> ({@code aidevs.domatowo.recon=true}): a read-only probe (cost-free actions:
 * help/getMap/actionCost/searchSymbol/getObjects/getLogs/expenses) that just logs raw bodies. Creates
 * and moves nothing, so it spends no action points.
 */
@Component
class DomatowoTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DomatowoTask.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * A positive inspect log affirms a person, e.g. "Osoba odnaleziona. To mężczyzna około 30 lat...".
     * Empty-cell logs use many varied phrasings but never these affirmative tokens. We match on the
     * affirmative side (not the absence of a single negative word) because the negative wording varies.
     * "to nasz człowiek" is safe: the negative variant reads "to NIE nasz człowiek".
     */
    private static final String[] POSITIVE_MARKERS = {"odnalezion", "mężczyzn", "to nasz człowiek", "partyzant"};

    private final DomatowoClient client;
    private final DomatowoPlanner planner;
    private final DomatowoProperties props;

    DomatowoTask(DomatowoClient client, DomatowoPlanner planner, DomatowoProperties props) {
        this.client = client;
        this.planner = planner;
        this.props = props;
    }

    @Override
    public String name() {
        return "domatowo";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "domatowo.solve")
    public Object solve() {
        if (props.recon()) {
            return recon();
        }
        return drive();
    }

    /**
     * Run the search-and-rescue operation: reset for a clean board + fresh 300 points, parse the map,
     * then execute each planned cluster sortie until a scout confirms the partisan, then call the
     * helicopter to his cell.
     */
    private Object drive() {
        client.send(Map.of("action", "reset"));
        var map = CityMap.parse(client.send(Action.getMap()).body());
        var missions = planner.plan(map);
        log.info("domatowo plan: {} cluster sortie(s): {}", missions.size(),
                missions.stream().map(m -> m.drop() + "->" + m.cells()).toList());

        int rounds = 0;
        for (var mission : missions) {
            // Spawn a transporter carrying one scout and ferry it to the cluster's road drop.
            var created = node(client.send(Action.createTransporter(1)).body());
            String transporter = created.path("object").asText();
            String scout = created.path("crew").path(0).path("id").asText();
            if (transporter.isBlank() || scout.isBlank()) {
                log.warn("Create returned no transporter/scout id; skipping mission {}", mission.drop());
                continue;
            }

            client.send(Map.of("action", "move", "object", transporter, "where", mission.drop()));
            client.send(Map.of("action", "dismount", "object", transporter, "passengers", 1));

            for (String cell : mission.cells()) {
                if (++rounds > props.maxRounds()) {
                    log.warn("Hit maxRounds={} without finding the partisan.", props.maxRounds());
                    return Map.of("status", "round cap reached", "rounds", rounds);
                }
                client.send(Map.of("action", "move", "object", scout, "where", cell));
                var inspect = client.send(Map.of("action", "inspect", "object", scout));
                int pointsLeft = node(inspect.body()).path("action_points_left").asInt(-1);

                String hit = findHit(client.send(Action.getLogs()).body());
                if (hit != null) {
                    log.info("Partisan confirmed at {} (points left ~{}). Calling helicopter.", hit, pointsLeft);
                    var resp = client.send(Action.callHelicopter(hit));
                    var flag = resp.flag();
                    if (flag.isPresent()) {
                        log.info("FLAG → {}", flag.get());
                        return Map.of("flag", flag.get(), "destination", hit);
                    }
                    log.warn("callHelicopter to {} returned no flag: {}", hit, resp.body());
                    return Map.of("status", "no flag", "destination", hit, "body", resp.body());
                }
            }
        }

        log.warn("Inspected all tall-block cells without a positive log.");
        return Map.of("status", "partisan not found");
    }

    /** Scan inspect logs for the entry that affirms a person and return its field, or null. */
    private String findHit(String getLogsBody) {
        for (JsonNode entry : node(getLogsBody).path("logs")) {
            String msg = entry.path("msg").asText("").toLowerCase();
            for (String marker : POSITIVE_MARKERS) {
                if (msg.contains(marker)) {
                    log.info("Positive inspect log at {}: {}", entry.path("field").asText(), entry.path("msg").asText());
                    return entry.path("field").asText();
                }
            }
        }
        return null;
    }

    /**
     * Read-only probe: all cost-free actions. Captures the raw bodies (see {@link DomatowoClient#send}
     * logging) so the action contract, map legend and coordinate convention can be learned. Creates and
     * moves nothing, so it spends no action points.
     */
    private Object recon() {
        log.info("=== domatowo RECON (read-only) ===");
        var bodies = new LinkedHashMap<String, String>();

        bodies.put("help", client.send(Action.help()).body());
        bodies.put("getMap", client.send(Action.getMap()).body());
        bodies.put("actionCost", client.send(Map.of("action", "actionCost")).body());
        bodies.put("searchSymbol(B3)", client.send(Map.of("action", "searchSymbol", "symbol", "B3")).body());
        bodies.put("getObjects", client.send(Map.of("action", "getObjects")).body());
        bodies.put("getLogs", client.send(Action.getLogs()).body());
        bodies.put("expenses", client.send(Map.of("action", "expenses")).body());

        log.info("=== domatowo RECON done. Captured {} raw bodies (see logs above). ===", bodies.size());
        return Map.of("mode", "recon", "samples", bodies);
    }

    private static JsonNode node(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            return MAPPER.createObjectNode();
        }
    }
}
