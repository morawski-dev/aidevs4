package com.morawski.dev.aidevs.tasks.task15savethem;

/**
 * A travel mode (the {@code /api/wehicles} entries: {@code walk}, {@code horse}, {@code car},
 * {@code rocket}) with its per-move resource cost. The two passability rules the planner needs are
 * <em>derived</em> from the fuel cost rather than hard-coded per name, because the book notes tie
 * them together exactly:
 * <ul>
 *   <li><b>powered</b> = burns fuel (car, rocket). Powered modes pay the extra tree-tile burn and
 *       are lost on water tiles.</li>
 *   <li><b>non-powered</b> = burns no fuel (walk, horse). These cross water safely and ignore trees
 *       (the {@code water-travel} note: only the horse among vehicles crosses water, and a traveler
 *       may always cross on foot).</li>
 * </ul>
 *
 * @param name        the exact keyword used in the answer array (e.g. {@code "rocket"}).
 * @param fuelPerMove fuel units burned entering a tile.
 * @param foodPerMove food units burned entering a tile.
 */
record Mode(String name, double fuelPerMove, double foodPerMove) {

    /** Powered modes burn fuel; they pay the tree penalty and cannot enter water. */
    boolean powered() {
        return fuelPerMove > 1e-9;
    }
}
