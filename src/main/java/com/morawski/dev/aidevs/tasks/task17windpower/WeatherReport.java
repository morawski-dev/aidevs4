package com.morawski.dev.aidevs.tasks.task17windpower;

import java.util.List;

/** The weather forecast as a list of hourly {@link WeatherSlot}s. */
record WeatherReport(List<WeatherSlot> slots) {
}
