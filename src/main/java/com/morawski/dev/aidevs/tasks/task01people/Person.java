package com.morawski.dev.aidevs.tasks.task01people;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.csv.CSVRecord;

import java.util.Arrays;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Person(String name, String surname, String gender, int born, String city, String job,
                     List<String> tags) {

    @JsonCreator
    public static Person fromJson(
            @JsonProperty("name") String name,
            @JsonProperty("surname") String surname,
            @JsonProperty("gender") String gender,
            @JsonProperty("born") int born,
            @JsonProperty("city") String city,
            @JsonProperty("job") String job) {
        return new Person(name, surname, gender, born, city, job, List.of());
    }

    public static Person fromCsv(CSVRecord row) {
        // birthDate is YYYY-MM-DD — extract year only
        var birthYear = Integer.parseInt(col(row, "birthDate").substring(0, 4));
        return new Person(
                col(row, "name"),
                col(row, "surname"),
                col(row, "gender"),
                birthYear,
                col(row, "birthPlace"),
                col(row, "job"),
                List.of()
        );
    }

    public Person withTags(List<String> newTags) {
        return new Person(name, surname, gender, born, city, job, newTags);
    }

    private static String col(CSVRecord row, String... keys) {
        for (var key : keys) {
            if (row.isMapped(key)) {
                var val = row.get(key);
                if (val != null && !val.isBlank()) return val.strip();
            }
        }
        throw new IllegalArgumentException(
                "None of %s found in CSV. Available headers: %s"
                        .formatted(Arrays.asList(keys), row.getParser().getHeaderNames()));
    }
}
