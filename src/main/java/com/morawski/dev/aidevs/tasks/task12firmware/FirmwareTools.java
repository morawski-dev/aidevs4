package com.morawski.dev.aidevs.tasks.task12firmware;

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
 * Tools exposed to the LLM for the firmware task (Spring AI generates the JSON schema and runs the
 * tool-execution loop). Two tools mirror the task's shape: run a shell command on the VM, and submit
 * the {@code ECCS-...} code. {@code submit_answer} captures any {@code {FLG:...}} so the
 * orchestrating {@link FirmwareTask} can stop once the Hub accepts the code.
 *
 * <p><b>Safety:</b> the security rules carry a hard penalty (touching {@code /etc}, {@code /root},
 * {@code /proc} or a directory's {@code .gitignore}-listed paths → API ban + VM reset). The system
 * prompt forbids them, but {@link #shellCommand} also <em>hard-blocks</em> the absolute forbidden
 * roots before any network call — defense-in-depth so one bad model decision can't trigger a ban.
 */
@Component
class FirmwareTools {

    private static final Logger log = LoggerFactory.getLogger(FirmwareTools.class);

    /** The on-screen code we must submit: {@code ECCS-} + 40 chars = 45 chars total. */
    private static final Pattern CODE = Pattern.compile("^ECCS-.{40}$");
    /** Reject the literal placeholder shown in the brief ({@code ECCS-xxxx...}). */
    private static final Pattern PLACEHOLDER = Pattern.compile("^ECCS-x+$", Pattern.CASE_INSENSITIVE);

    /**
     * Forbidden absolute roots — matched as whole path tokens so {@code /opt/firmware/...} work is
     * never blocked. Catches {@code /etc}, {@code /root}, {@code /proc} and their children
     * ({@code /etc/...}, {@code /proc/cpuinfo}, …) wherever they appear as an argument.
     */
    private static final Pattern FORBIDDEN_PATH =
            Pattern.compile("(^|[\\s=:'\"(])/(etc|root|proc)(/|\\b)");

    private final ShellClient client;

    /** Set once the Hub returns a flag for the accepted code; read by {@link FirmwareTask}. */
    private volatile String flag;
    /** Last raw {@code /verify} feedback, for diagnostics when the loop gives up. */
    private volatile String lastFeedback;

    FirmwareTools(ShellClient client) {
        this.client = client;
    }

    @Tool(name = "shell_command",
            description = "Wykonuje JEDNO polecenie w powłoce maszyny wirtualnej i zwraca surowe wyjście "
                    + "(stdout/stderr lub kod błędu API). Zestaw komend jest NIESTANDARDOWY — zacznij od "
                    + "'help', nie zakładaj, że zwykłe polecenia Linuksa działają (edycja plików też działa "
                    + "inaczej). ZAKAZANE są ścieżki /etc, /root, /proc oraz pliki/katalogi wymienione w "
                    + "napotkanych plikach .gitignore — ich naruszenie kończy się banem i resetem maszyny.")
    String shellCommand(
            @ToolParam(description = "Pojedyncze polecenie powłoki do wykonania na maszynie wirtualnej.")
            String command) {
        if (!StringUtils.hasText(command)) {
            return "Błąd: puste polecenie.";
        }
        if (FORBIDDEN_PATH.matcher(command).find()) {
            log.warn("BLOCKED forbidden-path command (not sent): {}", command);
            return "BLOCKED: polecenie odwołuje się do zakazanej ścieżki (/etc, /root lub /proc) — "
                    + "NIE wysłano go do API. Zasady bezpieczeństwa zabraniają tych katalogów (naruszenie = "
                    + "ban + reset maszyny). Pracuj w obrębie /opt/firmware/cooler i innych dozwolonych miejsc.";
        }
        return client.shell(command.trim()).body();
    }

    @Tool(name = "submit_answer",
            description = "Wysyła do Centrali kod uzyskany po poprawnym uruchomieniu cooler.bin i zwraca jej "
                    + "feedback albo flagę. Kod ma format ECCS- + 40 znaków (łącznie 45). Wywołaj dopiero, gdy "
                    + "binarka faktycznie wypisała kod — nie zgaduj.")
    String submitAnswer(
            @ToolParam(description = "Kod wyświetlony przez cooler.bin, dokładny format ECCS- + 40 znaków.")
            String confirmation) {

        // Cheap local format check so we don't waste a round-trip on an obviously malformed code.
        if (!StringUtils.hasText(confirmation)
                || !CODE.matcher(confirmation.trim()).matches()
                || PLACEHOLDER.matcher(confirmation.trim()).matches()) {
            log.info("submit_answer odrzucone lokalnie (zły format): '{}'", confirmation);
            return "Nie wysłano — confirmation musi mieć format ECCS- + 40 znaków = 45 znaków "
                    + "(otrzymano: '" + confirmation + "'). Uruchom poprawnie cooler.bin i przepisz dokładny kod.";
        }

        var answer = new LinkedHashMap<String, Object>();
        answer.put("confirmation", confirmation.trim());

        var resp = client.verify(answer);
        lastFeedback = resp.body();
        var found = resp.flag();
        if (found.isPresent()) {
            flag = found.get();
            return "SUKCES! Centrala zaakceptowała kod. Flaga znaleziona — zakończ pracę.";
        }
        return "Centrala odrzuciła kod. Feedback: " + resp.body();
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
