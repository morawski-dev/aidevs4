package com.morawski.dev.aidevs.tasks.task01people;

import com.morawski.dev.aidevs.common.CsvReader;
import com.morawski.dev.aidevs.hub.HubClient;
import com.morawski.dev.aidevs.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class PeopleTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(PeopleTask.class);

    private final HubClient hub;
    private final JobTagger tagger;

    PeopleTask(HubClient hub, JobTagger tagger) {
        this.hub = hub;
        this.tagger = tagger;
    }

    @Override
    public String name() {
        return "people";
    }

    @Override
    public Object solve() {
        log.info("Downloading people.csv...");
        var csv = hub.downloadData("people.csv");
        log.info("Downloaded {} bytes", csv.length);

        var allPeople = CsvReader.read(csv).stream().map(Person::fromCsv).toList();
        log.info("Total people in dataset: {}", allPeople.size());

        var candidates = PersonFilter.apply(allPeople);
        log.info("Candidates after filter (M, age 20-40, Grudziądz): {}", candidates.size());

        var tagged = tagger.tagInBatches(candidates);

        var result = tagged.stream()
                .filter(p -> p.tags().contains("transport"))
                .map(p -> new PeopleAnswer.Entry(p.name(), p.surname(), p.gender(), p.born(), p.city(), p.tags()))
                .toList();

        log.info("Result: {} people with 'transport' tag", result.size());
        return result;
    }
}
