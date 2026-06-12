package com.morawski.dev.aidevs.tasks.task24goingthere;

import org.springframework.stereotype.Component;

/**
 * Pure-logic move chooser for the 3-row grid (no LLM, no I/O — unit-tested).
 *
 * <p>The radio channel is jammed, so the hint's <em>side</em> is only partly reliable. A large labelled
 * sample of live play showed one rock-solid fact and one weak one:
 * <ul>
 *   <li><b>Side hint (port/starboard)</b> — the rock is on <em>some</em> side, but reliably <em>not</em>
 *       in the rocket's current lane. So {@link Command#GO} (stay in the lane) is always safe; moving
 *       toward a side is a gamble (the named side is often wrong), so we never do it.</li>
 *   <li><b>Bow hint (front)</b> — the rock is usually (≈70%) dead ahead in the current lane, so we must
 *       sidestep; we pick the sidestep that moves toward the base row. (A minority of "front" hints are
 *       wrong and put the rock on a side, which can still crash — that residual is absorbed by restarts.)</li>
 * </ul>
 *
 * <p>Row convergence therefore happens only on bow hints; side hints just hold the lane. With 3 rows a
 * safe in-bounds move always exists.
 *
 * <p>{@code leftRowDelta} encodes orientation: the row-number change of {@link Command#LEFT} ({@code -1}
 * if "left" goes up to a lower row number — confirmed live — or {@code +1} otherwise).
 */
@Component
class GoingtherePlanner {

    static final int ROWS = 3;

    /**
     * Choose the move command.
     *
     * @param currentRow   1-based current row (1..3)
     * @param baseRow      1-based target row at the goal column
     * @param avoid        the rock's side from the radio hint; never {@code null}
     * @param leftRowDelta {@code -1} or {@code +1}: the row-number change of {@link Command#LEFT}
     * @return a safe {@link Command}
     */
    Command next(int currentRow, int baseRow, AvoidSide avoid, int leftRowDelta) {
        if (leftRowDelta != 1 && leftRowDelta != -1) {
            throw new IllegalArgumentException("leftRowDelta must be ±1, was " + leftRowDelta);
        }

        // Side hint: the lane is clear, so hold it (moving toward an unreliable side would gamble).
        if (avoid != AvoidSide.FRONT) {
            return Command.GO;
        }

        // Bow hint: the rock is in the lane — sidestep toward the base row.
        Command best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Command c : new Command[]{Command.LEFT, Command.RIGHT}) {
            int row = currentRow + rowDelta(c, leftRowDelta);
            if (row < 1 || row > ROWS) {
                continue;
            }
            int score = Math.abs(row - baseRow);
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null) {
            throw new IllegalStateException("No safe sidestep from row " + currentRow);
        }
        return best;
    }

    private static int rowDelta(Command c, int leftRowDelta) {
        return switch (c) {
            case GO -> 0;
            case LEFT -> leftRowDelta;
            case RIGHT -> -leftRowDelta;
            case START -> throw new IllegalArgumentException("START is not a move");
        };
    }
}
