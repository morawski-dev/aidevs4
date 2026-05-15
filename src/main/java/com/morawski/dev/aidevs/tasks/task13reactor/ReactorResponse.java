package com.morawski.dev.aidevs.tasks.task13reactor;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/** Raw result of one {@code POST /verify} command: the HTTP status and the raw body string. */
record ReactorResponse(int status, String body) {

    /** Any {@code {FLG:...}} found in the body — returned by the Hub once the robot reaches the goal. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
