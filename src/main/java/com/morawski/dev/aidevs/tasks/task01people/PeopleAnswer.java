package com.morawski.dev.aidevs.tasks.task01people;

import java.util.List;

record PeopleAnswer(List<Entry> people) {

    record Entry(String name, String surname, String gender, int born, String city, List<String> tags) {
    }
}
