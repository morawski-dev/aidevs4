package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Deterministic 2-decimal formatting of the city area. The brief is explicit that {@code cityArea}
 * must be a real mathematical rounding (HALF_UP), not a truncation, and look like {@code "12.34"}.
 * We never trust the model to round — it returns the raw value and this pure helper rounds it.
 */
final class AreaFormat {

    private AreaFormat() {
    }

    /**
     * Round a raw area string to exactly two decimals, HALF_UP. Tolerates a decimal comma and stray
     * non-numeric characters (units, spaces) from the source text.
     *
     * @throws IllegalArgumentException if {@code raw} contains no parseable number.
     */
    static String round2(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("area is null");
        }
        // Normalise decimal comma, then keep only digits, dot and a leading sign.
        String cleaned = raw.trim().replace(',', '.').replaceAll("[^0-9.\\-]", "");
        // Collapse to the first well-formed number (handles e.g. "area 12.34 km2").
        var m = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(cleaned);
        if (!m.find()) {
            throw new IllegalArgumentException("no parseable number in area: '" + raw + "'");
        }
        return new BigDecimal(m.group()).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
