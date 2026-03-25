package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("aidevs")
public record AidevsProperties(HubProperties hub) {
}
