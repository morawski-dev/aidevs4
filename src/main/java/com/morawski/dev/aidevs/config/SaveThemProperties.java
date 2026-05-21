package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task15 {@code savethem} task — an agent that discovers tools via the Hub's
 * {@code /api/toolsearch}, gathers the map and vehicle data, then plans an optimal route to Skolwin.
 *
 * <p>The recon (tool discovery + decisions) is an agentic LLM loop; the route itself is computed
 * deterministically by {@code RoutePlanner}, so the model never has to do arithmetic. The resource
 * budgets and the tree-tile fuel penalty are game constants (from the task brief and the book
 * notes) and live here so they're easy to retune without touching code.
 *
 * @param model         OpenRouter model id for the tool-calling recon agent (must support tools).
 * @param maxIterations hard cap on outer conversation rounds before giving up on gathering findings.
 * @param memoryWindow  chat-memory window (lots of tool round-trips, so keep it generous).
 * @param fuelBudget    fuel units available for the whole journey.
 * @param foodBudget    food units available for the whole journey.
 * @param treePenalty   extra fuel a powered mode (car/rocket) burns entering a tree (T) tile.
 */
@ConfigurationProperties("aidevs.savethem")
public record SaveThemProperties(
        String model,
        int maxIterations,
        int memoryWindow,
        double fuelBudget,
        double foodBudget,
        double treePenalty
) {
}
