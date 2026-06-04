package com.morawski.dev.aidevs.tasks.task19filesystem;

import com.morawski.dev.aidevs.config.FilesystemProperties;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * S04E04 ({@code filesystem}) — logically organise Natan Rams's notes into a virtual filesystem on the
 * Centrala, built entirely through {@code POST /verify}. The required layout is three directories:
 * <ul>
 *   <li>{@code /miasta/<city>} — JSON of the goods each trading city needs and how many (no units),</li>
 *   <li>{@code /osoby/<first_last>} — the person managing trade in a city + a markdown link to it,</li>
 *   <li>{@code /towary/<good>} — markdown link(s) to the city/cities that sell that good.</li>
 * </ul>
 *
 * <p>Flow: download {@code natan_notes.zip} from the public Hub space, unzip the text notes, extract a
 * {@link TradeModel} with one strong-model call ({@link NotesExtractor}), deterministically build the
 * action list ({@link FsBuilder}), then drive {@code reset} → batched {@code createDirectory}/
 * {@code createFile} → {@code done}. The task detects its own {@code {FLG:...}} from {@code done}, so it
 * is {@link #selfSubmitting() self-submitting}.
 *
 * <p>Re-runnable: {@code reset} clears the filesystem first and {@code createFile} overwrites, so a
 * second run rebuilds the same structure and {@code done} returns the flag again.
 */
@Component
class FilesystemTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(FilesystemTask.class);

    private final HubClient hub;
    private final FsClient client;
    private final NotesExtractor extractor;
    private final FilesystemProperties props;

    FilesystemTask(HubClient hub, FsClient client, NotesExtractor extractor, FilesystemProperties props) {
        this.hub = hub;
        this.client = client;
        this.extractor = extractor;
        this.props = props;
    }

    @Override
    public String name() {
        return "filesystem";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "filesystem.solve")
    public Object solve() {
        // 1. Download + unzip Natan's notes from the public Hub space.
        var notes = NotesZip.read(hub.downloadPublic(props.notesZip()));
        log.info("Loaded {} note files: {}", notes.size(), notes.keySet());

        // 2. Extract the structured trade model (cities, needs, managers, offered goods).
        var model = extractor.extract(notes);

        // 3. Build the ordered action list deterministically.
        var actions = FsBuilder.build(model);
        log.info("Built {} filesystem actions", actions.size());

        // 4. Wipe any previous state, then create everything in order (batched).
        client.call(Map.of("action", "reset"));
        for (var batch : chunk(actions, Math.max(1, props.batchSize()))) {
            var resp = client.call(batch);
            if (!resp.ok()) {
                log.warn("A create batch returned HTTP {} — see body above. Continuing to done for feedback.",
                        resp.status());
            }
        }

        // 5. Validate. The flag comes back from done once the structure satisfies all rules.
        var done = client.call(Map.of("action", "done"));
        var flag = done.flag();
        if (flag.isPresent()) {
            log.info("FLAG → {}", flag.get());
            return Map.of("flag", flag.get());
        }
        log.warn("done() returned no flag — the structure is incomplete. Feedback: {}", done.body());
        return Map.of("status", "no flag", "feedback", done.body());
    }

    /** Split actions into batches of at most {@code size}, preserving order across batches. */
    private static List<List<Map<String, Object>>> chunk(List<Map<String, Object>> actions, int size) {
        var out = new java.util.ArrayList<List<Map<String, Object>>>();
        for (int i = 0; i < actions.size(); i += size) {
            out.add(actions.subList(i, Math.min(i + size, actions.size())));
        }
        return out;
    }
}
