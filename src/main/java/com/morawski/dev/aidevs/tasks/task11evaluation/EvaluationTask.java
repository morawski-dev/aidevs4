package com.morawski.dev.aidevs.tasks.task11evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.EvaluationProperties;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;

/**
 * S03E01 ({@code evaluation}) — find anomalies among ~10 000 sensor JSON readings and submit the ids of the
 * faulty files to the Centrala.
 *
 * <p>The work splits cleanly by cost. Anomaly types 1, 2 and 4 are <em>data</em> faults (value out of range,
 * or an inactive sensor returning non-zero) and are detected deterministically by {@link RangeValidator} —
 * no LLM, no token spend. The only type the LLM is needed for is type 3: a file whose data is valid but whose
 * operator note <em>claims</em> a fault. Because that verdict depends solely on the note text, we deduplicate
 * notes across the valid-data files (operators repeat themselves heavily) and {@link NoteClassifier} classifies
 * only the few dozen distinct notes — batched, returning minimal output. This is a single-shot submit, so the
 * task is a normal (non-self-submitting) task: {@code TaskRunner} posts the result and extracts the flag.
 */
@Component
class EvaluationTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(EvaluationTask.class);

    /** How many distinct notes to log as a sample, to sanity-check the OK/problem binary framing. */
    private static final int NOTE_SAMPLE = 30;

    private final HubClient hub;
    private final NoteClassifier classifier;
    private final EvaluationProperties props;
    private final ObjectMapper mapper;

    EvaluationTask(HubClient hub, NoteClassifier classifier, EvaluationProperties props, ObjectMapper mapper) {
        this.hub = hub;
        this.classifier = classifier;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "evaluation";
    }

    @Override
    @Observed(name = "evaluation.solve")
    public Object solve() {
        var readings = SensorZip.read(hub.downloadPublic(props.zipFile()), mapper);
        log.info("evaluation: parsed {} sensor files", readings.size());

        // Pass 1 (deterministic): flag data faults (types 1,2,4). Files with valid data become type-3
        // candidates, grouped by their distinct note text (the only thing the type-3 verdict depends on).
        var anomalies = new TreeSet<String>();
        var validByNote = new LinkedHashMap<String, List<String>>();
        for (var r : readings) {
            if (RangeValidator.isDataAnomaly(r)) {
                anomalies.add(r.fileId());
            } else {
                validByNote.computeIfAbsent(r.operatorNotes(), k -> new ArrayList<>()).add(r.fileId());
            }
        }
        log.info("evaluation: {} data anomalies (deterministic); {} valid-data files across {} distinct notes",
                anomalies.size(),
                validByNote.values().stream().mapToInt(List::size).sum(),
                validByNote.size());
        logNoteSample(validByNote.keySet());

        // Pass 2 (LLM, deduped): among valid-data files, a note that asserts a fault is a type-3 anomaly.
        var problematicNotes = classifier.classify(validByNote.keySet());
        for (var note : problematicNotes) {
            anomalies.addAll(validByNote.getOrDefault(note, List.of()));
        }
        log.info("evaluation: {} problem notes → {} total anomalies", problematicNotes.size(), anomalies.size());

        return new RecheckAnswer(List.copyOf(anomalies));
    }

    /** Day-1 gate: eyeball a sample of distinct notes to confirm they're assessment-style (OK vs problem). */
    private static void logNoteSample(Iterable<String> distinctNotes) {
        var sb = new StringBuilder("evaluation: sample of distinct valid-data notes:");
        int i = 0;
        for (var note : distinctNotes) {
            if (i++ >= NOTE_SAMPLE) {
                break;
            }
            sb.append("\n  - ").append(note.replace('\n', ' ').strip());
        }
        log.info(sb.toString());
    }
}
