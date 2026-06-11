package com.morawski.dev.aidevs.tasks.task25timetravel;

/**
 * Mapowanie roku docelowego na wymaganą fazę pracy rdzenia ({@code internalMode}), zgodnie z
 * dokumentacją CHRONOS-P1. Operator nie może ustawić {@code internalMode} ręcznie — zmienia się
 * automatycznie co kilka sekund; trzeba poczekać, aż trafi w fazę zgodną z rokiem docelowym:
 *
 * <ol>
 *   <li>{@code 1} — lata poniżej {@code 2000}</li>
 *   <li>{@code 2} — lata {@code 2000..2150}</li>
 *   <li>{@code 3} — lata {@code 2151..2300}</li>
 *   <li>{@code 4} — lata {@code 2301} i wyższe</li>
 * </ol>
 *
 * <p>Czysta logika — testowana w {@code InternalModeTest}.
 */
final class InternalMode {

    private InternalMode() {
    }

    static int forYear(int year) {
        if (year < 2000) {
            return 1;
        }
        if (year <= 2150) {
            return 2;
        }
        if (year <= 2300) {
            return 3;
        }
        return 4;
    }
}
