package com.morawski.dev.aidevs.tasks.task10drone;

import java.util.List;

/**
 * The drone flight program: an ordered list of instruction strings, sent verbatim as the
 * {@code answer.instructions} array on {@code POST /verify}. Produced by {@link DronePlanner} as
 * structured output and corrected each round from the Hub's error feedback.
 */
record Instructions(List<String> instructions) {

    boolean isEmpty() {
        return instructions == null || instructions.isEmpty();
    }
}
