package com.morawski.dev.aidevs.tasks.task15savethem;

import java.util.List;

/**
 * What the recon agent decides and hands to the deterministic layer. Deliberately thin: only the
 * <em>decisions</em> the agent is good at (which city to map, which tools to call, which travel
 * modes exist), never bulky data the agent might mistranscribe. The map grid and the vehicle
 * consumption numbers are fetched and parsed straight from the tools' JSON by {@link SaveThemService}.
 *
 * @param destinationCity  the city to fetch a map for (the goal — "Skolwin").
 * @param mapsToolUrl      URL of the tool that returns a city map (e.g. {@code /api/maps}).
 * @param vehiclesToolUrl  URL of the tool that returns per-vehicle data (e.g. {@code /api/wehicles}).
 * @param vehicleNames     every available travel mode (e.g. walk, horse, car, rocket).
 */
record Findings(
        String destinationCity,
        String mapsToolUrl,
        String vehiclesToolUrl,
        List<String> vehicleNames
) {
}
