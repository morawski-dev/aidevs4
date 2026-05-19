package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.morawski.dev.aidevs.config.NegotiationsProperties;
import com.morawski.dev.aidevs.tasks.Task;
import io.micrometer.observation.annotation.Observed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * S03E04 ({@code negotiations}) — register one tool the Centrala agent uses to find which cities sell the
 * items it needs for a wind turbine. We run as a web task (the agent calls {@link NegotiationsController}
 * live), and {@code solve()} both registers the tool and then polls for the asynchronous result, so the
 * task is {@link #selfSubmitting() self-submitting}.
 *
 * <p>Flow: warm up the catalog → submit {@code {tools:[{URL, description}]}} → poll {@code /verify
 * {action:"check"}} every {@code checkPauseMs} until the flag appears (or {@code maxChecks} is hit; the
 * flag is also visible on the Hub's {@code /debug} panel). The embedded server keeps serving the agent's
 * tool calls on other threads throughout.
 */
@Component
class NegotiationsTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(NegotiationsTask.class);

    /**
     * Tool description sent to the Hub — this is the agent's only instruction on when to call us and what
     * to pass. Must say: one item per call, free-text in {@code params}, returns the offering cities.
     */
    private static final String TOOL_DESCRIPTION = """
            Wyszukiwarka dostępności przedmiotów w miastach. Zwraca nazwy miast (oddzielone przecinkami), \
            w których można KUPIĆ podany przedmiot. W polu params przekaż nazwę lub opis JEDNEGO przedmiotu \
            w języku naturalnym, np. "rezystor 1 ohm 0.125W" albo "kabel o długości 10 metrów". \
            Aby znaleźć miasta oferujące kilka przedmiotów jednocześnie, wywołaj to narzędzie osobno dla \
            każdego przedmiotu i znajdź część wspólną zwróconych list miast.""";

    private final NegotiationsClient client;
    private final NegotiationsService service;
    private final NegotiationsProperties props;

    NegotiationsTask(NegotiationsClient client, NegotiationsService service, NegotiationsProperties props) {
        this.client = client;
        this.service = service;
        this.props = props;
    }

    @Override
    public String name() {
        return "negotiations";
    }

    @Override
    public boolean selfSubmitting() {
        return true;
    }

    @Override
    @Observed(name = "negotiations.solve")
    public Object solve() {
        if (!StringUtils.hasText(props.url())) {
            throw new IllegalStateException(
                    "aidevs.negotiations.url is not set. Start a tunnel (e.g. `ngrok http 3000`) and set "
                            + "NEGOTIATIONS_URL to the public endpoint, e.g. https://abc123.ngrok-free.app/api/negotiations");
        }

        // Index the catalog now so the agent's very first tool call is fast.
        service.warmUp();

        var tool = Map.of("URL", props.url(), "description", TOOL_DESCRIPTION);
        var answer = Map.of("tools", List.of(tool));
        log.info("Registering negotiations tool: {}", props.url());
        var submitResp = client.submit(answer);
        var earlyFlag = submitResp.flag();
        if (earlyFlag.isPresent()) {
            log.info("FLAG → {}", earlyFlag.get());
            return Map.of("flag", earlyFlag.get());
        }

        // Verification is asynchronous — poll until the agent finishes and the Hub returns the flag.
        for (int i = 1; i <= props.maxChecks(); i++) {
            sleep(props.checkPauseMs());
            log.info("Checking result ({}/{})...", i, props.maxChecks());
            var resp = client.check();
            var flag = resp.flag();
            if (flag.isPresent()) {
                log.info("FLAG → {}", flag.get());
                return Map.of("flag", flag.get(), "checks", i);
            }
        }

        log.warn("No flag after {} checks. The server stays up — inspect https://hub.ag3nts.org/debug or "
                + "re-run a check.", props.maxChecks());
        return Map.of("status", "no flag", "checks", props.maxChecks());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting between checks", e);
        }
    }
}
