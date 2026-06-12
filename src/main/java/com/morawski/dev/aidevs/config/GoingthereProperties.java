package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task24 {@code goingthere} rocket-navigation game.
 *
 * @param maxMoves     hard cap on move commands per run (a clean traverse is 11; the cap absorbs restarts)
 * @param maxRestarts  how many times the run may {@code start} over (after a crash) before giving up
 * @param maxRetries   bounded retries inside the client for retryable statuses (429/503) and corrupted
 *                     scanner/hint reads
 * @param backoffMs    unit of the client's linear backoff between retries
 * @param hintModel    OpenRouter model id for the radio-hint classifier (blank = global default)
 * @param hintMaxTokens completion-token cap for the hint classifier (the output is tiny; this also
 *                     avoids OpenRouter 402s when the default 65536 budget exceeds affordable credits)
 * @param hintSamples  how many times to re-request + re-classify each radio hint and majority-vote
 *                     (the channel is jammed, so a single hint is unreliable)
 * @param leftRowDelta row-number change of the {@link com.morawski.dev.aidevs.tasks.task24goingthere
 *                     LEFT} command: {@code -1} if "left" goes to a lower row number, {@code +1} otherwise.
 *                     Confirm with a recon run; only affects row convergence, never crash safety.
 * @param recon        when {@code true}, run a read-only probe (start + a few scanner/getmessage reads)
 *                     that just logs raw bodies to discover the API's JSON shapes and orientation
 */
@ConfigurationProperties("aidevs.goingthere")
public record GoingthereProperties(
        int maxMoves,
        int maxRestarts,
        int maxRetries,
        long backoffMs,
        String hintModel,
        int hintMaxTokens,
        int hintSamples,
        int leftRowDelta,
        boolean recon
) {
}
