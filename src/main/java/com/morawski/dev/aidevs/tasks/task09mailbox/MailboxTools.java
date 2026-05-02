package com.morawski.dev.aidevs.tasks.task09mailbox;

import com.morawski.dev.aidevs.config.MailboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tools exposed to the LLM for the mailbox task (Spring AI generates the JSON schema and runs the
 * tool-execution loop). Three tools mirror the task's shape: search for mails, read full bodies,
 * and submit the three answer fields. {@code submit_answer} captures any {@code {FLG:...}} so the
 * orchestrating {@link MailboxTask} can stop once the Hub accepts all three values.
 */
@Component
class MailboxTools {

    private static final Logger log = LoggerFactory.getLogger(MailboxTools.class);

    /** confirmation_code = "SEC-" + 32 chars = 36 chars total. */
    private static final Pattern CODE = Pattern.compile("^SEC-.{32}$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final ZmailClient zmail;
    private final MailboxProperties props;

    /** Set once the Hub returns a flag for a correct answer; read by {@link MailboxTask}. */
    private volatile String flag;
    /** Last raw {@code /verify} feedback, for diagnostics when the loop gives up. */
    private volatile String lastFeedback;

    MailboxTools(ZmailClient zmail, MailboxProperties props) {
        this.zmail = zmail;
        this.props = props;
    }

    @Tool(name = "search_mail",
            description = "Przeszukuje skrzynkę. Zwraca TYLKO metadane wiadomości (messageID, from, to, "
                    + "subject, date, snippet) — bez pełnej treści. Obsługuje operatory jak Gmail: słowa, "
                    + "\"fraza\", -wyklucz, from:, to:, subject:, OR, AND (brak operatora = AND). "
                    + "Aby przeczytać treść, użyj messageID w read_messages.")
    String searchMail(
            @ToolParam(description = "Zapytanie, np. from:proton.me, subject:hasło OR password, atak elektrownia")
            String query,
            @ToolParam(description = "Numer strony wyników (>=1).") int page) {
        int p = page >= 1 ? page : 1;
        return zmail.search(query, p, props.perPage()).body();
    }

    @Tool(name = "read_messages",
            description = "Pobiera PEŁNĄ treść wiadomości po identyfikatorze. Podaj 32-znakowy messageID "
                    + "(hash) — jest stabilny. NIE używaj rowID, bo zmienia się, gdy do skrzynki wpływają "
                    + "nowe maile. Można podać kilka messageID rozdzielonych przecinkami.")
    String readMessages(
            @ToolParam(description = "Jeden messageID (32-znakowy hash) lub kilka rozdzielonych przecinkami.")
            String ids) {
        if (!StringUtils.hasText(ids)) {
            return "Błąd: podaj przynajmniej jeden messageID.";
        }
        return zmail.getMessages(ids).body();
    }

    @Tool(name = "submit_answer",
            description = "Wysyła znalezione wartości do Centrali i zwraca jej feedback (które pole jest złe "
                    + "lub brakuje) albo flagę. Wszystkie trzy pola są wymagane. confirmation_code musi mieć "
                    + "format SEC- + 32 znaki (łącznie 36 znaków), date format YYYY-MM-DD.")
    String submitAnswer(
            @ToolParam(description = "Data planowanego ataku na elektrownię, format YYYY-MM-DD.") String date,
            @ToolParam(description = "Hasło do systemu pracowniczego.") String password,
            @ToolParam(description = "Kod potwierdzenia z ticketa działu bezpieczeństwa: SEC- + 32 znaki.")
            String confirmationCode) {

        // Cheap local format checks so we don't waste a round-trip on an obviously malformed code/date.
        var problems = new StringBuilder();
        if (!StringUtils.hasText(date) || !DATE.matcher(date.trim()).matches()) {
            problems.append("date musi mieć format YYYY-MM-DD (otrzymano: '").append(date).append("'). ");
        }
        if (!StringUtils.hasText(password)) {
            problems.append("password jest puste. ");
        }
        if (!StringUtils.hasText(confirmationCode) || !CODE.matcher(confirmationCode.trim()).matches()) {
            problems.append("confirmation_code musi mieć format SEC- + 32 znaki = 36 znaków (otrzymano: '")
                    .append(confirmationCode).append("'). ");
        }
        if (problems.length() > 0) {
            log.info("submit_answer odrzucone lokalnie: {}", problems);
            return "Nie wysłano — popraw format: " + problems + "Popraw wartości i spróbuj ponownie.";
        }

        var answer = new LinkedHashMap<String, Object>();
        answer.put("date", date.trim());
        answer.put("password", password.trim());
        answer.put("confirmation_code", confirmationCode.trim());

        var resp = zmail.verify(answer);
        lastFeedback = resp.body();
        var found = resp.flag();
        if (found.isPresent()) {
            flag = found.get();
            return "SUKCES! Centrala zaakceptowała odpowiedź. Flaga znaleziona — zakończ pracę.";
        }
        return "Centrala odrzuciła odpowiedź. Feedback (które pole jest złe/brakuje): " + resp.body();
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
