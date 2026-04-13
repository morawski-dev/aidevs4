package com.morawski.dev.aidevs.tasks.task01people;

import com.morawski.dev.aidevs.common.CsvReader;
import com.morawski.dev.aidevs.hub.HubClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SuspectsProvider {

    private final HubClient hub;
    private final JobTagger tagger;

    public SuspectsProvider(HubClient hub, JobTagger tagger) {
        this.hub = hub;
        this.tagger = tagger;
    }

    public List<Person> get() {
        var csv = hub.downloadData("people.csv");
        var all = CsvReader.read(csv).stream().map(Person::fromCsv).toList();
        var candidates = PersonFilter.apply(all);
        var tagged = tagger.tagInBatches(candidates);
        return tagged.stream()
                .filter(p -> p.tags().contains("transport"))
                .toList();
    }
}
