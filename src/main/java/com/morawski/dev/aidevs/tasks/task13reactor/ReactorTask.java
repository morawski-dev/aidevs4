package com.morawski.dev.aidevs.tasks.task13reactor;

import com.morawski.dev.aidevs.config.ReactorProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S03E03 ({@code reactor}) — drive a cooling-module transport robot across a 7×5 reactor board from
 * the bottom-left start (col 1, row 5) to the bottom-right goal (col 7, row 5) without being crushed
 * by the reactor blocks (each block occupies 2 cells and cycles up/down). Commands ({@code start},
 * {@code reset}, {@code left}, {@code wait}, {@code right}) are sent one at a time to {@code /verify};
 * every command advances the blocks one step, and the API returns the resulting board state.
 *
 * <p>The board is fully deterministic and observable, so the controller is pure logic (no LLM, no
 * vision — the graphical preview is for humans). The flag arrives inside a {@code /verify} response
 * once the robot reaches the goal, so the task is {@link #selfSubmitting() self-submitting}.
 *
 * <p><b>Recon mode</b> ({@code aidevs.reactor.recon=true}): a read-only probe that sends {@code start}
 * then a few {@code wait}s and just logs the raw bodies, so the exact board JSON shape and block
 * mechanics can be learned before the parser/simulator are wired. The robot never moves, so it can't
 * be crushed during recon.
 */
@Component
class ReactorTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ReactorTask.class);
    private static final int RECON_WAITS = 8;

    private final ReactorClient client;
    private final ReactorPlanner planner;
    private final ReactorProperties props;

    ReactorTask(ReactorClient client, ReactorPlanner planner, ReactorProperties props) {
        this.client = client;
        this.planner = planner;
        this.props = props;
    }

    @Override
    public String name() {
        return "reactor";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "reactor.solve")
    public Object solve() {
        if (props.recon()) {
            return recon();
        }
        return drive();
    }

    /**
     * Drive the robot to the goal. After {@code start}, re-plan from the actual board before every
     * command (BFS is tiny, so this is free) and send the plan's first command — this stays optimal
     * while self-correcting against any surprise in the live board. Reaching the goal column returns
     * the flag; a crush (or a phase with no safe path) triggers a {@code reset} and a fresh plan.
     */
    private Object drive() {
        var state = BoardState.parse(client.command(Command.START).body());
        log.info("Reactor start: robot=({},{}) goal=({},{}) blocks={}",
                state.robotCol(), state.robotRow(), state.goalCol(), state.goalRow(), state.blocks().size());

        int resets = 0;
        for (int sent = 0; sent < props.maxCommands(); sent++) {
            if (state.robotAtGoal()) {
                log.info("Robot reached the goal but no flag was seen in the body — done.");
                return Map.of("status", "reached goal (no flag in body)", "commands", sent);
            }

            var plan = planner.plan(state);
            if (plan == null || plan.isEmpty()) {
                // No safe path from this phase (or a parse hiccup): reset and try a fresh board.
                if (resets++ >= props.maxResets()) {
                    log.warn("No safe path and reset budget exhausted after {} commands.", sent);
                    return Map.of("status", "stuck", "commands", sent, "resets", resets);
                }
                log.warn("No safe path from current state; resetting (reset {}/{}).", resets, props.maxResets());
                state = BoardState.parse(client.command(Command.RESET).body());
                continue;
            }

            Command cmd = plan.getFirst();
            var resp = client.command(cmd);

            var flag = resp.flag();
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "commands", sent + 1);
            }

            var next = BoardState.parse(resp.body());
            if (next.robotAtGoal()) {
                log.info("Robot reached the goal (cmd={}, {} commands).", cmd, sent + 1);
                return Map.of("status", "reached goal", "commands", sent + 1, "message", next.message());
            }
            if (next.robotCrushed()) {
                if (resets++ >= props.maxResets()) {
                    log.warn("Robot crushed and reset budget exhausted after {} commands.", sent + 1);
                    return Map.of("status", "crushed", "commands", sent + 1, "resets", resets);
                }
                log.warn("Robot crushed (cmd={}); resetting (reset {}/{}).", cmd, resets, props.maxResets());
                state = BoardState.parse(client.command(Command.RESET).body());
                continue;
            }
            state = next;
        }

        log.warn("Gave up after {} commands without reaching the goal.", props.maxCommands());
        return Map.of("status", "no flag", "commands", props.maxCommands(), "resets", resets);
    }

    /**
     * Read-only probe: {@code start}, then {@code wait} a few times. The robot stays at the start
     * cell the whole time, so nothing can crush it; the point is purely to capture the raw board
     * JSON across a few block steps (see {@link ReactorClient#command} logging).
     */
    private Object recon() {
        log.info("=== Reactor RECON (read-only: start + {} waits) ===", RECON_WAITS);
        var bodies = new ArrayList<String>();

        var resp = client.command(Command.START);
        bodies.add(resp.body());

        for (int i = 0; i < RECON_WAITS; i++) {
            resp = client.command(Command.WAIT);
            bodies.add(resp.body());
        }

        log.info("=== Reactor RECON done. Collected {} raw bodies (see logs above). ===", bodies.size());
        return Map.of("mode", "recon", "samples", List.copyOf(bodies));
    }
}
