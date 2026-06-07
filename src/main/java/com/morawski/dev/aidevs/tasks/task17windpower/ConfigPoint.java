package com.morawski.dev.aidevs.tasks.task17windpower;

/**
 * One configuration point in the turbine schedule: a full-hour timestamp, the forecast wind at that
 * hour ({@code windMs} — needed to generate the signature, not sent in {@code config}), the blade
 * pitch and turbine mode to apply, and the {@code unlockCode} signature (filled in later, in
 * parallel, by {@code unlockCodeGenerator} — {@code null} until signed).
 */
record ConfigPoint(String date, String hour, double windMs, int pitchAngle, String turbineMode, String unlockCode) {

    /** The config-map key: {@code "YYYY-MM-DD HH:00:00"}. */
    String key() {
        return date + " " + hour;
    }

    /** A copy of this point carrying the generated signature. */
    ConfigPoint withCode(String code) {
        return new ConfigPoint(date, hour, windMs, pitchAngle, turbineMode, code);
    }
}
