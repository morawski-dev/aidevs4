package com.morawski.dev.aidevs.tasks.task13reactor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed snapshot of the reactor board returned by every {@code /verify} command.
 *
 * <p>The API encodes the board as a {@code rows × cols} grid (row 1 = top, the bottom row = the
 * robot's lane), plus the robot ({@code player}) and {@code goal} positions, the list of
 * {@link Block}s, and a {@code reached_goal} flag. Columns and rows are 1-based.
 */
record BoardState(
        int cols,
        int rows,
        int robotCol,
        int robotRow,
        int goalCol,
        int goalRow,
        List<Block> blocks,
        boolean reachedGoal,
        int code,
        String message
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Parse the raw {@code /verify} body into a {@link BoardState}. */
    static BoardState parse(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode board = root.path("board");
            int rows = board.size();
            int cols = rows > 0 ? board.get(0).size() : 0;

            JsonNode player = root.path("player");
            JsonNode goal = root.path("goal");

            var blocks = new ArrayList<Block>();
            for (JsonNode b : root.path("blocks")) {
                blocks.add(new Block(
                        b.path("col").asInt(),
                        b.path("top_row").asInt(),
                        b.path("bottom_row").asInt(),
                        Block.Direction.parse(b.path("direction").asText())));
            }

            return new BoardState(
                    cols,
                    rows,
                    player.path("col").asInt(),
                    player.path("row").asInt(),
                    goal.path("col").asInt(),
                    goal.path("row").asInt(),
                    List.copyOf(blocks),
                    root.path("reached_goal").asBoolean(false),
                    root.path("code").asInt(),
                    root.path("message").asText(""));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse reactor board: " + body, e);
        }
    }

    /** The robot only ever travels the bottom row; the goal is the bottom-right cell. */
    boolean robotAtGoal() {
        return robotCol == goalCol && robotRow == goalRow;
    }

    /** Does a block cover the robot's current cell? (Would mean it's been crushed.) */
    boolean robotCrushed() {
        return blocks.stream().anyMatch(b -> b.col() == robotCol
                && (b.topRow() == robotRow || b.bottomRow() == robotRow));
    }
}
