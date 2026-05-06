package com.morawski.dev.aidevs.hub;

import com.morawski.dev.aidevs.config.HubProperties;
import com.morawski.dev.aidevs.hub.dto.AnswerRequest;
import com.morawski.dev.aidevs.hub.dto.HubResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HubClient {

    private static final Logger log = LoggerFactory.getLogger(HubClient.class);

    private final RestClient http;
    private final HubProperties props;

    public HubClient(RestClient hubRestClient, HubProperties hub) {
        this.http = hubRestClient;
        this.props = hub;
    }

    public HubResponse submit(String task, Object answer) {
        var body = new AnswerRequest(props.apiKey(), task, answer);
        var response = http.post()
                .uri("/verify")
                .body(body)
                .retrieve()
                .body(HubResponse.class);
        if (response != null && response.code() != 0) {
            throw new HubException(response);
        }
        return response;
    }

    public byte[] downloadData(String filename) {
        var apiKey = props.apiKey();
        log.info("GET /data/{}...{}/{}", apiKey.substring(0, 4), apiKey.substring(apiKey.length() - 4), filename);
        return http.get()
                .uri("/data/{apiKey}/{filename}", apiKey, filename)
                .retrieve()
                .body(byte[].class);
    }

    /**
     * Download a public Hub file by its absolute path (no apikey in the URL). Some task inputs live under
     * {@code /dane/...} rather than the per-apikey {@code /data/{apikey}/...} space — e.g. task11's
     * {@code /dane/sensors.zip}. The {@code path} is resolved against the Hub base url.
     */
    public byte[] downloadPublic(String path) {
        log.info("GET {}", path);
        return http.get()
                .uri(path)
                .retrieve()
                .body(byte[].class);
    }
}
