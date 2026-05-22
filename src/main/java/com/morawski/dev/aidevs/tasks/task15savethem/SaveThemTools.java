package com.morawski.dev.aidevs.tasks.task15savethem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Tools exposed to the recon agent (Spring AI generates the schema and runs the tool loop):
 * <ul>
 *   <li>{@code search_tools} — find tools via {@code /api/toolsearch} (returns the 3 best matches),</li>
 *   <li>{@code call_tool} — call any discovered tool by its URL with a free-text query,</li>
 *   <li>{@code record_findings} — hand the gathered decisions to the deterministic planner.</li>
 * </ul>
 *
 * <p>All tool data and notes are in English, so queries must be in English. {@code record_findings}
 * captures the {@link Findings} (read by {@link SaveThemTask}); the bulky map/vehicle numbers are
 * fetched from the tools by the deterministic layer, not transcribed by the model.
 */
@Component
class SaveThemTools {

    private static final Logger log = LoggerFactory.getLogger(SaveThemTools.class);

    private final ToolHubClient hub;

    /** Set once the agent records a complete set of decisions; read by {@link SaveThemTask}. */
    private volatile Findings findings;

    SaveThemTools(ToolHubClient hub) {
        this.hub = hub;
    }

    @Tool(name = "search_tools",
            description = "Search the Hub's tool registry. Returns the 3 best-matching tools (name, url, "
                    + "description) as JSON. The query MUST be in English; you may use natural language or "
                    + "keywords. It returns only 3 results, so search several times with different wording "
                    + "to discover all the tools you need (e.g. for maps, for vehicles, for notes/rules).")
    String searchTools(
            @ToolParam(description = "English search phrase, e.g. 'terrain map' or 'available vehicles'.")
            String query) {
        return hub.searchTools(query);
    }

    @Tool(name = "call_tool",
            description = "Call a tool discovered via search_tools, by its url, passing a free-text English "
                    + "query. Every tool returns JSON and at most 3 best matches. Use it to read note "
                    + "archives (movement rules, terrain legend), to fetch a city map (query = the city "
                    + "name), or to look up a vehicle (query = the vehicle name).")
    String callTool(
            @ToolParam(description = "The tool url from search_tools, e.g. '/api/maps'.") String url,
            @ToolParam(description = "English query for the tool, e.g. a city name or a vehicle name.")
            String query) {
        if (!StringUtils.hasText(url)) {
            return "Error: provide the tool url from search_tools.";
        }
        return hub.call(url, query);
    }

    @Tool(name = "record_findings",
            description = "Record the decisions needed to plan the route, once you have discovered the tools "
                    + "and confirmed the facts. The system then fetches the map and the vehicle stats itself "
                    + "and computes the optimal route — you do NOT need to transcribe the map or do any math. "
                    + "Call this exactly once, when you are confident.")
    String recordFindings(
            @ToolParam(description = "The destination city to travel to (the goal), e.g. 'Skolwin'.")
            String destinationCity,
            @ToolParam(description = "URL of the tool that returns a city's terrain map, e.g. '/api/maps'.")
            String mapsToolUrl,
            @ToolParam(description = "URL of the tool that returns per-vehicle data, e.g. '/api/wehicles'.")
            String vehiclesToolUrl,
            @ToolParam(description = "ALL available travel modes, e.g. ['walk','horse','car','rocket']. "
                    + "Include every mode you found — missing one may discard the only viable option.")
            List<String> vehicleNames) {

        var problems = new StringBuilder();
        if (!StringUtils.hasText(destinationCity)) {
            problems.append("destinationCity is empty. ");
        }
        if (!StringUtils.hasText(mapsToolUrl)) {
            problems.append("mapsToolUrl is empty. ");
        }
        if (!StringUtils.hasText(vehiclesToolUrl)) {
            problems.append("vehiclesToolUrl is empty. ");
        }
        var names = normalize(vehicleNames);
        if (names.isEmpty()) {
            problems.append("vehicleNames is empty — list every travel mode you found. ");
        }
        if (!problems.isEmpty()) {
            log.info("record_findings rejected: {}", problems);
            return "Not recorded — " + problems + "Gather the missing facts and call record_findings again.";
        }

        this.findings = new Findings(destinationCity.trim(), mapsToolUrl.trim(), vehiclesToolUrl.trim(), names);
        log.info("Findings recorded: {}", findings);
        return "Recorded. The route planner will take it from here — you can stop now.";
    }

    Optional<Findings> findings() {
        return Optional.ofNullable(findings);
    }

    /** Clear captured state at the start of a run (the bean is a singleton). */
    void reset() {
        this.findings = null;
    }

    /** Lowercase, trim, split any comma-joined element, and de-duplicate the vehicle names. */
    private static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        return raw.stream()
                .filter(StringUtils::hasText)
                .flatMap(s -> Arrays.stream(s.split(",")))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }
}
