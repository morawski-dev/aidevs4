package com.morawski.dev.aidevs.tasks.task13reactor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for the reactor core (no network, no LLM). The block configuration and the
 * one-step transitions are taken verbatim from a live recon of the {@code reactor} API, so these
 * pin both the simulator's bounce math and the planner's safety/optimality.
 */
class ReactorPlannerTest {

    private final ReactorSimulator simulator = new ReactorSimulator();
    private final ReactorPlanner planner = new ReactorPlanner(simulator);

    /** The board state the API returns immediately after {@code start} (from recon). */
    private static BoardState startState() {
        var blocks = List.of(
                new Block(2, 1, 2, Block.Direction.DOWN),
                new Block(3, 1, 2, Block.Direction.DOWN),
                new Block(4, 1, 2, Block.Direction.DOWN),
                new Block(5, 4, 5, Block.Direction.UP),
                new Block(6, 2, 3, Block.Direction.UP));
        return new BoardState(7, 5, 1, 5, 7, 5, blocks, false, 100, "init");
    }

    @Test
    void simulatorReproducesTheFirstObservedStep() {
        // After one command the recon showed: col2 top=2 down, col5 top=3 up, col6 top=1 down (bounced).
        var next = simulator.stepBlocks(startState().blocks(), 5);
        assertThat(next).contains(
                new Block(2, 2, 3, Block.Direction.DOWN),
                new Block(5, 3, 4, Block.Direction.UP),
                new Block(6, 1, 2, Block.Direction.DOWN));
    }

    @Test
    void blocksReturnToStartAfterSixSteps() {
        // Every block oscillates over top rows 1..4, so the whole board's period is 6.
        var cfg = startState().blocks();
        for (int i = 0; i < 6; i++) {
            cfg = simulator.stepBlocks(cfg, 5);
        }
        assertThat(cfg).containsExactlyInAnyOrderElementsOf(startState().blocks());
    }

    @Test
    void blockedLaneMatchesBottomRowOccupancy() {
        // At start, col5's block sits at the bottom (top=4, bottom=5) — its lane is blocked.
        assertThat(simulator.blockedColumns(startState().blocks(), 5)).containsExactly(5);
    }

    @Test
    void planReachesGoalAndIsSafeUnderEveryMoveOrdering() {
        var plan = planner.plan(startState());
        assertThat(plan).isNotNull().isNotEmpty();

        // Replay the plan against the simulator and assert the robot never shares its lane with a
        // block — in the configuration *before* the step or *after* it (so it's safe whatever order
        // the robot and blocks resolve in).
        List<Block> cfg = startState().blocks();
        int col = startState().robotCol();
        for (Command cmd : plan) {
            int newCol = switch (cmd) {
                case RIGHT -> col + 1;
                case LEFT -> col - 1;
                default -> col;
            };
            assertThat(newCol).isBetween(1, 7);
            Set<Integer> now = simulator.blockedColumns(cfg, 5);
            List<Block> stepped = simulator.stepBlocks(cfg, 5);
            Set<Integer> after = simulator.blockedColumns(stepped, 5);
            assertThat(now).doesNotContain(newCol);
            assertThat(after).doesNotContain(newCol);
            cfg = stepped;
            col = newCol;
        }
        assertThat(col).isEqualTo(7);
    }

    @Test
    void planHasCorrectNetDisplacementAndIsCompact() {
        // Net horizontal displacement must be exactly +6 (col 1 → col 7); BFS may dodge with a
        // left+right pair instead of waiting, but it must not pad the path.
        var plan = planner.plan(startState());
        long rights = plan.stream().filter(c -> c == Command.RIGHT).count();
        long lefts = plan.stream().filter(c -> c == Command.LEFT).count();
        assertThat(rights - lefts).isEqualTo(6);
        assertThat(plan.size()).isLessThanOrEqualTo(12);
    }
}
