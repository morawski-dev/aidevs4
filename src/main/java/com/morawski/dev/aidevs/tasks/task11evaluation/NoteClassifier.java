package com.morawski.dev.aidevs.tasks.task11evaluation;

import com.morawski.dev.aidevs.config.EvaluationProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * LLM step for anomaly type 3: among files whose data is already known-valid, find those whose operator note
 * <em>asserts a fault</em> (the operator claims an error/problem while the readings are actually fine).
 *
 * <p>The verdict depends only on the note text — never on the file's data — which is what makes the caller's
 * exact-dedup-by-text sound: identical note ⇒ identical verdict. So we classify only the <em>distinct</em>
 * notes, batched, and the model returns only the 1-based indices of the problem notes (minimal output, since
 * output tokens are the expensive part). A strong model is used (configured) because scoring is exact-set
 * match and the OK/problem boundary on ambiguous notes is where this task quietly fails.
 */
@Component
class NoteClassifier {

    private static final Logger log = LoggerFactory.getLogger(NoteClassifier.class);

    private static final String SYSTEM = """
            You are auditing sensor operator notes from a power plant. Each note is already paired with
            readings CONFIRMED VALID by an external check, so the measurements themselves are fine.

            Your only job: decide which notes POSITIVELY ASSERT that something is WRONG — i.e. the operator
            claims a fault, suspicious/inconsistent/unstable/unreliable readings, an anomaly, degradation,
            or that the case was escalated/flagged/sent for investigation/maintenance because of a concern.

            CRITICAL — these notes are written to trick keyword matching. Most notes are REASSURING even when
            they contain words like "fault", "anomaly", "drift", "irregular", "escalation", "deviation",
            because those words are NEGATED. Read meaning, not keywords. Watch negation carefully:
            - NOT a problem (reassuring): "nothing suggests a fault condition", "no sign of abnormal activity",
              "no concerning drift is present", "there are no deviations to flag", "no escalation was triggered",
              "no corrective steps were needed", "we are still in a safe operating zone", "status stays green",
              "everything checks out", "readings look stable", "the operating envelope is respected".
            - IS a problem (asserts a fault): "these readings look suspicious", "the numbers feel inconsistent",
              "there is a visible anomaly here", "the latest behavior is concerning", "the report does not look
              healthy", "I flagged it for urgent verification", "I escalated this for engineering analysis",
              "documented it as a probable fault", "the output suggests a potential fault", "signs of malfunction".

            Be conservative: mark a note ONLY when the operator genuinely claims something is wrong. If the note
            is reassuring, neutral, descriptive, or merely says no problem was found, do NOT mark it.

            You are given a numbered list of notes. Return ONLY the 1-based indices of the PROBLEM notes,
            as JSON: {"problemIndices":[...]}. Return an empty list if none.
            """;

    private final LlmService llm;
    private final EvaluationProperties props;

    NoteClassifier(LlmService llm, EvaluationProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /** Structured output: 1-based indices (into the batch) of notes that assert a problem. */
    record Verdict(List<Integer> problemIndices) {
    }

    /** Returns the subset of {@code distinctNotes} whose text asserts a fault. */
    Set<String> classify(Collection<String> distinctNotes) {
        var notes = new ArrayList<>(distinctNotes);
        var problematic = new LinkedHashSet<String>();
        var batchSize = Math.max(1, props.noteBatchSize());

        for (int start = 0; start < notes.size(); start += batchSize) {
            var batch = notes.subList(start, Math.min(start + batchSize, notes.size()));
            var verdict = llm.extract(SYSTEM, render(batch), props.classifyModel(), Verdict.class);
            var hits = mapHits(batch, verdict);
            problematic.addAll(hits);
            log.info("evaluation: note batch {}-{} ({} notes) → {} problem notes",
                    start, start + batch.size(), batch.size(), hits.size());
        }
        return problematic;
    }

    /** Numbered (1-based) list, one note per line. */
    private static String render(List<String> batch) {
        var sb = new StringBuilder();
        for (int i = 0; i < batch.size(); i++) {
            sb.append(i + 1).append(". ").append(batch.get(i).replace('\n', ' ').strip()).append('\n');
        }
        return sb.toString();
    }

    /** Map valid 1-based indices back to note texts, ignoring out-of-range indices the model might return. */
    private static List<String> mapHits(List<String> batch, Verdict verdict) {
        if (verdict == null || verdict.problemIndices() == null) {
            return List.of();
        }
        return verdict.problemIndices().stream()
                .filter(i -> i != null && i >= 1 && i <= batch.size())
                .map(i -> batch.get(i - 1))
                .distinct()
                .toList();
    }
}
