package com.morawski.dev.aidevs.tasks.task09mailbox;

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
import java.util.HashMap;
import java.util.Map;

/**
 * Non-throwing client for the task09 mailbox dialog. Both the {@code zmail} mailbox API
 * ({@code POST /api/zmail}) and answer submission ({@code POST /verify}) live on
 * {@code hub.ag3nts.org}, so we reuse {@code hubRestClient}.
 *
 * <p>Unlike {@code HubClient.submit} (which throws on a non-zero Hub {@code code}), every call here
 * reads the raw status + body without throwing — the API's "error" replies (wrong query, missing
 * answer fields) are exactly the feedback the agent needs to read. Pattern follows
 * {@code RailwayClient.exchange}.
 */
@Component
class ZmailClient {

    private static final Logger log = LoggerFactory.getLogger(ZmailClient.class);
    private static final String TASK = "mailbox";

    private final RestClient http;
    private final String apiKey;

    ZmailClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.apiKey = hub.apiKey();
    }

    /** Full-text / Gmail-operator search; returns message metadata (no body). */
    ZmailResponse search(String query, int page, int perPage) {
        return zmail(Map.of("action", "search", "query", query, "page", page, "perPage", perPage));
    }

    /** List threads in the mailbox (metadata only). */
    ZmailResponse getInbox(int page, int perPage) {
        return zmail(Map.of("action", "getInbox", "page", page, "perPage", perPage));
    }

    /**
     * Fetch full message bodies by id. {@code ids} may be a single numeric rowID, a single 32-char
     * messageID hash, or a comma-separated list of them — the agent should prefer the stable
     * messageID hashes (rowIDs shift as the active mailbox changes).
     */
    ZmailResponse getMessages(String ids) {
        Object idsParam = parseIds(ids);
        var params = new HashMap<String, Object>();
        params.put("action", "getMessages");
        params.put("ids", idsParam);
        return zmail(params);
    }

    /**
     * Reset this apikey's request counter (per the API's own {@code reset} action). The zmail API
     * tracks requests per apikey — clearing the counter at the start of a run avoids exhausting the
     * budget mid-loop. Read-only on mailbox content; only the counter is cleared.
     */
    ZmailResponse reset() {
        return zmail(Map.of("action", "reset"));
    }

    /** Submit the three answer fields to {@code /verify}; returns the raw feedback or the flag. */
    ZmailResponse verify(Map<String, Object> answer) {
        var body = new AnswerRequest(apiKey, TASK, answer);
        var resp = exchange("/verify", body);
        log.info("mailbox /verify answer={} -> HTTP {}\n  body: {}", answer, resp.status(), resp.body());
        return resp;
    }

    private ZmailResponse zmail(Map<String, Object> action) {
        var body = new HashMap<String, Object>(action);
        body.put("apikey", apiKey);
        var resp = exchange("/api/zmail", body);
        log.info("zmail action={} -> HTTP {}\n  body: {}", action, resp.status(), resp.body());
        return resp;
    }

    /** Split a comma-separated id list into an array; a single id is sent as-is. */
    private static Object parseIds(String ids) {
        if (ids == null || !ids.contains(",")) {
            return ids == null ? "" : ids.trim();
        }
        return java.util.Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private ZmailResponse exchange(String uri, Object body) {
        return http.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                // exchange() hands us the raw response without default error handling,
                // so 4xx/5xx don't throw — we inspect status/body ourselves.
                .exchange((request, response) -> {
                    int status = response.getStatusCode().value();
                    String raw;
                    try (var in = response.getBody()) {
                        raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        log.warn("Failed to read response body from {}", uri, e);
                        raw = "";
                    }
                    return new ZmailResponse(status, raw);
                });
    }
}
