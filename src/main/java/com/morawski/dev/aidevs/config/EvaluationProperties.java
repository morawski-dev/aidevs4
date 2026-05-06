package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task11 {@code evaluation} sensor-anomaly task.
 *
 * @param zipFile       Hub data file with the ~10 000 sensor JSON files ({@code GET /data/{apikey}/sensors.zip})
 * @param classifyModel OpenRouter model id used to classify operator notes. Deterministic dedup collapses
 *                      10k files to a few dozen distinct notes, so a strong model costs almost nothing here
 *                      and the task's exact-set-match scoring rewards accuracy on the ambiguous-note boundary
 * @param noteBatchSize how many distinct notes to classify per LLM call (small batches reduce index miscounts)
 */
@ConfigurationProperties("aidevs.evaluation")
public record EvaluationProperties(
        String zipFile,
        String classifyModel,
        int noteBatchSize
) {
}
