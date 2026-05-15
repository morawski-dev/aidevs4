package com.morawski.dev.aidevs.tasks.task13reactor;

/**
 * The five commands the reactor robot API accepts ({@code POST /verify} with
 * {@code answer = {"command": "..."}}). Only one command may be sent per request, and every command
 * advances all reactor blocks by one step.
 *
 * <ul>
 *   <li>{@link #START} — begin the run; returns the first board state.</li>
 *   <li>{@link #RESET} — restart the run (robot back to start); used after a crush or a stall.</li>
 *   <li>{@link #LEFT} / {@link #RIGHT} — move the robot one column on the bottom row.</li>
 *   <li>{@link #WAIT} — robot stays put while the blocks advance (the only way to change the board
 *       without moving — wall-clock time does nothing).</li>
 * </ul>
 */
enum Command {
    START, RESET, LEFT, WAIT, RIGHT;

    /** Lowercase token the API expects, e.g. {@code RIGHT -> "right"}. */
    String toApi() {
        return name().toLowerCase();
    }
}
