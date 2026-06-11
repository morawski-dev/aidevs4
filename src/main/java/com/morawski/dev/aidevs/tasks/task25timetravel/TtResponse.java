package com.morawski.dev.aidevs.tasks.task25timetravel;

import com.morawski.dev.aidevs.hub.FlagExtractor;

import java.util.Optional;

/** Surowy wynik jednego wywołania {@code POST /verify} dla zadania timetravel: status HTTP + body. */
record TtResponse(int status, String body) {

    /** Flaga {@code {FLG:...}} (jeśli pojawiła się w body — np. po poprawnym otwarciu tunelu do 2024). */
    Optional<String> flag() {
        return FlagExtractor.extract(body);
    }

    boolean ok() {
        return status >= 200 && status < 300;
    }
}
