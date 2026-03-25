package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aidevs.hub")
public record HubProperties(String url, String apiKey) {
}
