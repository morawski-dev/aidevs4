package com.morawski.dev.aidevs.tasks.task07electricity;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/** Raw result of one {@code POST /verify} rotation: the HTTP status and the raw body string. */
record EleResponse(int status, String body) {

    /** Any {@code {FLG:...}} found in the body — returned by the Hub once the board is solved. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
