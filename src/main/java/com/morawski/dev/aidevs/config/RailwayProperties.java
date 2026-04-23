package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task05 railway activation.
 *
 * @param routeName               route to activate; {@code route_format} is {@code [a-z]-[0-9]{1,2}} (case-insensitive)
 * @param activateValue           status that activates (opens) the route — {@code RTOPEN} per the API's {@code help}
 * @param maxRetries              max 503 retries before giving up on a single call
 * @param baseBackoffMs           base delay for the exponential 503 backoff
 * @param maxBackoffMs            cap for the exponential 503 backoff
 * @param rateLimitSafetyMarginMs extra wait added on top of a rate-limit reset window
 */
@ConfigurationProperties("aidevs.railway")
public record RailwayProperties(
        String routeName,
        String activateValue,
        int maxRetries,
        long baseBackoffMs,
        long maxBackoffMs,
        long rateLimitSafetyMarginMs
) {
}
