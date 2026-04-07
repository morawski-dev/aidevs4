package com.morawski.dev.aidevs.tasks.task01people;

import java.time.Year;
import java.util.List;

final class PersonFilter {

    private PersonFilter() {
    }

    static List<Person> apply(List<Person> people) {
        int currentYear = Year.now().getValue();
        return people.stream()
                .filter(p -> "M".equalsIgnoreCase(p.gender()))
                .filter(p -> {
                    int age = currentYear - p.born();
                    return age >= 20 && age <= 40;
                })
                .filter(p -> "Grudziądz".equalsIgnoreCase(p.city()))
                .toList();
    }
}
