package com.morawski.dev.aidevs.tasks.task15savethem;

import java.util.List;

/**
 * The terrain grid (rows top→bottom = north→south, cols left→right). Tiles use the legend from the
 * {@code legend-markers} note: {@code .} empty, {@code T} tree, {@code W} water, {@code R} rock
 * (blocks every mode), {@code S} start, {@code G} goal. The start/goal cells are located once at
 * construction.
 */
final class Grid {

    static final char EMPTY = '.';
    static final char TREE = 'T';
    static final char WATER = 'W';
    static final char ROCK = 'R';
    static final char START = 'S';
    static final char GOAL = 'G';

    private final char[][] tiles;
    private final int rows;
    private final int cols;
    private final int startRow;
    private final int startCol;
    private final int goalRow;
    private final int goalCol;

    private Grid(char[][] tiles, int startRow, int startCol, int goalRow, int goalCol) {
        this.tiles = tiles;
        this.rows = tiles.length;
        this.cols = tiles[0].length;
        this.startRow = startRow;
        this.startCol = startCol;
        this.goalRow = goalRow;
        this.goalCol = goalCol;
    }

    /**
     * Build a grid from the {@code /api/maps} {@code map} field: a list of rows, each a list of
     * one-character tile strings. Validates that the grid is rectangular and contains exactly one
     * {@code S} and one {@code G}.
     */
    static Grid fromRows(List<? extends List<String>> map) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException("Empty map");
        }
        int rows = map.size();
        int cols = map.getFirst().size();
        var tiles = new char[rows][cols];
        int sr = -1;
        int sc = -1;
        int gr = -1;
        int gc = -1;
        for (int r = 0; r < rows; r++) {
            var row = map.get(r);
            if (row.size() != cols) {
                throw new IllegalArgumentException("Map is not rectangular: row " + r + " has " + row.size()
                        + " cells, expected " + cols);
            }
            for (int c = 0; c < cols; c++) {
                String cell = row.get(c);
                char ch = (cell == null || cell.isEmpty()) ? EMPTY : Character.toUpperCase(cell.charAt(0));
                tiles[r][c] = ch;
                if (ch == START) {
                    sr = r;
                    sc = c;
                } else if (ch == GOAL) {
                    gr = r;
                    gc = c;
                }
            }
        }
        if (sr < 0) {
            throw new IllegalArgumentException("Map has no start (S)");
        }
        if (gr < 0) {
            throw new IllegalArgumentException("Map has no goal (G)");
        }
        return new Grid(tiles, sr, sc, gr, gc);
    }

    char at(int row, int col) {
        return tiles[row][col];
    }

    boolean inBounds(int row, int col) {
        return row >= 0 && row < rows && col >= 0 && col < cols;
    }

    int rows() {
        return rows;
    }

    int cols() {
        return cols;
    }

    int startRow() {
        return startRow;
    }

    int startCol() {
        return startCol;
    }

    int goalRow() {
        return goalRow;
    }

    int goalCol() {
        return goalCol;
    }
}
