package com.morawski.dev.aidevs.tasks.task08failure;

/**
 * One parsed (and deduplicated) log event from {@code failure.log}.
 *
 * <p>The raw file repeats the same templated message hundreds of times, so {@link LogFilter}
 * collapses identical {@code (level, message)} pairs into a single {@code LogEvent}, remembering the
 * <em>first</em> occurrence (genesis of the anomaly) plus how many times it recurred and when it was
 * last seen — enough to reconstruct the causal chain without keeping thousands of duplicate lines.
 *
 * @param date        normalized {@code YYYY-MM-DD}
 * @param time        normalized {@code HH:MM} of the first occurrence
 * @param level       severity ({@code WARN}/{@code ERRO}/{@code CRIT}/...)
 * @param subsystem   detected plant subsystem id (e.g. {@code ECCS8}, {@code WTANK07}), or {@code UNKNOWN}
 * @param description full message text (one or more sentences), subsystem id embedded inside it
 * @param count       how many raw lines collapsed into this event
 * @param lastTime    {@code HH:MM} of the last occurrence (equals {@code time} when {@code count == 1})
 */
record LogEvent(
        String date,
        String time,
        String level,
        String subsystem,
        String description,
        int count,
        String lastTime
) {

    /** Full condensed line: {@code [date time] [LEVEL] <full description>}. */
    String renderFull() {
        return "[%s %s] [%s] %s".formatted(date, time, level, description);
    }

    /**
     * Lean condensed line: same timestamp/level but only the first sentence of the description
     * (which always carries the subsystem id and the anomaly), dropping the trailing commentary.
     */
    String renderShort() {
        return "[%s %s] [%s] %s".formatted(date, time, level, firstSentence(description));
    }

    /** The first sentence (up to and including the first period), or the whole text if none. */
    static String firstSentence(String text) {
        int dot = text.indexOf('.');
        return dot < 0 ? text : text.substring(0, dot + 1);
    }
}
