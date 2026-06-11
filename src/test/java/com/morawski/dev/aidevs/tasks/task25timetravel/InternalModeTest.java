package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalModeTest {

    @Test
    void mapsTheFourPhasesAtTheirBoundaries() {
        assertThat(InternalMode.forYear(1999)).isEqualTo(1);
        assertThat(InternalMode.forYear(2000)).isEqualTo(2);
        assertThat(InternalMode.forYear(2150)).isEqualTo(2);
        assertThat(InternalMode.forYear(2151)).isEqualTo(3);
        assertThat(InternalMode.forYear(2300)).isEqualTo(3);
        assertThat(InternalMode.forYear(2301)).isEqualTo(4);
    }

    @Test
    void theThreeMissionYears() {
        assertThat(InternalMode.forYear(2238)).isEqualTo(3); // jump for batteries
        assertThat(InternalMode.forYear(2026)).isEqualTo(2); // return to present
        assertThat(InternalMode.forYear(2024)).isEqualTo(2); // tunnel target
    }
}
