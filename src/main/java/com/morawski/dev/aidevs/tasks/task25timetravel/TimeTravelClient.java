package com.morawski.dev.aidevs.tasks.task25timetravel;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.hub.dto.AnswerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nie-rzucający klient {@code POST /verify} dla zadania {@code timetravel}. Jak
 * {@code ElectricityClient}/{@code RailwayClient}, używa {@code exchange(...)} i zwraca surowy
 * {@link TtResponse} (status + body) bez rzucania — odpowiedzi pośrednie miewają niezerowy {@code code}
 * (podpowiedzi, blokady), które chcemy <b>czytać</b>, a nie traktować jak wyjątek.
 *
 * <p>API konfiguruje wyłącznie {@code day}, {@code month}, {@code year}, {@code syncRatio} oraz
 * {@code stabilization} (i tylko w trybie {@code standby}). Przełączniki {@code PT-A}/{@code PT-B},
 * suwak {@code PWR} oraz {@code standby}/{@code active} ustawia operator ręcznie w preview.
 */
@Component
class TimeTravelClient {

    private static final Logger log = LoggerFactory.getLogger(TimeTravelClient.class);
    private static final String TASK = "timetravel";

    private final RestClient http;
    private final String apiKey;

    TimeTravelClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** {@code action:"help"} — lista dostępnych komend i zakres operacji. */
    TtResponse help() {
        return call(Map.of("action", "help"));
    }

    /** {@code action:"getConfig"} — odczyt aktualnego stanu urządzenia. */
    TtResponse getConfig() {
        return call(Map.of("action", "getConfig"));
    }

    /** {@code action:"reset"} — reset urządzenia. */
    TtResponse reset() {
        return call(Map.of("action", "reset"));
    }

    /**
     * {@code action:"timeTravel"} — wykonaj skok/otwórz tunel (to samo, co kliknięcie pulsującej sfery
     * w preview). Sukces zwraca {@code code:13}; przy poprawnym tunelu do daty docelowej odpowiedź niesie
     * pole {@code flag}. Wymaga {@code mode:active}, {@code fluxDensity:100} i właściwej fazy
     * {@code internalMode} — inaczej jest odrzucony (niezerowy {@code code}), co tu po prostu czytamy.
     */
    TtResponse timeTravel() {
        return call(Map.of("action", "timeTravel"));
    }

    /**
     * {@code action:"configure"} — ustaw jeden parametr API ({@code day}/{@code month}/{@code year}/
     * {@code syncRatio}/{@code stabilization}). Możliwe tylko w trybie {@code standby}.
     */
    TtResponse configure(String param, Object value) {
        var answer = new LinkedHashMap<String, Object>();
        answer.put("action", "configure");
        answer.put("param", param);
        answer.put("value", value);
        return call(answer);
    }

    /** Send one {@code {action, ...}} and return the raw response. Does not throw on non-2xx / code!=0. */
    private TtResponse call(Map<String, Object> action) {
        var body = new AnswerRequest(apiKey, TASK, action);
        var resp = http.post()
                .uri("/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling, so a non-2xx
                // (or Hub code!=0 inside a 200) doesn't throw — we inspect status/body ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read timetravel response body", e);
                        raw = "";
                    }
                    return new TtResponse(status, raw);
                });
        log.info("timetravel {} -> HTTP {}  body: {}", action, resp.status(), resp.body());
        return resp;
    }
}
