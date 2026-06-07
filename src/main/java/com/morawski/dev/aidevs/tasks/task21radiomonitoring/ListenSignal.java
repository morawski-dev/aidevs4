package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * One parsed {@code listen} chunk. A chunk is one of: a text {@code transcription} (sometimes just
 * radio noise), a base64 {@code attachment} described by {@code meta} (MIME) + {@code filesize}, or a
 * terminal signal that the material pool is exhausted.
 *
 * <p>Pure data + parsing; no I/O. {@link #parse} is defensive (missing fields → empty/zero) because
 * the exact body shape is recon-confirmed only at run time.
 */
record ListenSignal(int code, String message, String transcription, String meta, String attachment, long filesize) {

    /** Keywords that mark the "you have enough data" terminal reply (matched case-insensitively). */
    private static final String[] TERMINAL_HINTS = {
            "enough data", "no more", "no further", "pool is empty", "wystarczająco", "koniec", "zakończ"
    };

    static ListenSignal parse(String body, ObjectMapper mapper) {
        if (!StringUtils.hasText(body)) {
            return new ListenSignal(0, "", "", "", "", 0);
        }
        try {
            JsonNode n = mapper.readTree(body);
            return new ListenSignal(
                    n.path("code").asInt(0),
                    n.path("message").asText(""),
                    n.path("transcription").asText(""),
                    n.path("meta").asText(""),
                    n.path("attachment").asText(""),
                    n.path("filesize").asLong(0));
        } catch (Exception e) {
            // Not JSON we understand — treat as an empty/terminal-ish chunk; the driver logs the raw body.
            return new ListenSignal(0, body, "", "", "", 0);
        }
    }

    boolean hasText() {
        return StringUtils.hasText(transcription);
    }

    boolean hasAttachment() {
        return StringUtils.hasText(attachment);
    }

    /**
     * End-of-material detection: no usable payload (neither transcription nor attachment), or the
     * message explicitly says the pool is exhausted. The {@code maxListens} cap is the backstop.
     */
    boolean terminal() {
        if (messageSaysDone()) {
            return true;
        }
        return !hasText() && !hasAttachment();
    }

    private boolean messageSaysDone() {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        for (String hint : TERMINAL_HINTS) {
            if (m.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
