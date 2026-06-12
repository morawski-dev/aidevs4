package com.morawski.dev.aidevs.tasks.task24goingthere;

/**
 * The four commands the rocket API accepts ({@code POST /verify} with {@code answer={"command":"..."}}).
 * Every move command advances the rocket exactly one column to the right.
 *
 * <ul>
 *   <li>{@link #START} — begin a new game: generates a fresh map and returns the start position and the
 *       Grudziądz base row (col 12).</li>
 *   <li>{@link #GO} — fly straight: same row, next column.</li>
 *   <li>{@link #LEFT} — one row toward the "left" side + next column (which row number that is depends on
 *       the board orientation; see {@link GoingtherePlanner}).</li>
 *   <li>{@link #RIGHT} — one row toward the "right" side + next column.</li>
 * </ul>
 */
enum Command {
    START, GO, LEFT, RIGHT;

    /** Lowercase token the API expects, e.g. {@code LEFT -> "left"}. */
    String toApi() {
        return name().toLowerCase();
    }
}
