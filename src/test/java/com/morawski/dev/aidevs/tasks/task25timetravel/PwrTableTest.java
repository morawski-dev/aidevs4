package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PwrTableTest {

    @Test
    void theThreeMissionYears() {
        assertThat(PwrTable.forYear(2238)).isEqualTo(91); // jump for batteries
        assertThat(PwrTable.forYear(2026)).isEqualTo(28); // return to present
        assertThat(PwrTable.forYear(2024)).isEqualTo(19); // tunnel target
    }

    @Test
    void tableBoundaries() {
        assertThat(PwrTable.forYear(1500)).isEqualTo(3);
        assertThat(PwrTable.forYear(2499)).isEqualTo(97);
    }

    @Test
    void everySupportedYearHasAValue() {
        for (int year = 1500; year <= 2499; year++) {
            assertThat(PwrTable.forYear(year)).isBetween(1, 99);
        }
    }

    @Test
    void yearOutsideRangeThrows() {
        assertThatThrownBy(() -> PwrTable.forYear(1499)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PwrTable.forYear(2500)).isInstanceOf(IllegalArgumentException.class);
    }
}
