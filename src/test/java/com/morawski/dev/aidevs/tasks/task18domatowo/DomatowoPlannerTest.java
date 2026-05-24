package com.morawski.dev.aidevs.tasks.task18domatowo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for the search planner (no network, no LLM). Uses the real recon map and pins:
 * one mission per cluster, every tall-block cell covered exactly once, and each drop is a road cell
 * adjacent (Manhattan 1) to its cluster — i.e. scouts only ever walk the cheap last leg.
 */
class DomatowoPlannerTest {

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

    private final DomatowoPlanner planner = new DomatowoPlanner();

    private static CityMap map() {
        return CityMapTestSupport.parse(ROWS);
    }

    @Test
    void producesOneMissionPerCluster() {
        assertThat(planner.plan(map())).hasSize(3);
    }

    @Test
    void coversEveryTallBlockCellExactlyOnce() {
        var visited = planner.plan(map()).stream().flatMap(m -> m.cells().stream()).toList();
        assertThat(visited).containsExactlyInAnyOrder(
                "F1", "G1", "F2", "G2",
                "A10", "B10", "C10", "H10", "I10",
                "A11", "B11", "C11", "H11", "I11");
        assertThat(visited).doesNotHaveDuplicates();
    }

    @Test
    void everyDropIsRoadAndAdjacentToItsCluster() {
        var map = map();
        for (var mission : planner.plan(map)) {
            int[] drop = CityMap.toCell(mission.drop());
            assertThat(map.isRoad(drop[0], drop[1]))
                    .as("drop %s must be a road cell", mission.drop()).isTrue();
            int nearest = mission.cells().stream()
                    .mapToInt(c -> CityMap.manhattan(drop, CityMap.toCell(c))).min().orElseThrow();
            assertThat(nearest).as("drop %s adjacent to cluster", mission.drop()).isEqualTo(1);
        }
    }

    @Test
    void ordersClustersCheapestTransporterDistanceFirst() {
        var map = map();
        var dist = map.roadDistancesFromSpawn();
        var missions = planner.plan(map);
        int prev = -1;
        for (var mission : missions) {
            int[] drop = CityMap.toCell(mission.drop());
            int d = dist.get(CityMap.encode(drop[0], drop[1]));
            assertThat(d).as("non-decreasing drop distance").isGreaterThanOrEqualTo(prev);
            prev = d;
        }
    }
}
