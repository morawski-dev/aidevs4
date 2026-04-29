package com.morawski.dev.aidevs.tasks.task07electricity;

import java.util.ArrayList;
import java.util.List;

/**
 * A position on the 3×3 board. {@code row} is 1–3 from the top, {@code col} is 1–3 from the left.
 * The Hub addresses tiles as {@code AxB} (row × column) — see {@link #label()}. The power source is
 * the bottom-left tile, {@code 3x1}.
 */
record Cell(int row, int col) {

    /** Hub address of this tile, e.g. {@code "2x3"} (row 2, column 3). */
    String label() {
        return row + "x" + col;
    }

    /** All nine cells in reading order (top-left to bottom-right). */
    static List<Cell> grid() {
        var cells = new ArrayList<Cell>(9);
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 3; col++) {
                cells.add(new Cell(row, col));
            }
        }
        return cells;
    }
}
