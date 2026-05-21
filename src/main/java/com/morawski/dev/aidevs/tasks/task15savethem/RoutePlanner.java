package com.morawski.dev.aidevs.tasks.task15savethem;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Deterministic route optimiser for {@code savethem} — pure logic, no LLM, no I/O.
 *
 * <p>The traveller picks one vehicle at departure and may {@code dismount} once to continue on foot
 * (one-way; the rules forbid switching between vehicles). Fuel and food are spent per move and the
 * mission fails the instant either runs out, so the route, the vehicle, and the dismount point must
 * all be chosen together. Resource costs are small multiples of 0.1, so we track them as integer
 * tenths and the whole search space — {@code rows × cols × {in-vehicle, walking} × fuelₜₑₙₜₕₛ ×
 * foodₜₑₙₜₕₛ} — is tiny (~2M states), making an exact search trivial.
 *
 * <p>For each candidate start vehicle we run a <b>0-1 BFS</b> over that state space: a move costs 1
 * (we minimise the number of steps = "as fast as possible") and {@code dismount} costs 0. A state
 * is only entered if it stays within both budgets, so any route the search returns is guaranteed
 * feasible. Passability follows the book notes: rocks block everyone, water blocks powered modes
 * (car/rocket are lost), and entering a tree tile adds the powered-mode fuel penalty. The cheapest
 * (fewest-move) feasible route across all start vehicles wins.
 */
@Component
class RoutePlanner {

    /** Action codes stored per state for path reconstruction. */
    private static final byte NONE = -1;
    private static final byte DISMOUNT = 4;

    /**
     * Find the fastest feasible answer array, or {@code null} if no vehicle can reach the goal within
     * budget. The returned list is the full {@code /verify} answer: {@code [vehicleName, step, …]}
     * where steps are {@code up/down/left/right} and possibly {@code dismount}.
     */
    List<String> plan(Grid grid, List<Mode> modes, double fuelBudget, double foodBudget, double treePenalty) {
        Mode walk = modes.stream()
                .filter(m -> m.name().equalsIgnoreCase("walk"))
                .findFirst()
                .orElse(null);

        List<String> best = null;
        for (Mode start : modes) {
            var answer = planForStart(grid, start, walk, fuelBudget, foodBudget, treePenalty);
            if (answer != null && (best == null || answer.size() < best.size())) {
                best = answer;
            }
        }
        return best;
    }

    /** 0-1 BFS for a fixed start vehicle; returns the answer array (vehicle first) or {@code null}. */
    private List<String> planForStart(Grid grid, Mode start, Mode walk,
                                      double fuelBudget, double foodBudget, double treePenalty) {
        int rows = grid.rows();
        int cols = grid.cols();
        int fuelCap = tenths(fuelBudget);
        int foodCap = tenths(foodBudget);
        int fuelSize = fuelCap + 1;
        int foodSize = foodCap + 1;
        int size = rows * cols * 2 * fuelSize * foodSize;

        // dismount is only possible if we started in a (powered or mount) vehicle, not already walking.
        boolean canDismount = walk != null && !start.name().equalsIgnoreCase(walk.name());
        Mode walkMode = walk != null ? walk : start; // if "walk" wasn't supplied, the start mode is on foot

        int[] dist = new int[size];
        int[] parent = new int[size];
        byte[] action = new byte[size];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        Arrays.fill(action, NONE);

        boolean startWalking = start.name().equalsIgnoreCase(walkMode.name());
        int startIdx = index(grid.startRow(), grid.startCol(), startWalking, 0, 0, cols, fuelSize, foodSize);
        dist[startIdx] = 0;

        var deque = new ArrayDeque<Integer>();
        deque.add(startIdx);

        while (!deque.isEmpty()) {
            int s = deque.pollFirst();
            int food = s % foodSize;
            int t1 = s / foodSize;
            int fuel = t1 % fuelSize;
            int t2 = t1 / fuelSize;
            int walking = t2 % 2;
            int t3 = t2 / 2;
            int col = t3 % cols;
            int row = t3 / cols;

            if (row == grid.goalRow() && col == grid.goalCol()) {
                return reconstruct(parent, action, s, start.name());
            }

            Mode current = walking == 1 ? walkMode : start;

            // dismount (0-cost): switch to walking in place.
            if (canDismount && walking == 0) {
                int ns = index(row, col, true, fuel, food, cols, fuelSize, foodSize);
                if (dist[s] < dist[ns]) {
                    dist[ns] = dist[s];
                    parent[ns] = s;
                    action[ns] = DISMOUNT;
                    deque.addFirst(ns);
                }
            }

            // moves (1-cost).
            for (Direction d : Direction.values()) {
                int nr = row + d.dRow();
                int nc = col + d.dCol();
                if (!grid.inBounds(nr, nc)) {
                    continue;
                }
                char tile = grid.at(nr, nc);
                if (!passable(tile, current)) {
                    continue;
                }
                int nFuel = fuel + tenths(current.fuelPerMove())
                        + (current.powered() && tile == Grid.TREE ? tenths(treePenalty) : 0);
                int nFood = food + tenths(current.foodPerMove());
                if (nFuel > fuelCap || nFood > foodCap) {
                    continue; // would run out of fuel or food — infeasible
                }
                int ns = index(nr, nc, walking == 1, nFuel, nFood, cols, fuelSize, foodSize);
                if (dist[s] + 1 < dist[ns]) {
                    dist[ns] = dist[s] + 1;
                    parent[ns] = s;
                    action[ns] = (byte) d.ordinal();
                    deque.addLast(ns);
                }
            }
        }
        return null;
    }

    /** Rocks block everyone; water blocks powered modes (lost on entry); everything else is passable. */
    private static boolean passable(char tile, Mode mode) {
        if (tile == Grid.ROCK) {
            return false;
        }
        if (tile == Grid.WATER) {
            return !mode.powered();
        }
        return true;
    }

    private List<String> reconstruct(int[] parent, byte[] action, int goal, String vehicle) {
        var steps = new ArrayList<String>();
        int s = goal;
        while (parent[s] != -1) {
            byte a = action[s];
            steps.add(a == DISMOUNT ? "dismount" : Direction.values()[a].token());
            s = parent[s];
        }
        java.util.Collections.reverse(steps);
        var answer = new ArrayList<String>(steps.size() + 1);
        answer.add(vehicle);
        answer.addAll(steps);
        return answer;
    }

    private static int index(int row, int col, boolean walking, int fuel, int food,
                             int cols, int fuelSize, int foodSize) {
        int cell = row * cols + col;
        int withWalk = cell * 2 + (walking ? 1 : 0);
        return (withWalk * fuelSize + fuel) * foodSize + food;
    }

    private static int tenths(double v) {
        return (int) Math.round(v * 10);
    }
}
