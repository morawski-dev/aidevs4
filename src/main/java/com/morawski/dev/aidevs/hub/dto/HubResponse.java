package com.morawski.dev.aidevs.hub.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HubResponse(int code, String message) {
}
