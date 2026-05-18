package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.morawski.dev.aidevs.config.NegotiationsProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import com.morawski.dev.aidevs.tasks.task14negotiations.Catalog.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves the agent's free-text item request to a single catalog item code in two stages:
 * <ol>
 *   <li><b>Lexical pre-filter</b> ({@link #shortlist}) — pure, deterministic, unit-tested: rank items by
 *       diacritics-insensitive token overlap with the request and keep the top few candidates. This cuts
 *       the ~2000-item catalog down to a handful so the LLM prompt stays small.</li>
 *   <li><b>LLM disambiguation</b> — a strong model picks the single best candidate (or none). Needed
 *       because the agent paraphrases heavily ("kabel długości 10 metrów" vs the exact catalog name) and
 *       must read units/negation semantically, which token matching alone can't.</li>
 * </ol>
 */
@Component
class ItemMatcher {

    private static final Logger log = LoggerFactory.getLogger(ItemMatcher.class);

    private static final String SYSTEM = """
            Jesteś precyzyjnym asystentem katalogu części. Otrzymasz zapytanie użytkownika w języku
            naturalnym (po polsku) oraz ponumerowaną listę kandydatów z katalogu, każdy z polem 'code'.
            Wybierz DOKŁADNIE JEDEN element, który najlepiej odpowiada zapytaniu, i zwróć jego 'code'.
            Czytaj znaczenie (typ części, wartości, jednostki), nie tylko słowa kluczowe. Jeśli żaden
            kandydat naprawdę nie pasuje do zapytania, ustaw found=false i pozostaw code puste.
            Nie wymyślaj kodów spoza listy.
            """;

    private final LlmService llm;
    private final NegotiationsProperties props;

    ItemMatcher(LlmService llm, NegotiationsProperties props) {
        this.llm = llm;
        this.props = props;
    }

    Optional<String> match(String query, Catalog catalog) {
        if (!StringUtils.hasText(query)) {
            return Optional.empty();
        }
        var candidates = shortlist(query, catalog.items(), Math.max(1, props.shortlistSize()));
        if (candidates.isEmpty()) {
            log.info("No lexical candidates for query: {}", query);
            return Optional.empty();
        }

        var allowed = candidates.stream().map(Item::code).collect(Collectors.toSet());
        var match = llm.extract(SYSTEM, userPrompt(query, candidates), props.matchModel(), ItemMatch.class);
        if (match == null || !match.found() || !allowed.contains(match.code())) {
            log.info("LLM found no match for query '{}' (candidates={}, returned={})",
                    query, allowed.size(), match);
            return Optional.empty();
        }
        var picked = candidates.stream().filter(i -> i.code().equals(match.code())).findFirst().orElseThrow();
        log.info("Matched '{}' -> [{}] {}", query, picked.code(), picked.name());
        return Optional.of(picked.code());
    }

    /**
     * Top-{@code size} catalog items by token overlap with {@code query}, best first. An exact token hit
     * scores 2; a partial (one token is a substring of the other, length ≥ 3) scores 1. Items with no
     * overlap are dropped; ties break toward the shorter, then alphabetically-earlier name (more specific,
     * stable ordering). Pure function — no I/O — so it's unit-testable.
     */
    static List<Item> shortlist(String query, List<Item> items, int size) {
        var qTokens = Tokens.of(query);
        if (qTokens.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(item -> new Scored(item, score(qTokens, item.tokens())))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingInt(s -> s.item().name().length())
                        .thenComparing(s -> s.item().name()))
                .limit(size)
                .map(Scored::item)
                .toList();
    }

    private static double score(Set<String> qTokens, Set<String> itemTokens) {
        double s = 0;
        for (var qt : qTokens) {
            if (itemTokens.contains(qt)) {
                s += 2;
            } else if (qt.length() >= 3 && itemTokens.stream()
                    .anyMatch(it -> it.length() >= 3 && (it.contains(qt) || qt.contains(it)))) {
                // Partial only between sufficiently long tokens, so 1-char units ("w", "v") don't
                // spuriously match unrelated words (e.g. "osobowy".contains("w")).
                s += 1;
            }
        }
        return s;
    }

    private static String userPrompt(String query, List<Item> candidates) {
        var sb = new StringBuilder("Zapytanie: ").append(query).append("\n\nKandydaci:\n");
        int i = 1;
        for (var c : candidates) {
            sb.append(i++).append(". code=").append(c.code()).append("  ").append(c.name()).append('\n');
        }
        return sb.toString();
    }

    private record Scored(Item item, double score) {
    }
}
