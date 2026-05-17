package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task14 negotiations server (S03E04).
 *
 * @param url             full public URL of the tool endpoint (tunnel + path), registered with the Hub,
 *                        e.g. {@code https://abc123.ngrok-free.app/api/negotiations}
 * @param matchModel      OpenRouter model id used to pick the catalog item from the agent's natural-language
 *                        request (the request paraphrases heavily, so a strong model is preferred)
 * @param shortlistSize   how many catalog candidates the lexical pre-filter hands to the model per lookup
 * @param maxOutputBytes  cap on the {@code output} string (UTF-8); the whole tool reply must stay under 500 bytes
 * @param maxChecks       how many times {@code solve()} polls {@code /verify {action:check}} for the flag
 * @param checkPauseMs    pause between {@code check} polls (verification is asynchronous, min ~30-60s)
 * @param citiesFile      public Hub path for the city name↔code file
 * @param itemsFile       public Hub path for the item name↔code catalog
 * @param connectionsFile public Hub path for the itemCode→cityCode availability file
 */
@ConfigurationProperties("aidevs.negotiations")
public record NegotiationsProperties(
        String url,
        String matchModel,
        int shortlistSize,
        int maxOutputBytes,
        int maxChecks,
        long checkPauseMs,
        String citiesFile,
        String itemsFile,
        String connectionsFile
) {
}
