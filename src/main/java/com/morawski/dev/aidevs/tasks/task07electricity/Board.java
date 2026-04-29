package com.morawski.dev.aidevs.tasks.task07electricity;

import java.util.EnumSet;
import java.util.Map;

/**
 * The perceived state of the 3×3 board: for each {@link Cell}, the set of edges its cable exits
 * through. Produced by {@link BoardVision} from a PNG (current or target).
 */
record Board(Map<Cell, EnumSet<Edge>> tiles) {

    EnumSet<Edge> at(Cell cell) {
        return tiles.get(cell);
    }

    /** Multi-line text rendering of the nine tiles (edge letters per cell), for logging/debugging. */
    String toAscii() {
        var sb = new StringBuilder();
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 3; col++) {
                var edges = tiles.get(new Cell(row, col));
                sb.append(String.format("%-5s", edges == null ? "?" : render(edges)));
                if (col < 3) {
                    sb.append("| ");
                }
            }
            if (row < 3) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private static String render(EnumSet<Edge> edges) {
        if (edges.isEmpty()) {
            return "-";
        }
        var sb = new StringBuilder();
        for (Edge e : edges) {
            sb.append(e.name());
        }
        return sb.toString();
    }
}
