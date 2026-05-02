package com.morawski.dev.aidevs.tasks.task09mailbox;

import com.morawski.dev.aidevs.config.MailboxProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * S03 ({@code mailbox}) — search a compromised operator's mailbox (the {@code zmail} API, which
 * behaves like Gmail search) for three values and submit them to the Centrala:
 * <ul>
 *   <li>{@code date} — when the security team plans to attack our power plant (YYYY-MM-DD),</li>
 *   <li>{@code password} — the employee-system password still sitting in the mailbox,</li>
 *   <li>{@code confirmation_code} — the security ticket's code ({@code SEC-} + 32 chars).</li>
 * </ul>
 *
 * <p>This is an agentic Function-Calling task: {@link MailboxConversation} gives a tool-calling
 * model three tools ({@code search_mail}, {@code read_messages}, {@code submit_answer}) and Spring
 * AI runs the inner tool loop. Because the mailbox is <em>active</em> (new mail can arrive mid-run)
 * and the Hub's feedback drives correction, this task drives the dialog itself and detects its own
 * {@code {FLG:...}} — it is {@link #selfSubmitting() self-submitting}; the {@code TaskRunner} must
 * not submit again.
 *
 * <p>An outer loop re-prompts the agent (in the same conversation memory) up to
 * {@code maxIterations} times so it keeps searching/retrying — the missing value may only have just
 * arrived in the mailbox.
 */
@Component
class MailboxTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(MailboxTask.class);
    private static final String CONVERSATION_ID = "mailbox";

    private static final String INITIAL_PROMPT = """
            Rozpocznij pracę. Znajdź trzy wartości (date, password, confirmation_code), a gdy je masz,
            wyślij je przez submit_answer. Korzystaj z feedbacku Centrali, aż dostaniesz flagę.""";

    private static final String CONTINUE_PROMPT = """
            Nadal nie mamy flagi. Przeanalizuj ostatni feedback Centrali, popraw błędne lub uzupełnij
            brakujące wartości i wyślij ponownie. Skrzynka jest aktywna — jeśli czegoś brakuje, ponów
            wyszukiwanie (mogły wpłynąć nowe maile) i spróbuj innych zapytań.""";

    private final MailboxConversation conversation;
    private final MailboxTools tools;
    private final ZmailClient zmail;
    private final MailboxProperties props;

    MailboxTask(MailboxConversation conversation, MailboxTools tools, ZmailClient zmail, MailboxProperties props) {
        this.conversation = conversation;
        this.tools = tools;
        this.zmail = zmail;
        this.props = props;
    }

    @Override
    public String name() {
        return "mailbox";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "mailbox.solve")
    public Object solve() {
        tools.reset();
        // Clear this apikey's request counter so the agentic loop has a fresh budget (the zmail API
        // tracks requests per apikey and exposes a reset action for exactly this).
        zmail.reset();
        int maxIter = Math.max(1, props.maxIterations());

        for (int i = 1; i <= maxIter; i++) {
            String prompt = (i == 1) ? INITIAL_PROMPT : CONTINUE_PROMPT;
            log.info("Mailbox round {}/{}", i, maxIter);
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
                maxIter, tools.lastFeedback().orElse("(brak — agent nie wysłał odpowiedzi)"));
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
            throw new IllegalStateException("Interrupted while pausing between mailbox rounds", e);
        }
    }
}
