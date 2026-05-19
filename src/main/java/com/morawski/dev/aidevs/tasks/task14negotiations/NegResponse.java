package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/** Raw result of one {@code POST /verify} call: the HTTP status and the raw body string. */
record NegResponse(int status, String body) {

    /** Any {@code {FLG:...}} the Hub returns once the agent has solved the task with our tools. */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
