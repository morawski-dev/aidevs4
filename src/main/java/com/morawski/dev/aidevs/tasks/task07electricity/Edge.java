package com.morawski.dev.aidevs.tasks.task07electricity;

/**
 * One of the four edges of a square tile through which a cable can exit. Order is clockwise
 * (N→E→S→W), so a 90° clockwise rotation maps each edge to the next via {@link #rotateCW()}.
 */
enum Edge {
    N, E, S, W;

    /** This edge after rotating the tile 90° clockwise: top→right→bottom→left→top. */
    Edge rotateCW() {
        return switch (this) {
            case N -> E;
            case E -> S;
            case S -> W;
            case W -> N;
        };
    }
}
