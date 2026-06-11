package com.morawski.dev.aidevs.tasks.task25timetravel;

import com.morawski.dev.aidevs.config.TimeTravelProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.OptionalInt;

/**
 * S05E05 ({@code timetravel}) — <b>asystent</b> operatora kieszonkowej maszyny czasu CHRONOS-P1.
 * Cel: otworzyć tunel do <b>12 listopada 2024</b> (dzień przed znalezieniem Rafała). Na tunel brakuje
 * energii, więc plan zakłada dodatkowy skok po baterie:
 *
 * <ol>
 *   <li>skok w przyszłość → <b>5 listopada 2238</b> (skok do tej daty doładowuje baterię do 3/3),</li>
 *   <li>powrót do teraźniejszości → <b>dzisiejsza data</b> (bateria spada do 2/3 = 66%),</li>
 *   <li>tunel z teraźniejszości → <b>12 listopada 2024</b> (wymaga ≥60% baterii; zwraca flagę).</li>
 * </ol>
 *
 * <p>Podział pracy (potwierdzony reconem): <b>API</b> ustawia {@code day/month/year/syncRatio/
 * stabilization} (tylko w {@code standby}); <b>operator</b> ustawia w preview {@code PT-A}, {@code PT-B},
 * suwak {@code PWR} oraz {@code standby}/{@code active}. Sam „skok" to akcja API {@code timeTravel}
 * (odpowiednik kliknięcia pulsującej sfery) — asystent wykonuje ją sam, gdy {@code fluxDensity}=100 i
 * faza {@code internalMode} pasuje do roku. Flagę zwraca odpowiedź {@code timeTravel} po poprawnym tunelu.
 *
 * <p>{@link #selfSubmitting()} = {@code true}; klient {@link TimeTravelClient} jest nie-rzucający.
 * {@code aidevs.timetravel.recon=true} odpala sondę rozpoznawczą.
 */
