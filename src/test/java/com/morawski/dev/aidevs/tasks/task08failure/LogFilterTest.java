package com.morawski.dev.aidevs.tasks.task08failure;

import com.morawski.dev.aidevs.config.FailureProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogFilterTest {

    private static final FailureProperties PROPS = new FailureProperties(
            "failure.log",
            1450,
            List.of("WARN", "ERRO", "ERROR", "CRIT"),
            List.of("ECCS8", "WTRPMP", "WTANK07", "STMTURB12", "WSTPOOL2", "PWR01", "FIRMWARE"),
            "openai/gpt-4o-mini",
            6);

    private final LogFilter filter = new LogFilter(PROPS);

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void dropsNoiseLevelsKeepsSeverities() {
        var raw = """
                [2026-06-09 06:00:16] [INFO] Primary feed acknowledgment on WTRPMP received. Rotation profile is smooth.
                [2026-06-09 06:02:28] [WARN] Pressure jitter near STMTURB12 is above baseline. Automatic damping remains engaged.
                [2026-06-09 06:04:13] [CRIT] ECCS8 reported runaway outlet temperature. Protection interlock initiated reactor trip.
                """;
        var events = filter.parseAndFilter(bytes(raw));
        assertThat(events).hasSize(2); // INFO dropped, WARN + CRIT kept
        assertThat(events).extracting(LogEvent::level).containsExactly("WARN", "CRIT");
    }

    @Test
    void normalizesTimestampToHourMinute() {
        var raw = "[2026-06-09 06:04:13] [CRIT] ECCS8 reported runaway outlet temperature. Protection interlock initiated reactor trip.\n";
        var e = filter.parseAndFilter(bytes(raw)).getFirst();
        assertThat(e.date()).isEqualTo("2026-06-09");
        assertThat(e.time()).isEqualTo("06:04"); // seconds dropped
        assertThat(e.renderFull()).startsWith("[2026-06-09 06:04] [CRIT] ECCS8");
    }

    @Test
    void deduplicatesTemplatedMessagesKeepingFirstOccurrenceAndCount() {
        var raw = """
                [2026-06-09 06:03:20] [WARN] Thermal drift on ECCS8 exceeds advisory threshold. Corrective ramp is queued.
                [2026-06-09 06:10:48] [WARN] Thermal drift on ECCS8 exceeds advisory threshold. Corrective ramp is queued.
                [2026-06-09 06:41:03] [WARN] Thermal drift on ECCS8 exceeds advisory threshold. Corrective ramp is queued.
                """;
        var events = filter.parseAndFilter(bytes(raw));
        assertThat(events).hasSize(1);
        var e = events.getFirst();
        assertThat(e.count()).isEqualTo(3);
        assertThat(e.time()).isEqualTo("06:03");     // first occurrence
        assertThat(e.lastTime()).isEqualTo("06:41");  // last occurrence
    }

    @Test
    void detectsKnownSubsystemEvenWhenNotFirstToken() {
        // "Safety bootstrap ... marker SAFETY_CHECK=pass. FIRMWARE continues ..." -> FIRMWARE, not SAFETY.
        var msg = "Safety bootstrap read missing environment marker SAFETY_CHECK=pass. FIRMWARE continues in restricted validation mode.";
        assertThat(filter.detectSubsystem(msg)).isEqualTo("FIRMWARE");
    }

    @Test
    void detectsSubsystemByEarliestKnownIdInText() {
        assertThat(filter.detectSubsystem("Input ripple on PWR01 crossed warning limits.")).isEqualTo("PWR01");
        assertThat(filter.detectSubsystem("Fill trajectory in WTANK07 is slower than expected.")).isEqualTo("WTANK07");
    }

    @Test
    void preservesChronologicalGenesisOrder() {
        var raw = """
                [2026-06-09 06:02:28] [WARN] Pressure jitter near STMTURB12 is above baseline.
                [2026-06-09 06:04:13] [CRIT] ECCS8 reported runaway outlet temperature.
                [2026-06-09 06:11:40] [WARN] Input ripple on PWR01 crossed warning limits.
                """;
        var events = filter.parseAndFilter(bytes(raw));
        assertThat(events).extracting(LogEvent::subsystem).containsExactly("STMTURB12", "ECCS8", "PWR01");
    }

    @Test
    void ignoresUnparseableLines() {
        var raw = """
                not a log line at all
                [2026-06-09 06:04:13] [CRIT] ECCS8 reported runaway outlet temperature.

                """;
        assertThat(filter.parseAndFilter(bytes(raw))).hasSize(1);
    }
}
