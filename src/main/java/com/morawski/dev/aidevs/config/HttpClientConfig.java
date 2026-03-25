package com.morawski.dev.aidevs.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
class HttpClientConfig {

    @Bean
    RestClient hubRestClient(HubProperties hub) {
        return RestClient.builder()
                .baseUrl(hub.url())
                .build();
    }
}
