package com.morawski.dev.aidevs.tasks.task07electricity;

import com.morawski.dev.aidevs.config.ElectricityProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Task 7 ({@code electricity}) — solve a 3×3 electrical wiring puzzle so power reaches all three
 * power plants from the source (bottom-left, {@code 3x1}), matching a fixed target schematic. The
 * only allowed move is rotating a tile 90° clockwise, and <strong>one rotation = one {@code /verify}
 * request</strong>, so rotations must be planned, not guessed.
 *
 * <p>Division of labour (course hint): {@link BoardVision} (a vision model) perceives each tile's
 * cable edges, {@link Rotations} computes how many clockwise turns each tile needs deterministically,
 * and this task sends them. After each batch it re-fetches a fresh board and recomputes the residual,
 * which self-corrects single perception errors without a reset.
 *
 * <p>The flag arrives inside the {@code /verify} response of the rotation that completes the circuit,
 * so the task is {@link #selfSubmitting() self-submitting} — it detects {@code {FLG:...}} itself.
 */
@Component
class ElectricityTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ElectricityTask.class);

    private final ElectricityClient client;
    private final BoardVision vision;
    private final ElectricityProperties props;

    ElectricityTask(ElectricityClient client, BoardVision vision, ElectricityProperties props) {
        this.client = client;
        this.vision = vision;
        this.props = props;
    }

    @Override
    public String name() {
        return "electricity";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "electricity.solve")
    public Object solve() {
        // The target schematic is fixed — read it once.
        var target = vision.describe(client.downloadSolved());
        log.info("Target board:\n{}", target.toAscii());

        if (props.resetOnStart()) {
            client.downloadBoard(true);
        }

        for (int round = 1; round <= props.maxRounds(); round++) {
            log.info("=== Round {}/{} ===", round, props.maxRounds());
            var current = vision.describe(client.downloadBoard(false));

            int planned = 0;
            int rotations = 0;
            int unreadable = 0;

            for (var cell : Cell.grid()) {
                var cur = current.at(cell);
                var tgt = target.at(cell);
                if (cur == null || tgt == null) {
                    continue;
                }
                int k = Rotations.requiredRotations(cur, tgt);
                if (k < 0) {
                    // No rotation maps current→target: a perception error (different piece read).
                    // Leave it; the next round re-reads a fresh image and may resolve it.
                    log.warn("Tile {}: no rotation maps {} -> {} (perception error, will re-read)", cell.label(), cur, tgt);
                    unreadable++;
                    continue;
                }
                if (k == 0) {
                    continue;
                }
                planned++;
                log.info("Tile {}: {} -> {} needs {} clockwise rotation(s)", cell.label(), cur, tgt, k);
                for (int i = 0; i < k; i++) {
                    var resp = client.rotate(cell.label());
                    rotations++;
                    var flag = resp.flag();
                    if (flag.isPresent()) {
                        return found(flag.get(), cell.label(), round);
                    }
                }
            }

            log.info("Round {} done: {} tile(s) rotated ({} request(s)), {} unreadable.",
                    round, planned, rotations, unreadable);

            if (planned == 0 && unreadable == 0) {
                // Perception says the board already matches the target, yet no flag came back.
                // If it were truly solved the completing rotation would have carried the flag, so
                // this is almost certainly a misperception — stop rather than spin.
                log.warn("Board matches target per perception but no flag was returned. "
                        + "Likely a vision misread; inspect the logged boards or try a different vision model.");
                return Map.of("status", "no flag", "reason", "perceived-solved");
            }
        }

        log.warn("Exhausted {} rounds without a flag.", props.maxRounds());
        return Map.of("status", "no flag", "rounds", props.maxRounds());
    }

    private Object found(String flag, String cell, int round) {
        log.info("FLAG → {}", flag);
        return Map.of("flag", flag, "cell", cell, "round", round);
    }
}
