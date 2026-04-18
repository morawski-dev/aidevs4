package com.morawski.dev.aidevs.tasks.task04sendit;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DeclarationBuilderTest {

    private final DeclarationBuilder builder = new DeclarationBuilder();

    @Test
    void rendersTemplateOneToOne() {
        var shipment = new Shipment(
                LocalDate.of(2026, 6, 9),
                "Gdańsk", "450202122", "Żarnowiec", "X-01", "A",
                "kasety z paliwem do reaktora", 2800, 4, "", "0 PP");

        var expected = """
                SYSTEM PRZESYŁEK KONDUKTORSKICH - DEKLARACJA ZAWARTOŚCI
                ======================================================
                DATA: 2026-06-09
                PUNKT NADAWCZY: Gdańsk
                ------------------------------------------------------
                NADAWCA: 450202122
                PUNKT DOCELOWY: Żarnowiec
                TRASA: X-01
                ------------------------------------------------------
                KATEGORIA PRZESYŁKI: A
                ------------------------------------------------------
                OPIS ZAWARTOŚCI (max 200 znaków): kasety z paliwem do reaktora
                ------------------------------------------------------
                DEKLAROWANA MASA (kg): 2800
                ------------------------------------------------------
                WDP: 4
                ------------------------------------------------------
                UWAGI SPECJALNE:
                ------------------------------------------------------
                KWOTA DO ZAPŁATY: 0 PP
                ------------------------------------------------------
                OŚWIADCZAM, ŻE PODANE INFORMACJE SĄ PRAWDZIWE.
                BIORĘ NA SIEBIE KONSEKWENCJĘ ZA FAŁSZYWE OŚWIADCZENIE.
                ======================================================""";

        assertThat(builder.build(shipment)).isEqualTo(expected);
    }

    @Test
    void taskShipmentResolvesTheTwoUnknowns() {
        var s = Shipment.forTask();
        assertThat(s.kategoria()).isEqualTo("A");      // 0 PP + closed-route eligibility
        assertThat(s.trasa()).isEqualTo("X-01");       // Gdańsk → Żarnowiec
        assertThat(s.kwotaDoZaplaty()).isEqualTo("0 PP");
        assertThat(s.wdp()).isEqualTo(4);
        assertThat(s.uwagiSpecjalne()).isEmpty();
    }
}
