package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task06 {@code categorize} prompt-engineering loop.
 *
 * @param csvFile       data file with the 10 items to classify ({@code GET /data/{apikey}/{file}}); rotates every few minutes
 * @param maxIterations hard cap on submit→refine rounds (the Hub's 1.5 PP budget allows only a few full attempts)
 * @param engineerModel OpenRouter model used as the "prompt engineer" that rewrites the classifier prompt from Hub errors
 */
@ConfigurationProperties("aidevs.categorize")
public record CategorizeProperties(
        String csvFile,
        int maxIterations,
        String engineerModel
) {
}
