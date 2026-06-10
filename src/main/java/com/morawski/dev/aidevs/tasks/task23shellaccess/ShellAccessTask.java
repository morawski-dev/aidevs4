package com.morawski.dev.aidevs.tasks.task23shellaccess;

import com.morawski.dev.aidevs.config.ShellAccessProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * S05E03 ({@code shellaccess}) — drive a remote Linux server through a single shell channel
 * ({@code POST /verify} with {@code answer:{cmd:...}}) to mine the time-archive logs under {@code /data}
 * for when/where Rafał's body was found, then print the answer JSON. The answer's date must be the day
 * BEFORE the body was found. The same {@code /verify} call both runs the command and returns the
 * {@code {FLG:...}} once the printed JSON is correct.
 *
 * <p>This is an agentic Function-Calling task: {@link ShellAccessConversation} gives a tool-calling
 * model one tool ({@code execute_command}) and Spring AI runs the inner tool loop. Because the task
 * drives the dialog itself and detects its own {@code {FLG:...}}, it is {@link #selfSubmitting()
 * self-submitting}; the {@code TaskRunner} must not submit again. An outer loop re-prompts the agent
 * (in the same conversation memory) up to {@code maxIterations} times so it keeps going after a stall
 * or a partial exploration. Pattern follows task12 {@code firmware}.
 */
@Component
class ShellAccessTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ShellAccessTask.class);
    private static final String CONVERSATION_ID = "shellaccess";

    private static final String INITIAL_PROMPT = """
            Rozpocznij pracę. Obejrzyj katalog /data (ls -la /data, find /data -type f), przeczytaj logi
            i ustal: kiedy znaleziono ciało Rafała, w jakim mieście i jakie są współrzędne tego miejsca.
            Następnie wypisz poprawny JSON — PAMIĘTAJ, że pole date musi być DZIEŃ WCZEŚNIEJ niż data
            znalezienia ciała.""";

    private static final String CONTINUE_PROMPT = """
            Nadal nie mamy flagi. Przeanalizuj dotychczasowe wyniki i ostatnią odpowiedź serwera, ustal
            co jest nie tak (zła data? złe miasto? złe współrzędne? zły format JSON?), popraw i ponownie
            wypisz JSON. PAMIĘTAJ: pole date to DZIEŃ WCZEŚNIEJ niż dzień znalezienia ciała Rafała, a
            liczby (longitude/latitude) podaj bez cudzysłowów.""";

    private final ShellAccessConversation conversation;
    private final ShellAccessTools tools;
    private final ShellAccessProperties props;

    ShellAccessTask(ShellAccessConversation conversation, ShellAccessTools tools, ShellAccessProperties props) {
        this.conversation = conversation;
        this.tools = tools;
        this.props = props;
    }

    @Override
    public String name() {
        return "shellaccess";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "shellaccess.solve")
    public Object solve() {
        tools.reset();
        int maxIter = Math.max(1, props.maxIterations());

        for (int i = 1; i <= maxIter; i++) {
            String prompt = (i == 1) ? INITIAL_PROMPT : CONTINUE_PROMPT;
            log.info("ShellAccess round {}/{}", i, maxIter);
            String reply = conversation.run(CONVERSATION_ID, prompt);
            log.info("Agent (round {}): {}", i, reply);

            var flag = tools.capturedFlag();
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "rounds", i);
            }
            pause();
        }

        log.warn("No flag after {} rounds. Last server reply: {}",
                maxIter, tools.lastFeedback().orElse("(brak — agent nie wysłał polecenia)"));
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
            throw new IllegalStateException("Interrupted while pausing between shellaccess rounds", e);
        }
    }
}
