package com.morawski.dev.aidevs.tasks.task05railway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.RailwayProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * S01E05 ({@code railway}) — activate railway route {@code X-01} through an undocumented,
 * self-documenting API. The {@code help} action returns the full contract; from it the required
 * sequence to change a route's status is: {@code reconfigure} → {@code setstatus} → {@code save}
 * (you must enter reconfigure mode before setting the status). Activating the route means opening
 * it ({@code value=RTOPEN}).
 *
 * <p>This task drives the whole multi-step dialog itself via {@link RailwayClient} (503 retry +
 * rate-limit aware) and detects the {@code {FLG:...}} in one of the responses, so it is
 * {@link #selfSubmitting() self-submitting} — the {@code TaskRunner} must not submit again.
 * The exact actions/parameters/order come straight from {@code help}; nothing is guessed.
 */
@Component
class RailwayTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(RailwayTask.class);

    private final RailwayClient client;
    private final RailwayProperties props;
    private final ObjectMapper mapper;

    RailwayTask(RailwayClient client, RailwayProperties props, ObjectMapper mapper) {
        this.client = client;
        this.props = props;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "railway";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "railway.solve")
    public Object solve() {
        var route = props.routeName();

        // 1. Recon: the API documents itself. Log it so the sequence below can be verified.
        var help = client.call(Map.of("action", "help"));
        var helpFlag = help.flag();
        if (helpFlag.isPresent()) {
            return found(helpFlag.get(), "help");
        }

        // 2. Deterministic activation sequence from help: enter reconfigure mode, open the route,
        //    then save. Check for the flag after every step and stop early on any failure so we
        //    don't burn the restrictive rate limit on doomed follow-up calls.
        var steps = List.<Map<String, Object>>of(
                Map.of("action", "reconfigure", "route", route),
                Map.of("action", "setstatus", "route", route, "value", props.activateValue()),
                Map.of("action", "save", "route", route)
        );

        for (var step : steps) {
            var resp = client.call(step);

            var flag = resp.flag();
            if (flag.isPresent()) {
                return found(flag.get(), String.valueOf(step.get("action")));
            }

            if (!resp.ok() || isLogicalFailure(resp)) {
                log.warn("Action {} failed (HTTP {}). Read the logged body above for the reason "
                        + "(wrong param / wrong order / wrong value), fix it and rerun.",
                        step.get("action"), resp.status());
                return Map.of("error", "action failed", "action", step.get("action"), "status", resp.status());
            }
        }

        log.warn("Activation sequence completed without a flag for route {}. Inspect the logged "
                + "responses — the activation value or order may differ from the assumptions.", route);
        return Map.of("status", "no flag", "route", route);
    }

    private Object found(String flag, String viaAction) {
        log.info("FLAG → {}", flag);
        return Map.of("flag", flag, "viaAction", viaAction);
    }

    /** The API replies with HTTP 200 + {@code {"ok": false, ...}} on logical errors; treat that as a stop. */
    private boolean isLogicalFailure(RailwayResponse resp) {
        try {
            var node = mapper.readTree(resp.body());
            return node.has("ok") && !node.get("ok").asBoolean(true);
        } catch (Exception e) {
            return false; // not JSON we understand — don't assume failure
        }
    }
}
