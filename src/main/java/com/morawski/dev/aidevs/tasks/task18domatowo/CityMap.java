package com.morawski.dev.aidevs.tasks.task18domatowo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed snapshot of the Domatowo {@code getMap} layout (constant across resets — only the partisan
 * position re-rolls). The API returns the board as a {@code size×size} grid of tile <em>keys</em>
 * ({@code "road"}, {@code "block3"}, …) plus a {@code tiles} dictionary mapping each key to its
 * label/symbol.
 *
 * <p>Coordinates are 1-based labels {@code A1..K11}: the column is a letter ({@code A}=0) and the row
 * is a number ({@code 1}=top). Internally we use 0-based {@code (col, row)}.
 */
final class CityMap {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern BLOCK_FLOORS = Pattern.compile("block(\\d+)");
    /** Transporters spawn at the next free slot A6→D6; A6 is the canonical first slot (and frees up
     *  again once a transporter drives off), so we measure transporter cost from it. */
    private static final int SPAWN_ROW = 5; // 0-based row index of row "6"
    private static final int SPAWN_COL = 0;  // 0-based col index of "A"

    private final int size;
    private final String[][] keys; // [row][col] = tile key, e.g. "road", "block3"
    private final Set<String> tallestBlockKeys;

    private CityMap(int size, String[][] keys, Set<String> tallestBlockKeys) {
        this.size = size;
        this.keys = keys;
        this.tallestBlockKeys = tallestBlockKeys;
    }

    /** Parse a raw {@code getMap} body into a {@link CityMap}. */
    static CityMap parse(String body) {
        try {
            JsonNode map = MAPPER.readTree(body).path("map");
            int size = map.path("size").asInt();

            // Tallest "blok": among tile keys block1/block2/block3, keep the highest floor count.
            int maxFloors = 0;
            var blockFloors = new HashMap<String, Integer>();
            for (var it = map.path("tiles").fieldNames(); it.hasNext(); ) {
                String key = it.next();
                Matcher m = BLOCK_FLOORS.matcher(key);
                if (m.matches()) {
                    int floors = Integer.parseInt(m.group(1));
                    blockFloors.put(key, floors);
                    maxFloors = Math.max(maxFloors, floors);
                }
            }
            final int top = maxFloors;
            var tallest = new HashSet<String>();
            blockFloors.forEach((k, f) -> {
                if (f == top) {
                    tallest.add(k);
                }
            });

            JsonNode grid = map.path("grid");
            var keys = new String[size][size];
            for (int r = 0; r < size; r++) {
                JsonNode rowNode = grid.get(r);
                for (int c = 0; c < size; c++) {
                    keys[r][c] = rowNode.get(c).asText();
                }
            }
            return new CityMap(size, keys, Set.copyOf(tallest));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Domatowo map: " + body, e);
        }
    }

    int size() {
        return size;
    }

    boolean inBounds(int col, int row) {
        return col >= 0 && col < size && row >= 0 && row < size;
    }

    String keyAt(int col, int row) {
        return keys[row][col];
    }

    boolean isRoad(int col, int row) {
        return "road".equals(keys[row][col]);
    }

    /** Cells of the tallest blocks (the partisan's hiding-place candidates), in row-major order. */
    List<int[]> candidates() {
        var out = new ArrayList<int[]>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (tallestBlockKeys.contains(keys[r][c])) {
                    out.add(new int[]{c, r});
                }
            }
        }
        return out;
    }

    /** Group the candidate cells into 4-adjacency-connected clusters. */
    List<List<int[]>> candidateClusters() {
        var cells = candidates();
        var present = new HashSet<Long>();
        cells.forEach(cell -> present.add(encode(cell[0], cell[1])));

        var clusters = new ArrayList<List<int[]>>();
        var seen = new HashSet<Long>();
        for (var cell : cells) {
            long id = encode(cell[0], cell[1]);
            if (seen.contains(id)) {
                continue;
            }
            var cluster = new ArrayList<int[]>();
            var queue = new ArrayDeque<int[]>();
            queue.add(cell);
            seen.add(id);
            while (!queue.isEmpty()) {
                var cur = queue.poll();
                cluster.add(cur);
                for (int[] d : DIRS) {
                    int nc = cur[0] + d[0];
                    int nr = cur[1] + d[1];
                    long nid = encode(nc, nr);
                    if (present.contains(nid) && !seen.contains(nid)) {
                        seen.add(nid);
                        queue.add(new int[]{nc, nr});
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    /**
     * Road-BFS distances (in steps) from the transporter spawn cell (A6) over road tiles. Only road
     * cells reachable from the spawn appear in the map; the distance is the transporter move cost to
     * reach that cell (cost = 1/field).
     */
    Map<Long, Integer> roadDistancesFromSpawn() {
        var dist = new HashMap<Long, Integer>();
        var queue = new ArrayDeque<int[]>();
        if (isRoad(SPAWN_COL, SPAWN_ROW)) {
            dist.put(encode(SPAWN_COL, SPAWN_ROW), 0);
            queue.add(new int[]{SPAWN_COL, SPAWN_ROW});
        }
        while (!queue.isEmpty()) {
            var cur = queue.poll();
            int d = dist.get(encode(cur[0], cur[1]));
            for (int[] dir : DIRS) {
                int nc = cur[0] + dir[0];
                int nr = cur[1] + dir[1];
                if (inBounds(nc, nr) && isRoad(nc, nr)) {
                    long nid = encode(nc, nr);
                    if (!dist.containsKey(nid)) {
                        dist.put(nid, d + 1);
                        queue.add(new int[]{nc, nr});
                    }
                }
            }
        }
        return dist;
    }

    static int manhattan(int[] a, int[] b) {
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
    }

    /** Convert 0-based {@code (col, row)} to a label like {@code "A6"}. */
    static String toLabel(int col, int row) {
        return "" + (char) ('A' + col) + (row + 1);
    }

    static String toLabel(int[] cell) {
        return toLabel(cell[0], cell[1]);
    }

    /** Parse a label like {@code "A6"} into 0-based {@code [col, row]}. */
    static int[] toCell(String label) {
        int col = Character.toUpperCase(label.charAt(0)) - 'A';
        int row = Integer.parseInt(label.substring(1)) - 1;
        return new int[]{col, row};
    }

    static long encode(int col, int row) {
        return ((long) col << 20) | (row & 0xFFFFF);
    }

    private static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
}
