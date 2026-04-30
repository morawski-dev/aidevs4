package com.morawski.dev.aidevs.tasks.task08failure;

import com.morawski.dev.aidevs.config.FailureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, LLM-free first pass over the raw {@code failure.log}: parse every line, drop the
 * noise levels (INFO/DEBUG/TRACE), normalize the timestamp to {@code YYYY-MM-DD HH:MM}, detect the
 * plant subsystem, and <em>deduplicate</em> the heavily templated messages into one {@link LogEvent}
 * per distinct {@code (level, message)} — keeping the first occurrence (genesis) plus an occurrence
 * count and last-seen time.
 *
 * <p>This is where the bulk of the compression happens: the file's thousands of lines collapse to a
 * few dozen distinct events that still cover every subsystem and the full WARN→ERRO→CRIT escalation.
 * Pure, side-effect-free logic (beyond logging) — unit-testable without network or LLM.
 */
@Component
class LogFilter {

    private static final Logger log = LoggerFactory.getLogger(LogFilter.class);

    /** {@code [2026-06-09 06:04:13] [CRIT] <message>} — seconds are dropped during normalization. */
    private static final Pattern LINE = Pattern.compile(
            "^\\[(\\d{4}-\\d{2}-\\d{2}) (\\d{2}):(\\d{2}):\\d{2}]\\s*\\[([A-Z]+)]\\s*(.*)$");

    private final FailureProperties props;

    LogFilter(FailureProperties props) {
        this.props = props;
    }

    /** Parse the raw bytes, keep only the configured severity levels, and deduplicate. */
    List<LogEvent> parseAndFilter(byte[] raw) {
        var text = new String(raw, StandardCharsets.UTF_8);
        var lines = text.split("\\R");

        // LinkedHashMap preserves first-seen order → events come out in chronological genesis order.
        var byKey = new LinkedHashMap<String, Accumulator>();
        int parsed = 0;
        int kept = 0;
        var levelCounts = new TreeMap<String, Integer>();

        for (var line : lines) {
            if (line.isBlank()) {
                continue;
            }
            var m = LINE.matcher(line.trim());
            if (!m.matches()) {
                continue; // not a standard log line — skip (no multi-line stack traces in this file)
            }
            parsed++;
            var date = m.group(1);
            var time = m.group(2) + ":" + m.group(3); // HH:MM
            var level = m.group(4);
            var message = m.group(5).trim();

            if (!keepLevel(level)) {
                continue;
            }
            kept++;
            levelCounts.merge(level, 1, Integer::sum);

            var key = level + "|" + message;
            var acc = byKey.get(key);
            if (acc == null) {
                byKey.put(key, new Accumulator(date, time, level, detectSubsystem(message), message));
            } else {
                acc.count++;
                acc.lastTime = time;
            }
        }

        var events = new ArrayList<LogEvent>(byKey.size());
        var subsystemCounts = new TreeMap<String, Integer>();
        for (var acc : byKey.values()) {
            events.add(acc.toEvent());
            subsystemCounts.merge(acc.subsystem, 1, Integer::sum);
        }

        log.info("LogFilter: {} total lines, {} parsed, {} kept (levels {}), {} distinct events after dedup",
                lines.length, parsed, kept, levelCounts, events.size());
        log.info("LogFilter: distinct events per subsystem: {}", subsystemCounts);
        return events;
    }

    private boolean keepLevel(String level) {
        for (var keep : props.levels()) {
            if (keep.equalsIgnoreCase(level)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the plant subsystem a message refers to: prefer the first <em>known</em> subsystem id that
     * appears in the text (earliest position wins), falling back to the first uppercase token of 4+
     * chars, then {@code UNKNOWN}. Keeps coverage reporting and feedback mapping reliable.
     */
    String detectSubsystem(String message) {
        var upper = message.toUpperCase(Locale.ROOT);
        String best = null;
        int bestIdx = Integer.MAX_VALUE;
        for (var sub : props.subsystems()) {
            int idx = upper.indexOf(sub.toUpperCase(Locale.ROOT));
            if (idx >= 0 && idx < bestIdx) {
                bestIdx = idx;
                best = sub;
            }
        }
        if (best != null) {
            return best;
        }
        Matcher token = FALLBACK_ID.matcher(message);
        return token.find() ? token.group() : "UNKNOWN";
    }

    private static final Pattern FALLBACK_ID = Pattern.compile("[A-Z][A-Z0-9]{3,}");

    /** Mutable per-key tally used while folding duplicate lines together. */
    private static final class Accumulator {
        final String date;
        final String time;
        final String level;
        final String subsystem;
        final String description;
        int count = 1;
        String lastTime;

        Accumulator(String date, String time, String level, String subsystem, String description) {
            this.date = date;
            this.time = time;
            this.level = level;
            this.subsystem = subsystem;
            this.description = description;
            this.lastTime = time;
        }

        LogEvent toEvent() {
            return new LogEvent(date, time, level, subsystem, description, count, lastTime);
        }
    }

    /** Group distinct events by subsystem (insertion/chronological order preserved within each group). */
    static Map<String, List<LogEvent>> bySubsystem(List<LogEvent> events) {
        var map = new LinkedHashMap<String, List<LogEvent>>();
        for (var e : events) {
            map.computeIfAbsent(e.subsystem(), k -> new ArrayList<>()).add(e);
        }
        return map;
    }
}
