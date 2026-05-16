package com.morawski.dev.aidevs.tasks.task13reactor;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic controller for the reactor robot — pure logic, no LLM, no I/O.
 *
 * <p>The board is fully deterministic and periodic: blocks advance one step per command and each
 * block cycles with a small period, so the whole board repeats every {@code P} steps. That makes the
 * search space tiny: a node is {@code (robotColumn, stepPhase mod P)}. A breadth-first search from the
 * current state over {@code RIGHT / WAIT / LEFT} finds the shortest safe sequence to the goal column
 * (which, being block-free, is reached as soon as the robot stands on it).
 *
 * <p><b>Safety is checked under every possible move ordering.</b> A move onto column {@code c} is
 * allowed only if {@code c}'s bottom row is free both in the current configuration <em>and</em> in the
 * next one — so it's safe whether the robot moves before the blocks, after them, or simultaneously.
 * The robot never even momentarily shares its lane with a block.
 */
@Component
class ReactorPlanner {

    /** Safety cap on how far we'll simulate to detect the board's period. */
    private static final int MAX_PERIOD = 64;

    private final ReactorSimulator simulator;

    ReactorPlanner(ReactorSimulator simulator) {
        this.simulator = simulator;
    }

    /**
     * Shortest safe command sequence ({@link Command#RIGHT}/{@link Command#WAIT}/{@link Command#LEFT})
     * from the given state to the goal column, or {@code null} if no safe path exists.
     */
    List<Command> plan(BoardState state) {
        int cols = state.cols();
        int rows = state.rows();
        int goalCol = state.goalCol();

        List<Set<Integer>> blockedByPhase = blockedByPhase(state.blocks(), rows);
        int period = blockedByPhase.size();

        if (state.robotCol() == goalCol) {
            return List.of();
        }

        // BFS over (col, phase). visited[col][phase]; track parent + command to reconstruct the path.
        var visited = new boolean[cols + 1][period];
        var parentCol = new int[cols + 1][period];
        var parentPhase = new int[cols + 1][period];
        var parentCmd = new Command[cols + 1][period];

        var queue = new ArrayDeque<int[]>();
        visited[state.robotCol()][0] = true;
        queue.add(new int[]{state.robotCol(), 0});

        while (!queue.isEmpty()) {
            int[] node = queue.poll();
            int col = node[0];
            int phase = node[1];
            int nextPhase = (phase + 1) % period;

            // Prefer RIGHT (progress), then WAIT, then LEFT — among equal-length paths, makes progress first.
            for (Command cmd : List.of(Command.RIGHT, Command.WAIT, Command.LEFT)) {
                int newCol = col + delta(cmd);
                if (newCol < 1 || newCol > cols) {
                    continue;
                }
                // Strict safety: destination lane free now AND after the blocks step.
                if (blockedByPhase.get(phase).contains(newCol)
                        || blockedByPhase.get(nextPhase).contains(newCol)) {
                    continue;
                }
                if (newCol == goalCol) {
                    return reconstruct(parentCol, parentPhase, parentCmd, col, phase, cmd);
                }
                if (!visited[newCol][nextPhase]) {
                    visited[newCol][nextPhase] = true;
                    parentCol[newCol][nextPhase] = col;
                    parentPhase[newCol][nextPhase] = phase;
                    parentCmd[newCol][nextPhase] = cmd;
                    queue.add(new int[]{newCol, nextPhase});
                }
            }
        }
        return null;
    }

    /** Blocked-column set for each step phase 0..P-1, where P is the board's repeat period. */
    private List<Set<Integer>> blockedByPhase(List<Block> blocks, int rows) {
        var result = new ArrayList<Set<Integer>>();
        List<Block> cfg = blocks;
        String start = signature(cfg);
        do {
            result.add(simulator.blockedColumns(cfg, rows));
            cfg = simulator.stepBlocks(cfg, rows);
        } while (!signature(cfg).equals(start) && result.size() < MAX_PERIOD);
        return result;
    }

    private static int delta(Command cmd) {
        return switch (cmd) {
            case RIGHT -> 1;
            case LEFT -> -1;
            default -> 0; // WAIT
        };
    }

    private List<Command> reconstruct(int[][] parentCol, int[][] parentPhase, Command[][] parentCmd,
                                      int fromCol, int fromPhase, Command lastCmd) {
        var path = new ArrayList<Command>();
        path.add(lastCmd);
        // Walk parents back to the BFS root (the start node has no recorded parent command).
        int col = fromCol;
        int phase = fromPhase;
        while (parentCmd[col][phase] != null) {
            path.add(parentCmd[col][phase]);
            int pc = parentCol[col][phase];
            int pp = parentPhase[col][phase];
            col = pc;
            phase = pp;
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private static String signature(List<Block> blocks) {
        // Order is stable from the API, but sort defensively so equal configs compare equal.
        return blocks.stream()
                .map(b -> b.col() + ":" + b.topRow() + ":" + b.dir())
                .sorted()
                .reduce("", (a, b) -> a + "|" + b);
    }
}
