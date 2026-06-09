package com.morawski.dev.aidevs.tasks.task22phonecall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, negation-aware extraction of the single <em>passable</em> road from the operator's
 * transcribed reply. The operator answers our roads question with a status for each of RD224/RD472/RD820;
 * the partisans need exactly one road that is open.
 *
 * <p>Pure logic (no LLM), so this is the unit-tested piece (see {@code RoadParserTest}). The hard part is
 * negation: "RD224 <b>nie</b> jest przejezdna" and "RD224 jest <b>nie</b>przejezdna" both mean blocked,
 * so a naive keyword match would misread them. When the deterministic pass can't single out exactly one
 * passable road, it returns empty and the caller falls back to the LLM.
 */
final class RoadParser {

    /** The three roads in the brief, in the canonical form we send back to the Hub. */
    static final List<String> ROADS = List.of("RD224", "RD472", "RD820");

    enum Status { PASSABLE, BLOCKED, UNKNOWN }

    // Road code possibly written with a space or hyphen ("RD 224", "RD-472" — both seen from STT);
    // captured digits are normalised to "RD###".
    private static final Pattern ROAD_PATTERN = Pattern.compile("rd[\\s\\-]*(224|472|820)");

    // "open" signals and "closed" signals (diacritic-tolerant substrings, matched on lowercased text).
    private static final List<String> PASSABLE_WORDS = List.of("przejezdn", "otwart", "przejazd", "przejecha", "przejed");
    private static final List<String> BLOCKED_WORDS = List.of(
            "zamkni", "zamkną", "zablokowan", "zniszczon", "uszkodzon", "niedost", "zawalon", "zasypan", "nieczynn");

    private RoadParser() {
    }

    /** The single clearly-passable road code, or empty if zero / more than one / ambiguous. */
    static Optional<String> findPassable(String transcript) {
        var statuses = statuses(transcript);
        var passable = statuses.entrySet().stream()
                .filter(e -> e.getValue() == Status.PASSABLE)
                .map(Map.Entry::getKey)
                .toList();
        return passable.size() == 1 ? Optional.of(passable.get(0)) : Optional.empty();
    }

    /** Per-road status aggregated across every mention of that road in the transcript. */
    static Map<String, Status> statuses(String transcript) {
        var result = new LinkedHashMap<String, Status>();
        ROADS.forEach(r -> result.put(r, Status.UNKNOWN));
        if (transcript == null || transcript.isBlank()) {
            return result;
        }
        String text = transcript.toLowerCase(Locale.ROOT);

        // Split the text into segments, one per road mention (from this mention to the next).
        var mentions = mentions(text);
        for (int i = 0; i < mentions.size(); i++) {
            var m = mentions.get(i);
            int end = (i + 1 < mentions.size()) ? mentions.get(i + 1).start() : text.length();
            String segment = text.substring(m.start(), end);
            result.merge(m.road(), classify(segment), RoadParser::combine);
        }
        return result;
    }

    private static List<Mention> mentions(String text) {
        var out = new java.util.ArrayList<Mention>();
        Matcher matcher = ROAD_PATTERN.matcher(text);
        while (matcher.find()) {
            out.add(new Mention("RD" + matcher.group(1), matcher.start()));
        }
        return out;
    }

    /** Classify one segment as passable/blocked/unknown, treating negated "open" words as blocked. */
    private static Status classify(String segment) {
        int passable = 0;
        int blocked = 0;

        for (String word : PASSABLE_WORDS) {
            int from = 0;
            int idx;
            while ((idx = segment.indexOf(word, from)) >= 0) {
                if (isNegated(segment, idx)) {
                    blocked++; // "nie ... przejezdna" / "nieprzejezdna"
                } else {
                    passable++;
                }
                from = idx + word.length();
            }
        }
        for (String word : BLOCKED_WORDS) {
            if (segment.contains(word)) {
                blocked++;
            }
        }

        if (passable > 0 && blocked == 0) {
            return Status.PASSABLE;
        }
        if (blocked > 0 && passable == 0) {
            return Status.BLOCKED;
        }
        return Status.UNKNOWN; // nothing said, or contradictory signals
    }

    /** True if a standalone "nie" appears shortly before {@code idx} (negating the open-word that follows). */
    private static boolean isNegated(String segment, int idx) {
        int windowStart = Math.max(0, idx - 20);
        String before = segment.substring(windowStart, idx);
        return Pattern.compile("\\bnie\\b").matcher(before).find();
    }

    private static Status combine(Status a, Status b) {
        if (a == b) {
            return a;
        }
        if (a == Status.UNKNOWN) {
            return b;
        }
        if (b == Status.UNKNOWN) {
            return a;
        }
        return Status.UNKNOWN; // PASSABLE vs BLOCKED for the same road → ambiguous
    }

    private record Mention(String road, int start) {
    }
}
