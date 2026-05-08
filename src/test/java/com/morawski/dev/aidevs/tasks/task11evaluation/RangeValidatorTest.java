package com.morawski.dev.aidevs.tasks.task11evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangeValidatorTest {

    /** Builds a reading with all measurement fields 0 except those passed; notes are irrelevant to data checks. */
    private static SensorReading reading(String sensorType, double temp, double pressure,
                                         double water, double voltage, double humidity) {
        return new SensorReading("id", sensorType, temp, pressure, water, voltage, humidity, "note");
    }

    @Test
    void singleActiveSensorInRangeIsClean() {
        // temperature active and in range; every other (inactive) field is 0
        assertThat(RangeValidator.isDataAnomaly(reading("temperature", 612, 0, 0, 0, 0))).isFalse();
    }

    @Test
    void activeSensorOutOfRangeIsAnomaly() {
        assertThat(RangeValidator.isDataAnomaly(reading("temperature", 900, 0, 0, 0, 0))).isTrue(); // > 873
        assertThat(RangeValidator.isDataAnomaly(reading("temperature", 500, 0, 0, 0, 0))).isTrue(); // < 553
    }

    @Test
    void rangeBoundariesAreInclusive() {
        assertThat(RangeValidator.isDataAnomaly(reading("temperature", 553, 0, 0, 0, 0))).isFalse();
        assertThat(RangeValidator.isDataAnomaly(reading("temperature", 873, 0, 0, 0, 0))).isFalse();
    }

    @Test
    void inactiveSensorReturningDataIsAnomaly() {
        // water sensor that also reports voltage (the prompt's example of a type-4 fault)
        assertThat(RangeValidator.isDataAnomaly(reading("water", 0, 0, 10.0, 230.0, 0))).isTrue();
    }

    @Test
    void activeSensorReadingZeroIsAnomaly() {
        // temperature active but reads 0 → out of [553,873]
        assertThat(RangeValidator.isDataAnomaly(reading("temperature", 0, 0, 0, 0, 0))).isTrue();
    }

    @Test
    void integratedSensorBothInRangeIsClean() {
        // voltage/temperature: both active and in range, all other fields 0
        assertThat(RangeValidator.isDataAnomaly(reading("voltage/temperature", 600, 0, 0, 230.0, 0))).isFalse();
    }

    @Test
    void integratedSensorOneFieldOutOfRangeIsAnomaly() {
        assertThat(RangeValidator.isDataAnomaly(reading("voltage/temperature", 600, 0, 0, 240.0, 0))).isTrue();
    }

    @Test
    void integratedSensorWithExtraneousFieldIsAnomaly() {
        // voltage/temperature both fine, but humidity (inactive) is non-zero
        assertThat(RangeValidator.isDataAnomaly(reading("voltage/temperature", 600, 0, 0, 230.0, 55.0))).isTrue();
    }

    @Test
    void unknownSensorTypeWithAllZerosIsClean() {
        // unknown token → no sensor active; all fields 0 → nothing out of place
        assertThat(RangeValidator.isDataAnomaly(reading("magic", 0, 0, 0, 0, 0))).isFalse();
    }

    @Test
    void everyOtherFieldMustBeZeroForSingleSensor() {
        // pressure active and in range, but water_level is non-zero → anomaly
        assertThat(RangeValidator.isDataAnomaly(reading("pressure", 0, 100, 8.0, 0, 0))).isTrue();
    }
}
