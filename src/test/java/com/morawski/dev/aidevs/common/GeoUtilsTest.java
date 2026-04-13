package com.morawski.dev.aidevs.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeoUtilsTest {

    @Test
    void samePoint_isZero() {
        assertThat(GeoUtils.haversineKm(52.0, 21.0, 52.0, 21.0)).isEqualTo(0.0);
    }

    @Test
    void warsawToKrakow_isAbout252km() {
        // Warsaw: 52.2297, 21.0122 — Krakow: 50.0647, 19.9450
        double dist = GeoUtils.haversineKm(52.2297, 21.0122, 50.0647, 19.9450);
        assertThat(dist).isBetween(245.0, 260.0);
    }

    @Test
    void isSymmetric() {
        double d1 = GeoUtils.haversineKm(51.0, 17.0, 54.0, 18.5);
        double d2 = GeoUtils.haversineKm(54.0, 18.5, 51.0, 17.0);
        assertThat(d1).isCloseTo(d2, org.assertj.core.data.Offset.offset(0.001));
    }
}
