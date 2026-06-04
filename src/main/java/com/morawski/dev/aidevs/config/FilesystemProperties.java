package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task19 {@code filesystem} task (S05E02) — order Natan's notes into a virtual
 * filesystem on the Centrala, built entirely through {@code POST /verify} (actions {@code reset} /
 * {@code createDirectory} / {@code createFile} / {@code done}, batchable).
 *
 * <p>The notes are downloaded once from the <b>public</b> {@code /dane/natan_notes.zip} space (not the
 * per-apikey {@code /data/{apikey}/...} space), so {@link #notesZip()} is an absolute Hub path resolved
 * by {@code HubClient.downloadPublic}. A strong {@link #model() model} reads the free-form Polish prose
 * into a structured trade model (cities + demand quantities, the person managing each city, and the
 * goods each city sells); the filesystem is then assembled deterministically and validated by
 * {@code done}.
 *
 * @param notesZip      public Hub path of the notes archive (e.g. {@code /dane/natan_notes.zip}).
 * @param model         OpenRouter model id used to extract the structured trade model from the notes.
 * @param maxTokens     completion-token cap for the extraction call; the output is tiny, and OpenRouter
 *                      rejects requests whose requested budget exceeds the account's affordable tokens.
 * @param batchSize     max actions sent per {@code /verify} batch (the API runs an array sequentially).
 * @param maxRetries    bounded retries inside {@code FsClient} for retryable statuses (429/503).
 * @param retryPauseMs  unit of {@code FsClient}'s linear backoff between retries.
 */
@ConfigurationProperties("aidevs.filesystem")
public record FilesystemProperties(
        String notesZip,
        String model,
        int maxTokens,
        int batchSize,
        int maxRetries,
        long retryPauseMs
) {
}
