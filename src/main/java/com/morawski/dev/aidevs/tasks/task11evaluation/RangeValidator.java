package com.morawski.dev.aidevs.tasks.task11evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Deterministic, LLM-free detection of <em>data</em> anomalies (anomaly types 1, 2 and 4). A reading is a
 * data anomaly when any active sensor's value is outside its valid range, or any inactive sensor's field is
 * non-zero (a sensor returning data it shouldn't). Note that this also subsumes type 2 ("operator says OK
 * but the data is wrong") — bad data is flagged here regardless of what the note claims — so the LLM only
 * has to handle type 3 (valid data, but the note asserts a problem).
 *
 * <p>Pure logic, so this is the one piece that's unit-tested (see {@code RangeValidatorTest}).
 */
final class RangeValidator {

    private static final Logger log = LoggerFactory.getLogger(RangeValidator.class);

    /** Sensor token → measurement field accessor and inclusive valid range for an <em>active</em> sensor. */
    enum Sensor {
        TEMPERATURE("temperature", 553, 873, SensorReading::temperatureK),
        PRESSURE("pressure", 60, 160, SensorReading::pressureBar),
        WATER("water", 5.0, 15.0, SensorReading::waterLevelMeters),
        VOLTAGE("voltage", 229.0, 231.0, SensorReading::voltageSupplyV),
        HUMIDITY("humidity", 40.0, 80.0, SensorReading::humidityPercent);

        private final String token;
        private final double min;
        private final double max;
        private final ToDoubleFunction<SensorReading> value;

        Sensor(String token, double min, double max, ToDoubleFunction<SensorReading> value) {
            this.token = token;
            this.min = min;
            this.max = max;
            this.value = value;
        }
    }

    private RangeValidator() {
    }

    static boolean isDataAnomaly(SensorReading r) {
        var active = activeSensors(r.sensorType());
        for (var s : Sensor.values()) {
            var v = s.value.applyAsDouble(r);
            if (active.contains(s)) {
                if (v < s.min || v > s.max) {
                    return true; // active sensor reading out of range (type 1)
                }
            } else if (v != 0.0) {
                return true; // inactive sensor returning data it shouldn't (type 4)
            }
        }
        return false;
    }

    /** Parse {@code sensor_type} ("voltage/temperature") into the set of active sensors. */
    private static Set<Sensor> activeSensors(String sensorType) {
        var active = EnumSet.noneOf(Sensor.class);
        if (sensorType == null || sensorType.isBlank()) {
            return active;
        }
        for (var raw : sensorType.split("/")) {
            var token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            Arrays.stream(Sensor.values())
                    .filter(s -> s.token.equals(token))
                    .findFirst()
                    .ifPresentOrElse(active::add,
                            () -> log.warn("evaluation: unknown sensor_type token '{}' in '{}'", token, sensorType));
        }
        return active;
    }
}
