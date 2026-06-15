package com.morawski.dev.aidevs.tasks.task18domatowo;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Deterministic search planner (no LLM, no network). Turns a {@link CityMap} into an ordered list of
 * {@link Mission}s — one per cluster of tallest blocks — that minimise expensive scout walking by
 * ferrying scouts cheaply on roads (1/field) close to each cluster, then sweeping the cluster on foot
 * (7/field) with a nearest-neighbour order.
 *
 * <p>For each cluster the drop is the spawn-reachable road cell closest (Manhattan) to the cluster;
 * clusters are ordered cheapest-transporter-distance first so an early find spends the least.
 */
@Component
class DomatowoPlanner {

    /** Build the ordered missions covering every tallest-block cell on the map. */
    List<Mission> plan(CityMap map) {
        Map<Long, Integer> roadDist = map.roadDistancesFromSpawn();
        var clusters = map.candidateClusters();

        var missions = new ArrayList<RankedMission>();
        for (var cluster : clusters) {
            int[] drop = bestDrop(cluster, roadDist);
            if (drop == null) {
                // No spawn-reachable road next to this cluster — fall back to its own first cell so the
                // scout still gets created near it (the API computes whatever path it can).
                drop = cluster.getFirst();
            }
            var ordered = nearestNeighbourOrder(cluster, drop);
            int rank = roadDist.getOrDefault(CityMap.encode(drop[0], drop[1]), Integer.MAX_VALUE);
            var cellLabels = ordered.stream().map(CityMap::toLabel).toList();
            missions.add(new RankedMission(rank, new Mission(CityMap.toLabel(drop), cellLabels)));
        }

        missions.sort(Comparator.comparingInt(RankedMission::rank));
        return missions.stream().map(RankedMission::mission).toList();
    }

    /** The spawn-reachable road cell with the smallest Manhattan distance to any cell of the cluster. */
    private int[] bestDrop(List<int[]> cluster, Map<Long, Integer> roadDist) {
        int[] best = null;
        int bestManhattan = Integer.MAX_VALUE;
        int bestRoadDist = Integer.MAX_VALUE;
        for (var entry : roadDist.entrySet()) {
            long id = entry.getKey();
            int col = (int) (id >> 20);
            int row = (int) (id & 0xFFFFF);
            int[] road = {col, row};
            int m = cluster.stream().mapToInt(cell -> CityMap.manhattan(road, cell)).min().orElse(Integer.MAX_VALUE);
            int rd = entry.getValue();
            if (m < bestManhattan || (m == bestManhattan && rd < bestRoadDist)) {
                bestManhattan = m;
                bestRoadDist = rd;
                best = road;
            }
        }
        return best;
    }

    /** Greedy nearest-neighbour visiting order over the cluster cells, starting from {@code from}. */
    private List<int[]> nearestNeighbourOrder(List<int[]> cluster, int[] from) {
        var remaining = new ArrayList<>(cluster);
        var order = new ArrayList<int[]>();
        int[] cur = from;
        while (!remaining.isEmpty()) {
            int[] next = null;
            int bestD = Integer.MAX_VALUE;
            for (var cell : remaining) {
                int d = CityMap.manhattan(cur, cell);
                if (d < bestD) {
                    bestD = d;
                    next = cell;
                }
            }
            order.add(next);
            remaining.remove(next);
            cur = next;
        }
        return order;
    }

    private record RankedMission(int rank, Mission mission) {
    }
}
