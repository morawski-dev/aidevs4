package com.morawski.dev.aidevs.tasks.task17windpower;

/**
 * Turbine operating limits read from the {@code documentation} payload (returned directly, not queued):
 * the storm cut-off ({@code cutoffWindMs}, above which the turbine must be feathered to avoid blade
 * damage) and the minimum wind at which it can generate ({@code minOperationalWindMs}). {@code NaN}
 * means the value wasn't found in the docs. The {@code turbinecheck} report carries only live status
 * (battery, pitch), not these thresholds — so the schedule's limits come from here.
 */
record TurbineSpec(double cutoffWindMs, double minOperationalWindMs) {
}
