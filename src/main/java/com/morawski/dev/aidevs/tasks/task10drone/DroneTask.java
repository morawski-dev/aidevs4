package com.morawski.dev.aidevs.tasks.task10drone;

import com.morawski.dev.aidevs.config.DroneProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Task 10 ({@code drone}) — program an armed drone to bomb the dam near the Żarnowiec power plant
 * (code {@code PWR6132PL}) instead of the plant itself, to flood the reactor's cooling system.
 *
 * <p>Two stages (course hint): {@link DroneVision} (a vision model) locates the dam sector on the
 * terrain map — counting the grid and picking the over-saturated water sector — and {@link
 * DronePlanner} builds the flight instructions reactively from the drone's API docs, correcting each
 * attempt from the Hub's precise error feedback. The docs and the dam sector are fixed, so they're
 * read once; only the instructions change between rounds.
 *
 * <p>The flag arrives inside the {@code /verify} response of a successful run, so the task is
 * {@link #selfSubmitting() self-submitting} — it detects {@code {FLG:...}} itself and the
 * {@code TaskRunner} must not submit again.
 */
@Component
class DroneTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(DroneTask.class);

    private final DroneClient client;
    private final DroneVision vision;
    private final DronePlanner planner;
    private final DroneProperties props;

    DroneTask(DroneClient client, DroneVision vision, DronePlanner planner, DroneProperties props) {
        this.client = client;
        this.vision = vision;
        this.planner = planner;
        this.props = props;
    }

    @Override
    public String name() {
        return "drone";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "drone.solve")
    public Object solve() {
        // Recon (fixed inputs, read once): the instruction language and the dam sector.
        var docs = client.downloadDocs();
        var dam = vision.locateDam(client::downloadMap);

        int maxIter = Math.max(1, props.maxIterations());
        var history = new ArrayList<Attempt>();

        for (int i = 1; i <= maxIter; i++) {
            log.info("=== Drone round {}/{} ===", i, maxIter);
            // Round 1: the known-good template with the detected dam sector plugged in (reliable).
            // Later rounds: the reactive LLM planner corrects from the Hub's feedback (handles a
            // changed map/contract the template no longer fits).
            List<String> instructions;
            if (i == 1) {
                instructions = DronePlanner.template(dam, props.plantCode());
            } else {
                var plan = planner.next(docs, dam, history);
                if (plan == null || plan.isEmpty()) {
                    log.warn("Planner returned no instructions; retrying.");
                    history.add(new Attempt(List.of(), "planner produced an empty instruction list"));
                    continue;
                }
                instructions = plan.instructions();
            }

            var resp = client.submit(instructions);
            var flag = resp.flag();
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "rounds", i, "instructions", instructions);
            }
            history.add(new Attempt(instructions, resp.body()));
        }

        var last = history.isEmpty() ? "(no attempts)" : history.getLast().feedback();
        log.warn("No flag after {} rounds. Last Hub feedback: {}", maxIter, last);
        return Map.of("status", "no flag", "rounds", maxIter, "lastFeedback", last);
    }
}
