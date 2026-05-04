package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task10 {@code drone} task — fly an armed drone to drop its payload on the
 * dam sector (not the power plant). Perception (locate the dam on the map) is split from logic
 * (build the flight instructions reactively from the drone's API docs and the Hub's error feedback).
 *
 * @param visionModel     OpenRouter vision model used only as the perception <em>fallback</em> when the
 *                        red grid can't be detected; a strong model is recommended for grid counting
 *                        (e.g. {@code openai/gpt-5.4} or {@code openai/gpt-4o}).
 * @param plannerModel    model that builds the flight instructions reactively from the docs + dam
 *                        sector + Hub feedback (the fallback after the round-1 template); a blank value
 *                        falls back to the global default.
 * @param maxIterations   hard cap on submit→read-error→correct rounds.
 * @param plantCode       the power plant's identification code (the nominal mission target, e.g.
 *                        {@code PWR6132PL}); passed to the planner as context.
 * @param mapRetries      how many times to re-download the map waiting for the annotated frame — the
 *                        endpoint rotates between the grid+boosted-water version (parseable) and a raw
 *                        frame (no grid), so we retry past raw frames like railway's 503s.
 * @param mapRetryPauseMs pause between map re-downloads while waiting for an annotated frame.
 */
@ConfigurationProperties("aidevs.drone")
public record DroneProperties(
        String visionModel,
        String plannerModel,
        int maxIterations,
        String plantCode,
        int mapRetries,
        long mapRetryPauseMs
) {
}
