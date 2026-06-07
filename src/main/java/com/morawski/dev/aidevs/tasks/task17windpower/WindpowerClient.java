package com.morawski.dev.aidevs.tasks.task17windpower;

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
 * Non-throwing client for the windpower {@code POST /verify} dialog (one method per action). Like
 * {@code ElectricityClient}/{@code RailwayClient}, it reads the raw status + body via {@code exchange}
 * so a non-zero Hub {@code code} ("report not ready", validation feedback) is returned for inspection
 * instead of thrown. The {@code RestClient} is stateless, so these methods are safe to call
 * concurrently — which the task does, fanning out report requests and unlock-code generation.
 */
@Component
class WindpowerClient {

    private static final Logger log = LoggerFactory.getLogger(WindpowerClient.class);
    private static final String TASK = "windpower";

    private final RestClient http;
    private final String apiKey;

    WindpowerClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** {@code help} — list available functions and their parameters (recon). */
    WindResponse help() {
        return call(action("help"));
    }

    /** {@code start} — open the service window (likely starts the 40 s clock). */
    WindResponse start() {
        return call(action("start"));
    }

    /**
     * {@code get} — request task data by {@code param}. {@code documentation} is returned directly;
     * {@code weather}/{@code turbinecheck}/{@code powerplantcheck} are queued and fetched via {@link #getResult()}.
     */
    WindResponse get(String param) {
        var body = action("get");
        body.put("param", param);
        return call(body);
    }

    /** {@code getResult} — fetch one completed queued response (tagged with {@code sourceFunction}); consumed once. */
    WindResponse getResult() {
        return call(action("getResult"));
    }

    /**
     * {@code unlockCodeGenerator} — queue generation of the md5 signature for one config point. Per help the
     * signature is over {@code startDate}, {@code startHour}, {@code windMs} and {@code pitchAngle} (not the
     * mode). The result is asynchronous — collect it via {@link #getResult()}.
     */
    WindResponse unlockCode(ConfigPoint point) {
        var body = action("unlockCodeGenerator");
        body.put("startDate", point.date());
        body.put("startHour", point.hour());
        body.put("windMs", point.windMs());
        body.put("pitchAngle", point.pitchAngle());
        return call(body);
    }

    /** {@code config} — store the whole schedule at once (batch form: {@code configs} map). */
    WindResponse config(Map<String, Object> configs) {
        var body = action("config");
        body.put("configs", configs);
        return call(body);
    }

    /** {@code turbinecheck} — mandatory turbine self-test before {@code done}. */
    WindResponse turbinecheck() {
        return call(action("turbinecheck"));
    }

    /** {@code done} — final validation; carries {@code {FLG:...}} on success within the time limit. */
    WindResponse done() {
        return call(action("done"));
    }

    private static Map<String, Object> action(String name) {
        var m = new LinkedHashMap<String, Object>();
        m.put("action", name);
        return m;
    }

    /** Send one {@code {action, ...}} answer and return the raw response. Does not throw on 4xx/5xx/code!=0. */
    private WindResponse call(Map<String, Object> answer) {
        var body = new AnswerRequest(apiKey, TASK, answer);
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
                        log.warn("Failed to read windpower response body", e);
                        raw = "";
                    }
                    return new WindResponse(status, raw);
                });
        log.info("windpower action={} -> HTTP {}  body: {}", answer.get("action"), resp.status(), resp.body());
        return resp;
    }
}
