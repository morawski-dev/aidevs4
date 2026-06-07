package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task17 {@code windpower} turbine scheduler.
 *
 * <p>The defining constraint is a <strong>hard 40-second deadline</strong> with asynchronous,
 * queued API functions whose results arrive in random order and can be fetched only once — so the
 * task fans out report requests and unlock-code generation concurrently rather than serially.
 *
 * @param recon              when {@code true}, {@code solve()} only probes the API ({@code help} +
 *                           {@code start} + queue reports + a few {@code getResult} polls) and logs
 *                           raw bodies, to learn the exact action names and report JSON shape before
 *                           wiring the timed flow. Mirrors {@code aidevs.reactor.recon}.
 * @param deadlineMs         time budget for the whole timed flow (start→done), with margin under 40 s
 * @param pollIntervalMs     pause between {@code getResult} polls while waiting for queued reports
 * @param maxPollAttempts    hard cap on {@code getResult} polls (safety net against an empty queue loop)
 * @param featherPitch       blade pitch that offers no resistance during a storm (blades feathered)
 * @param productionPitch    blade pitch optimised for power generation at the production point
 * @param idleMode           {@code turbineMode} value that parks the turbine (no production) in a storm
 * @param productionMode     {@code turbineMode} value that generates power at the production point
 */
@ConfigurationProperties("aidevs.windpower")
public record WindpowerProperties(
        boolean recon,
        long deadlineMs,
        long pollIntervalMs,
        int maxPollAttempts,
        int featherPitch,
        int productionPitch,
        String idleMode,
        String productionMode
) {
}
