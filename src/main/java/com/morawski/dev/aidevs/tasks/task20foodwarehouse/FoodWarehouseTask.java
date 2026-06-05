package com.morawski.dev.aidevs.tasks.task20foodwarehouse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.FoodWarehouseProperties;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S04E05 ({@code foodwarehouse}) — prepare one correct warehouse order per city in
 * {@code food4cities.json}: each order routed to that city's numeric {@code destination}, created by a
 * valid transport user, signed by the {@code signatureGenerator}, and filled with <em>exactly</em> the
 * goods the city needs (no shortages, no surplus). When the full set is correct, {@code done} returns
 * the flag.
 *
 * <p>Deterministic driver, <b>no LLM</b> (wzór {@code okoeditor}/{@code railway}): the whole API is the
 * single {@code POST /verify} endpoint (the {@code answer.tool} field). The task is self-submitting —
 * it drives the multi-step dialog itself and detects its own {@code {FLG:...}}.
 *
 * <p>Data sources (all confirmed by recon):
 * <ul>
 *   <li><b>city needs</b> — the public {@code food4cities.json} ({@code {city: {item: qty}}});</li>
 *   <li><b>destination code</b> — read-only SQLite {@code destinations(name → destination_id)};</li>
 *   <li><b>creatorID</b> — a user with the transport role (every seeded order's creator has role 2,
 *       "Obsługa transportów"); we pick a distinct one per city;</li>
 *   <li><b>signature</b> — {@code signatureGenerator} (SHA1 of the creator's login+birthday+destination).</li>
 * </ul>
 *
 * <p>Flow: {@code reset} (drop any leftover orders so {@code append} can't double quantities) → build
 * each order ({@code signatureGenerator → orders.create → orders.append} batch) → {@code done}.
 */
@Component
class FoodWarehouseTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(FoodWarehouseTask.class);

    private final FoodWarehouseClient client;
    private final HubClient hub;
    private final FoodWarehouseProperties props;
    private final ObjectMapper mapper;

    FoodWarehouseTask(FoodWarehouseClient client, HubClient hub,
                      FoodWarehouseProperties props, ObjectMapper mapper) {
        this.client = client;
        this.hub = hub;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "foodwarehouse";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "foodwarehouse.solve")
    public Object solve() {
        // 0. Clean slate: reset drops any orders we added in a prior run (append accumulates quantities,
        //    so re-running without reset would double the goods) and restores the seeded set.
        client.reset();

        // 1. City needs (authoritative quantities) and 2. the transport users that may create orders.
        var cityNeeds = downloadCityNeeds();
        var creators = transportCreators();
        if (creators.isEmpty()) {
            throw new IllegalStateException("No transport-role (id=%d) users found to create orders"
                    .formatted(props.transportRoleId()));
        }
        log.info("Preparing {} orders; {} transport creators available", cityNeeds.size(), creators.size());

        // 3. One signed, filled order per city.
        int i = 0;
        var created = new ArrayList<String>();
        for (var entry : cityNeeds.entrySet()) {
            String city = entry.getKey();
            Map<String, Integer> items = entry.getValue();

            var dest = resolveDestination(city);
            var creator = creators.get(i % creators.size());
            i++;

            String signature = signature(creator, dest.id());
            String title = props.titleTemplate().formatted(dest.name());
            String orderId = createOrder(title, creator.userId(), dest.id(), signature);
            appendItems(orderId, items);

            created.add("%s -> dest %d (creator %d), order %s".formatted(dest.name(), dest.id(), creator.userId(), orderId));
            log.info("Order ready for {}: dest={}, creator={}, id={}, items={}",
                    dest.name(), dest.id(), creator.userId(), orderId, items);
        }

        // 4. Final validation → flag.
        var done = client.done();
        var flag = done.flag();
        if (flag.isPresent()) {
            log.info("FLAG → {}", flag.get());
            return Map.of("flag", flag.get(), "orders", created.size());
        }
        log.warn("done did not return a flag. Created {} orders. Last done body: {}", created.size(), done.body());
        return Map.of("status", "no flag", "orders", created, "doneBody", done.body());
    }

    /** Download and parse {@code food4cities.json} → ordered {@code city → {item → qty}}. */
    private LinkedHashMap<String, LinkedHashMap<String, Integer>> downloadCityNeeds() {
        byte[] raw = hub.downloadPublic(props.foodFile());
        try {
            return mapper.readValue(raw, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse " + props.foodFile(), e);
        }
    }

    /** Active users with the transport role — the only valid order creators (recon). */
    private List<Creator> transportCreators() {
        var resp = client.database(
                "SELECT user_id, login, birthday FROM users WHERE role = %d AND is_active = 1"
                        .formatted(props.transportRoleId()));
        var creators = new ArrayList<Creator>();
        for (JsonNode row : rows(resp, "users")) {
            creators.add(new Creator(
                    row.path("user_id").asInt(),
                    row.path("login").asText(),
                    row.path("birthday").asText()));
        }
        return creators;
    }

    /** Resolve a city's numeric destination code (case-insensitive on the city name). */
    private Destination resolveDestination(String city) {
        String safe = city.replace("'", "''");
        var resp = client.database(
                "SELECT destination_id, name FROM destinations WHERE LOWER(name) = LOWER('%s')".formatted(safe));
        var rows = rows(resp, "destinations");
        if (rows.isEmpty()) {
            throw new IllegalStateException("No destination found for city '%s'".formatted(city));
        }
        var row = rows.get(0);
        return new Destination(row.path("destination_id").asInt(), row.path("name").asText());
    }

    private String signature(Creator creator, int destination) {
        var resp = client.generateSignature(creator.login(), creator.birthday(), destination);
        String hash = json(resp).path("hash").asText(null);
        if (hash == null || hash.isBlank()) {
            throw new IllegalStateException("signatureGenerator returned no hash for %s/%d: %s"
                    .formatted(creator.login(), destination, resp.body()));
        }
        return hash;
    }

    private String createOrder(String title, int creatorId, int destination, String signature) {
        var resp = client.ordersCreate(title, creatorId, destination, signature);
        String id = json(resp).path("order").path("id").asText(null);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("orders.create returned no id (creator %d, dest %d): %s"
                    .formatted(creatorId, destination, resp.body()));
        }
        return id;
    }

    private void appendItems(String orderId, Map<String, Integer> items) {
        var resp = client.ordersAppend(orderId, items);
        if (!resp.ok()) {
            throw new IllegalStateException("orders.append failed for %s: %s".formatted(orderId, resp.body()));
        }
    }

    /** Parse a response body into a JSON tree; throws with the raw body if it isn't valid JSON. */
    private JsonNode json(FoodWarehouseResponse resp) {
        try {
            return mapper.readTree(resp.body());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse foodwarehouse response: " + resp.body(), e);
        }
    }

    /** Extract the {@code rows} array from a {@code database} response, asserting the expected table. */
    private List<JsonNode> rows(FoodWarehouseResponse resp, String expectedTable) {
        var node = json(resp);
        var rows = node.path("rows");
        if (!rows.isArray()) {
            throw new IllegalStateException("database query on '%s' returned no rows: %s"
                    .formatted(expectedTable, resp.body()));
        }
        var out = new ArrayList<JsonNode>();
        rows.forEach(out::add);
        return out;
    }

    private record Creator(int userId, String login, String birthday) {
    }

    private record Destination(int id, String name) {
    }
}
