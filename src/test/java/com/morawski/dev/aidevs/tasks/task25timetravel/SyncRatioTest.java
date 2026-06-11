package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SyncRatioTest {

    @Test
    void rawFollowsDocFormula() {
        // 5*8 + 11*12 + 2238*7 = 40 + 132 + 15666 = 15838 ; 15838 mod 101 = 82
        assertThat(SyncRatio.raw(5, 11, 2238)).isEqualTo(82);
        // 15*8 + 6*12 + 2026*7 = 120 + 72 + 14182 = 14374 ; 14374 mod 101 = 32
        assertThat(SyncRatio.raw(15, 6, 2026)).isEqualTo(32);
        // 12*8 + 11*12 + 2024*7 = 96 + 132 + 14168 = 14396 ; 14396 mod 101 = 54
        assertThat(SyncRatio.raw(12, 11, 2024)).isEqualTo(54);
    }

    @Test
    void rawIsAlwaysInZeroToHundred() {
        for (int year = 1500; year <= 2499; year++) {
            int r = SyncRatio.raw(31, 12, year);
            assertThat(r).isBetween(0, 100);
        }
    }

    @Test
    void forDateIsRawOverHundredWithTwoDecimals() {
        assertThat(SyncRatio.forDate(LocalDate.of(2238, 11, 5)))
                .isEqualByComparingTo("0.82");
        assertThat(SyncRatio.forDate(LocalDate.of(2026, 6, 15)))
                .isEqualByComparingTo("0.32");
        assertThat(SyncRatio.forDate(LocalDate.of(2024, 11, 12)))
                .isEqualByComparingTo("0.54");
    }

    @Test
    void forDateAlwaysHasScaleTwoSoItSerialisesWithTwoDecimals() {
        // scale 2 means 50 -> "0.50" (not "0.5") and 100 -> "1.00"; the API wants two decimals.
        BigDecimal v = SyncRatio.forDate(LocalDate.of(2026, 6, 15));
        assertThat(v.scale()).isEqualTo(2);
        assertThat(SyncRatio.formatted(LocalDate.of(2026, 6, 15))).isEqualTo("0.32");
    }
}
