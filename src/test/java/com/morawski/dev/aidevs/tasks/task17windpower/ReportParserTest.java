package com.morawski.dev.aidevs.tasks.task17windpower;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportParserTest {

    private final ReportParser parser = new ReportParser();

    // The real weather report shape from recon: forecast[] of { timestamp, windMs, precipitationMm, temperatureC }.
    private static final String WEATHER_BODY = """
            {
              "code": 12,
              "message": "Queued response retrieved.",
              "sourceFunction": "weather",
              "intervalHours": 2,
              "forecastDays": 7,
              "unit": { "windMs": "m/s", "precipitationMm": "mm", "temperatureC": "C" },
              "forecast": [
                { "timestamp": "2026-06-15 00:00:00", "windMs": 3.4, "precipitationMm": 0, "temperatureC": 26.8 },
                { "timestamp": "2026-06-16 18:00:00", "windMs": 25,  "precipitationMm": 0, "temperatureC": 31.6 },
                { "timestamp": "2026-06-15 14:00:00", "windMs": 3.9, "precipitationMm": 0, "temperatureC": 30.9 }
              ]
            }
            """;

    @Test
    void classifiesBySourceFunction() {
        assertThat(parser.classify(Json.parse(WEATHER_BODY))).isEqualTo(ReportParser.ReportType.WEATHER);
        assertThat(parser.classify(Json.parse("{\"sourceFunction\":\"turbinecheck\"}"))).isEqualTo(ReportParser.ReportType.TURBINE);
        assertThat(parser.classify(Json.parse("{\"sourceFunction\":\"powerplantcheck\"}"))).isEqualTo(ReportParser.ReportType.REQUIREMENTS);
        // empty-queue reply has no sourceFunction
        assertThat(parser.classify(Json.parse("{\"code\":11,\"message\":\"No completed queued response is available yet.\"}")))
                .isEqualTo(ReportParser.ReportType.UNKNOWN);
    }

    @Test
    void parsesWeatherTimestampIntoFullHourSlots() {
        var slots = parser.weather(Json.parse(WEATHER_BODY)).slots();

        assertThat(slots).hasSize(3);
        assertThat(slots.get(0).date()).isEqualTo("2026-06-15");
        assertThat(slots.get(0).hour()).isEqualTo("00:00:00");
        assertThat(slots.get(0).wind()).isEqualTo(3.4);
        // the storm hour (25 m/s) keys correctly to "YYYY-MM-DD HH:00:00"
        assertThat(slots.get(1).key()).isEqualTo("2026-06-16 18:00:00");
        assertThat(slots.get(1).wind()).isEqualTo(25.0);
    }

    @Test
    void normalizeHourHandlesVariants() {
        assertThat(ReportParser.normalizeHour("18:00:00")).isEqualTo("18:00:00");
        assertThat(ReportParser.normalizeHour("9")).isEqualTo("09:00:00");
        assertThat(ReportParser.normalizeHour("23:45")).isEqualTo("23:00:00");
        assertThat(ReportParser.normalizeHour("not-an-hour")).isNull();
    }

    @Test
    void parsesTurbineLimitsFromDocumentationSafetyBlock() {
        // The real documentation payload nests the thresholds under "safety".
        String docs = """
                {
                  "code": 50,
                  "ratedPowerKw": 14,
                  "safety": {
                    "cutoffWindMs": 14,
                    "minOperationalWindMs": 4
                  }
                }
                """;
        var spec = parser.spec(Json.parse(docs));
        assertThat(spec.cutoffWindMs()).isEqualTo(14.0);
        assertThat(spec.minOperationalWindMs()).isEqualTo(4.0);
    }

    @Test
    void parsesPowerDeficitRangeToUpperBound() {
        // powerplantcheck reports the deficit as a range string "3-4" → we must cover the upper bound.
        String body = """
                { "sourceFunction": "powerplantcheck", "powerDeficitKw": "3-4", "producedPowerKw": 0 }
                """;
        assertThat(parser.requirements(Json.parse(body)).deficitKw()).isEqualTo(4.0);
    }

    @Test
    void upperBoundHandlesPlainRangeAndPlus() {
        assertThat(ReportParser.upperBound("3-4")).isEqualTo(4.0);
        assertThat(ReportParser.upperBound("14+")).isEqualTo(14.0);
        assertThat(ReportParser.upperBound("7")).isEqualTo(7.0);
        assertThat(ReportParser.upperBound(null)).isNaN();
    }
}
