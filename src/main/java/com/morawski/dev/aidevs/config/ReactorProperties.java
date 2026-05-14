package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task13 {@code reactor} robot-navigation puzzle.
 *
 * @param maxCommands hard cap on commands sent in one run (each command = one {@code /verify} round-trip)
 * @param maxResets   how many times the run may {@code reset} (after a crush / stall) before giving up
 * @param maxRetries  bounded retries inside {@link com.morawski.dev.aidevs.tasks.task13reactor reactor}'s
 *                    client for deliberately retryable statuses (429 rate limit / 503 overload)
 * @param backoffMs   unit of the client's linear backoff between retries
 * @param recon       when {@code true}, run a read-only probe (start + a few waits) that just logs raw
 *                    bodies to discover the API's board JSON shape, instead of attempting to solve
 */
@ConfigurationProperties("aidevs.reactor")
public record ReactorProperties(
        int maxCommands,
        int maxResets,
        int maxRetries,
        long backoffMs,
        boolean recon
) {
}
