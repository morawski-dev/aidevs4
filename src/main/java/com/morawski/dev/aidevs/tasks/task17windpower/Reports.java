package com.morawski.dev.aidevs.tasks.task17windpower;

/**
 * Everything needed to plan the schedule: the weather forecast, the turbine limits (from
 * documentation), and the plant's power deficit (from powerplantcheck).
 */
record Reports(WeatherReport weather, TurbineSpec spec, PlantRequirements requirements) {
}
