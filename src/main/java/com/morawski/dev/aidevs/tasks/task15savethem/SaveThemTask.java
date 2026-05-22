package com.morawski.dev.aidevs.tasks.task15savethem;

import com.morawski.dev.aidevs.config.SaveThemProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * S03E05 ({@code savethem}) — plan an optimal route for a messenger from the base to the city of
 * Skolwin. We are given only a tool-search endpoint, so an agent must first DISCOVER the other tools
 * (map, vehicles, rule notes) and gather the facts; then the route is computed deterministically.
 *
 * <p>Two layers, by design:
 * <ul>
 *   <li><b>Recon (agentic, LLM):</b> {@link SaveThemConversation} gives a tool-calling model three
 *       tools ({@code search_tools}, {@code call_tool}, {@code record_findings}) and Spring AI runs
 *       the inner loop. An outer loop re-prompts up to {@code maxIterations} until the agent records
 *       its {@link Findings}. Mirrors task09's mailbox agent.</li>
 *   <li><b>Planning (deterministic):</b> {@link SaveThemService} fetches and parses the map and
 *       vehicle stats straight from the tools' JSON (no LLM transcription) and {@link RoutePlanner}
 *       finds the fastest feasible route within the fuel/food budgets.</li>
 * </ul>
 *
 * <p>This is a normal task: {@code solve()} returns the answer array {@code [vehicle, step, …]} and
 * the {@code TaskRunner} submits it to {@code /verify} and extracts the {@code {FLG:...}}.
 */
@Component
class SaveThemTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(SaveThemTask.class);
    private static final String CONVERSATION_ID = "savethem";

    private static final String INITIAL_PROMPT = """
            Begin. Discover the tools, learn the rules, confirm the destination is Skolwin, and enumerate every
            travel mode. When you are confident, call record_findings once.""";

    private static final String CONTINUE_PROMPT = """
            You have not recorded complete findings yet. Keep searching with different English queries, read the
            notes and the vehicle tool to list ALL travel modes, then call record_findings.""";

    private final SaveThemConversation conversation;
    private final SaveThemTools tools;
    private final SaveThemService service;
    private final RoutePlanner planner;
    private final SaveThemProperties props;

    SaveThemTask(SaveThemConversation conversation, SaveThemTools tools, SaveThemService service,
                 RoutePlanner planner, SaveThemProperties props) {
        this.conversation = conversation;
        this.tools = tools;
        this.service = service;
        this.planner = planner;
        this.props = props;
    }

    @Override
    public String name() {
        return "savethem";
    }

    @Override
    @Observed(name = "savethem.solve")
    public Object solve() {
        tools.reset();
        int maxIter = Math.max(1, props.maxIterations());

        Findings findings = null;
        for (int i = 1; i <= maxIter && findings == null; i++) {
            String prompt = (i == 1) ? INITIAL_PROMPT : CONTINUE_PROMPT;
            log.info("Recon round {}/{}", i, maxIter);
            String reply = conversation.run(CONVERSATION_ID, prompt);
            log.info("Agent (round {}): {}", i, reply);
            findings = tools.findings().orElse(null);
        }

        if (findings == null) {
            throw new IllegalStateException(
                    "Recon agent did not record findings after " + maxIter + " rounds.");
        }

        var world = service.build(findings);
        List<String> answer = planner.plan(world.grid(), world.modes(),
                props.fuelBudget(), props.foodBudget(), props.treePenalty());
        if (answer == null) {
            throw new IllegalStateException("No feasible route within fuel=" + props.fuelBudget()
                    + " / food=" + props.foodBudget() + " budgets for the gathered world.");
        }

        log.info("Planned route ({} tokens): {}", answer.size(), answer);
        return answer;
    }
}
