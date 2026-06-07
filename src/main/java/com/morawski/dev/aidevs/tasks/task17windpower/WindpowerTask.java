package com.morawski.dev.aidevs.tasks.task17windpower;

import com.fasterxml.jackson.databind.JsonNode;
import com.morawski.dev.aidevs.config.WindpowerProperties;
import com.morawski.dev.aidevs.tasks.Task;
import com.morawski.dev.aidevs.tasks.task17windpower.ReportParser.ReportType;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Task S04E02 ({@code windpower}) — program a wind-turbine schedule so the power plant gets the energy it
 * needs, <strong>within a hard 40-second session</strong> (the brief: running every action serially
 * will not fit). The API is asynchronous and queued: {@code get(param)} enqueues a report
 * ({@code weather} / {@code turbinecheck} / {@code powerplantcheck}), {@code getResult} returns one
 * completed item (tagged with {@code sourceFunction}, removed from the queue), results arrive in
 * random order, and {@code unlockCodeGenerator} is likewise async. So the work is fanned out on
 * virtual threads: enqueue the three reports at once, then enqueue every config point's signature at
 * once — the critical path is a few <em>max</em>es, not a sum.
 *
 * <p>Perception/logic split, mirroring the other tasks: {@link ReportParser} turns raw bodies into
 * typed reports, {@link SchedulePlanner} (pure logic, unit-tested) decides storm-protection and the
 * production point, and this task drives the timed Hub dialog. {@code done} carries the flag, so the
 * task is {@link #selfSubmitting() self-submitting}.
 *
 * <p>{@code aidevs.windpower.recon=true} runs an untimed reconnaissance (documentation + the three
 * report bodies + one real unlock-code result) so the parser field names and rules can be confirmed.
 */
@Component
class WindpowerTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(WindpowerTask.class);

    private static final String DOCUMENTATION = "documentation";
    private static final List<String> REPORTS = List.of("weather", "turbinecheck", "powerplantcheck");

    // Candidate keys for the signature in an unlockCodeGenerator result. Deliberately excludes bare
    // "code" so it can't grab the response's numeric status "code" field; tune once recon shows the body.
    private static final String[] CODE_KEYS = {"unlockcode", "unlock_code", "signature", "podpis", "sign"};
    private static final String[] SOURCE_KEYS = {"sourcefunction", "source"};
    private static final String[] DATE_KEYS = {"startdate", "date", "data"};
    private static final String[] HOUR_KEYS = {"starthour", "hour", "godzin"};

    private final WindpowerClient client;
    private final ReportParser parser;
    private final SchedulePlanner planner;
    private final WindpowerProperties props;

    WindpowerTask(WindpowerClient client, ReportParser parser, SchedulePlanner planner, WindpowerProperties props) {
        this.client = client;
        this.parser = parser;
        this.planner = planner;
        this.props = props;
    }

    @Override
    public String name() {
        return "windpower";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "windpower.solve")
    public Object solve() {
        return props.recon() ? recon() : run();
    }

    // --- timed flow ----------------------------------------------------------

    private Object run() {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            // FAZA 1 — open the service window; the 40 s clock starts here.
            client.start();
            long t0 = System.currentTimeMillis();
            long deadline = t0 + props.deadlineMs();

            // FAZA 2 — read the turbine limits from documentation (returned directly, not queued) and
            // enqueue the three queued reports concurrently.
            TurbineSpec spec = parser.spec(client.get(DOCUMENTATION).json());
            log.info("Turbine spec — cutoff {} m/s, min operational {} m/s", spec.cutoffWindMs(), spec.minOperationalWindMs());
            CompletableFuture<?>[] queued = REPORTS.stream()
                    .map(p -> CompletableFuture.runAsync(() -> client.get(p), pool))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(queued).join();

            // FAZA 3 — poll getResult until all three reports are collected (random order, fetched once).
            Reports reports = collectReports(spec, deadline);

            // FAZA 4 — deterministic plan (storm protection + production point), still unsigned.
            List<ConfigPoint> points = planner.plan(reports);
            log.info("Planned {} config point(s): {}", points.size(), points);
            if (points.isEmpty()) {
                log.warn("No config points planned — reports likely mis-parsed (run with recon=true to inspect bodies).");
            }

            // FAZA 5 — enqueue every point's signature at once, then collect the codes from the queue.
            List<ConfigPoint> signed = sign(pool, points, deadline);

            // FAZA 6 — store the whole schedule in one request.
            client.config(toConfigs(signed));

            // FAZA 7 — mandatory turbine self-test before done (turbinecheck was already retrieved in FAZA 3).
            // FAZA 8 — final validation; the flag rides on the done response.
            WindResponse done = client.done();
            long elapsed = System.currentTimeMillis() - t0;
            log.info("windpower timed flow finished in {} ms (budget {} ms, hard limit 40000 ms)", elapsed, props.deadlineMs());

            return done.flag()
                    .map(flag -> {
                        log.info("FLAG → {}", flag);
                        return (Object) Map.of("flag", flag, "elapsedMs", elapsed);
                    })
                    .orElseGet(() -> {
                        log.warn("No flag in done response: {}", done.body());
                        return Map.of("status", "no flag", "elapsedMs", elapsed, "body", done.body());
                    });
        }
    }

    /**
     * Poll {@code getResult}, classifying each body by {@code sourceFunction}, until all three queued
     * reports are in. Draining {@code turbinecheck} here (even though its data isn't used for planning)
     * satisfies the brief's "run turbinecheck before done". The {@code spec} comes from documentation.
     */
    private Reports collectReports(TurbineSpec spec, long deadline) {
        var collected = new EnumMap<ReportType, JsonNode>(ReportType.class);
        int attempts = 0;
        while (collected.size() < REPORTS.size()
                && System.currentTimeMillis() < deadline
                && attempts < props.maxPollAttempts()) {
            attempts++;
            JsonNode node = client.getResult().json();
            ReportType type = parser.classify(node);
            if (type != ReportType.UNKNOWN && !collected.containsKey(type)) {
                collected.put(type, node);
                log.info("Collected {} report ({}/{})", type, collected.size(), REPORTS.size());
                continue;
            }
            sleep(props.pollIntervalMs());
        }
        if (collected.size() < REPORTS.size()) {
            log.warn("Only {}/{} reports collected after {} poll(s) before deadline.", collected.size(), REPORTS.size(), attempts);
        }

        WeatherReport weather = collected.containsKey(ReportType.WEATHER)
                ? parser.weather(collected.get(ReportType.WEATHER)) : new WeatherReport(List.of());
        PlantRequirements requirements = collected.containsKey(ReportType.REQUIREMENTS)
                ? parser.requirements(collected.get(ReportType.REQUIREMENTS)) : new PlantRequirements(0.0);
        log.info("Reports — weather slots: {}, plant deficit: {} kW", weather.slots().size(), requirements.deficitKw());
        return new Reports(weather, spec, requirements);
    }

    /**
     * Enqueue every point's {@code unlockCodeGenerator} job concurrently, then drain the queue, matching
     * each returned signature to its point by {@code startDate}+{@code startHour} (falling back to arrival
     * order for results that don't echo the timestamp).
     */
    private List<ConfigPoint> sign(ExecutorService pool, List<ConfigPoint> points, long deadline) {
        if (points.isEmpty()) {
            return points;
        }
        CompletableFuture<?>[] jobs = points.stream()
                .map(p -> CompletableFuture.runAsync(() -> client.unlockCode(p), pool))
                .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(jobs).join();

        var byKey = new HashMap<String, String>();
        var loose = new ArrayDeque<String>();
        int collected = 0;
        int attempts = 0;
        while (collected < points.size()
                && System.currentTimeMillis() < deadline
                && attempts < props.maxPollAttempts()) {
            attempts++;
            JsonNode node = client.getResult().json();
            String source = Json.findText(node, SOURCE_KEYS);
            if (source != null && source.toLowerCase().contains("unlock")) {
                String code = Json.findText(node, CODE_KEYS);
                String date = Json.findText(node, DATE_KEYS);
                String hour = ReportParser.normalizeHour(Json.findText(node, HOUR_KEYS));
                if (date != null && hour != null) {
                    byKey.put(date + " " + hour, code == null ? "" : code);
                } else {
                    loose.add(code == null ? "" : code);
                }
                collected++;
                continue;
            }
            sleep(props.pollIntervalMs());
        }
        if (collected < points.size()) {
            log.warn("Only {}/{} unlock codes collected before deadline.", collected, points.size());
        }
        return attachCodes(points, byKey, loose);
    }

    private static List<ConfigPoint> attachCodes(List<ConfigPoint> points, Map<String, String> byKey, Deque<String> loose) {
        var signed = new ArrayList<ConfigPoint>(points.size());
        for (ConfigPoint p : points) {
            String code = byKey.get(p.key());
            if (code == null) {
                code = loose.poll();
            }
            if (code == null || code.isBlank()) {
                log.warn("No unlockCode for {} — config will likely be rejected.", p.key());
                code = "";
            }
            signed.add(p.withCode(code));
        }
        return signed;
    }

    private static Map<String, Object> toConfigs(List<ConfigPoint> points) {
        var configs = new LinkedHashMap<String, Object>();
        for (ConfigPoint p : points) {
            configs.put(p.key(), Map.of(
                    "pitchAngle", p.pitchAngle(),
                    "turbineMode", p.turbineMode(),
                    "unlockCode", p.unlockCode()));
        }
        return configs;
    }

    // --- recon (untimed) -----------------------------------------------------

    /**
     * Probe the API to learn the report shapes and rules: documentation (returned directly), then the three
     * queued report bodies, then one real {@code unlockCodeGenerator} result built from an actual weather
     * slot — everything logged raw so the parser keys, units, endurance and pitch rules can be confirmed.
     */
    private Object recon() {
        log.info("=== windpower RECON (no timed flow) ===");
        log.info("help: {}", client.help().body());
        log.info("start: {}", client.start().body());
        long t0 = System.currentTimeMillis();

        log.info("documentation: {}", client.get(DOCUMENTATION).body());
        REPORTS.forEach(p -> log.info("enqueue get({}): {}", p, client.get(p).body()));

        // Drain the three queued report results, keyed by sourceFunction.
        var bySource = new LinkedHashMap<String, JsonNode>();
        int attempts = 0;
        while (bySource.size() < REPORTS.size()
                && System.currentTimeMillis() - t0 < 30_000
                && attempts < props.maxPollAttempts()) {
            attempts++;
            WindResponse r = client.getResult();
            String source = Json.findText(r.json(), SOURCE_KEYS);
            if (source != null) {
                bySource.put(source, r.json());
                log.info("RESULT [{}]: {}", source, r.body());
            } else {
                sleep(props.pollIntervalMs());
            }
        }
        log.info("collected sources: {}", bySource.keySet());

        reconProbeUnlock(bySource, t0);
        return Map.of("recon", true, "sources", bySource.keySet());
    }

    /** Sign one real weather slot so the unlock-result shape (where the code/echoed params live) is logged. */
    private void reconProbeUnlock(Map<String, JsonNode> bySource, long t0) {
        JsonNode weatherNode = bySource.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains("weather"))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (weatherNode == null) {
            log.warn("No weather result collected — cannot probe unlockCodeGenerator.");
            return;
        }
        List<WeatherSlot> slots = parser.weather(weatherNode).slots();
        if (slots.isEmpty()) {
            log.warn("Parsed 0 weather slots — inspect the weather body above and fix ReportParser keys.");
            return;
        }
        WeatherSlot s = slots.get(0);
        var probe = new ConfigPoint(s.date(), s.hour(), s.wind(), props.featherPitch(), props.idleMode(), null);
        log.info("probe unlockCodeGenerator for {} windMs={}: {}", probe.key(), s.wind(), client.unlockCode(probe).body());

        int attempts = 0;
        while (System.currentTimeMillis() - t0 < 38_000 && attempts < props.maxPollAttempts()) {
            attempts++;
            WindResponse r = client.getResult();
            String source = Json.findText(r.json(), SOURCE_KEYS);
            if (source != null && source.toLowerCase().contains("unlock")) {
                log.info("UNLOCK RESULT: {}", r.body());
                return;
            }
            sleep(props.pollIntervalMs());
        }
        log.warn("Did not collect an unlock result within the recon window.");
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling getResult", e);
        }
    }
}
