package com.morawski.dev.aidevs.tasks.task10drone;

import com.morawski.dev.aidevs.config.DroneProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the drone's flight program ({@link Instructions}) reactively: it reads the drone's API
 * documentation, knows the dam sector and the (decoy) plant code, and corrects each attempt from the
 * Hub's error feedback. The maths is trivial — the hard part is using the right instructions from a
 * documentation that deliberately mixes colliding/unnecessary functions, so the model is told to use
 * the minimum needed and to trust the Hub's precise error messages over guessing.
 */
@Component
class DronePlanner {

    private static final String SYSTEM = """
            You program an ARMED bombing drone by emitting a JSON array of instruction strings
            ("instructions"), sent verbatim to the Hub.

            MISSION: make the drone drop its single explosive payload on the DAM sector — NOT on the
            power plant. Officially the drone is sent against the plant, but the bomb must actually
            land on the dam (to flood the reactor's cooling system). The dam sector's coordinates are
            the real impact target.

            RULES:
            - You are given the drone's API documentation. Use ONLY the instructions actually needed to
              power up, set altitude, aim at the DAM SECTOR, fly, and destroy the target. The docs
              deliberately contain colliding and unnecessary functions — ignore what you don't need and
              save tokens.
            - Aim the impact at the DAM SECTOR coordinates using the documentation's sector-coordinate
              instruction. Sector (1,1) is the upper-left corner: x = column from the left, y = row
              from the top.
            - You also get the power-plant code (the nominal/decoy destination). Use it only if the
              documentation requires a destination object to start the flight — the landing SECTOR must
              still be the dam.
            - You get the history of your previous attempts and the Hub's exact error feedback. Read it
              carefully and fix the next instruction list accordingly; do not repeat a rejected attempt.
            - If earlier attempts left the drone in a broken, accumulated configuration, you may begin
              the list with a factory reset (hardReset) and reconfigure cleanly.

            Output ONLY the instruction list, in execution order.
            """;

    private final LlmService llm;
    private final DroneProperties props;

    DronePlanner(LlmService llm, DroneProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /**
     * The known-good flight program, with the dam sector and plant code plugged in. The minimal
     * working sequence (confirmed against the Hub) is: factory reset → set the nominal (decoy) plant
     * destination → aim the landing SECTOR at the dam → power up → set mission objectives
     * ({@code destroy} then {@code return}) <em>before</em> flight (they execute during it; omitting
     * {@code return} loses the drone) → fly. Used as the first attempt; the reactive {@link #next}
     * loop is the fallback if the Hub rejects it (e.g. a changed map/contract).
     */
    static List<String> template(DamLocation dam, String plantCode) {
        return List.of(
                "hardReset",
                "setDestinationObject(" + plantCode + ")",
                "set(" + dam.damCol() + "," + dam.damRow() + ")",
                "set(engineON)",
                "set(100%)",
                "set(20m)",
                "set(destroy)",
                "set(return)",
                "flyToLocation");
    }

    /** Produce the next flight program from the docs, the dam sector, the plant code, and the history. */
    Instructions next(String docs, DamLocation dam, List<Attempt> history) {
        var user = new StringBuilder();
        user.append("=== DRONE API DOCUMENTATION ===\n").append(docs).append("\n\n");
        user.append("=== DAM SECTOR (real impact target) ===\n")
                .append("column (x, from left) = ").append(dam.damCol()).append('\n')
                .append("row (y, from top) = ").append(dam.damRow()).append('\n')
                .append("grid size = ").append(dam.cols()).append(" columns x ").append(dam.rows()).append(" rows\n\n");
        user.append("=== POWER PLANT CODE (nominal/decoy destination) ===\n")
                .append(props.plantCode()).append("\n\n");

        if (history.isEmpty()) {
            user.append("=== HISTORY ===\n(no attempts yet — produce your best first instruction list)\n");
        } else {
            user.append("=== HISTORY (previous attempts and Hub feedback) ===\n");
            for (int i = 0; i < history.size(); i++) {
                var a = history.get(i);
                user.append("Attempt ").append(i + 1).append(":\n")
                        .append("  instructions: ").append(a.instructions()).append('\n')
                        .append("  Hub feedback: ").append(a.feedback()).append('\n');
            }
            user.append("\nFix the next instruction list based on the latest feedback.\n");
        }

        return llm.extract(SYSTEM, user.toString(), props.plannerModel(), Instructions.class);
    }
}
