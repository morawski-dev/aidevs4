package com.morawski.dev.aidevs.tasks.task25timetravel;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Wskaźnik temporalny ({@code sync ratio}) liczony z daty docelowej, zgodnie z dokumentacją
 * CHRONOS-P1 (<a href="https://hub.ag3nts.org/dane/timetravel.md">timetravel.md</a>).
 *
 * <p>Reguła z dokumentacji: zsumuj iloczyny składowych daty przez ich wagi
 * ({@code dzień×8 + miesiąc×12 + rok×7}), a wynik podziel modulo {@code 101}. Otrzymana liczba
 * {@code 0..100} to wskaźnik temporalny. API przyjmuje go jako liczbę dziesiętną z przedziału
 * {@code 0..1} z dokładnością do dwóch miejsc po przecinku: {@code 0 → 0.00}, {@code 37 → 0.37},
 * {@code 100 → 1.00}.
 *
 * <p>Czysta logika — bez sieci/LLM; testowana w {@code SyncRatioTest}.
 */
final class SyncRatio {

    private static final int MODULO = 101;
    static final int DAY_WEIGHT = 8;
    static final int MONTH_WEIGHT = 12;
    static final int YEAR_WEIGHT = 7;

    private SyncRatio() {
    }

    /** Surowy wskaźnik temporalny {@code 0..100} (przed konwersją na ułamek). */
    static int raw(int day, int month, int year) {
        int sum = day * DAY_WEIGHT + month * MONTH_WEIGHT + year * YEAR_WEIGHT;
        return Math.floorMod(sum, MODULO);
    }

    static int raw(LocalDate date) {
        return raw(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    /**
     * Wartość gotowa do wysłania przez API: ułamek {@code 0.00..1.00} (skala 2), więc serializuje się
     * jako {@code 0.82}, {@code 1.00}, {@code 0.05}. {@link BigDecimal} gwarantuje dwa miejsca po
     * przecinku (np. {@code 0.50}, nie {@code 0.5}).
     */
    static BigDecimal forDate(LocalDate date) {
        return new BigDecimal(raw(date)).movePointLeft(2);
    }

    /** Tekstowa postać do instrukcji dla operatora (np. {@code "0.82"}). */
    static String formatted(LocalDate date) {
        return forDate(date).toPlainString();
    }
}
