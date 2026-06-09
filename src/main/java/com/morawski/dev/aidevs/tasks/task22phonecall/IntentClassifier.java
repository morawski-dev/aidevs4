package com.morawski.dev.aidevs.tasks.task22phonecall;

import com.morawski.dev.aidevs.config.PhonecallProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Classifies the operator's transcribed turn into an {@link Intent}. A cheap, deterministic keyword pass
 * (Polish) handles the obvious cases; when it's inconclusive, a strong model resolves the rest (the
 * operator's phrasing can be indirect, and Polish negation is easy to misread). The state machine itself
 * stays deterministic — this only <em>reads</em> the operator.
 */
@Component
class IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(IntentClassifier.class);

    private static final List<String> PASSWORD_WORDS = List.of("hasł", "uwierzytel", "autoryz", "zweryfik", "tożsam");
    private static final List<String> WHY_WORDS = List.of("dlaczego", "po co", "w jakim celu", "z jakiego powodu", "czemu");
    private static final List<String> CONFIRM_WORDS = List.of(
            "wyłączon", "wyłączył", "wyłączam", "wyłączę", "zdjęt", "deaktyw", "dezaktyw", "zrobione", "gotowe", "załatwione");

    private static final String SYSTEM = """
            Jesteś klasyfikatorem intencji operatora w rozmowie telefonicznej. Na podstawie WYPOWIEDZI OPERATORA
            zwróć dokładnie jedną etykietę:
            - ASKS_PASSWORD: operator prosi o hasło / uwierzytelnienie / potwierdzenie tożsamości.
            - ASKS_WHY: operator pyta, dlaczego / po co chcemy wyłączyć monitoring.
            - CONFIRMS_DISABLED: operator potwierdza, że monitoring został wyłączony / sprawa załatwiona.
            - OTHER: cokolwiek innego (powitanie, podanie statusu dróg, pytanie inne niż powyższe).
            Odpowiadasz wyłącznie polami struktury.
            """;

    private final LlmService llm;
    private final PhonecallProperties props;

    IntentClassifier(LlmService llm, PhonecallProperties props) {
        this.llm = llm;
        this.props = props;
    }

    Intent classify(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return Intent.OTHER;
        }
        String text = transcript.toLowerCase(Locale.ROOT);

        if (containsAny(text, PASSWORD_WORDS)) {
            return Intent.ASKS_PASSWORD;
        }
        if (containsAny(text, WHY_WORDS)) {
            return Intent.ASKS_WHY;
        }
        if (containsAny(text, CONFIRM_WORDS)) {
            return Intent.CONFIRMS_DISABLED;
        }
        return llmClassify(transcript);
    }

    private Intent llmClassify(String transcript) {
        try {
            // Cap max_tokens: this is a one-word label, and OpenRouter 402s when the default budget
            // (65536) exceeds what the account can afford.
            var verdict = llm.extract(SYSTEM, "WYPOWIEDŹ OPERATORA:\n" + transcript, props.classifyModel(), 256, IntentVerdict.class);
            if (verdict != null && verdict.intent() != null) {
                return switch (verdict.intent().trim().toUpperCase(Locale.ROOT)) {
                    case "ASKS_PASSWORD" -> Intent.ASKS_PASSWORD;
                    case "ASKS_WHY" -> Intent.ASKS_WHY;
                    case "CONFIRMS_DISABLED" -> Intent.CONFIRMS_DISABLED;
                    default -> Intent.OTHER;
                };
            }
        } catch (Exception e) {
            log.warn("Intent LLM fallback failed, defaulting to OTHER: {}", e.getMessage());
        }
        return Intent.OTHER;
    }

    private static boolean containsAny(String text, List<String> needles) {
        return needles.stream().anyMatch(text::contains);
    }

    /** Structured-output shape for the LLM fallback. */
    record IntentVerdict(String intent) {
    }
}
