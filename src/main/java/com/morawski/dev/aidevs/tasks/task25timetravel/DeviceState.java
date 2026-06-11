package com.morawski.dev.aidevs.tasks.task25timetravel;

import java.util.Optional;

/**
 * Odczytany stan urządzenia z odpowiedzi {@code getConfig} (pole {@code config}) — kształt potwierdzony
 * reconem. Pola mogą być {@code null}, gdy odpowiedź jest częściowa/nie-JSON; pełne {@code raw} body jest
 * zawsze zachowane.
 *
 * @param internalMode      bieżąca faza rdzenia (1..4) — zmienia się automatycznie
 * @param fluxDensity       gęstość strumienia w procentach (0..100); skok wymaga 100
 * @param day               ustawiony dzień docelowy
 * @param month             ustawiony miesiąc docelowy
 * @param year              ustawiony rok docelowy
 * @param syncRatio         ustawiony sync ratio (tekstowo, jak zwrócony)
 * @param stabilization     ustawiona wartość stabilization
 * @param stabilizationHint surowa podpowiedź z pola {@code needConfig} (łamigłówka słowna)
 * @param deviceMode        tryb pracy: {@code standby}/{@code active}
 * @param condition         {@code stable} (UI: „doskonały") / {@code unstable} („niestabilny")
 * @param batteryStatus     poziom baterii jako {@code "n/3"}
 * @param ptA               stan przełącznika PT-A (z preview)
 * @param ptB               stan przełącznika PT-B (z preview)
 * @param pwr               pozycja suwaka PWR (z preview)
 * @param currentDate       data, w której obecnie znajduje się maszyna ({@code yyyy-MM-dd})
 * @param code              pole {@code code} z odpowiedzi
 * @param message           pole {@code message} z odpowiedzi
 * @param raw               pełne, surowe body
 */
record DeviceState(
        Integer internalMode,
        Integer fluxDensity,
        Integer day,
        Integer month,
        Integer year,
        String syncRatio,
        String stabilization,
        String stabilizationHint,
        String deviceMode,
        String condition,
        String batteryStatus,
        Boolean ptA,
        Boolean ptB,
        Integer pwr,
        String currentDate,
        Integer code,
        String message,
        String raw
) {

    boolean fluxReady() {
        return fluxDensity != null && fluxDensity >= 100;
    }

    boolean internalModeIs(int wanted) {
        return internalMode != null && internalMode == wanted;
    }

    boolean isActive() {
        return deviceMode != null && deviceMode.toLowerCase().contains("active");
    }

    boolean isStandby() {
        return deviceMode != null && deviceMode.toLowerCase().contains("standby");
    }

    boolean isStable() {
        return "stable".equalsIgnoreCase(condition);
    }

    /** Naładowane ogniwa z {@code "n/3"} (0 gdy nie da się odczytać). */
    int batteryCharged() {
        if (batteryStatus == null) {
            return 0;
        }
        int slash = batteryStatus.indexOf('/');
        String head = slash >= 0 ? batteryStatus.substring(0, slash) : batteryStatus;
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Tunel wymaga ≥60% baterii — czyli co najmniej 2 z 3 ogniw. */
    boolean tunnelBatteryOk() {
        return batteryCharged() >= 2;
    }

    Optional<String> hint() {
        return Optional.ofNullable(stabilizationHint).filter(s -> !s.isBlank());
    }
}
