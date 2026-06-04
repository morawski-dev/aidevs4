package com.morawski.dev.aidevs.tasks.task19filesystem;

import com.morawski.dev.aidevs.config.FilesystemProperties;
import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads Natan's free-form Polish notes into a {@link TradeModel} with one strong-model structured call.
 * The three source files play distinct roles (per their README):
 * <ul>
 *   <li>{@code ogloszenia.txt} — each city's demand (good + quantity) → {@code needs},</li>
 *   <li>{@code rozmowy.txt} — Natan's diary; who manages trade in each city → {@code manager}
 *       (first name and surname are often mentioned separately and must be merged),</li>
 *   <li>{@code transakcje.txt} — {@code Seller -> good -> Buyer} lines; the seller offers the good →
 *       {@code offers}.</li>
 * </ul>
 * The model normalises Polish declensions to nominative (singular for goods); {@code FsBuilder}
 * transliterates to ASCII. An LLM is used because the notes need semantic reading: merging split
 * names, inferring that Natan himself manages his home city, and singularising irregular Polish nouns.
 */
@Component
class NotesExtractor {

    private static final Logger log = LoggerFactory.getLogger(NotesExtractor.class);

    private static final String SYSTEM = """
            You extract a structured trade model from a trader's handwritten Polish notes.
            The notes describe a logistics network of towns. Read them carefully and return the model.

            You are given several files. Their roles:
            - an announcements file ("ogloszenia"): each town's DEMAND — what goods it needs and how many.
              Record the bare quantity only, NEVER the unit (e.g. "45 chlebow" -> 45; "120 butelek wody" -> 120;
              "ziemniaki 100 kg" -> 100; "ryz 55 workow" -> 55).
            - a conversations diary ("rozmowy"): who is responsible for trade in each town (the MANAGER).
              A person's first name and surname may appear in DIFFERENT sentences about the same town —
              merge them into one full "First Last". The author of the notes manages his own home town.
            - a transactions file ("transakcje"): lines "Seller -> good -> Buyer". The SELLER OFFERS that good.
              Collect, per town, the distinct goods it sells (the goods where it appears as the seller).

            Rules for the output:
            - One entry per town that participates in trade.
            - Town names: Polish nominative (e.g. "z Opalina" / "do Domatowa" -> "Opalino" / "Domatowo").
            - A good name is the COMMODITY itself, in Polish nominative SINGULAR — never a unit, container
              or portion word. Strip the packaging: "120 butelek wody" -> good "woda"; "25 porcji wolowiny"
              -> "wolowina"; "45 workow ryzu" / "ryz 55 workow" -> "ryz"; "95 kg ziemniakow" / "ziemniaki"
              -> "ziemniak"; "chlebow" -> "chleb"; "lopaty" -> "lopata"; "mlotkow" -> "mlotek"; "wiertarek"
              -> "wiertarka"; "kilofow" -> "kilof"; "porcji kurczaka" -> "kurczak".
            - The transactions file lists goods in their clean canonical form — prefer exactly those spellings
              (but singular). Use the SAME normalised good name in both 'needs' and 'offers'.
            - manager: full "First Last". If the notes give only a surname for a town's contact in one
              sentence and only a first name in another sentence about that same town, they are the same person.
            - Keep Polish letters as written; do not transliterate (that happens later).
            - Do not invent towns, people, goods or quantities that are not in the notes.
            Respond with ONLY the JSON object, no explanation or preamble.
            """;

    private final LlmService llm;
    private final FilesystemProperties props;

    NotesExtractor(LlmService llm, FilesystemProperties props) {
        this.llm = llm;
        this.props = props;
    }

    TradeModel extract(Map<String, String> notes) {
        var user = notes.entrySet().stream()
                .map(e -> "===== FILE: " + e.getKey() + " =====\n" + e.getValue())
                .collect(Collectors.joining("\n\n"));
        // The model occasionally wraps the JSON in prose, which breaks structured parsing; retry a few times.
        RuntimeException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                var model = llm.extract(SYSTEM, user, props.model(), props.maxTokens(), TradeModel.class);
                log.info("Extracted trade model: {} cities", model.cities() == null ? 0 : model.cities().size());
                return model;
            } catch (RuntimeException e) {
                last = e;
                log.warn("Extraction attempt {}/3 failed to parse the model reply: {}", attempt, e.getMessage());
            }
        }
        throw last;
    }
}
