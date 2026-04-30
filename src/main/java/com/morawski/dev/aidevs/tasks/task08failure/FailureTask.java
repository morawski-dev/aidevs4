package com.morawski.dev.aidevs.tasks.task08failure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.FailureProperties;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * S03E05 ({@code failure}) — a plant suffered a failure and we hold a huge system log from that day.
 * We must send the Centrala a <em>condensed</em> log that (1) keeps only events relevant to a
 * root-cause analysis, (2) fits within 1500 tokens, and (3) stays one-event-per-line with a
 * {@code YYYY-MM-DD HH:MM} timestamp, a severity level and the subsystem id on every line. If the
 * technicians can run their analysis the Hub returns {@code {FLG:...}}; otherwise they reply with
 * precise feedback (which subsystem is missing/unclear, or that we blew the token limit), which we
 * use to augment and resend.
 *
 * <p>The heavy lifting is deterministic and LLM-free: {@link LogFilter} streams the raw file, drops
 * INFO/DEBUG noise, normalizes timestamps and deduplicates the templated messages down to a few dozen
 * distinct events covering every subsystem and the full WARN→ERRO→CRIT escalation. {@link TokenCounter}
 * gates every submission against the budget; {@link LogCompressor} (a cheap model) is only a fallback
 * to paraphrase descriptions under budget when trimming alone doesn't fit. The task drives the whole
 * submit→read-feedback→augment loop itself, so it is {@link #selfSubmitting() self-submitting}.
 */
@Component
class FailureTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(FailureTask.class);

    private final HubClient hub;
    private final FailureClient client;
    private final LogFilter filter;
    private final TokenCounter counter;
    private final LogCompressor compressor;
    private final FailureProperties props;
    private final ObjectMapper mapper;

    FailureTask(HubClient hub, FailureClient client, LogFilter filter, TokenCounter counter,
                LogCompressor compressor, FailureProperties props, ObjectMapper mapper) {
        this.hub = hub;
        this.client = client;
        this.filter = filter;
        this.counter = counter;
        this.compressor = compressor;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "failure";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "failure.solve")
    public Object solve() {
        var raw = hub.downloadData(props.logFile());
        var events = filter.parseAndFilter(raw);

        // FULL rendering is the canonical material we keep in memory for re-fitting / handing to the
        // LLM when the technicians ask us to rebalance details. It's small (a few dozen lines).
        log.info("failure: full deduped set = {} lines, {} tokens (budget {})",
                events.size(), counter.count(render(events, Detail.FULL, Set.of())), counter.budget());

        // Two knobs the feedback turns: a global detail tier (raised when nothing specific is named)
        // and a set of subsystems the technicians explicitly couldn't analyse (always rendered full).
        var globalDetail = leanestFittingTier(events);
        var detailed = new LinkedHashSet<String>();

        for (int iter = 1; iter <= props.maxIterations(); iter++) {
            var condensed = ensureFits(render(events, globalDetail, detailed), events, detailed);
            log.info("failure iteration {}/{}: submitting {} lines, {} tokens (detail={}, full-detail subsystems={})",
                    iter, props.maxIterations(), condensed.lines().count(), counter.count(condensed),
                    globalDetail, detailed);

            var resp = client.submitLogs(condensed);

            var flag = resp.flag();
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "iterations", iter);
            }

            var feedback = message(resp.body());
            if (looksLikeLimitError(feedback)) {
                // Centrala's tokenizer is denser than ours — back off our local budget so the next
                // render is leaner, then retry. (ensureFits also compresses as a hard backstop.)
                log.warn("failure: Centrala reported a size/limit problem — lowering detail and retrying");
                globalDetail = leaner(globalDetail);
                if (!detailed.isEmpty() && globalDetail == Detail.SHORT) {
                    detailed.clear(); // we've exhausted the cheap detail; drop targeted full-detail too
                }
            } else {
                // Incompleteness feedback names the subsystem(s) the technicians couldn't analyse.
                // Map them onto our events and render those full; if none are named, raise the global tier.
                var named = namedSubsystems(feedback);
                if (named.isEmpty()) {
                    log.info("failure: incompleteness feedback names no known subsystem — raising global detail");
                    globalDetail = richer(globalDetail);
                } else {
                    log.info("failure: technicians need more detail on {} — rendering those events full", named);
                    detailed.addAll(named);
                }
            }
        }

        var last = render(events, globalDetail, detailed);
        log.warn("failure: no flag after {} iterations. Last condensed log ({} tokens):\n{}",
                props.maxIterations(), counter.count(last), last);
        return Map.of("status", "no flag", "iterations", props.maxIterations());
    }

    /** The leanest→richest detail tier scan, returning the richest rendering that still fits the budget. */
    private Detail leanestFittingTier(List<LogEvent> events) {
        for (var detail : Detail.values()) { // FULL, HYBRID, SHORT (richest first)
            if (counter.fits(render(events, detail, Set.of()))) {
                log.info("failure: starting detail tier = {}", detail);
                return detail;
            }
        }
        return Detail.SHORT; // even SHORT is over budget — ensureFits will LLM-compress before submit
    }

    /**
     * Guarantee the candidate is within budget before any submission. The deterministic dedup gets us
     * close but the leanest render still tops the Centrala's real limit, so the LLM compressor trims
     * the rest — steered to keep the FULL causal detail of any subsystem the technicians flagged, so a
     * round of compression can't silently drop the very thing they asked for (which made convergence
     * luck-dependent otherwise).
     */
    private String ensureFits(String condensed, List<LogEvent> events, Set<String> detailed) {
        if (counter.fits(condensed)) {
            return condensed;
        }
        log.warn("failure: candidate over budget ({} tokens) — compressing before submit",
                counter.count(condensed));
        var base = condensed.isBlank() ? render(events, Detail.SHORT, Set.of()) : condensed;
        var hint = detailed.isEmpty() ? null
                : "Keep the FULL causal detail (all sentences) for these subsystems: " + String.join(", ", detailed);
        return compressor.compress(base, hint); // best effort; logged downstream
    }

    /** Detail tiers ordered richest → leanest. */
    private enum Detail { FULL, HYBRID, SHORT }

    private static Detail richer(Detail d) {
        return switch (d) {
            case SHORT -> Detail.HYBRID;
            case HYBRID, FULL -> Detail.FULL;
        };
    }

    private static Detail leaner(Detail d) {
        return switch (d) {
            case FULL -> Detail.HYBRID;
            case HYBRID, SHORT -> Detail.SHORT;
        };
    }

    /**
     * Render every event one-per-line. An event is rendered FULL when the global tier says so, or when
     * its subsystem was explicitly flagged by the technicians; otherwise it's trimmed to its first
     * sentence. All subsystems always stay represented — only the description length varies.
     */
    private static String render(List<LogEvent> events, Detail global, Set<String> detailedSubsystems) {
        var sb = new StringBuilder();
        for (var e : events) {
            boolean full = global == Detail.FULL
                    || (global == Detail.HYBRID && isSevere(e.level()))
                    || detailedSubsystems.contains(e.subsystem());
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(full ? e.renderFull() : e.renderShort());
        }
        return sb.toString();
    }

    /** Known subsystem ids explicitly mentioned in the technicians' feedback. */
    private List<String> namedSubsystems(String feedback) {
        var upper = feedback.toUpperCase(Locale.ROOT);
        return props.subsystems().stream()
                .filter(s -> upper.contains(s.toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static boolean isSevere(String level) {
        return level.equalsIgnoreCase("CRIT") || level.equalsIgnoreCase("ERRO")
                || level.equalsIgnoreCase("ERROR");
    }

    /** Pull the Hub's {@code message} out of the JSON body, falling back to the raw body. */
    private String message(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            var node = mapper.readTree(body);
            var msg = node.path("message");
            return msg.isMissingNode() ? body : msg.asText(body);
        } catch (Exception e) {
            return body;
        }
    }

    /** Heuristic: does the feedback complain about size/token limit rather than missing content? */
    private static boolean looksLikeLimitError(String feedback) {
        var f = feedback.toLowerCase(Locale.ROOT);
        return f.contains("token") || f.contains("1500") || f.contains("limit")
                || f.contains("too long") || f.contains("too large") || f.contains("exceed");
    }
}
