package com.morawski.dev.aidevs.tasks.task24goingthere;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic tests for the move chooser (no network, no LLM):
 * <ul>
 *   <li>side hints ⇒ always GO (hold the clear lane);</li>
 *   <li>bow (front) hints ⇒ sidestep (never GO), in bounds, toward the base row.</li>
 * </ul>
 */
class GoingtherePlannerTest {

    private final GoingtherePlanner planner = new GoingtherePlanner();

    @Test
    void sideHintsAlwaysHoldTheLane() {
        for (int leftDelta : new int[]{-1, 1}) {
            for (int row = 1; row <= 3; row++) {
                for (int base = 1; base <= 3; base++) {
                    for (AvoidSide avoid : new AvoidSide[]{AvoidSide.LEFT, AvoidSide.RIGHT}) {
                        assertThat(planner.next(row, base, avoid, leftDelta))
                                .as("side hint row=%d base=%d avoid=%s d=%d", row, base, avoid, leftDelta)
                                .isEqualTo(Command.GO);
                    }
                }
            }
        }
    }

    @Test
    void bowHintsSidestepInBoundsAndNeverGo() {
        for (int leftDelta : new int[]{-1, 1}) {
            for (int row = 1; row <= 3; row++) {
                for (int base = 1; base <= 3; base++) {
                    Command move = planner.next(row, base, AvoidSide.FRONT, leftDelta);
                    assertThat(move).as("front never GO (row=%d)", row).isNotEqualTo(Command.GO);
                    int dest = row + delta(move, leftDelta);
                    assertThat(dest).as("front sidestep in bounds (row=%d d=%d)", row, leftDelta).isBetween(1, 3);
                }
            }
        }
    }

    @Test
    void bowSidestepsTowardBase() {
        assertThat(planner.next(2, 1, AvoidSide.FRONT, -1)).isEqualTo(Command.LEFT);  // up toward row1
        assertThat(planner.next(2, 3, AvoidSide.FRONT, -1)).isEqualTo(Command.RIGHT); // down toward row3
    }

    @Test
    void bowAtEdgesTakesTheOnlyInBoundsSidestep() {
        assertThat(planner.next(1, 1, AvoidSide.FRONT, -1)).isEqualTo(Command.RIGHT); // LEFT would be off-map
        assertThat(planner.next(3, 3, AvoidSide.FRONT, -1)).isEqualTo(Command.LEFT);  // RIGHT would be off-map
    }

    @Test
    void respectsInvertedOrientation() {
        // left=down (delta +1): rock ahead, base above (row1) ⇒ RIGHT moves up to row 1.
        assertThat(planner.next(2, 1, AvoidSide.FRONT, 1)).isEqualTo(Command.RIGHT);
    }

    @Test
    void rejectsInvalidOrientation() {
        assertThatThrownBy(() -> planner.next(2, 2, AvoidSide.FRONT, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int delta(Command move, int leftDelta) {
        return switch (move) {
            case GO -> 0;
            case LEFT -> leftDelta;
            case RIGHT -> -leftDelta;
            case START -> throw new IllegalStateException("planner never returns START");
        };
    }
}
