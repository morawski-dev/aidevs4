package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StateReaderTest {

    /** The real getConfig shape: config nested under "config", with a top-level code. */
    @Test
    void parsesRealGetConfigShape() {
        String body = """
                {"code":12,"message":"Current configuration returned.","config":{
                  "currentDate":"2238-11-05","day":5,"month":11,"year":2238,
                  "syncRatio":0.82,"stabilization":189,"condition":"stable","fluxDensity":100,
                  "batteryStatus":"3/3","PTA":false,"PTB":true,"PWR":91,"mode":"active","internalMode":3}}""";

        DeviceState s = StateReader.parse(body);

        assertThat(s.internalMode()).isEqualTo(3);
        assertThat(s.internalModeIs(3)).isTrue();
        assertThat(s.fluxDensity()).isEqualTo(100);
        assertThat(s.fluxReady()).isTrue();
        assertThat(s.day()).isEqualTo(5);
        assertThat(s.month()).isEqualTo(11);
        assertThat(s.year()).isEqualTo(2238);
        assertThat(s.syncRatio()).isEqualTo("0.82");
        assertThat(s.stabilization()).isEqualTo("189");
        assertThat(s.condition()).isEqualTo("stable");
        assertThat(s.isStable()).isTrue();
        assertThat(s.batteryStatus()).isEqualTo("3/3");
        assertThat(s.batteryCharged()).isEqualTo(3);
        assertThat(s.tunnelBatteryOk()).isTrue();
        assertThat(s.ptA()).isFalse();
        assertThat(s.ptB()).isTrue();
        assertThat(s.pwr()).isEqualTo(91);
        assertThat(s.currentDate()).isEqualTo("2238-11-05");
        assertThat(s.isActive()).isTrue();
        assertThat(s.code()).isEqualTo(12);
        assertThat(s.raw()).isEqualTo(body);
    }

    @Test
    void readsStabilizationHintFromNeedConfig() {
        String body = """
                {"code":11,"config":{"day":5,"month":11,"year":2238},
                 "needConfig":"...zalecane jest obniżenie poziomu o siedemset jedenaście..."}""";

        DeviceState s = StateReader.parse(body);

        assertThat(s.hint()).contains("...zalecane jest obniżenie poziomu o siedemset jedenaście...");
    }

    @Test
    void parsesPercentStringsAndLowBattery() {
        String body = """
                {"config":{"fluxDensity":"80%","batteryStatus":"1/3","mode":"standby","PTA":true,"PTB":false,"PWR":28}}""";

        DeviceState s = StateReader.parse(body);

        assertThat(s.fluxDensity()).isEqualTo(80);
        assertThat(s.fluxReady()).isFalse();
        assertThat(s.batteryCharged()).isEqualTo(1);
        assertThat(s.tunnelBatteryOk()).isFalse();
        assertThat(s.isStandby()).isTrue();
        assertThat(s.ptA()).isTrue();
        assertThat(s.ptB()).isFalse();
        assertThat(s.pwr()).isEqualTo(28);
    }

    @Test
    void missingFieldsAreNullAndRawIsPreserved() {
        String body = "{\"something\":\"else\"}";

        DeviceState s = StateReader.parse(body);

        assertThat(s.internalMode()).isNull();
        assertThat(s.fluxDensity()).isNull();
        assertThat(s.fluxReady()).isFalse();
        assertThat(s.ptA()).isNull();
        assertThat(s.batteryCharged()).isZero();
        assertThat(s.hint()).isEmpty();
        assertThat(s.raw()).isEqualTo(body);
    }

    @Test
    void nonJsonBodyDoesNotThrowAndKeepsRaw() {
        String body = "Service Unavailable";

        DeviceState s = StateReader.parse(body);

        assertThat(s.internalMode()).isNull();
        assertThat(s.raw()).isEqualTo(body);
    }
}
