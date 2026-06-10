package com.morawski.dev.aidevs.tasks.task23shellaccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * Single tool exposed to the LLM for the shellaccess task (Spring AI generates the JSON schema and runs
 * the tool-execution loop). One tool is enough: the same {@code /verify} call both runs a shell command
 * on the server and — once the printed stdout is the correct answer JSON — returns the {@code {FLG:...}}.
 * So exploration and submission share one path; {@link #executeCommand} captures any flag so the
 * orchestrating {@link ShellAccessTask} can stop.
 *
 * <p>No forbidden-path guard is needed (unlike task12 firmware): the brief grants standard Linux tools
 * over {@code /data} and sets no security trap — the tool is deliberately thin.
 */
@Component
class ShellAccessTools {

    private static final Logger log = LoggerFactory.getLogger(ShellAccessTools.class);

    private final ShellAccessClient client;

    /** Set once the Hub returns a flag for the correct JSON; read by {@link ShellAccessTask}. */
    private volatile String flag;
    /** Last raw {@code /verify} reply, for diagnostics when the loop gives up. */
    private volatile String lastFeedback;

    ShellAccessTools(ShellAccessClient client) {
        this.client = client;
    }

    @Tool(name = "execute_command",
            description = "Wykonuje JEDNO polecenie powłoki na zdalnym serwerze i zwraca jego surowe wyjście "
                    + "(stdout/stderr). Serwer ma standardowe narzędzia Linuksa oraz 'jq' i 'grep'. Logi "
                    + "znajdują się w katalogu /data. Aby ZGŁOSIĆ odpowiedź, wypisz na ekran (np. echo lub "
                    + "jq -n) poprawny JSON — gdy będzie prawidłowy, serwer zwróci flagę w tym samym wyniku.")
    String executeCommand(
            @ToolParam(description = "Pojedyncze polecenie powłoki do wykonania na serwerze.")
            String command) {
        if (!StringUtils.hasText(command)) {
            return "Błąd: puste polecenie.";
        }
        var resp = client.run(command.trim());
        lastFeedback = resp.body();
        var found = resp.flag();
        if (found.isPresent()) {
            flag = found.get();
            log.info("Flag captured: {}", found.get());
            return "SUKCES! Serwer zaakceptował JSON i zwrócił flagę — zakończ pracę.\n" + resp.body();
        }
        return resp.body();
    }

    Optional<String> capturedFlag() {
        return Optional.ofNullable(flag);
    }

    Optional<String> lastFeedback() {
        return Optional.ofNullable(lastFeedback);
    }

    /** Clear captured state at the start of a run (the bean is a singleton). */
    void reset() {
        this.flag = null;
        this.lastFeedback = null;
    }
}
