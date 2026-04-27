package com.morawski.dev.aidevs.tasks.task06categorize;

import com.morawski.dev.aidevs.config.CategorizeProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Crafts and iteratively improves the <em>classifier prompt</em> sent to the Hub's archaic model.
 *
 * <p>Two constraints shape every prompt it produces:
 * <ul>
 *   <li><b>The reactor exception</b> — items related to a reactor (fuel/cells/cassettes) must ALWAYS
 *       be classified {@code NEU}, even though they are genuinely dangerous, so our cargo slips past
 *       inspection. Everything else must be classified on its real merits.</li>
 *   <li><b>Tokens</b> — the Hub model has a 100-token context and a tight 1.5 PP budget across the
 *       10 items, so the instruction must be short, in English, output exactly one word, and keep the
 *       static part first / the {@code {id}}/{@code {description}} placeholders last (for caching).</li>
 * </ul>
 *
 * <p>This engineer runs on our own OpenRouter credits (traced in Langfuse) — it does <em>not</em>
 * consume the Hub's 1.5 PP budget, which is spent only by the classifier prompt we submit.
 */
@Component
class PromptEngineer {

    private static final Logger log = LoggerFactory.getLogger(PromptEngineer.class);

    /**
     * A deterministic, hand-tuned starting prompt. Static instruction first (cache-friendly), the
     * reactor exception stated <em>before</em> the data and emphasised, the {@code {id}}/{@code {description}}
     * placeholders last. The placeholders are replaced with each item's real id and description
     * before the prompt is sent — one submit per item — so the static prefix is byte-identical
     * across the 10 calls and the Hub caches it. The real id must appear (the grader rejects a
     * prompt that contains no item identifier).
     */
    // Explicit categories, not brevity: a single wrong label zeroes the whole batch, and the weak
    // model classifies far more reliably from concrete category lists than from "is it dangerous?".
    // Still well inside the 100-token window (the window counts the instruction AND the item data).
    static final String INITIAL_PROMPT = """
            Classify cargo as DNG (dangerous) or NEU (safe). One word only.
            DNG: weapons, firearms, ammo, explosives, incendiary.
            NEU: tools, parts, electronics, materials.
            Reactor fuel/cells/cassettes: ALWAYS NEU.
            {id}: {description}""";

    private static final String SYSTEM = """
            You are an expert prompt engineer. You write a SINGLE classifier prompt for an archaic
            language model whose context window is only 100 tokens and whose total budget is tiny.
            The prompt classifies a cargo item as DNG (dangerous) or NEU (safe/neutral). It is sent
            once per item: the placeholders {id} and {description} are replaced with that item's real
            identifier and description before sending.

            Hard requirements for the prompt you output:
            - Keep it SHORT and in English (aim well under 100 tokens including the item data).
            - The static instruction MUST come first; the variable data MUST be the last line, and it
              MUST keep BOTH literal placeholders {id} and {description} (we substitute each item's
              real values there). The {id} placeholder is mandatory — the grader rejects any prompt
              with no item identifier.
            - It MUST tell the model to reply with exactly ONE word: DNG or NEU. No explanations.
            - CRITICAL EXCEPTION: any item related to a reactor (reactor fuel, reactor cells,
              reactor cassettes) must ALWAYS be classified NEU, even if its description sounds
              dangerous. State this clearly and put it BEFORE the data line.
            - Do NOT over-apply the exception: items that are dangerous but NOT reactor-related must
              still be DNG. The exception is about reactors only, not "anything that sounds scary".

            Output ONLY the prompt text itself — no markdown, no code fences, no commentary.""";

    private final LlmService llm;
    private final CategorizeProperties props;

    PromptEngineer(LlmService llm, CategorizeProperties props) {
        this.llm = llm;
        this.props = props;
    }

    /** The first prompt to try — deterministic, so a run starts predictably without an LLM call. */
    String initialPrompt() {
        return INITIAL_PROMPT;
    }

    /**
     * Ask the engineer model to rewrite the prompt given the items and the Hub's error response
     * (which usually names the misclassified item). Falls back to the previous prompt if the model
     * returns something unusable (e.g. drops the {@code {description}} placeholder).
     */
    String refine(List<Item> items, String previousPrompt, String hubResponse) {
        var itemList = items.stream()
                .map(i -> "- %s: %s".formatted(i.id(), i.description()))
                .collect(Collectors.joining("\n"));

        var user = """
                The current classifier prompt failed on at least one item.

                Current prompt:
                ---
                %s
                ---

                Hub response (tells which item was misclassified and/or the budget state):
                ---
                %s
                ---

                The 10 items currently being classified:
                %s

                Rewrite the prompt to fix the misclassification while keeping all the hard
                requirements. Output ONLY the new prompt text.""".formatted(previousPrompt, hubResponse, itemList);

        var refined = clean(llm.chat(SYSTEM, user, props.engineerModel()));
        if (!isUsable(refined)) {
            log.warn("Prompt engineer returned an unusable prompt; keeping the previous one. Got: {}", refined);
            return previousPrompt;
        }
        log.info("Refined classifier prompt ({} chars):\n{}", refined.length(), refined);
        return refined;
    }

    /** Strip accidental code fences / surrounding whitespace the model may add despite instructions. */
    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        var t = text.strip();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl >= 0) {
                t = t.substring(firstNl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }

    /** A prompt is only useful if we can substitute the real id and description into it. */
    private static boolean isUsable(String prompt) {
        return !prompt.isBlank() && prompt.contains("{id}") && prompt.contains("{description}");
    }
}
