package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tabela zalecanego poziomu ochrony powłoki (suwak {@code PWR}) zależnego od roku docelowego,
 * przepisana 1:1 z dokumentacji CHRONOS-P1 i wczytywana z zasobu
 * {@code timetravel/protection-table.md}. To pozycja suwaka, którą operator ustawia ręcznie
 * w preview (PWR nie jest konfigurowalny przez API).
 *
 * <p>Parser jest tolerancyjny: zbiera każdą parę „rok | ochrona" z markdownowej tabeli (rok
 * {@code 1500..2499}), ignorując nagłówek i separator. Czysta logika — testowana w {@code PwrTableTest}.
 */
final class PwrTable {

    private static final String RESOURCE = "timetravel/protection-table.md";
    private static final int MIN_YEAR = 1500;
    private static final int MAX_YEAR = 2499;
    /** Pary „rok | ochrona": czterocyfrowy rok, kreska pionowa, 1-2 cyfry ochrony. */
    private static final Pattern PAIR = Pattern.compile("(\\d{4})\\s*\\|\\s*(\\d{1,2})");

    private static final Map<Integer, Integer> TABLE = load();

    private PwrTable() {
    }

    /** Zalecany poziom ochrony (PWR) dla roku; rzuca, gdy rok poza obsługiwanym zakresem/tabelą. */
    static int forYear(int year) {
        Integer pwr = TABLE.get(year);
        if (pwr == null) {
            throw new IllegalArgumentException(
                    "Brak poziomu ochrony PWR dla roku " + year + " (obsługiwany zakres " + MIN_YEAR + ".." + MAX_YEAR + ")");
        }
        return pwr;
    }

    private static Map<Integer, Integer> load() {
        String text;
        try {
            text = StreamUtils.copyToString(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Nie można wczytać tabeli ochrony PWR z " + RESOURCE, e);
        }

        var table = new HashMap<Integer, Integer>();
        for (String line : text.split("\\R")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            Matcher m = PAIR.matcher(line);
            while (m.find()) {
                int year = Integer.parseInt(m.group(1));
                if (year >= MIN_YEAR && year <= MAX_YEAR) {
                    table.put(year, Integer.parseInt(m.group(2)));
                }
            }
        }
        if (table.size() != MAX_YEAR - MIN_YEAR + 1) {
            throw new IllegalStateException(
                    "Tabela ochrony PWR niekompletna: oczekiwano " + (MAX_YEAR - MIN_YEAR + 1)
                            + " lat, wczytano " + table.size());
        }
        return Map.copyOf(table);
    }
}
