package com.morawski.dev.aidevs.tasks.task17windpower;

/**
 * Power-plant requirements from {@code powerplantcheck}. {@code deficitKw} is the power the plant is
 * short ({@code powerDeficitKw}, e.g. the range {@code "3-4"} parsed to its upper bound 4) — the
 * production point must generate at least this much. {@code 0} means none reported.
 */
record PlantRequirements(double deficitKw) {
}
