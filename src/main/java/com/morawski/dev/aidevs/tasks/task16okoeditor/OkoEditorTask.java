package com.morawski.dev.aidevs.tasks.task16okoeditor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.OkoEditorProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S05E01 ({@code okoeditor}) — make changes to the OKO Operations Center through the Centrala's
 * {@code /verify} API (the "backdoor"); manual edits in the web panel ({@code https://oko.ag3nts.org/})
 * are forbidden. Three required edits, then {@code done}:
 * <ol>
 *   <li>reclassify the Skolwin <em>incident</em> from movement-of-vehicles/people ({@code MOVE03}) to
 *       animals ({@code MOVE04}), keeping the word "Skolwin" in the title,</li>
 *   <li>mark the Skolwin <em>task</em> done and note that animals (beavers) were seen there,</li>
 *   <li>repurpose a spare incident into a human-movement report near the (uninhabited) Komarowo
 *       ({@code MOVE01}).</li>
 * </ol>
 *
 * <p>This is a <b>deterministic</b> driver — no LLM. The OKO API is update-only and write-only: it
 * cannot list records, so their IDs come from the panel's list pages (the brief supplied a login for
 * exactly this), and the classification codes were recovered from the API's validation feedback. All
 * of that is configuration ({@link OkoEditorProperties}); {@code solve()} just applies the three
 * {@code update}s and calls {@code done}. The task drives the dialog itself and detects its own
 * {@code {FLG:...}}, so it is {@link #selfSubmitting() self-submitting}; the {@code TaskRunner} must
 * not submit again.
 *
 * <p>Re-runnable: {@code update} is an upsert-by-id overwrite, so applying the same edits twice is
 * idempotent and {@code done} returns the flag again.
 */
@Component
class OkoEditorTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(OkoEditorTask.class);

    private static final String PAGE_INCYDENTY = "incydenty";
    private static final String PAGE_ZADANIA = "zadania";

    private final OkoClient client;
    private final OkoEditorProperties props;
    private final ObjectMapper mapper;

    OkoEditorTask(OkoClient client, OkoEditorProperties props, ObjectMapper mapper) {
        this.client = client;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "okoeditor";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "okoeditor.solve")
    public Object solve() {
        // 1. Reclassify the Skolwin incident report: MOVE03 (vehicles/people) -> MOVE04 (animals).
        //    The word "Skolwin" must stay in the title (the done check verifies it).
        var edit1 = update(PAGE_INCYDENTY, props.skolwinId(),
                props.animalsCode() + " Zwierzęta zaobserwowane nieopodal miasta Skolwin",
                "Rekonesans terenu wykrył zwierzęta (m.in. bobry) w okolicy miasta Skolwin. "
                        + "To nie są pojazdy ani ludzie.",
                null);

        // 2. Mark the Skolwin task as done and note the animals (beavers) seen there.
        var edit2 = updateTaskDone(props.skolwinId(),
                "Zbadano nagrania z okolic Skolwina. Widziano tam zwierzęta, m.in. bobry. "
                        + "Brak śladów ludzi i pojazdów.");

        // 3. Repurpose a spare incident into a human-movement report near Komarowo (MOVE01) — the
        //    decoy that redirects the operators away from Skolwin.
        var edit3 = update(PAGE_INCYDENTY, props.komarowoIncidentId(),
                props.humanMovementCode() + " Wykrycie ruchu ludzi w okolicach miasta Komarowo",
                "Zarejestrowano ruch ludzi w okolicach miasta Komarowo. Zalecana obserwacja sektora.",
                null);

        for (var edit : List.of(edit1, edit2, edit3)) {
            if (!isSuccess(edit)) {
                log.warn("An edit did not report success — see the logged body above. Aborting before done.");
                return Map.of("status", "edit failed", "lastBody", edit.body());
            }
        }

        // 4. Finalize. The flag comes back from done once all three conditions are satisfied.
        var done = client.call(Map.of("action", "done"));
        var flag = done.flag();
        if (flag.isPresent()) {
            log.info("FLAG → {}", flag.get());
            return Map.of("flag", flag.get());
        }
        log.warn("done() returned no flag — a condition is unmet. Feedback: {}", done.body());
        return Map.of("status", "no flag", "feedback", done.body());
    }

    private OkoResponse update(String page, String id, String title, String content, String done) {
        var answer = new LinkedHashMap<String, Object>();
        answer.put("action", "update");
        answer.put("page", page);
        answer.put("id", id);
        if (title != null) {
            answer.put("title", title);
        }
        if (content != null) {
            answer.put("content", content);
        }
        if (done != null) {
            answer.put("done", done);
        }
        return client.call(answer);
    }

    private OkoResponse updateTaskDone(String id, String content) {
        return update(PAGE_ZADANIA, id, null, content, "YES");
    }

    /** The API replies with HTTP 200 + {@code {"status":"success", "code":110, ...}} on a good edit. */
    private boolean isSuccess(OkoResponse resp) {
        try {
            var node = mapper.readTree(resp.body());
            return node.path("status").asText("").equals("success") || node.path("code").asInt(-1) > 0;
        } catch (Exception e) {
            return false; // not JSON we understand — treat as failure and stop before done
        }
    }
}