@Component
class TimeTravelTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(TimeTravelTask.class);
    private static final int JUMP_OK = 13; // timeTravel completed

    private final TimeTravelClient client;
    private final TimeTravelProperties props;

    private OperatorConsole console;
    private String flag;

    TimeTravelTask(TimeTravelClient client, TimeTravelProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public String name() {
        return "timetravel";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "timetravel.solve")
    public Object solve() {
        this.console = new OperatorConsole(props.interactive());
        if (props.recon()) {
            return recon();
        }
        return runMission();
    }

    // --- mission -------------------------------------------------------------

    private Object runMission() {
        LocalDate batteryDate = LocalDate.parse(props.batteryDate());
        LocalDate presentDate = LocalDate.parse(props.presentDate());
        LocalDate rafalDate = LocalDate.parse(props.rafalDate());

        console.section("MASZYNA CZASU CHRONOS-P1 — asystent operatora");
        console.info("Plan: (1) skok do %s po baterie → (2) powrót do teraźniejszości %s → (3) tunel do %s."
                .formatted(batteryDate, presentDate, rafalDate));
        console.info("API ustawia: day/month/year/syncRatio/stabilization (tylko w standby).");
        console.info("Ty ustawiasz w preview: PT-A, PT-B, suwak PWR oraz standby/active. Skok wykonuję ja (timeTravel).");

        console.section("Reset urządzenia");
        client.reset();

        runHop("SKOK 1/3 — do %s (po baterie)".formatted(batteryDate), batteryDate, HopKind.FUTURE_JUMP);
        if (flag != null) {
            return done();
        }
        runHop("SKOK 2/3 — powrót do teraźniejszości %s".formatted(presentDate), presentDate, HopKind.PAST_JUMP);
        if (flag != null) {
            return done();
        }
        runHop("KROK 3/3 — TUNEL z teraźniejszości do %s".formatted(rafalDate), rafalDate, HopKind.TUNNEL);
        return done();
    }

    /**
     * Jeden skok/tunel: standby → konfiguracja API (data + syncRatio + stabilization) → instrukcje
     * ręczne (PT-A/PT-B/PWR/active) → oczekiwanie na flux=100% i właściwą fazę → akcja {@code timeTravel}.
     */
    private void runHop(String title, LocalDate target, HopKind kind) {
        console.section(title);

        // A) device must be in standby to accept API configuration.
        console.instruct("Przełącz urządzenie w tryb STANDBY (preview).");
        console.waitEnter("Naciśnij Enter, gdy urządzenie jest w trybie standby");
        DeviceState before = StateReader.parse(client.getConfig().body());
        if (before.deviceMode() != null && !before.isStandby()) {
            console.warn("Urządzenie nie jest w standby (tryb=%s) — konfiguracja API zostanie odrzucona."
                    .formatted(before.deviceMode()));
        }

        // B) API-side configuration: date, syncRatio, then stabilization from the API's own hint.
        console.info("Ustawiam datę docelową i sync ratio przez API...");
        client.configure("year", target.getYear());
        client.configure("month", target.getMonthValue());
        client.configure("day", target.getDayOfMonth());

        BigDecimal sync = SyncRatio.forDate(target);
        console.info("sync ratio dla %s = %s (z dokumentacji).".formatted(target, sync.toPlainString()));
        client.configure("syncRatio", sync);

        configureStabilization(target);

        // C) manual preview settings the assistant can only instruct, not set.
        int pwr = PwrTable.forYear(target.getYear());
        int wantMode = InternalMode.forYear(target.getYear());
        console.instruct("Ustaw przełączniki: " + kind.switchesInstruction());
        console.instruct("Ustaw suwak PWR = %d (zalecana ochrona dla roku %d).".formatted(pwr, target.getYear()));
        if (kind.isTunnel()) {
            console.instruct("To TUNEL: PT-A i PT-B jednocześnie ON; wymaga baterii ≥ 60%% (2/3).");
        }
        console.instruct("Przełącz urządzenie w tryb ACTIVE.");
        console.info("Docelowa faza internalMode dla roku %d to %d (zmienia się sama — poczekam na nią)."
                .formatted(target.getYear(), wantMode));
        console.waitEnter("Naciśnij Enter, gdy ustawisz PT-A/PT-B, suwak PWR i tryb ACTIVE");

        // D + E) wait for flux=100% (right internalMode) and perform the jump via the timeTravel action.
        performJump(kind, wantMode);
    }

    /**
     * Stabilization: po ustawieniu pełnej daty API zwraca podpowiedź w {@code needConfig} (słowna
     * łamigłówka). {@link StabilizationHint} liczy ją deterministycznie; w razie niepowodzenia prosimy
     * operatora o ręczne wpisanie wartości.
     */
    private void configureStabilization(LocalDate target) {
        DeviceState st = StateReader.parse(client.getConfig().body());
        OptionalInt resolved = st.hint().map(StabilizationHint::resolve).orElse(OptionalInt.empty());

        if (resolved.isPresent()) {
            console.info("Podpowiedź API → stabilization = %d.".formatted(resolved.getAsInt()));
            client.configure("stabilization", resolved.getAsInt());
            return;
        }

        st.hint().ifPresent(h -> console.info("Podpowiedź API (nieprzetworzona): " + h));
        String typed = console.ask("Nie umiem policzyć stabilization automatycznie. Wpisz wartość dla "
                + target + " (0-1000, Enter = pomiń):");
        if (typed.isBlank()) {
            console.warn("Pomijam stabilization — ustaw je ręcznie, jeśli flux nie osiągnie 100%.");
            return;
        }
        StabilizationHint.resolve(typed).ifPresentOrElse(
                v -> client.configure("stabilization", v),
                () -> {
                    try {
                        client.configure("stabilization", Integer.parseInt(typed.trim()));
                    } catch (NumberFormatException e) {
                        console.warn("Niepoprawna wartość stabilization: " + typed);
                    }
                });
    }

    /**
     * Pętla: czekaj aż {@code fluxDensity}=100 (co wymaga właściwej fazy {@code internalMode} + poprawnych
     * ustawień ręcznych), wtedy wywołaj {@code timeTravel}. Sukces = {@code code:13}; przy tunelu do daty
     * docelowej odpowiedź niesie flagę (wyłuskiwaną przez {@link #checkFlag}).
     */
    private void performJump(HopKind kind, int wantMode) {
        console.info("Czekam na flux=100%% (faza internalMode=%d) i wykonuję %s przez timeTravel..."
                .formatted(wantMode, kind.label()));
        for (int attempt = 1; attempt <= props.maxPollAttempts(); attempt++) {
            DeviceState st = StateReader.parse(client.getConfig().body());

            if (st.fluxReady()) {
                TtResponse jump = client.timeTravel();
                checkFlag(jump);
                DeviceState after = StateReader.parse(jump.body());
                if (after.code() != null && after.code() == JUMP_OK) {
                    console.instruct("%s wykonany. Aktualna data: %s, bateria: %s."
                            .formatted(kind.label(), after.currentDate(), after.batteryStatus()));
                    return;
                }
                console.info("timeTravel jeszcze nieprzyjęty (code=%s) — czekam na właściwą fazę..."
                        .formatted(after.code()));
            } else {
                if (st.internalMode() != null && !st.internalModeIs(wantMode)) {
                    console.info("internalMode=%d (czekam na %d) — zmienia się co kilka sekund."
                            .formatted(st.internalMode(), wantMode));
                }
                if (st.fluxDensity() != null && !st.fluxReady()) {
                    console.info("flux=%d%% (<100) — sprawdź PT-A/PT-B, suwak PWR oraz tryb ACTIVE."
                            .formatted(st.fluxDensity()));
                }
                if (st.internalMode() == null && st.fluxDensity() == null) {
                    console.warn("Nie odczytuję internalMode/flux — zweryfikuj stan w preview.");
                }
            }
            sleep(props.pollIntervalMs());
        }
        console.warn("Nie udało się wykonać %s w limicie %d prób — sprawdź ustawienia ręczne w preview."
                .formatted(kind.label(), props.maxPollAttempts()));
    }

    // --- recon ---------------------------------------------------------------

    private Object recon() {
        console.section("TIMETRAVEL — RECON (rozpoznanie API)");
        client.help();
        client.getConfig();
        client.reset();
        client.configure("year", 2238);
        client.configure("month", 11);
        client.configure("day", 5);
        client.configure("syncRatio", SyncRatio.forDate(LocalDate.of(2238, 11, 5)));
        client.getConfig(); // obejrzyj needConfig + pola PTA/PTB/PWR/batteryStatus/internalMode/flux
        client.reset();
        console.info("Recon zakończony — sprawdź logi (pola configu, needConfig, code dla timeTravel).");
        return Map.of("mode", "recon");
    }

    // --- helpers -------------------------------------------------------------

    private Object done() {
        if (flag != null) {
            log.info("FLAG → {}", flag);
            return Map.of("flag", flag);
        }
        console.info("Nie wykryto flagi. Jeśli tunel został otwarty poprawnie, sprawdź preview / logi powyżej.");
        return Map.of("status", "brak flagi (sprawdź preview / logi)");
    }

    private void checkFlag(TtResponse resp) {
        if (flag == null) {
            resp.flag().ifPresent(f -> {
                flag = f;
                console.section("FLAGA: {FLG:%s}".formatted(f));
            });
        }
    }

    private void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Przerwano oczekiwanie", e);
        }
    }
}
