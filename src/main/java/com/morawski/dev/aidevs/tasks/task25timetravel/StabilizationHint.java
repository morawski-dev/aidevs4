package com.morawski.dev.aidevs.tasks.task25timetravel;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * Rozwiązuje podpowiedź {@code stabilization}, którą API zwraca w polu {@code needConfig} po ustawieniu
 * pełnej daty. Podpowiedź jest <b>słowną łamigłówką po polsku</b>: podaje wartość bazową i korektę, np.
 *
 * <ul>
 *   <li>„…sugerują zwykle <b>dziewięćset</b> jednostek… <b>obniżenie</b> poziomu o <b>siedemset
 *       jedenaście</b>" → 900 − 711 = <b>189</b></li>
 *   <li>„…<b>siedemset</b> punktów… <b>odjąć sześćset osiemdziesiąt cztery</b>" → 700 − 684 = <b>16</b></li>
 *   <li>„…poziom <b>sześćset</b>… <b>zwiększyć</b> tę nastawę o <b>395</b>" → 600 + 395 = <b>995</b></li>
 * </ul>
 *
 * Bierzemy dwie pierwsze liczby (słownie lub cyframi): pierwsza to baza, druga to korekta; znak korekty
 * wynika ze słów kluczowych (obniżyć/odjąć/zmniejszyć = minus; zwiększyć/dodać/podwyższyć = plus).
 *
 * <p>Czysta logika, deterministyczna (bez LLM) — testowana w {@code StabilizationHintTest}.
 */
final class StabilizationHint {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Map<String, Integer> WORDS = numberWords();
    // Diakrytyki usuwane przed dopasowaniem: "obniżenie"→"obnizenie", "zwiększyć"→"zwiekszyc".
    private static final String[] SUBTRACT = {"obniz", "odejm", "odj", "zmniejsz", "mniej", "minus", "pomniejsz", "redukc", "redukuj"};
    private static final String[] ADD = {"zwieksz", "dodac", "dodaj", "dodanie", "wiecej", "podwyzsz", "plus", "powieksz", "podnie", "zwiekszenie"};

    private StabilizationHint() {
    }

    /** Wyliczona wartość stabilization z tekstu podpowiedzi, lub pusto gdy nie da się jej odczytać. */
    static OptionalInt resolve(String text) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        String norm = normalize(text);
        var numbers = extractNumbers(norm);
        if (numbers.isEmpty()) {
            return OptionalInt.empty();
        }
        int base = numbers.get(0);
        if (numbers.size() == 1) {
            return OptionalInt.of(base);          // brak korekty — sama wartość bazowa
        }
        int delta = numbers.get(1);
        // Subtract on an explicit "lower/subtract" keyword; otherwise treat the correction as additive.
        boolean subtract = containsAny(norm, SUBTRACT);
        return OptionalInt.of(subtract ? base - delta : base + delta);
    }

    /** Kolejne liczby w tekście — słowne ciągi są sumowane (np. „siedemset jedenaście"=711), cyfry brane wprost. */
    private static java.util.List<Integer> extractNumbers(String norm) {
        var numbers = new java.util.ArrayList<Integer>();
        int current = 0;
        boolean inWordNumber = false;
        for (String token : norm.split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if (DIGITS.matcher(token).matches()) {
                if (inWordNumber) {
                    numbers.add(current);
                    current = 0;
                    inWordNumber = false;
                }
                numbers.add(Integer.parseInt(token));
            } else if (WORDS.containsKey(token)) {
                current += WORDS.get(token);
                inWordNumber = true;
            } else if (inWordNumber) {
                numbers.add(current);
                current = 0;
                inWordNumber = false;
            }
        }
        if (inWordNumber) {
            numbers.add(current);
        }
        return numbers;
    }

    private static boolean containsAny(String norm, String[] needles) {
        for (String n : needles) {
            if (norm.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** Lower-case, ł→l, usuń pozostałe diakrytyki (NFD + usunięcie znaków łączących). */
    private static String normalize(String text) {
        String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private static Map<String, Integer> numberWords() {
        var w = new HashMap<String, Integer>();
        w.put("zero", 0);
        w.put("jeden", 1);
        w.put("jedna", 1);
        w.put("dwa", 2);
        w.put("dwie", 2);
        w.put("trzy", 3);
        w.put("cztery", 4);
        w.put("piec", 5);
        w.put("szesc", 6);
        w.put("siedem", 7);
        w.put("osiem", 8);
        w.put("dziewiec", 9);
        w.put("dziesiec", 10);
        w.put("jedenascie", 11);
        w.put("dwanascie", 12);
        w.put("trzynascie", 13);
        w.put("czternascie", 14);
        w.put("pietnascie", 15);
        w.put("szesnascie", 16);
        w.put("siedemnascie", 17);
        w.put("osiemnascie", 18);
        w.put("dziewietnascie", 19);
        w.put("dwadziescia", 20);
        w.put("trzydziesci", 30);
        w.put("czterdziesci", 40);
        w.put("piecdziesiat", 50);
        w.put("szescdziesiat", 60);
        w.put("siedemdziesiat", 70);
        w.put("osiemdziesiat", 80);
        w.put("dziewiecdziesiat", 90);
        w.put("sto", 100);
        w.put("dwiescie", 200);
        w.put("trzysta", 300);
        w.put("czterysta", 400);
        w.put("piecset", 500);
        w.put("szescset", 600);
        w.put("siedemset", 700);
        w.put("osiemset", 800);
        w.put("dziewiecset", 900);
        w.put("tysiac", 1000);
        return Map.copyOf(w);
    }
}
