package com.morawski.dev.aidevs.tasks.task08failure;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import com.morawski.dev.aidevs.config.FailureProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts tokens locally so we never ship a {@code logs} field over the Centrala's 1500-token limit.
 * Uses jtokkit's {@code cl100k_base} encoder (the tokenizer behind {@code platform.openai.com/tokenizer},
 * which the task points at) and validates against a configured budget set a little below 1500, since
 * the Centrala's exact tokenizer is unknown. If the encoder ever fails, falls back to a deliberately
 * conservative {@code chars/3} estimate (denser than the real ~4, so it under-reports the limit).
 */
@Component
class TokenCounter {

    private static final Logger log = LoggerFactory.getLogger(TokenCounter.class);

    private final Encoding encoding;
    private final int budget;

    TokenCounter(FailureProperties props) {
        this.encoding = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
        this.budget = props.tokenBudget();
    }

    int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return encoding.countTokens(text);
        } catch (RuntimeException e) {
            int approx = (int) Math.ceil(text.length() / 3.0);
            log.warn("Token encoder failed ({}); falling back to conservative chars/3 estimate = {}",
                    e.getMessage(), approx);
            return approx;
        }
    }

    /** Whether {@code text} is at or below the configured budget (margin under the Centrala's 1500). */
    boolean fits(String text) {
        return count(text) <= budget;
    }

    int budget() {
        return budget;
    }
}
