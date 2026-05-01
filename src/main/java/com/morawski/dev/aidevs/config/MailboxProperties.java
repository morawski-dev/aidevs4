package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task09 {@code mailbox} task — an agentic Function-Calling loop that
 * searches an operator's mailbox (the {@code zmail} API) for three values and submits them.
 *
 * @param model         OpenRouter model id for the tool-calling agent; must support tool calls.
 *                      The course hint suggests a cheap model — this is search + fact extraction,
 *                      not heavy reasoning.
 * @param maxIterations hard cap on outer conversation rounds (the mailbox is active, so we re-prompt
 *                      and re-search until the flag appears or this cap is hit).
 * @param perPage       page size for search/getInbox (the API allows 5–20).
 * @param retryPauseMs  short pause between outer rounds — gives new mail time to arrive.
 */
@ConfigurationProperties("aidevs.mailbox")
public record MailboxProperties(
        String model,
        int maxIterations,
        int perPage,
        long retryPauseMs
) {
}
