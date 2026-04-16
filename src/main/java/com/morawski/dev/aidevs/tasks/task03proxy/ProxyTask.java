package com.morawski.dev.aidevs.tasks.task03proxy;

import com.morawski.dev.aidevs.config.ProxyProperties;
import com.morawski.dev.aidevs.tasks.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Submits the public endpoint URL to the Hub as the "proxy" task.
 * The server is already listening (web mode is enabled for --task=proxy), so the Hub's
 * callbacks during /verify hit the live ProxyController.
 */
@Component
class ProxyTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(ProxyTask.class);

    private final ProxyProperties props;

    ProxyTask(ProxyProperties props) {
        this.props = props;
    }

    @Override
    public String name() {
        return "proxy";
    }

    @Override
    public Object solve() {
        if (!StringUtils.hasText(props.url())) {
            throw new IllegalStateException(
                    "aidevs.proxy.url is not set. Start a tunnel (e.g. `ngrok http 3000`) and set "
                            + "PROXY_URL to the public endpoint, e.g. https://abc123.ngrok-free.app/proxy");
        }
        log.info("Submitting proxy endpoint: url={}, sessionID={}", props.url(), props.sessionId());
        return Map.of("url", props.url(), "sessionID", props.sessionId());
    }
}
