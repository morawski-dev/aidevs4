package com.morawski.dev.aidevs.tasks.task01people;

import com.morawski.dev.aidevs.llm.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class JobTagger {

    private static final Logger log = LoggerFactory.getLogger(JobTagger.class);
    private static final int BATCH_SIZE = 20;

    private static final String SYSTEM_PROMPT = """
            Twoim zadaniem jest przypisanie tagów do listy osób na podstawie opisu ich pracy.
            Opis jest pełnym zdaniem w języku polskim — przeanalizuj go i dobierz odpowiednie tagi.

            Dostępne tagi (użyj dokładnie tych nazw):
            - IT: Programiści, administratorzy systemów, analitycy danych, testerzy, devops, inżynierowie oprogramowania.
            - transport: Kierowcy, kurierzy, logistycy, spedytorzy, piloci, marynarze, taksówkarze, operatorzy pojazdów.
            - edukacja: Nauczyciele, wykładowcy, trenerzy, pedagodzy, wychowawcy, instruktorzy.
            - medycyna: Lekarze, pielęgniarki, farmaceuci, dentyści, ratownicy medyczni, terapeuci.
            - praca z ludźmi: Obsługa klienta, sprzedawcy, menedżerowie, doradcy, pracownicy socjalni, HR.
            - praca z pojazdami: Mechanicy, serwisanci, operatorzy maszyn budowlanych, kierowcy, piloci.
            - praca fizyczna: Budowlańcy, robotnicy, magazynierzy, rolnicy, pracownicy produkcji, monterzy.

            Dla każdej osoby (identyfikowanej przez pole id) zwróć odpowiednie tagi.
            Jedna osoba może mieć wiele tagów. Odpowiedz wyłącznie w formacie JSON.
            """;

    record TaggedPerson(int id, List<String> tags) {
    }

    record TagBatchResult(List<TaggedPerson> people) {
    }

    private final LlmService llm;

    JobTagger(LlmService llm) {
        this.llm = llm;
    }

    List<Person> tagInBatches(List<Person> people) {
        var result = new ArrayList<>(people);

        for (int i = 0; i < people.size(); i += BATCH_SIZE) {
            var batch = people.subList(i, Math.min(i + BATCH_SIZE, people.size()));
            log.info("Tagging batch {}/{}", (i / BATCH_SIZE) + 1, (people.size() + BATCH_SIZE - 1) / BATCH_SIZE);
            var tagged = tagBatch(batch, i);
            for (var t : tagged.people()) {
                result.set(t.id(), result.get(t.id()).withTags(t.tags()));
            }
        }
        return result;
    }

    private TagBatchResult tagBatch(List<Person> batch, int offset) {
        var sb = new StringBuilder("Przypisz tagi zawodowe do poniższych osób:\n\n");
        for (int i = 0; i < batch.size(); i++) {
            sb.append("id: ").append(offset + i)
                    .append(", zawód: ").append(batch.get(i).job())
                    .append("\n");
        }
        return llm.extract(SYSTEM_PROMPT, sb.toString(), TagBatchResult.class);
    }
}
