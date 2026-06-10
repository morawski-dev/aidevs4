package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task23 {@code shellaccess} task — an agentic Function-Calling loop that drives
 * a remote Linux server through a single shell channel ({@code POST /verify} with {@code answer:{cmd}})
 * to read the time-archive logs under {@code /data}, find when/where Rafał's body was found, and print
 * the answer JSON (with the date set to the day BEFORE the finding). The same {@code /verify} call both
 * runs the command and returns the {@code {FLG:...}} once the printed JSON is correct.
 *
 * @param model         OpenRouter model id for the tool-calling agent; must support tool calls. A strong
 *                      reasoning model is used because the agent must cross-reference logs and apply the
 *                      "day before" date trap — {@code anthropic/claude-sonnet-4-6}.
 * @param maxTokens     completion-token cap per turn; the per-turn output (a tool call + short reasoning)
 *                      is small, and OpenRouter 402s a request whose default budget (65536) exceeds the
 *                      account's affordable tokens. A non-positive value leaves the provider default.
 * @param maxIterations hard cap on outer conversation rounds (re-prompt the same memory so the agent
 *                      keeps working after a stall or a partial exploration).
 * @param maxRetries    bounded retries inside the shell client for deliberately retryable statuses
 *                      (429 rate limit / 503).
 * @param retryPauseMs  short pause between outer rounds (and the unit of the client's linear backoff).
 */
@ConfigurationProperties("aidevs.shellaccess")
public record ShellAccessProperties(
        String model,
        int maxTokens,
        int maxIterations,
        int maxRetries,
        long retryPauseMs
) {
}
