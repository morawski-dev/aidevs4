package com.morawski.dev.aidevs.tasks.task17windpower;

import com.morawski.dev.aidevs.config.WindpowerProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulePlannerTest {

    // Fixed config so the assertions can reason about exact pitch/mode values.
    private static final WindpowerProperties PROPS = new WindpowerProperties(
            false, 38000, 400, 200, 90, 0, "idle", "production");

    // Real turbine limits from documentation: storm at/above 14 m/s, generation needs >= 4 m/s.
    private static final TurbineSpec SPEC = new TurbineSpec(14, 4);

    private final SchedulePlanner planner = new SchedulePlanner(PROPS);

    private static WeatherSlot slot(String hour, double wind) {
        return new WeatherSlot("2026-06-16", hour, wind);
    }

    @Test
    void stormsFeathered_strongestGeneratingHourProduces_weakHourIgnored() {
        var weather = new WeatherReport(List.of(
                slot("04:00:00", 3),    // below 4 m/s → can't generate, not configured
                slot("10:00:00", 6),    // production candidate
                slot("12:00:00", 8),    // strongest non-storm → production point
                slot("18:00:00", 25),   // >= 14 → storm
                slot("20:00:00", 15))); // >= 14 → storm
        var reports = new Reports(weather, SPEC, new PlantRequirements(4));

        var points = planner.plan(reports);

        assertThat(points).hasSize(3);

        var storms = points.stream().filter(p -> p.turbineMode().equals("idle")).toList();
        assertThat(storms).hasSize(2);
        assertThat(storms).allMatch(p -> p.pitchAngle() == 90);
        assertThat(storms).extracting(ConfigPoint::hour).containsExactlyInAnyOrder("18:00:00", "20:00:00");

        var production = points.stream().filter(p -> p.turbineMode().equals("production")).toList();
        assertThat(production).hasSize(1);
        assertThat(production.get(0).hour()).isEqualTo("12:00:00"); // strongest generating hour (8 m/s)
        assertThat(production.get(0).pitchAngle()).isEqualTo(0);
        // the 3 m/s hour is below the operational minimum and must not be configured
        assertThat(points).noneMatch(p -> p.hour().equals("04:00:00"));
    }

    @Test
    void cutoffIsInclusive_windEqualToCutoffIsAStorm() {
        var weather = new WeatherReport(List.of(
                slot("12:00:00", 14),   // == cutoff → storm
                slot("14:00:00", 8)));  // production point
        var reports = new Reports(weather, SPEC, new PlantRequirements(4));

        var points = planner.plan(reports);

        assertThat(points).hasSize(2);
        assertThat(points.stream().filter(p -> p.hour().equals("12:00:00")))
                .allMatch(p -> p.turbineMode().equals("idle") && p.pitchAngle() == 90);
        assertThat(points.stream().filter(p -> p.hour().equals("14:00:00")))
                .allMatch(p -> p.turbineMode().equals("production"));
    }

    @Test
    void unknownCutoff_noStorms_strongestHourProduces() {
        var weather = new WeatherReport(List.of(
                slot("10:00:00", 6),
                slot("18:00:00", 25)));
        var reports = new Reports(weather, new TurbineSpec(Double.NaN, 4), new PlantRequirements(4));

        var points = planner.plan(reports);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).turbineMode()).isEqualTo("production");
        assertThat(points.get(0).hour()).isEqualTo("18:00:00"); // no cut-off → strongest hour is the candidate
    }

    @Test
    void allHoursBelowMinimum_yieldNoPoints() {
        var weather = new WeatherReport(List.of(slot("10:00:00", 2), slot("12:00:00", 3)));
        var reports = new Reports(weather, SPEC, new PlantRequirements(4));
        assertThat(planner.plan(reports)).isEmpty();
    }
}
