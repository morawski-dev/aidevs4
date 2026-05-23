package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task16 {@code okoeditor} task — a deterministic driver that edits the OKO
 * Operations Center through the Centrala's {@code /verify} API (the "backdoor"). No LLM is used: the
 * API is update-only/write-only and the three required edits are fully concrete, so the task just
 * applies them and calls {@code done}.
 *
 * <p>The record IDs and classification codes below were read from the OKO web panel
 * ({@code https://oko.ag3nts.org/}, for which the brief supplied a login) and from the API's own
 * validation feedback. The API cannot list records, so the IDs are not discoverable through it — only
 * the panel's list pages expose them. Opening any record's <em>detail</em>/<em>edit</em> page in the
 * panel is a trap that bans the API key (cleared by re-logging in), so only the list pages are read.
 *
 * <p>Classification is encoded as a 6-char title code: a 4-char type ({@code MOVE}/{@code PROB}/
 * {@code RECO}) plus a 2-digit subtype. For {@code MOVE}, subtype {@code 03} = vehicles/people and
 * {@code 04} = animals; {@code 01} = human movement.
 *
 * @param skolwinId           32-char-hex id of the Skolwin records (the incydenty report and the
 *                            zadania task share this id).
 * @param komarowoIncidentId  32-char-hex id of a spare incident repurposed into the Komarowo report
 *                            (the API only updates existing records — a new id returns -880).
 * @param animalsCode         6-char title code marking the Skolwin report as about animals (MOVE04).
 * @param humanMovementCode   6-char title code marking the Komarowo report as human movement (MOVE01).
 * @param maxRetries          bounded retries inside {@code OkoClient} for retryable statuses (429/503).
 * @param retryPauseMs        unit of {@code OkoClient}'s linear backoff between retries.
 */
@ConfigurationProperties("aidevs.okoeditor")
public record OkoEditorProperties(
        String skolwinId,
        String komarowoIncidentId,
        String animalsCode,
        String humanMovementCode,
        int maxRetries,
        long retryPauseMs
) {
}
