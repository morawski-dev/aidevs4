package com.morawski.dev.aidevs.tasks.task04sendit;

import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Renders the SPK declaration string 1:1 with the template from {@code zalacznik-E.md}.
 * The layout (headers, {@code ===}/{@code ---} separators, field order, units) is reproduced
 * verbatim — the Hub validates both values <em>and</em> formatting.
 */
@Component
class DeclarationBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    String build(Shipment s) {
        var filled = """
                SYSTEM PRZESYŁEK KONDUKTORSKICH - DEKLARACJA ZAWARTOŚCI
                ======================================================
                DATA: %s
                PUNKT NADAWCZY: %s
                ------------------------------------------------------
                NADAWCA: %s
                PUNKT DOCELOWY: %s
                TRASA: %s
                ------------------------------------------------------
                KATEGORIA PRZESYŁKI: %s
                ------------------------------------------------------
                OPIS ZAWARTOŚCI (max 200 znaków): %s
                ------------------------------------------------------
                DEKLAROWANA MASA (kg): %d
                ------------------------------------------------------
                WDP: %d
                ------------------------------------------------------
                UWAGI SPECJALNE: %s
                ------------------------------------------------------
                KWOTA DO ZAPŁATY: %s
                ------------------------------------------------------
                OŚWIADCZAM, ŻE PODANE INFORMACJE SĄ PRAWDZIWE.
                BIORĘ NA SIEBIE KONSEKWENCJĘ ZA FAŁSZYWE OŚWIADCZENIE.
                ======================================================""".formatted(
                s.date().format(DATE),
                s.punktNadawczy(),
                s.nadawca(),
                s.punktDocelowy(),
                s.trasa(),
                s.kategoria(),
                s.opisZawartosci(),
                s.masaKg(),
                s.wdp(),
                s.uwagiSpecjalne(),
                s.kwotaDoZaplaty());

        // An empty UWAGI SPECJALNE value leaves a trailing space after the colon; keep the
        // output canonical (no trailing whitespace on any line) so it matches the template exactly.
        return filled.lines().map(String::stripTrailing).collect(Collectors.joining("\n"));
    }
}
