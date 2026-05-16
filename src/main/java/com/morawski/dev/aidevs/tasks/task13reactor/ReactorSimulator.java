package com.morawski.dev.aidevs.tasks.task13reactor;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-logic model of how the reactor blocks move — no I/O, no state. Each command advances every
 * block one cell in its current direction; a block bounces when it reaches the top ({@code topRow == 1})
 * or the bottom ({@code bottomRow == rows}, i.e. {@code topRow == rows - 1}). The reported direction is
 * the post-bounce one, so this matches the API: a block that just hit the bottom is already {@code UP}.
 *
 * <p>This is the only thing the {@link ReactorPlanner} needs to look ahead, because the robot's lane in
 * a column is blocked exactly when that column holds a block with {@code bottomRow == rows}.
 */
@Component
class ReactorSimulator {

    /** Advance all blocks one step (one command). Robot-independent — blocks move on every command. */
    List<Block> stepBlocks(List<Block> blocks, int rows) {
        int maxTop = rows - 1; // block height is 2, so the lowest top row is rows-1 (bottom = rows)
        var next = new ArrayList<Block>(blocks.size());
        for (Block b : blocks) {
            int top = b.topRow() + (b.dir() == Block.Direction.DOWN ? 1 : -1);
            Block.Direction dir = b.dir();
            if (top <= 1) {
                top = 1;
                dir = Block.Direction.DOWN;
            } else if (top >= maxTop) {
                top = maxTop;
                dir = Block.Direction.UP;
            }
            next.add(new Block(b.col(), top, top + 1, dir));
        }
        return List.copyOf(next);
    }

    /** Columns whose bottom row is occupied by a block in this configuration — the robot's lane is blocked there. */
    java.util.Set<Integer> blockedColumns(List<Block> blocks, int rows) {
        var blocked = new java.util.HashSet<Integer>();
        for (Block b : blocks) {
            if (b.bottomRow() == rows) {
                blocked.add(b.col());
            }
        }
        return blocked;
    }
}
