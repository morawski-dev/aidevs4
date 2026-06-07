package com.morawski.dev.aidevs.tasks.task17windpower;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw windpower bodies into typed values. Field names below are the real ones confirmed by the
 * recon pass:
 * <ul>
 *   <li>{@code documentation} → {@link TurbineSpec}: {@code safety.cutoffWindMs} (storm threshold) and
 *       {@code safety.minOperationalWindMs} (min generating wind).</li>
 *   <li>{@code get(weather)} → {@link WeatherReport}: {@code forecast[]} of {@code timestamp}+{@code windMs}.</li>
 *   <li>{@code powerplantcheck} → {@link PlantRequirements}: {@code powerDeficitKw} (a range like {@code "3-4"}).</li>
 * </ul>
 * Queued results ({@code getResult}) are tagged with {@code sourceFunction}; {@link #classify(JsonNode)}
 * routes by that tag. Pure logic over a JSON tree (no I/O, no LLM).
 */
@Component
class ReportParser {

    enum ReportType { WEATHER, TURBINE, REQUIREMENTS, UNKNOWN }

    private static final String[] SLOT_ARRAY_KEYS = {"forecast", "weather", "hours", "slots", "entries"};
    private static final String[] WIND_KEYS = {"windms", "wind", "speed"};
    private static final String[] DATE_KEYS = {"startdate", "date", "data", "day"};
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    /**
     * Classify a {@code getResult} payload by its {@code sourceFunction} tag ({@code weather} /
     * {@code turbinecheck} / {@code powerplantcheck} / {@code unlockCodeGenerator}). An empty-queue reply
     * ("no completed response yet") has no source → UNKNOWN.
     */
    ReportType classify(JsonNode node) {
        String source = Json.findText(node, "sourcefunction", "source");
        if (source == null) {
            return ReportType.UNKNOWN;
        }
        String s = source.toLowerCase();
        if (s.contains("weather")) {
            return ReportType.WEATHER;
        }
        if (s.contains("turbine")) {
            return ReportType.TURBINE;
        }
        if (s.contains("powerplant") || s.contains("power")) {
            return ReportType.REQUIREMENTS;
        }
        return ReportType.UNKNOWN;
    }

    /** Turbine limits from the documentation payload: storm cut-off and min generating wind. */
    TurbineSpec spec(JsonNode docs) {
        Double cutoff = Json.findNumber(docs, "cutoffwindms", "cutoff", "shutdown");
        Double minOp = Json.findNumber(docs, "minoperationalwindms", "minoperational", "minwind");
        return new TurbineSpec(cutoff == null ? Double.NaN : cutoff, minOp == null ? Double.NaN : minOp);
    }

    WeatherReport weather(JsonNode node) {
        JsonNode array = Json.findObjectArray(node, SLOT_ARRAY_KEYS);
        List<WeatherSlot> slots = new ArrayList<>();
        if (array != null) {
            for (JsonNode item : array) {
                Double wind = Json.findNumber(item, WIND_KEYS);
                if (wind == null) {
                    continue;
                }
                String[] when = resolveDateHour(item);
                if (when[0] == null || when[1] == null) {
                    continue;
                }
                slots.add(new WeatherSlot(when[0], when[1], wind));
            }
        }
        return new WeatherReport(slots);
    }

    /** The plant's power deficit; {@code powerDeficitKw} is a range string like {@code "3-4"} → upper bound 4. */
    PlantRequirements requirements(JsonNode node) {
        String deficit = Json.findText(node, "powerdeficit", "deficit", "missing", "required");
        double kw = upperBound(deficit);
        return new PlantRequirements(Double.isNaN(kw) ? 0.0 : kw);
    }

    /**
     * Resolve a slot's {@code date}/{@code hour}. The weather feed carries a single combined
     * {@code timestamp} ({@code "YYYY-MM-DD HH:MM:SS"}); it's read first and split, with any explicit
     * separate {@code date}/{@code hour} fields overriding it. Hour is normalised to a full hour
     * {@code "HH:00:00"} (the task requires minutes/seconds = 0).
     *
     * <p>The timestamp is fetched by narrow keys, not a broad hour matcher — otherwise {@code "timestamp"}
     * would match a {@code "time"} candidate and be mis-read as the hour.
     */
    private static String[] resolveDateHour(JsonNode item) {
        String datePart = null;
        String hourPart = null;

        String timestamp = Json.findText(item, "timestamp", "datetime");
        if (timestamp != null) {
            String w = timestamp.trim();
            String[] parts = w.contains("T") ? w.split("T", 2) : w.split("\\s+", 2);
            datePart = parts[0];
            if (parts.length == 2) {
                hourPart = parts[1];
            }
        }

        String date = Json.findText(item, DATE_KEYS);
        String hour = Json.findText(item, "starthour", "godzin");
        if (date != null) {
            datePart = date;
        }
        if (hour != null) {
            hourPart = hour;
        }
        return new String[]{datePart == null ? null : datePart.trim(), normalizeHour(hourPart)};
    }

    /** Normalise an hour to {@code "HH:00:00"} from {@code "H"}, {@code "HH"}, {@code "HH:MM"} or {@code "HH:MM:SS"}. */
    static String normalizeHour(String hour) {
        if (hour == null) {
            return null;
        }
        String h = hour.trim();
        if (h.isEmpty()) {
            return null;
        }
        int colon = h.indexOf(':');
        String hh = colon >= 0 ? h.substring(0, colon) : h;
        try {
            int value = Integer.parseInt(hh.trim());
            if (value < 0 || value > 23) {
                return null;
            }
            return "%02d:00:00".formatted(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Largest number in a string — handles a plain value, a range ({@code "3-4"} → 4) or {@code "14+"} → 14. */
    static double upperBound(String text) {
        if (text == null) {
            return Double.NaN;
        }
        double max = Double.NaN;
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            double v = Double.parseDouble(m.group().replace(',', '.'));
            max = Double.isNaN(max) ? v : Math.max(max, v);
        }
        return max;
    }
}
