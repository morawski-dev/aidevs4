package com.morawski.dev.aidevs.tasks.task11evaluation;

/**
 * One sensor JSON file from {@code sensors.zip}. Every file carries all five measurement fields; fields
 * for sensors that aren't part of {@link #sensorType()} should read {@code 0}.
 *
 * @param fileId          file id without directory or {@code .json} extension (e.g. {@code "0001"}) — this
 *                        is exactly what we submit in the {@code recheck} array
 * @param sensorType      active sensor(s); {@code /}-separated for integrated sensors (e.g. {@code voltage/temperature})
 * @param temperatureK    temperature in Kelvin
 * @param pressureBar     pressure in bar
 * @param waterLevelMeters water level in meters
 * @param voltageSupplyV  supply voltage in V
 * @param humidityPercent humidity in percent
 * @param operatorNotes   the operator's free-text note (English)
 */
record SensorReading(
        String fileId,
        String sensorType,
        double temperatureK,
        double pressureBar,
        double waterLevelMeters,
        double voltageSupplyV,
        double humidityPercent,
        String operatorNotes
) {
}
