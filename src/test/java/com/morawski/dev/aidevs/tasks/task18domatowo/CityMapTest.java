package com.morawski.dev.aidevs.tasks.task18domatowo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for the Domatowo map parsing/classification (no network, no LLM). The 11×11
 * grid is reproduced verbatim from a live {@code getMap} recon (symbols → tile keys), so these pin
 * the tallest-block detection, clustering and coordinate conversion the planner relies on.
 */
class CityMapTest {

    /** The 11×11 board from recon, one row per string, 2-char symbols (space-padded) separated by '|'. */
    private static final String[] ROWS = {
            "DR|UL|UL|UL|  |B3|B3|DR|  |PK|PK",
            "DR|DR|  |UL|UL|B3|B3|DR|UL|PK|PK",
            "  |  |  |UL|PK|  |  |DR|UL|  |  ",
            "B1|B1|  |UL|PK|SZ|SZ|SZ|UL|BS|BS",
            "B1|B1|  |UL|PK|SZ|SZ|SZ|UL|BS|BS",
            "UL|UL|UL|UL|UL|UL|UL|UL|UL|UL|  ",
            "B2|B2|  |UL|  |KS|KS|KS|  |DR|  ",
            "B2|B2|  |UL|  |KS|KS|KS|  |DR|  ",
            "  |UL|UL|UL|UL|UL|UL|UL|UL|UL|  ",
            "B3|B3|B3|  |DR|  |  |B3|B3|DR|  ",
            "B3|B3|B3|  |DR|  |  |B3|B3|DR|  ",
    };

    private static CityMap realMap() {
        return CityMapTestSupport.parse(ROWS);
    }

    @Test
    void parsesElevenBySeven() {
        var map = realMap();
        assertThat(map.size()).isEqualTo(11);
        assertThat(map.isRoad(0, 5)).isTrue();   // A6 is road (spawn row)
        assertThat(map.isRoad(10, 5)).isFalse(); // K6 is empty
        assertThat(map.keyAt(0, 3)).isEqualTo("block1"); // A4
    }

    @Test
    void findsTheFourteenTallestBlockCells() {
        var labels = realMap().candidates().stream().map(CityMap::toLabel).toList();
        // block3 cells from searchSymbol("B3") recon.
        assertThat(labels).containsExactlyInAnyOrder(
                "F1", "G1", "F2", "G2",
                "A10", "B10", "C10", "H10", "I10",
                "A11", "B11", "C11", "H11", "I11");
    }

    @Test
    void groupsTallestBlocksIntoThreeClusters() {
        var clusters = realMap().candidateClusters();
        assertThat(clusters).hasSize(3);
        var sizes = clusters.stream().map(List::size).sorted().toList();
        assertThat(sizes).containsExactly(4, 4, 6); // NE 4, SE 4, SW 6
    }

    @Test
    void roadReachabilityReachesEveryDropPoint() {
        var dist = realMap().roadDistancesFromSpawn();
        // The three cluster drop roads must all be reachable from the A6–D6 spawn over roads.
        assertThat(dist).containsKeys(
                CityMap.encode(CityMap.toCell("C9")[0], CityMap.toCell("C9")[1]),   // SW
                CityMap.encode(CityMap.toCell("I9")[0], CityMap.toCell("I9")[1]),   // SE
                CityMap.encode(CityMap.toCell("E2")[0], CityMap.toCell("E2")[1]));  // NE
        // The only vertical road link between row 6 and row 9 is column D, so C9 costs 7 from spawn.
        assertThat(dist.get(CityMap.encode(2, 8))).isEqualTo(7);
    }

    @Test
    void convertsCoordinatesBothWays() {
        assertThat(CityMap.toLabel(0, 5)).isEqualTo("A6");
        assertThat(CityMap.toLabel(5, 0)).isEqualTo("F1");
        assertThat(CityMap.toCell("A6")).containsExactly(0, 5);
        assertThat(CityMap.toCell("K11")).containsExactly(10, 10);
    }

}
