package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task18 {@code domatowo} search-and-rescue operation.
 *
 * <p>We must find a partisan hiding in one of the tallest buildings of a bombed 11×11 city and call a
 * helicopter to his cell, all within a 300-action-point budget. Transporters (cheap movement, streets
 * only) ferry scouts (expensive movement, on foot anywhere) close to candidate buildings, which scouts
 * then inspect.
 *
 * @param recon          when {@code true}, run a read-only probe ({@code help} + {@code getMap}) that
 *                       just logs raw bodies to discover the API contract / map legend, without
 *                       creating or moving any unit (so no action points are spent)
 * @param maxActions     hard action-point budget for the whole operation (brief: 300)
 * @param maxScouts      max scouts that may exist at once (brief: 8)
 * @param maxTransporters max transporters that may exist at once (brief: 4)
 * @param maxRounds      hard cap on action round-trips in one run (safety net against loops)
 * @param maxRetries     bounded retries inside {@code DomatowoClient} for retryable statuses (429/503)
 * @param backoffMs      unit of the client's linear backoff between retries (ms)
 * @param visionModel    vision model used only as a fallback to read the tallest buildings off the
 *                       preview image when {@code getMap} doesn't encode height unambiguously
 * @param previewUrl     the human map preview (used by the vision fallback)
 */
@ConfigurationProperties("aidevs.domatowo")
public record DomatowoProperties(
        boolean recon,
        int maxActions,
        int maxScouts,
        int maxTransporters,
        int maxRounds,
        int maxRetries,
        long backoffMs,
        String visionModel,
        String previewUrl
) {
}
