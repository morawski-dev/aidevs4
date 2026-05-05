package com.morawski.dev.aidevs.tasks.task10drone;

import java.util.List;

/** One past submission and the Hub's feedback for it — fed back to the planner to correct course. */
record Attempt(List<String> instructions, String feedback) {
}
