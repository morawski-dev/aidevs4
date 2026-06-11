package com.morawski.dev.aidevs.tasks.task25timetravel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Cienka warstwa interakcji z operatorem maszyny czasu. Drukuje konkretne instrukcje i — w trybie
 * interaktywnym — pauzuje na {@code stdin}, czekając aż człowiek wykona ręczną czynność w preview
 * (przełączniki PT-A/PT-B, suwak PWR, standby/active, kliknięcie sfery).
 *
 * <p>Czyta z {@code System.in} przez {@link BufferedReader} (a nie {@code System.console()}, które bywa
 * {@code null} pod {@code mvn spring-boot:run}). Gdy tryb interaktywny jest wyłączony lub {@code stdin}
 * jest niedostępny, instrukcje są tylko logowane (bez blokowania) — dzięki temu zadanie da się też
 * uruchomić „na sucho" / w CI.
 */
class OperatorConsole {

    private static final Logger log = LoggerFactory.getLogger(OperatorConsole.class);

    private final boolean interactive;
    private final BufferedReader in;

    OperatorConsole(boolean interactive) {
        this.interactive = interactive;
        this.in = interactive ? new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)) : null;
    }

    /** Nagłówek etapu — wyraźnie oddziela kolejne fazy operacji w logu operatora. */
    void section(String title) {
        log.info("\n========================================================\n>>> {}\n========================================================", title);
    }

    /** Jedna konkretna instrukcja dla operatora. */
    void instruct(String message) {
        log.info(">>> {}", message);
    }

    /** Informacja/diagnostyka (nie wymaga akcji). */
    void info(String message) {
        log.info("    {}", message);
    }

    /** Ostrzeżenie (coś jest nie tak, ale można kontynuować). */
    void warn(String message) {
        log.warn("!!! {}", message);
    }

    /**
     * Poczekaj, aż operator potwierdzi wykonanie ręcznej czynności (Enter). Bez trybu interaktywnego
     * tylko loguje prompt i wraca natychmiast.
     */
    void waitEnter(String prompt) {
        if (!interactive || in == null) {
            log.info("[NIEINTERAKTYWNY] {} (pomijam pauzę)", prompt);
            return;
        }
        log.info(">>> {}  [Enter, aby kontynuować]", prompt);
        readLine();
    }

    /**
     * Zadaj operatorowi pytanie i zwróć wpisaną odpowiedź (przycięta). Bez trybu interaktywnego zwraca
     * pusty łańcuch (asystent użyje sensownego domyślnego zachowania).
     */
    String ask(String prompt) {
        if (!interactive || in == null) {
            log.info("[NIEINTERAKTYWNY] {} (brak odpowiedzi)", prompt);
            return "";
        }
        log.info(">>> {} ", prompt);
        String line = readLine();
        return line == null ? "" : line.trim();
    }

    private String readLine() {
        try {
            return in.readLine();
        } catch (IOException e) {
            log.warn("Nie można odczytać stdin — kontynuuję bez pauzy.", e);
            return null;
        }
    }
}
