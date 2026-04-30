package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for the task08 {@code failure} log-condensation task.
 *
 * @param logFile        Hub data file with the raw system log ({@code GET /data/{apikey}/failure.log})
 * @param tokenBudget    hard ceiling (in tokens) for the {@code logs} field; aim below the Centrala's 1500
 *                       limit (e.g. 1450) since we don't know its exact tokenizer
 * @param levels         severity levels to keep; everything else (INFO/DEBUG/TRACE) is dropped as noise
 * @param subsystems     known plant subsystem identifiers, used to tag/group events and report coverage
 * @param compressModel  cheap OpenRouter model used only as a fallback to paraphrase descriptions under budget
 * @param maxIterations  hard cap on submit→read-feedback→augment rounds before giving up
 */
@ConfigurationProperties("aidevs.failure")
public record FailureProperties(
        String logFile,
        int tokenBudget,
        List<String> levels,
        List<String> subsystems,
        String compressModel,
        int maxIterations
) {
}
