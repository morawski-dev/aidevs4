package com.morawski.dev.aidevs.tasks.task10drone;

/**
 * Vision read of the terrain map: the grid size and the sector that holds the dam, all 1-indexed
 * ({@code (1,1)} = upper-left). The dam is the sector with the most-intensified water colour.
 *
 * @param cols      number of grid columns (left-to-right)
 * @param rows      number of grid rows (top-to-bottom)
 * @param damCol    dam sector column, counting from the left, starting at 1
 * @param damRow    dam sector row, counting from the top, starting at 1
 * @param reasoning short justification (for the log; e.g. why that sector was chosen)
 */
record DamLocation(int cols, int rows, int damCol, int damRow, String reasoning) {

    /** Whether the sector lies inside the reported grid (a sanity check on the vision read). */
    boolean withinGrid() {
        return cols > 0 && rows > 0
                && damCol >= 1 && damCol <= cols
                && damRow >= 1 && damRow <= rows;
    }
}
