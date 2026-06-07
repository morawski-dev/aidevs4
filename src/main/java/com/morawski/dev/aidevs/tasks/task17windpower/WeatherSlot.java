package com.morawski.dev.aidevs.tasks.task17windpower;

/**
 * One hourly weather-forecast entry: a full-hour timestamp ({@code date} = {@code "YYYY-MM-DD"},
 * {@code hour} = {@code "HH:00:00"}) and the forecast wind. Used to detect storms (wind above the
 * turbine's endurance) and to pick the production point.
 */
record WeatherSlot(String date, String hour, double wind) {

    /** The config-map key for this slot: {@code "YYYY-MM-DD HH:00:00"}. */
    String key() {
        return date + " " + hour;
    }
}
