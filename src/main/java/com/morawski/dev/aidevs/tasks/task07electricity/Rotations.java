package com.morawski.dev.aidevs.tasks.task07electricity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pure, network-free rotation maths for the wiring puzzle (the only deterministic part — unit
 * tested). The vision model says <em>which edges</em> a tile's cable exits; this decides
 * <em>how many 90° clockwise rotations</em> turn the current edge set into the target.
 *
 * <p>Brute-forcing {@code k ∈ {0,1,2,3}} and picking the smallest match handles piece symmetries
 * for free: a straight wire matches at two values of {@code k} (period 2), a cross at every {@code k}
 * (always {@code 0}); we take the fewest rotations.
 */
final class Rotations {

    private Rotations() {
    }

    /** {@code edges} after rotating the tile 90° clockwise {@code k} times (any integer, normalised mod 4). */
    static EnumSet<Edge> rotateCW(Set<Edge> edges, int k) {
        int steps = ((k % 4) + 4) % 4;
        var current = EnumSet.noneOf(Edge.class);
        current.addAll(edges);
        for (int i = 0; i < steps; i++) {
            var next = EnumSet.noneOf(Edge.class);
            for (Edge e : current) {
                next.add(e.rotateCW());
            }
            current = next;
        }
        return current;
    }

    /**
     * Smallest number of 90° clockwise rotations ({@code 0..3}) that turns {@code current} into
     * {@code target}, or {@code -1} if no rotation matches (a perception error — the two tiles are
     * different pieces, not just differently oriented).
     */
    static int requiredRotations(Set<Edge> current, Set<Edge> target) {
        for (int k = 0; k < 4; k++) {
            if (rotateCW(current, k).equals(target)) {
                return k;
            }
        }
        return -1;
    }
}
