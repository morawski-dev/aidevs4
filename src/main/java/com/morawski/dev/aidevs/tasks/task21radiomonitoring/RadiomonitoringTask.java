package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.RadiomonitoringProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * S05E01 {@code radiomonitoring} (task21) — intercept and analyse radio traffic, then transmit a
 * report on the city codenamed "Syjon" (its real name, area, warehouse count and contact phone).
 *
 * <p>The whole API is {@code POST /verify} ({@code action} = {@code start|listen|transmit}). The task
 * drives the dialog itself and detects its own {@code {FLG:...}}, so it is {@link #selfSubmitting()
 * self-submitting}. The interesting part is cost-aware routing: the {@code listen} stream mixes noise,
 * text transcriptions and (possibly large) base64 binaries, and dumping binaries into an LLM is
 * expensive — so {@link RadioRouter} decides locally what to drop, decode for free, or send to the
 * right model, and only the resulting text snippets feed a single {@link ReportSynthesizer} call.
 * {@code cityArea} is rounded deterministically in Java ({@link AreaFormat}), never by the model.
 */
@Component
class RadiomonitoringTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(RadiomonitoringTask.class);

    private final RadiomonitoringClient client;
    private final RadioRouter router;
    private final ReportSynthesizer synthesizer;
    private final RadiomonitoringProperties props;
    private final ObjectMapper mapper;

    RadiomonitoringTask(RadiomonitoringClient client, RadioRouter router, ReportSynthesizer synthesizer,
                        RadiomonitoringProperties props, ObjectMapper mapper) {
        this.client = client;
        this.router = router;
        this.synthesizer = synthesizer;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "radiomonitoring";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "radiomonitoring.solve")
    public Object solve() {
        // 1. Open the session.
        client.start();

        // 2. Listen loop: collect useful snippets until the pool is exhausted (or the safety cap).
        var snippets = new ArrayList<Snippet>();
        int maxListens = Math.max(1, props.maxListens());
        for (int i = 1; i <= maxListens; i++) {
            var resp = client.listen();
            var signal = ListenSignal.parse(resp.body(), mapper);
            if (signal.terminal()) {
                log.info("listen#{}: terminal signal — material exhausted (code={}, message='{}')",
                        i, signal.code(), signal.message());
                break;
            }
            router.route(signal, i).ifPresent(snippet -> {
                snippets.add(snippet);
                log.info("collected {} ({} chars)", snippet.source(), snippet.text().length());
            });
            if (i == maxListens) {
                log.warn("Reached maxListens={} without a terminal signal — proceeding with what we have", maxListens);
            }
        }

        if (snippets.isEmpty()) {
            throw new IllegalStateException("No usable material collected from the listen stream");
        }
        log.info("Collected {} useful snippet(s); synthesizing the report", snippets.size());

        // 3. One synthesis call → the four facts.
        var raw = synthesizer.synthesize(snippets);
        validate(raw);
        String cityArea = AreaFormat.round2(raw.areaRaw());

        var report = new LinkedHashMap<String, Object>();
        report.put("cityName", raw.cityName().trim());
        report.put("cityArea", cityArea);
        report.put("warehousesCount", raw.warehousesCount());      // stays a JSON number
        report.put("phoneNumber", raw.phoneNumber().trim());
        log.info("Report for Syjon → {}", report);

        // 4. Transmit (unless dry-run).
        if (props.dryRun()) {
            log.warn("dry-run enabled — NOT transmitting. Computed report: {}", report);
            return Map.of("status", "dry-run", "report", report, "snippets", snippets.size());
        }

        var transmit = client.transmit(report);
        var flag = transmit.flag();
        if (flag.isPresent()) {
            log.info("FLAG → {}", flag.get());
            return Map.of("flag", flag.get(), "report", report);
        }
        log.warn("transmit returned no flag. Feedback: {}", transmit.body());
        return Map.of("status", "no flag", "report", report, "feedback", transmit.body());
    }

    /** All four fields must be present — otherwise fail loudly (revisit the recon / router). */
    private void validate(ReportSynthesizer.RawReport r) {
        var missing = new ArrayList<String>();
        if (r == null) {
            throw new IllegalStateException("synthesis returned null");
        }
        if (!StringUtils.hasText(r.cityName())) {
            missing.add("cityName");
        }
        if (!StringUtils.hasText(r.areaRaw())) {
            missing.add("cityArea");
        }
        if (r.warehousesCount() == null) {
            missing.add("warehousesCount");
        }
        if (!StringUtils.hasText(r.phoneNumber())) {
            missing.add("phoneNumber");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing report field(s) " + missing
                    + " — collected material was insufficient. Synthesis: " + r);
        }
    }
}
