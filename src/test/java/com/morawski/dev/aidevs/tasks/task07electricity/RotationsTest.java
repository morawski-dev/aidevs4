package com.morawski.dev.aidevs.tasks.task07electricity;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

class RotationsTest {

    @Test
    void rotateElbowClockwise() {
        // N+E elbow rotated 90° CW becomes E+S.
        assertThat(Rotations.rotateCW(EnumSet.of(Edge.N, Edge.E), 1))
                .isEqualTo(EnumSet.of(Edge.E, Edge.S));
    }

    @Test
    void rotateFullCircleIsIdentity() {
        var edges = EnumSet.of(Edge.N, Edge.E, Edge.W);
        assertThat(Rotations.rotateCW(edges, 4)).isEqualTo(edges);
    }

    @Test
    void rotateNormalisesNegativeAndLargeK() {
        var edges = EnumSet.of(Edge.N);
        assertThat(Rotations.rotateCW(edges, -1)).isEqualTo(EnumSet.of(Edge.W)); // one CCW = three CW
        assertThat(Rotations.rotateCW(edges, 5)).isEqualTo(EnumSet.of(Edge.E));  // 5 mod 4 = 1
    }

    @Test
    void emptyTileRotatesToEmpty() {
        assertThat(Rotations.rotateCW(EnumSet.noneOf(Edge.class), 3))
                .isEqualTo(EnumSet.noneOf(Edge.class));
    }

    @Test
    void elbowNeedsThreeRotations() {
        // W+N -> needs 3 CW to reach S+W (equivalently one CCW).
        assertThat(Rotations.requiredRotations(EnumSet.of(Edge.W, Edge.N), EnumSet.of(Edge.S, Edge.W)))
                .isEqualTo(3);
    }

    @Test
    void alreadyAlignedNeedsZero() {
        assertThat(Rotations.requiredRotations(EnumSet.of(Edge.E, Edge.S), EnumSet.of(Edge.E, Edge.S)))
                .isZero();
    }

    @Test
    void straightWireHasPeriodTwoSoMinimalRotationIsZero() {
        // N+S == N+S after 180°, so target N+S from current N+S is 0, and from E+W is 1.
        assertThat(Rotations.requiredRotations(EnumSet.of(Edge.N, Edge.S), EnumSet.of(Edge.N, Edge.S)))
                .isZero();
        assertThat(Rotations.requiredRotations(EnumSet.of(Edge.E, Edge.W), EnumSet.of(Edge.N, Edge.S)))
                .isEqualTo(1);
    }

    @Test
    void crossIsRotationInvariant() {
        var cross = EnumSet.allOf(Edge.class);
        assertThat(Rotations.requiredRotations(cross, cross)).isZero();
    }

    @Test
    void noMatchReturnsMinusOne() {
        // An elbow (2 edges) can never become a T-junction (3 edges) by rotation.
        assertThat(Rotations.requiredRotations(EnumSet.of(Edge.N, Edge.E), EnumSet.of(Edge.N, Edge.E, Edge.S)))
                .isEqualTo(-1);
    }
}
