package com.morawski.dev.aidevs.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the task03 proxy server.
 *
 * @param url       full public URL of the endpoint (tunnel + path), submitted to the Hub
 * @param sessionId arbitrary alphanumeric id the Hub uses as the session id while testing
 * @param model     OpenRouter chat model id used for the conversation (tool-calling capable)
 */
@ConfigurationProperties("aidevs.proxy")
public record ProxyProperties(String url, String sessionId, String model) {
}
