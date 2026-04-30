package com.morawski.dev.aidevs.tasks.task08failure;

import com.morawski.dev.aidevs.config.FailureProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * LLM fallback that paraphrases an already-filtered, deduplicated log into the token budget while
 * preserving the three mandatory elements of every line ({@code [date time]}, {@code [LEVEL]},
 * subsystem id) and one event per line. The cheap {@code compressModel} sees only the pre-sieved set
 * (a few dozen lines), never the raw file — it shortens descriptions, it does not choose what to keep
 * (the deterministic {@link LogFilter} already did the choosing).
 *
 * <p>Only used when deterministic dedup + first-sentence trimming still exceeds the budget, or when
 * the technicians' feedback asks us to rebalance which details survive. Loops a few times, tightening
 * the target, until {@link TokenCounter#fits} holds.
 */
@Component
class LogCompressor {

    private static final Logger log = LoggerFactory.getLogger(LogCompressor.class);
    private static final int MAX_ROUNDS = 4;

    private static final String SYSTEM = """
            You compress power-plant system logs for a root-cause analysis. You are given log lines,
            one event per line, in the form:
              [YYYY-MM-DD HH:MM] [LEVEL] description
            Rewrite them shorter so the WHOLE output fits within %d tokens. Hard rules:
            - Keep EVERY line — never drop a line or merge two events into one.
            - Preserve on each line, exactly: the [YYYY-MM-DD HH:MM] timestamp, the [LEVEL] tag,
              and the subsystem identifier (e.g. ECCS8, WTANK07, WTRPMP, PWR01, STMTURB12, WSTPOOL2,
              FIRMWARE) that appears in the description.
            - You may shorten/paraphrase the rest of the description (drop filler, keep the symptom
              and any trip/shutdown action).
            - Every plant subsystem present in the input MUST remain represented in the output.
            - Output ONLY the log lines, one per line, no commentary, no code fences, no numbering.
            """;

    private final LlmService llm;
    private final TokenCounter counter;
    private final FailureProperties props;

    LogCompressor(LlmService llm, TokenCounter counter, FailureProperties props) {
        this.llm = llm;
        this.counter = counter;
        this.props = props;
    }

    /** Compress {@code logs} until it fits the budget (or rounds run out). */
    String compress(String logs) {
        return compress(logs, null);
    }

    /**
     * Compress {@code logs} into budget, optionally steered by technician {@code feedback} (e.g. which
     * subsystem they couldn't analyse) so the rewrite keeps/restores exactly what they asked for.
     */
    String compress(String logs, String feedback) {
        var current = logs;
        for (int round = 1; round <= MAX_ROUNDS; round++) {
            int target = Math.max(200, props.tokenBudget() - round * 50); // tighten each round
            var user = new StringBuilder();
            if (StringUtils.hasText(feedback)) {
                user.append("The technicians could not complete the analysis. Their feedback:\n")
                        .append(feedback.trim())
                        .append("\nMake sure the issues they raised are clearly represented.\n\n");
            }
            user.append("Log lines to compress (current count = ")
                    .append(current.lines().count()).append("):\n").append(current);

            var out = clean(llm.chat(SYSTEM.formatted(target), user.toString(), props.compressModel()));
            int tokens = counter.count(out);
            log.info("LogCompressor round {}/{}: target {} tok -> {} lines, {} tokens",
                    round, MAX_ROUNDS, target, out.lines().count(), tokens);

            if (counter.fits(out)) {
                return out;
            }
            current = out; // feed the shorter version back in and tighten further
        }
        log.warn("LogCompressor: still over budget after {} rounds — returning best effort", MAX_ROUNDS);
        return current;
    }

    /** Strip code fences / stray blank lines an LLM sometimes wraps around the output. */
    private static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.lines()
                .map(String::strip)
                .filter(l -> !l.isEmpty() && !l.startsWith("```"))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
