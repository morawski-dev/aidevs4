package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task21 {@code radiomonitoring} task — a self-submitting driver that runs a
 * radio-monitoring session ({@code start} → repeated {@code listen} → {@code transmit}) and reports
 * four facts about the city codenamed "Syjon" (name, area, warehouse count, contact phone).
 *
 * <p>The whole API is the single endpoint {@code POST /verify} ({@code answer.action} selects
 * {@code start|listen|transmit}). The {@code listen} stream mixes useless radio noise, text
 * transcriptions, and base64 binaries (described by a {@code meta} MIME). The task's core is a
 * <em>cost-aware router</em>: it decodes/sniffs each attachment locally and only sends true images
 * (and possibly audio) to a model — text/json are parsed for free and noise is dropped — so the
 * three models below are picked per data type, keeping the (potentially large) base64 payloads out
 * of the LLM wherever possible.
 *
 * @param visionModel  vision model that OCRs text out of image attachments.
 * @param audioModel   audio-capable model, only used if an audio attachment actually appears
 *                     (voice usually arrives pre-transcribed in the {@code transcription} field).
 * @param synthModel      strong model for the final single-shot synthesis of the four report fields.
 * @param synthMaxTokens  completion-token cap for the synthesis call. OpenRouter 402s a request whose
 *                        default budget (65536) exceeds the account's affordable tokens; the structured
 *                        output is tiny, so a small cap keeps it affordable.
 * @param maxListens   safety cap on the {@code listen} loop (the terminal signal normally ends it).
 * @param maxRetries   bounded retries inside {@code RadiomonitoringClient} for retryable statuses (429/503).
 * @param retryPauseMs unit of {@code RadiomonitoringClient}'s linear backoff between retries.
 * @param dryRun       when true, compute and log the report but skip {@code transmit} (first-run inspection).
 */
@ConfigurationProperties("aidevs.radiomonitoring")
public record RadiomonitoringProperties(
        String visionModel,
        String audioModel,
        String synthModel,
        int synthMaxTokens,
        int maxListens,
        int maxRetries,
        long retryPauseMs,
        boolean dryRun
) {
}
