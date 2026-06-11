package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StabilizationHintTest {

    // The three real hints captured live from the device, one per mission hop.

    @Test
    void hop2238_wordsMinusWords() {
        String hint = "Moduł historyczny zakończył analizę warunków podróży. Dla tego rodzaju skoku "
                + "podręczniki operatora sugerują zwykle dziewięćset jednostek. Mimo to, w celu oszczędzania "
                + "baterii i ograniczenia zbędnego obciążenia zalecane jest obniżenie poziomu o siedemset "
                + "jedenaście. To powinno utrzymać maszynę w bezpiecznym paśmie pracy.";
        assertThat(StabilizationHint.resolve(hint)).hasValue(189); // 900 - 711
    }

    @Test
    void hop2026_wordsMinusWords() {
        String hint = "Po zestawieniu raportów z ostatnich misji wybrano następujący wariant konfiguracji. "
                + "Najbardziej typowy poziom dla podobnej podróży to siedemset punktów stabilizacji. Ze "
                + "względu na poprawioną wydajność nowych stabilizatorów można od tej wartości odjąć "
                + "sześćset osiemdziesiąt cztery jednostek. Tak skorygowany parametr powinien zapewnić "
                + "rozsądny margines bezpieczeństwa.";
        assertThat(StabilizationHint.resolve(hint)).hasValue(16); // 700 - 684
    }

    @Test
    void hop2024_wordsPlusDigits() {
        String hint = "Po porównaniu bieżących odczytów z archiwami epoki stwierdzono następującą "
                + "rekomendację. W dokumentacji serwisowej dla podobnych warunków najczęściej pojawia się "
                + "poziom sześćset. Jednak z uwagi na wzmożoną aktywność Słońca warto zwiększyć tę nastawę "
                + "o 395 punktów. Takie ustawienie poprawia szanse na stabilne wejście w tunel czasowy.";
        assertThat(StabilizationHint.resolve(hint)).hasValue(995); // 600 + 395
    }

    @Test
    void compoundWordNumbersSumCorrectly() {
        // "dwieście trzydzieści cztery" = 234 ; "o sto" added -> 334
        assertThat(StabilizationHint.resolve("baza dwieście trzydzieści cztery, zwiększyć o sto"))
                .hasValue(334);
    }

    @Test
    void singleNumberWithNoCorrectionReturnsBase() {
        assertThat(StabilizationHint.resolve("ustaw poziom na dziewięćset")).hasValue(900);
    }

    @Test
    void blankOrNumberlessReturnsEmpty() {
        assertThat(StabilizationHint.resolve("")).isEmpty();
        assertThat(StabilizationHint.resolve(null)).isEmpty();
        assertThat(StabilizationHint.resolve("brak konkretnej wartości")).isEmpty();
    }
}
