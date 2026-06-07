package com.morawski.dev.aidevs.tasks.task17windpower;

import com.morawski.dev.aidevs.config.WindpowerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic core of the windpower task (the family of {@code RangeValidator}/{@code Rotations}/
 * {@code ReactorPlanner}: pure logic, unit-tested, no network, no LLM). Given the reports it produces
 * the list of {@link ConfigPoint}s — still <em>unsigned</em> ({@code unlockCode == null}); the task
 * fills the signatures in parallel afterwards.
 *
 * <p>Rules from the documentation:
 * <ul>
 *   <li><b>Storm</b> = wind at or above {@code cutoffWindMs} (14 m/s; 14+ damages the blades) → feather
 *       the blades (pitch 90°, 0% yield) and park the turbine ({@code idle}) — no resistance, no production.</li>
 *   <li><b>Production point</b> = a non-storm hour at or above {@code minOperationalWindMs} (4 m/s, below
 *       which it can't generate) → pitch 0° (max capture) and {@code production}. We pick the strongest
 *       such hour: it captures the most power and is the most likely to cover the plant's deficit.</li>
 * </ul>
 * If the cut-off is unknown ({@code NaN}) no hour is treated as a storm (degraded but safe fallback).
 */
@Component
class SchedulePlanner {

    private static final Logger log = LoggerFactory.getLogger(SchedulePlanner.class);

    private final WindpowerProperties props;

    SchedulePlanner(WindpowerProperties props) {
        this.props = props;
    }

    List<ConfigPoint> plan(Reports reports) {
        double cutoff = reports.spec().cutoffWindMs();
        double minWind = Double.isNaN(reports.spec().minOperationalWindMs()) ? 0.0 : reports.spec().minOperationalWindMs();

        var points = new ArrayList<ConfigPoint>();
        WeatherSlot productionCandidate = null;

        for (WeatherSlot slot : reports.weather().slots()) {
            boolean storm = !Double.isNaN(cutoff) && slot.wind() >= cutoff;
            if (storm) {
                // Secure the turbine: feathered blades, parked mode — no resistance, no production.
                points.add(new ConfigPoint(slot.date(), slot.hour(), slot.wind(),
                        props.featherPitch(), props.idleMode(), null));
            } else if (slot.wind() >= minWind
                    && (productionCandidate == null || slot.wind() > productionCandidate.wind())) {
                // Strongest non-storm hour that can actually generate = most power available.
                productionCandidate = slot;
            }
        }

        if (productionCandidate != null) {
            points.add(new ConfigPoint(productionCandidate.date(), productionCandidate.hour(),
                    productionCandidate.wind(), props.productionPitch(), props.productionMode(), null));
            double deficit = reports.requirements().deficitKw();
            log.info("Production point {} at {} m/s (plant deficit {} kW).",
                    productionCandidate.key(), productionCandidate.wind(), deficit);
        } else {
            log.warn("No non-storm hour at/above the minimum operational wind — cannot place a production point.");
        }
        return points;
    }
}
