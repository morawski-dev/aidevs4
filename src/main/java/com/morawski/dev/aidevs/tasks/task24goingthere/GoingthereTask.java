package com.morawski.dev.aidevs.tasks.task24goingthere;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.GoingthereProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S05E04 ({@code goingthere}) — fly a ground rocket across an invisible 3-row × 12-column corridor from
 * the start (col 1, row 2) to the Grudziądz base (col 12, base row given at {@code start}). Each column
 * holds one rock; the only forward sensor is a per-step radio hint. Before every move the OKO radar
 * scanner must be checked and any lock neutralised, or the rocket is shot down. Both the scanner data
 * and the API itself are adversarially unreliable (corrupted bodies + random errors) — every call is
 * retried.
 *
 * <p>The flag arrives inside a {@code /verify} response once the rocket reaches the base, so the task is
 * {@link #selfSubmitting() self-submitting}. Perception (hint reading via {@link HintInterpreter}) is
 * split from logic ({@link GoingtherePlanner}); the rocket's position is tracked deterministically and
 * cross-checked against each parsed {@link GameState}.
 *
 * <p><b>Recon mode</b> ({@code aidevs.goingthere.recon=true}): a read-only probe that does {@code start}
 * and a few scanner/getmessage reads (no movement, so it can't crash) and just logs the raw bodies, so
 * the JSON field names, hint phrasing, scanner corruption shape, and board orientation can be confirmed
 * before committing the parsers.
 */
@Component
class GoingthereTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(GoingthereTask.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int GOAL_COL = 12;
    private static final int START_COL = 1;
    private static final int START_ROW = 2;

    private final GoingthereClient client;
    private final GoingtherePlanner planner;
    private final HintInterpreter hints;
    private final GoingthereProperties props;

    GoingthereTask(GoingthereClient client, GoingtherePlanner planner, HintInterpreter hints,
                   GoingthereProperties props) {
        this.client = client;
        this.planner = planner;
        this.hints = hints;
        this.props = props;
    }

    @Override
    public String name() {
        return "goingthere";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "goingthere.solve")
    public Object solve() {
        return props.recon() ? recon() : drive();
    }

    private Object drive() {
        int leftRowDelta = props.leftRowDelta() == 0 ? -1 : props.leftRowDelta();
        int moves = 0;

        for (int restart = 0; restart <= props.maxRestarts(); restart++) {
            var startResp = client.command(Command.START);
            var startFlag = startResp.flag();
            if (startFlag.isPresent()) {
                return Map.of("flag", startFlag.get(), "moves", moves);
            }
            var start = GameState.parse(startResp.body());

            int baseRow = start.baseRow().orElse(START_ROW);
            int row = start.row().orElse(START_ROW);
            int col = start.col().orElse(START_COL);
            if (start.baseRow().isEmpty()) {
                log.warn("Base row not found in start body — defaulting to {} (confirm field name via recon).", baseRow);
            }
            log.info("Start (attempt {}/{}): pos=({},{}) baseRow={}", restart + 1, props.maxRestarts() + 1, col, row, baseRow);

            boolean crashed = false;
            while (col < GOAL_COL && moves < props.maxMoves()) {
                clearRadar();

                AvoidSide avoid = nextAvoidSide();
                Command move = planner.next(row, baseRow, avoid, leftRowDelta);
                log.info("col={} row={} baseRow={} avoid={} -> {}", col, row, baseRow, avoid, move);

                var resp = client.command(move);
                moves++;

                var flag = resp.flag();
                if (flag.isPresent()) {
                    log.info("FLAG → {}", flag.get());
                    return Map.of("flag", flag.get(), "moves", moves);
                }

                var next = GameState.parse(resp.body());
                if (next.crashed()) {
                    log.warn("Crash reported after {} (attempt {}): {}", move, restart + 1, next.message());
                    crashed = true;
                    break;
                }

                // Deterministic track is authoritative; trust the parsed value only when present.
                col = next.col().orElse(col + 1);
                row = next.row().orElse(clampRow(row + rowDelta(move, leftRowDelta)));
            }

            if (!crashed) {
                if (col >= GOAL_COL) {
                    log.info("Reached the goal column without a flag in the body after {} moves.", moves);
                    return Map.of("status", "reached goal (no flag in body)", "moves", moves);
                }
                log.warn("Move budget exhausted after {} moves.", moves);
                return Map.of("status", "move budget exhausted", "moves", moves);
            }
        }

        log.warn("Restart budget exhausted after {} moves.", moves);
        return Map.of("status", "restart budget exhausted", "moves", moves);
    }

    /** Re-scan until the position is clear, disarming any lock; bounded so jamming can't loop forever. */
    private void clearRadar() {
        int attempts = Math.max(10, props.maxRetries() * 3);
        for (int i = 0; i < attempts; i++) {
            var reading = ScannerParser.parse(client.scan().body());
            switch (reading.kind()) {
                case CLEAR -> {
                    return;
                }
                case DETECTED -> {
                    String hash = Sha1.hex(reading.detectionCode() + "disarm");
                    log.info("Radar lock: frequency={} detectionCode={} -> disarmHash={}",
                            reading.frequency(), reading.detectionCode(), hash);
                    var disarmResp = client.disarm(reading.frequency(), hash);
                    log.info("Disarm response: HTTP {} body: {}", disarmResp.status(), disarmResp.body());
                    // Loop re-scans to confirm the lock is gone before we move.
                }
                case CORRUPT -> log.warn("Corrupted scanner read ({}/{}) — re-scanning.", i + 1, attempts);
            }
        }
        log.warn("Scanner never confirmed clear after {} attempts — proceeding (may be shot).", attempts);
    }

    /**
     * Fetch the forward radio hint several times and majority-vote the interpreted side. The radio
     * channel is jammed (hints are individually unreliable), so redundant sampling + consensus recovers
     * the true side when re-requests re-roll.
     */
    private AvoidSide nextAvoidSide() {
        int samples = Math.max(1, props.hintSamples());
        var votes = new java.util.EnumMap<AvoidSide, Integer>(AvoidSide.class);
        for (int i = 0; i < samples; i++) {
            String hint = extractHint(client.message().body());
            if (hint == null || hint.isBlank()) {
                continue;
            }
            try {
                AvoidSide side = hints.interpret(hint);
                votes.merge(side, 1, Integer::sum);
            } catch (RuntimeException e) {
                log.warn("Hint interpretation failed: {}", e.toString());
            }
        }
        if (votes.isEmpty()) {
            throw new IllegalStateException("Could not obtain any usable radio hint in " + samples + " samples");
        }
        var winner = votes.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow();
        log.info("Hint votes {} -> {}", votes, winner.getKey());
        return winner.getKey();
    }

    /** Pull the {@code hint} field from the getmessage body; fall back to the whole body if not JSON. */
    private static String extractHint(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            var node = MAPPER.readTree(body).path("hint");
            if (!node.isMissingNode() && !node.isNull()) {
                return node.asText();
            }
        } catch (Exception ignored) {
            // Not JSON (jammed) — fall through to using the raw body as the hint text.
        }
        return body;
    }

    private static int rowDelta(Command move, int leftRowDelta) {
        return switch (move) {
            case GO -> 0;
            case LEFT -> leftRowDelta;
            case RIGHT -> -leftRowDelta;
            case START -> 0;
        };
    }

    private static int clampRow(int row) {
        return Math.max(1, Math.min(GoingtherePlanner.ROWS, row));
    }

    /**
     * Read-only probe: {@code start}, then a couple of scanner + getmessage reads. The rocket never
     * moves, so it can't be crushed; the point is to capture the raw bodies and confirm field names,
     * hint phrasing, scanner corruption shape, and the left/right row orientation.
     */
    private Object recon() {
        log.info("=== goingthere RECON (read-only: start + scanner/getmessage reads) ===");
        var samples = new LinkedHashMap<String, Object>();
        samples.put("start", client.command(Command.START).body());
        samples.put("scan-1", client.scan().body());
        samples.put("hint-1", client.message().body());
        samples.put("scan-2", client.scan().body());
        samples.put("hint-2", client.message().body());
        log.info("=== goingthere RECON done (see raw bodies logged above). ===");
        return Map.of("mode", "recon", "samples", samples);
    }
}
