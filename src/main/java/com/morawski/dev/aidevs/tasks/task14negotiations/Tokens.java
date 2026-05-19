package com.morawski.dev.aidevs.tasks.task14negotiations;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Diacritics-insensitive tokeniser used for the lexical pre-filter. Lower-cases, strips Polish accents
 * (NFD + drop combining marks, so "łopatki"→"lopatki", "1 Ω"→"1") and splits on any non-alphanumeric
 * run. Pure and deterministic, so the ranking it feeds is unit-testable without the network or an LLM.
 */
final class Tokens {

    private static final Pattern COMBINING = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private Tokens() {
    }

    /** Normalised form: lower-case, accents removed, "ł"/"Ł" mapped to "l" (NFD leaves those untouched). */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        var decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        var noMarks = COMBINING.matcher(decomposed).replaceAll("");
        return noMarks.toLowerCase().replace('ł', 'l').replace('Ł', 'l');
    }

    /** Distinct alphanumeric tokens (insertion-ordered), e.g. "Rezystor 1 Ω" → [rezystor, 1]. */
    static Set<String> of(String text) {
        var out = new LinkedHashSet<String>();
        for (var t : NON_ALNUM.split(normalize(text))) {
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }
}
