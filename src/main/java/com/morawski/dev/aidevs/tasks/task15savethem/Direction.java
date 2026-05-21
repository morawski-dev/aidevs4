package com.morawski.dev.aidevs.tasks.task15savethem;

/**
 * The four movement commands the {@code savethem} API accepts. The map is oriented "standard"
 * (north at the top, per the {@code orientation-and-api} note), so {@code up} decreases the row
 * index and {@code right} increases the column index.
 */
enum Direction {
    UP(-1, 0, "up"),
    DOWN(1, 0, "down"),
    LEFT(0, -1, "left"),
    RIGHT(0, 1, "right");

    private final int dRow;
    private final int dCol;
    private final String token;

    Direction(int dRow, int dCol, String token) {
        this.dRow = dRow;
        this.dCol = dCol;
        this.token = token;
    }

    int dRow() {
        return dRow;
    }

    int dCol() {
        return dCol;
    }

    /** The lowercase keyword sent in the answer array (e.g. {@code "right"}). */
    String token() {
        return token;
    }
}
