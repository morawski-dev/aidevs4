package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.morawski.dev.aidevs.config.RadiomonitoringProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Single strong-model synthesis of the four report fields from all collected {@link Snippet}s. The
 * router has already done the cost-heavy lifting (only useful text reaches here), so this is one
 * cheap call. "Syjon" is a codename — the model must resolve it to the real city and pull the area
 * (as a raw number, <em>not</em> rounded — Java rounds), warehouse count and contact phone.
 *
 * <p>We use a plain-text chat call and parse the JSON out ourselves rather than Spring AI's strict
 * {@code BeanOutputConverter}: the strong model (Anthropic via OpenRouter) likes to prepend a line or
 * two of reasoning before the JSON object, which the strict converter rejects. Extracting the
 * outermost {@code { ... }} tolerates that.
 */
@Component
class ReportSynthesizer {

    private static final String SYSTEM = """
            You are an intelligence analyst piecing together intercepted radio fragments. The fragments
            (transcriptions, OCR'd images, decoded files) collectively describe a city referred to by
            the CODENAME "Syjon" (Zion). Some fragments are noise — ignore them.

            From the fragments, determine EXACTLY these four facts about the city codenamed "Syjon":
              - cityName: the REAL name of the city (not the codename "Syjon").
              - areaRaw: the city's area as a plain number string (e.g. "12.3" or "12.345"). DO NOT round
                         it — return the value exactly as found. Use a dot as the decimal separator.
              - warehousesCount: how many warehouses are in the city, as an integer.
              - phoneNumber: the phone number of the city's contact person (digits, keep as given).

            Cross-reference fragments: the codename, the real name, the area, the warehouse count and the
            phone may each appear in DIFFERENT fragments (including transcribed audio and OCR'd images).
            Search ALL fragments carefully before concluding a value is absent.

            Respond with a single JSON object and these exact keys:
            {"cityName": "...", "areaRaw": "...", "warehousesCount": 0, "phoneNumber": "..."}
            You may reason briefly first, but the JSON object MUST appear in your reply.""";

    private final LlmService llm;
    private final RadiomonitoringProperties props;
    private final ObjectMapper mapper;

    ReportSynthesizer(LlmService llm, RadiomonitoringProperties props, ObjectMapper mapper) {
        this.llm = llm;
        this.props = props;
        this.mapper = mapper;
    }

    RawReport synthesize(List<Snippet> snippets) {
        var user = new StringBuilder("Intercepted fragments:\n\n");
        for (Snippet s : snippets) {
            user.append("=== ").append(s.source()).append(" ===\n").append(s.text()).append("\n\n");
        }
        String reply = llm.chat(SYSTEM, user.toString(), props.synthModel(), props.synthMaxTokens());
        return parse(reply);
    }

    /** Pull the outermost {@code { ... }} object out of the reply (tolerating a prose preamble / fences). */
    private RawReport parse(String reply) {
        if (reply == null) {
            throw new IllegalStateException("synthesis returned no content");
        }
        int start = reply.indexOf('{');
        int end = reply.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("no JSON object found in synthesis reply: " + reply);
        }
        String json = reply.substring(start, end + 1);
        try {
            return mapper.readValue(json, RawReport.class);
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse synthesis JSON: " + json, e);
        }
    }

    /** The four facts, as read from the material. {@code areaRaw} is rounded deterministically later. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawReport(String cityName, String areaRaw, Integer warehousesCount, String phoneNumber) {
    }
}
