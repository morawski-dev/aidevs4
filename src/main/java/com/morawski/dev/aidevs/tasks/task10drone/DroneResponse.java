package com.morawski.dev.aidevs.tasks.task10drone;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/** Raw result of one {@code POST /verify} submission: the HTTP status and the raw body string. */
record DroneResponse(int status, String body) {

    /** Any {@code {FLG:...}} found in the body — returned by the Hub once the mission succeeds. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
