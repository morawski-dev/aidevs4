package com.morawski.dev.aidevs.tasks.task24goingthere;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Best-effort view of a {@code /verify} game response. The exact JSON field names the {@code goingthere}
 * API uses are confirmed by a {@link GoingthereTask#solve() recon} run, so this parser is intentionally
 * lenient: it probes a list of plausible keys for the current position and the base row, and falls back
 * to keyword scanning for the win/crash signals. The task keeps its own deterministic position track as
 * the primary authority, using these parsed values to seed the base row and to corroborate crashes.
 *
 * <p>All position values are {@link OptionalInt} so "not present / not parseable" is explicit rather
 * than a misleading zero.
 */
record GameState(
        OptionalInt row,
        OptionalInt col,
        OptionalInt baseRow,
        OptionalInt stoneRow,
        OptionalInt code,
        boolean crashed,
        boolean finished,
        String message
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Strong destruction signals (PL + EN). Deliberately avoids "skała/rock", which appears in normal
    // column descriptions, so a routine board readout is never mistaken for a crash.
    private static final Pattern CRASH = Pattern.compile(
            "(?i)rozbi|zestrzel|crash|destroyed|shot down|out of (the )?map|poza\\s+map|game ?over|przegr");
    private static final Pattern WIN = Pattern.compile(
            "(?i)grudzi[aą]dz|baz[ae]|reached|arrived|dotar|wygra|congratulation|gratulacj|success");

    static GameState parse(String body) {
        var empty = OptionalInt.empty();
        if (body == null || body.isBlank()) {
            return new GameState(empty, empty, empty, empty, empty, false, false, "");
        }
        String message = body;
        boolean crashed = CRASH.matcher(body).find();
        boolean finished = WIN.matcher(body).find();
        OptionalInt row = OptionalInt.empty();
        OptionalInt col = OptionalInt.empty();
        OptionalInt baseRow = OptionalInt.empty();
        OptionalInt stoneRow = OptionalInt.empty();
        OptionalInt code = OptionalInt.empty();

        try {
            JsonNode root = MAPPER.readTree(body);
            message = root.path("message").asText(body);
            // Confirmed shape (recon): player.{row,col}, base.{row,col}, currentColumn.stoneRow, code.
            row = anyInt(root, "player.row", "row", "y", "position.row", "position.y", "player.y", "current.row");
            col = anyInt(root, "player.col", "col", "column", "x", "position.col", "position.x", "player.x", "current.col");
            baseRow = anyInt(root,
                    "base.row", "baseRow", "base_row", "targetRow", "target_row", "goalRow", "goal_row",
                    "base.y", "target.row", "target.y", "goal.row", "goal.y", "destination.row");
            stoneRow = anyInt(root, "currentColumn.stoneRow", "currentColumn.stone_row", "column.stoneRow", "stoneRow");
            code = anyInt(root, "code");

            JsonNode crashedNode = firstPresent(root, "crashed", "crash", "dead", "destroyed");
            if (crashedNode != null && crashedNode.isBoolean() && crashedNode.asBoolean()) {
                crashed = true;
            }
            JsonNode aliveNode = firstPresent(root, "alive");
            if (aliveNode != null && aliveNode.isBoolean() && !aliveNode.asBoolean()) {
                crashed = true;
            }
            JsonNode finishedNode = firstPresent(root, "finished", "won", "success", "reached_goal", "reachedGoal");
            if (finishedNode != null && finishedNode.isBoolean() && finishedNode.asBoolean()) {
                finished = true;
            }
        } catch (Exception ignored) {
            // Corrupted/non-JSON body — fall back to the keyword signals computed above.
        }
        return new GameState(row, col, baseRow, stoneRow, code, crashed, finished, message);
    }

    /** First of the dotted paths that resolves to an integer-ish node. */
    private static OptionalInt anyInt(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = at(root, path);
            if (node != null && (node.isInt() || node.isLong() || node.canConvertToInt()
                    || (node.isTextual() && node.asText().matches("-?\\d+")))) {
                return OptionalInt.of(node.asInt());
            }
        }
        return OptionalInt.empty();
    }

    private static JsonNode firstPresent(JsonNode root, String... paths) {
        for (String path : paths) {
            JsonNode node = at(root, path);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    /** Resolve a dotted path (e.g. {@code "player.row"}); {@code null} if any segment is absent. */
    private static JsonNode at(JsonNode root, String path) {
        JsonNode node = root;
        for (String segment : path.split("\\.")) {
            node = node.path(segment);
            if (node.isMissingNode()) {
                return null;
            }
        }
        return node;
    }
}
