package com.morawski.dev.aidevs.hub;

import com.morawski.dev.aidevs.hub.dto.HubResponse;

public class HubException extends RuntimeException {

    private final HubResponse response;

    public HubException(HubResponse response) {
        super("Hub error (code=%d): %s".formatted(response.code(), response.message()));
        this.response = response;
    }

    public HubResponse response() {
        return response;
    }
}
