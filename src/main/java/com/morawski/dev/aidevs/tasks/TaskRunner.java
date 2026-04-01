package com.morawski.dev.aidevs.tasks;

import com.morawski.dev.aidevs.hub.FlagExtractor;
import com.morawski.dev.aidevs.hub.HubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class TaskRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final Map<String, Task> tasks;
    private final HubClient hub;

    TaskRunner(List<Task> taskList, HubClient hub) {
        this.tasks = taskList.stream().collect(Collectors.toMap(Task::name, Function.identity()));
        this.hub = hub;
    }

    @Override
    public void run(ApplicationArguments args) {
        var values = args.getOptionValues("task");
        if (values == null || values.isEmpty()) {
            log.info("No --task specified. Available: {}", tasks.keySet());
            return;
        }
        var taskName = values.getFirst();
        var task = Objects.requireNonNull(tasks.get(taskName),
                "Unknown task: %s. Available: %s".formatted(taskName, tasks.keySet()));

        log.info("Running task: {}", taskName);
        var answer = task.solve();
        var response = hub.submit(taskName, answer);
        log.info("Hub response: {}", response);
        FlagExtractor.extract(response).ifPresent(flag -> log.info("FLAG → {}", flag));
    }
}
