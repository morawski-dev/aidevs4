package com.morawski.dev.aidevs.tasks.task11evaluation;

import java.util.List;

/** The {@code answer} payload for {@code /verify}: serializes to {@code {"recheck":[...]}}. */
record RecheckAnswer(List<String> recheck) {
}
