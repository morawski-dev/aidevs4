package com.morawski.dev.aidevs.hub;

import com.morawski.dev.aidevs.hub.dto.HubResponse;

import java.util.Optional;
import java.util.regex.Pattern;

public final class FlagExtractor {

    private static final Pattern FLAG_PATTERN = Pattern.compile("\\{FLG:([^}]+)}");

    private FlagExtractor() {
    }

    public static Optional<String> extract(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        var matcher = FLAG_PATTERN.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public static Optional<String> extract(HubResponse response) {
        return response == null ? Optional.empty() : extract(response.message());
    }
}
