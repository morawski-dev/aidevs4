package com.morawski.dev.aidevs.tasks.task15savethem;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for the route optimiser (no network, no LLM). The map, the vehicle
 * consumption numbers, the budgets and the tree penalty are all taken verbatim from a live recon of
 * the {@code savethem} tools ({@code /api/maps?Skolwin}, {@code /api/wehicles}, {@code /api/books}),
 * so these pin both the resource model and that a feasible route exists and is correctly found.
 */
class RoutePlannerTest {

    private final RoutePlanner planner = new RoutePlanner();

    private static final double FUEL_BUDGET = 10.0;
    private static final double FOOD_BUDGET = 10.0;
    private static final double TREE_PENALTY = 0.2;

    /** The four travel modes (from /api/wehicles). powered = burns fuel = can't cross water. */
    private static final List<Mode> MODES = List.of(
            new Mode("walk", 0.0, 2.5),
            new Mode("horse", 0.0, 1.6),
            new Mode("car", 0.7, 1.0),
            new Mode("rocket", 1.0, 0.1));

    /** The Skolwin map (from /api/maps), rows north→south. S at (7,0), G at (4,8). */
    private static Grid skolwin() {
        String[] rows = {
                "........WW",
                ".......WW.",
                ".T....WW..",
                "......W...",
                "..T...W.G.",
                "....R.W...",
                "...RR.WW..",
                "SR.....W..",
                "......WW..",
                ".....WW...",
        };
        var map = Arrays.stream(rows)
                .map(r -> r.chars().mapToObj(ch -> String.valueOf((char) ch)).toList())
                .toList();
        return Grid.fromRows(map);
    }

    @Test
    void findsAFeasibleRouteToTheGoal() {
        var answer = planner.plan(skolwin(), MODES, FUEL_BUDGET, FOOD_BUDGET, TREE_PENALTY);

        assertThat(answer).isNotNull();
        // [vehicle, steps...]; the vehicle must be one of the known modes.
        assertThat(MODES.stream().map(Mode::name)).contains(answer.getFirst());
        Replay replay = replay(skolwin(), answer);
        assertThat(replay.reachedGoal).as("route must end on the goal tile").isTrue();
        assertThat(replay.fuelUsed).as("fuel within budget").isLessThanOrEqualTo(FUEL_BUDGET + 1e-9);
        assertThat(replay.foodUsed).as("food within budget").isLessThanOrEqualTo(FOOD_BUDGET + 1e-9);
    }

    @Test
    void pureGroundVehiclesCannotAffordOrCrossTheWaterBarrier() {
        // car/rocket are lost on water and can't reach G alone; walk/horse can cross but the 11-move
        // minimum costs too much food. So the only viable plan must start with rocket and dismount.
        var answer = planner.plan(skolwin(), MODES, FUEL_BUDGET, FOOD_BUDGET, TREE_PENALTY);
        assertThat(answer.getFirst()).isEqualTo("rocket");
        assertThat(answer).contains("dismount");
    }

    @Test
    void routeIsTheShortestPossibleElevenSteps() {
        // S(7,0)→G(4,8): Manhattan distance is 3 + 8 = 11; dismount is a free in-place command, so a
        // minimal answer has 11 movement steps plus one dismount plus the vehicle = 13 tokens.
        var answer = planner.plan(skolwin(), MODES, FUEL_BUDGET, FOOD_BUDGET, TREE_PENALTY);
        long moves = answer.stream().skip(1).filter(s -> !s.equals("dismount")).count();
        assertThat(moves).isEqualTo(11);
    }

    @Test
    void infeasibleWhenBudgetsAreTooSmall() {
        var answer = planner.plan(skolwin(), MODES, 1.0, 1.0, TREE_PENALTY);
        assertThat(answer).isNull();
    }

    // --- helpers ---

    private record Replay(boolean reachedGoal, double fuelUsed, double foodUsed) {
    }

    /** Replays an answer array against the rules to independently verify legality and cost. */
    private Replay replay(Grid grid, List<String> answer) {
        Mode start = MODES.stream().filter(m -> m.name().equals(answer.getFirst())).findFirst().orElseThrow();
        Mode walk = MODES.stream().filter(m -> m.name().equals("walk")).findFirst().orElseThrow();
        Mode current = start;
        int r = grid.startRow();
        int c = grid.startCol();
        double fuel = 0;
        double food = 0;
        for (String step : answer.subList(1, answer.size())) {
            if (step.equals("dismount")) {
                current = walk;
                continue;
            }
            Direction d = switch (step) {
                case "up" -> Direction.UP;
                case "down" -> Direction.DOWN;
                case "left" -> Direction.LEFT;
                case "right" -> Direction.RIGHT;
                default -> throw new IllegalStateException("bad step: " + step);
            };
            r += d.dRow();
            c += d.dCol();
            assertThat(grid.inBounds(r, c)).as("step stays on the map").isTrue();
            char tile = grid.at(r, c);
            assertThat(tile).as("never step on a rock").isNotEqualTo(Grid.ROCK);
            if (current.powered()) {
                assertThat(tile).as("powered modes never enter water").isNotEqualTo(Grid.WATER);
            }
            fuel += current.fuelPerMove() + (current.powered() && tile == Grid.TREE ? 0.2 : 0);
            food += current.foodPerMove();
        }
        boolean atGoal = r == grid.goalRow() && c == grid.goalCol();
        return new Replay(atGoal, fuel, food);
    }
}
