package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task12 {@code firmware} task — an agentic Function-Calling loop that drives
 * a locked-down Linux VM through a remote shell API ({@code POST /api/shell}) to boot the cooler
 * controller binary, read the {@code ECCS-...} code it prints, and submit it to the Centrala.
 *
 * @param model         OpenRouter model id for the tool-calling agent; must support tool calls. The
 *                      course hint recommends a strong reasoning model (the shell command set is
 *                      non-standard and the agent must adapt) — {@code anthropic/claude-sonnet-4-6}.
 * @param maxIterations hard cap on outer conversation rounds (re-prompt the same memory so the agent
 *                      keeps working after a stall, a ban wait, or a partial exploration).
 * @param maxRetries    bounded retries inside {@link com.morawski.dev.aidevs.tasks.task12firmware}'s
 *                      shell client for deliberately retryable statuses (429 rate limit / 503).
 * @param retryPauseMs  short pause between outer rounds.
 */
@ConfigurationProperties("aidevs.firmware")
public record FirmwareProperties(
        String model,
        int maxIterations,
        int maxRetries,
        long retryPauseMs
) {
}
