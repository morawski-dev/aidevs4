package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AreaFormatTest {

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertThat(AreaFormat.round2("12.345")).isEqualTo("12.35"); // HALF_UP, not truncation
        assertThat(AreaFormat.round2("12.344")).isEqualTo("12.34");
        assertThat(AreaFormat.round2("12.005")).isEqualTo("12.01");
    }

    @Test
    void padsToExactlyTwoDecimals() {
        assertThat(AreaFormat.round2("12")).isEqualTo("12.00");
        assertThat(AreaFormat.round2("12.3")).isEqualTo("12.30");
        assertThat(AreaFormat.round2("0")).isEqualTo("0.00");
    }

    @Test
    void normalisesDecimalComma() {
        assertThat(AreaFormat.round2("12,34")).isEqualTo("12.34");
        assertThat(AreaFormat.round2("12,3")).isEqualTo("12.30");
    }

    @Test
    void extractsNumberFromSurroundingText() {
        assertThat(AreaFormat.round2("area 12.34 km2")).isEqualTo("12.34");
        assertThat(AreaFormat.round2("  powierzchnia: 8.7 km²  ")).isEqualTo("8.70");
    }

    @Test
    void rejectsBlankOrNonNumeric() {
        assertThatThrownBy(() -> AreaFormat.round2(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AreaFormat.round2("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AreaFormat.round2("no number here")).isInstanceOf(IllegalArgumentException.class);
    }
}
