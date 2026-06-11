package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguracja zadania task25 {@code timetravel} — asystenta operatora maszyny czasu CHRONOS-P1.
 *
 * @param recon           gdy {@code true}, uruchom sondę rozpoznawczą (help + getConfig + próbny
 *                        configure) i tylko zaloguj surowe odpowiedzi, zamiast prowadzić skoki
 * @param pollIntervalMs  odstęp między odczytami {@code getConfig} w pętli oczekiwania na gotowość
 * @param maxPollAttempts twardy limit prób w pętli oczekiwania (zabezpieczenie przed nieskończoną pętlą)
 * @param interactive     gdy {@code true}, asystent pauzuje na {@code stdin} (prowadzi operatora krok po
 *                        kroku); gdy {@code false}, tylko drukuje instrukcje i robi krótką pauzę
 * @param presentDate     „teraźniejszość" powrotu (dzisiejsza data), format {@code yyyy-MM-dd}
 * @param batteryDate     data skoku po nowe baterie, format {@code yyyy-MM-dd}
 * @param rafalDate       data docelowa tunelu (spotkanie z Rafałem), format {@code yyyy-MM-dd}
 */
@ConfigurationProperties("aidevs.timetravel")
public record TimeTravelProperties(
        boolean recon,
        long pollIntervalMs,
        int maxPollAttempts,
        boolean interactive,
        String presentDate,
        String batteryDate,
        String rafalDate
) {
}
