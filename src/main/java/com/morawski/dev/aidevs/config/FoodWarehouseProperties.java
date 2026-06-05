package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task20 {@code foodwarehouse} task — a deterministic driver (no LLM) that
 * prepares one warehouse order per city listed in {@code food4cities.json}, fills each with exactly
 * the goods that city needs, signs it, and calls {@code done} for the flag.
 *
 * <p>The whole API is the single multi-action endpoint {@code POST /verify} (the {@code answer.tool}
 * field selects {@code help|orders|signatureGenerator|database|reset|done}). The task reads the city
 * needs from the public {@code food4cities.json}, resolves each city's numeric {@code destination}
 * and a valid {@code creatorID} from the read-only SQLite database, and obtains the per-order
 * {@code signature} from the {@code signatureGenerator} tool (SHA1 of login+birthday+destination).
 *
 * <p>Empirical constraints learned from recon: {@code create} requires a {@code creatorID} that is an
 * existing user, and every seeded order's creator has role {@code 2} ("Obsługa transportów"), so the
 * task picks creators from that role. {@code done} is a perfect oracle — it returns the still-missing
 * cities with their destination codes and required items.
 *
 * @param foodFile        public Hub path to the city-needs JSON ({@code /dane/...}, downloaded via
 *                        {@code HubClient.downloadPublic} — not the per-apikey {@code /data/...} space).
 * @param titleTemplate   {@code String.format} template for an order title, given the city name.
 * @param transportRoleId role id whose users may create transport orders (2 = "Obsługa transportów").
 * @param maxRetries      bounded retries inside {@code FoodWarehouseClient} for retryable statuses (429/503).
 * @param retryPauseMs    unit of {@code FoodWarehouseClient}'s linear backoff between retries.
 */
@ConfigurationProperties("aidevs.foodwarehouse")
public record FoodWarehouseProperties(
        String foodFile,
        String titleTemplate,
        int transportRoleId,
        int maxRetries,
        long retryPauseMs
) {
}
