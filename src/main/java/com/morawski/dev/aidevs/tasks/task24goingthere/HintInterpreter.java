package com.morawski.dev.aidevs.tasks.task24goingthere;

import com.morawski.dev.aidevs.config.GoingthereProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns a free-form English radio hint into the {@link AvoidSide} (the rock's side in the rocket's
 * command frame). The hints are deliberately phrased in odd, often <em>nautical</em> language
 * ("rock off the port bow", "obstruction dead ahead", "hard to starboard"), so a pure keyword match is
 * brittle — an LLM classifier reads the intent. The model returns a tiny {@link HintVerdict} structure.
 */
@Component
class HintInterpreter {

    private static final Logger log = LoggerFactory.getLogger(HintInterpreter.class);

    private static final String SYSTEM = """
            You read ONE radio navigation hint and report WHICH SINGLE SIDE the rock (obstacle) is on,
            in the column directly ahead of a rocket, from the rocket's own point of view as it flies
            forward. Exactly one of the three sides holds the rock; the other two are clear.

            Vocabulary — map every phrasing to one of three sides:
              - PORT  = the rocket's LEFT side (also: left, left wing, left edge, "to port").
              - STARBOARD = the rocket's RIGHT side (also: right, right wing, right edge, "to starboard").
              - BOW   = straight AHEAD, same lane (also: nose, front, fore, dead ahead, the middle, the
                        centre, the cockpit/heading/flight line/line of travel, "straight out").

            Find which side the ROCK is on. The rock is named by words like: rock, stone, obstacle,
            obstruction, hazard, danger, trouble, impact, mass, solid, blocked, "aimed at", "waiting",
            "posted", "resting", "crowding", "keeping pace", "is off the ___ side". The OTHER sides are
            described as safe: open, clear, clean, free, usable, space, breathing room, friendly, "no
            issue", "nothing", "no danger". BEWARE double negatives ("the clear option is NOT starboard"
            ⇒ the rock IS on starboard). Decide by where the rock is, ignoring decorative wording.

            Answer with exactly one value for "side":
              - LEFT  → the rock is on the rocket's left / port.
              - RIGHT → the rock is on the rocket's right / starboard.
              - FRONT → the rock is straight ahead (bow / nose / middle), same lane.
            """;

    private final LlmService llm;
    private final GoingthereProperties props;

    HintInterpreter(LlmService llm, GoingthereProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /** Classify one hint; retries a couple of times on a malformed/empty model reply. */
    AvoidSide interpret(String hint) {
        String user = "Radio hint: \"" + hint + "\"\nWhich side is the rock on?";
        int attempts = Math.max(1, props.maxRetries());
        RuntimeException last = null;
        for (int i = 0; i < attempts; i++) {
            try {
                HintVerdict verdict = llm.extract(SYSTEM, user, props.hintModel(),
                        props.hintMaxTokens() > 0 ? props.hintMaxTokens() : 100, HintVerdict.class);
                if (verdict != null && verdict.side() != null) {
                    log.info("hint \"{}\" -> rock {} (avoid {})", hint, verdict.side(), verdict.side().forbidden().toApi());
                    return verdict.side();
                }
                log.warn("Hint classifier returned no side (attempt {}/{}): \"{}\"", i + 1, attempts, hint);
            } catch (RuntimeException e) {
                last = e;
                log.warn("Hint classifier failed (attempt {}/{}): {}", i + 1, attempts, e.toString());
            }
        }
        throw new IllegalStateException("Could not interpret radio hint after " + attempts + " attempts: " + hint, last);
    }
}
