package com.morawski.dev.aidevs.tasks.task06categorize;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.common.CsvReader;
import com.morawski.dev.aidevs.config.CategorizeProperties;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S02 ({@code categorize}) — write a single classifier prompt that an archaic, 100-token model
 * applies to 10 cargo items, labelling each DNG (dangerous) or NEU (safe). The twist: items related
 * to the reactor (our cargo) must ALWAYS come out NEU even though they are dangerous, so the
 * transport slips past inspection. When all 10 are classified as intended the Hub returns {@code {FLG:...}}.
 *
 * <p>It is an iterative loop, not a one-shot submit. Each attempt is a clean batch: {@code reset}
 * (renews the 1.5 PP balance) → fetch fresh CSV → submit one prompt <em>per item</em> with that
 * item's real id and description substituted in (the Hub does not substitute placeholders, and it
 * rejects a prompt that carries no item identifier). After all 10 are classified as intended the
 * flag appears in one of the responses, so the task is {@link #selfSubmitting() self-submitting}
 * and logs its own flag. If the batch fails, {@link PromptEngineer#refine} rewrites the prompt from
 * the per-item responses and the next attempt retries the whole batch.
 */
@Component
class CategorizeTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(CategorizeTask.class);

    private final CategorizeClient client;
    private final PromptEngineer engineer;
    private final HubClient hub;
    private final CategorizeProperties props;
    private final ObjectMapper mapper;

    CategorizeTask(CategorizeClient client, PromptEngineer engineer, HubClient hub,
                   CategorizeProperties props, ObjectMapper mapper) {
        this.client = client;
        this.engineer = engineer;
        this.hub = hub;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "categorize";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "categorize.solve")
    public Object solve() {
        var prompt = engineer.initialPrompt();

        for (int attempt = 1; attempt <= props.maxIterations(); attempt++) {
            // reset renews the balance to 1.5 and clears the session, so every attempt is a clean
            // full batch of 10. Fetch a fresh CSV each time — the file rotates every few minutes.
            client.reset();
            var items = fetchItems();
            log.info("categorize attempt {}/{} — classifying {} items one prompt each",
                    attempt, props.maxIterations(), items.size());

            var feedback = new StringBuilder();
            var flag = Optional.<String>empty();
            for (var item : items) {
                var resp = client.submitPrompt(fill(prompt, item));
                flag = resp.flag();
                if (flag.isPresent()) {
                    break;
                }
                feedback.append("- ").append(item.id()).append(": ").append(item.description())
                        .append("\n  -> ").append(oneLine(resp.body())).append('\n');
                // A wrong classification zeroes the balance and resets progress; a context overflow
                // means the prompt is too long. Either way the batch is dead — stop and refine,
                // rather than burning the rest of the items on a doomed/zero-balance session.
                if (!accepted(resp.body())) {
                    log.warn("categorize: batch aborted at item {} (not accepted) — refining and retrying",
                            item.id());
                    break;
                }
            }

            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "attempts", attempt);
            }

            if (attempt == props.maxIterations()) {
                break; // last batch done — no point refining a prompt we won't submit
            }
            prompt = engineer.refine(items, prompt, feedback.toString());
        }

        log.warn("categorize: no flag after {} attempts. Inspect the logged Hub responses — the "
                + "classifier prompt may need tuning or the budget ran out.", props.maxIterations());
        return Map.of("status", "no flag", "attempts", props.maxIterations());
    }

    /**
     * Whether the Hub accepted a per-item submit. {@code code >= 0} means progress (1 = item
     * accepted, 0 = final accept carrying the flag); negative codes are errors — a wrong
     * classification ({@code -890}, which zeroes the balance) or a context overflow ({@code -920}).
     */
    private boolean accepted(String body) {
        try {
            return mapper.readTree(body).path("code").asInt(-1) >= 0;
        } catch (Exception e) {
            return false; // unparseable — treat as a stop
        }
    }

    /** Substitute one item's real id and description into the prompt template before sending. */
    private static String fill(String template, Item item) {
        return template.replace("{id}", item.id()).replace("{description}", item.description());
    }

    /** Always pull a fresh CSV — the file rotates every few minutes, so a cached copy goes stale. */
    private List<Item> fetchItems() {
        var data = hub.downloadData(props.csvFile());
        var records = CsvReader.read(data);
        var items = records.stream().map(CategorizeTask::toItem).toList();
        log.info("categorize: loaded {} items from {}", items.size(), props.csvFile());
        items.forEach(i -> log.info("  item {} -> {}", i.id(), i.description()));
        return items;
    }

    /** Read by column index — the header names aren't documented, but it's always (identifier, description). */
    private static Item toItem(CSVRecord record) {
        var id = record.get(0).trim();
        var description = record.size() > 1 ? record.get(1).trim() : "";
        return new Item(id, description);
    }

    /** Collapse a response body to a single line so the per-item feedback stays compact for the engineer. */
    private static String oneLine(String body) {
        return body == null ? "" : body.replaceAll("\\s+", " ").trim();
    }
}
