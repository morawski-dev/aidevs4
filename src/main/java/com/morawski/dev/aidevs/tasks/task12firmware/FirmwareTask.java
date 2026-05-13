package com.morawski.dev.aidevs.tasks.task12firmware;

import com.morawski.dev.aidevs.config.FirmwareProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * S03E02 ({@code firmware}) — boot the cooler controller binary inside a locked-down Linux VM exposed
 * only through a remote shell API ({@code POST /api/shell}), read the {@code ECCS-...} code it prints
 * once it runs correctly, and submit it to the Centrala as {@code {confirmation: ...}}.
 *
 * <p>This is an agentic Function-Calling task: {@link FirmwareConversation} gives a tool-calling
 * model two tools ({@code shell_command}, {@code submit_answer}) and Spring AI runs the inner tool
 * loop. The agent explores the VM, finds the access password (stored in several places), repairs
 * {@code settings.ini}, runs {@code /opt/firmware/cooler/cooler.bin}, and submits the printed code.
 * Because the task drives the dialog itself and detects its own {@code {FLG:...}}, it is
 * {@link #selfSubmitting() self-submitting}; the {@code TaskRunner} must not submit again.
 *
 * <p>An outer loop re-prompts the agent (in the same conversation memory) up to {@code maxIterations}
 * times so it keeps going after a stall, a ban wait, or a partial exploration. The security rules
 * (no {@code /etc} / {@code /root} / {@code /proc}, respect {@code .gitignore}) are enforced both in
 * the system prompt and as a hard block in {@link FirmwareTools} — a violation bans the API and
 * resets the VM.
 */
@Component
class FirmwareTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(FirmwareTask.class);
    private static final String CONVERSATION_ID = "firmware";

    private static final String INITIAL_PROMPT = """
            Rozpocznij pracę. Zacznij od 'help', poznaj powłokę, a następnie doprowadź do poprawnego
            uruchomienia /opt/firmware/cooler/cooler.bin: znajdź hasło dostępowe, popraw settings.ini,
            uruchom binarkę i przepisz wyświetlony kod ECCS-..., po czym wyślij go przez submit_answer.
            Pamiętaj o zasadach bezpieczeństwa (zakaz /etc, /root, /proc oraz plików z .gitignore).""";

    private static final String CONTINUE_PROMPT = """
            Nadal nie mamy flagi. Przeanalizuj dotychczasowe wyniki i feedback Centrali, ustal czego
            jeszcze brakuje (hasło? konfiguracja w settings.ini? sposób uruchomienia binarki?), wykonaj
            kolejne kroki i ponownie uruchom cooler.bin oraz wyślij poprawny kod. Trzymaj się zasad
            bezpieczeństwa. Jeśli zbyt mocno namieszałeś, rozważ 'reboot' i zacznij od czysta.""";

    private final FirmwareConversation conversation;
    private final FirmwareTools tools;
    private final FirmwareProperties props;

    FirmwareTask(FirmwareConversation conversation, FirmwareTools tools, FirmwareProperties props) {
        this.conversation = conversation;
        this.tools = tools;
        this.props = props;
    }

    @Override
    public String name() {
        return "firmware";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "firmware.solve")
    public Object solve() {
        tools.reset();
        int maxIter = Math.max(1, props.maxIterations());

        for (int i = 1; i <= maxIter; i++) {
            String prompt = (i == 1) ? INITIAL_PROMPT : CONTINUE_PROMPT;
            log.info("Firmware round {}/{}", i, maxIter);
            String reply = conversation.run(CONVERSATION_ID, prompt);
            log.info("Agent (round {}): {}", i, reply);

            var flag = tools.capturedFlag();
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "rounds", i);
            }
            pause();
        }

        log.warn("No flag after {} rounds. Last Centrala feedback: {}",
                maxIter, tools.lastFeedback().orElse("(brak — agent nie wysłał kodu)"));
        return Map.of("status", "no flag", "rounds", maxIter,
                "lastFeedback", tools.lastFeedback().orElse(""));
    }

    private void pause() {
        long ms = props.retryPauseMs();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while pausing between firmware rounds", e);
        }
    }
}
