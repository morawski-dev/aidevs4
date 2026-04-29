package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task07 {@code electricity} wiring puzzle.
 *
 * @param visionModel  OpenRouter vision model used to read each tile's cable edges (e.g. {@code google/gemini-3-flash-preview})
 * @param maxRounds    hard cap on perceive→plan→rotate→verify rounds (each round re-reads a fresh board and applies residual rotations)
 * @param tileUpscale  integer factor to upscale each cropped tile before sending to the vision model ({@code 1} = off); bigger tiles read more reliably
 * @param votesPerTile how many times to ask the vision model per tile, taking a per-edge majority (1 = single read); vision calls don't cost a {@code /verify} request
 * @param resetOnStart whether to reset the board ({@code electricity.png?reset=1}) before the first round for a deterministic start
 */
@ConfigurationProperties("aidevs.electricity")
public record ElectricityProperties(
        String visionModel,
        int maxRounds,
        int tileUpscale,
        int votesPerTile,
        boolean resetOnStart
) {
}
