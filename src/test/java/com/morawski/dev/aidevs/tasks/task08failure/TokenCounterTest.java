package com.morawski.dev.aidevs.tasks.task08failure;

import com.morawski.dev.aidevs.config.FailureProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCounterTest {

    private static TokenCounter counter(int budget) {
        return new TokenCounter(new FailureProperties(
                "failure.log", budget,
                List.of("WARN", "ERRO", "ERROR", "CRIT"),
                List.of("ECCS8"), "openai/gpt-4o-mini", 6));
    }

    @Test
    void countsTokensWithCl100kEncoder() {
        var counter = counter(1450);
        // A handful of words tokenizes to a handful of tokens — fewer than characters, more than one.
        int tokens = counter.count("ECCS8 reported runaway outlet temperature");
        assertThat(tokens).isBetween(5, 12);
    }

    @Test
    void emptyAndNullCountZero() {
        var counter = counter(1450);
        assertThat(counter.count("")).isZero();
        assertThat(counter.count(null)).isZero();
    }

    @Test
    void fitsRespectsBudget() {
        var sample = "[2026-06-09 06:04] [CRIT] ECCS8 reported runaway outlet temperature.";
        assertThat(counter(1450).fits(sample)).isTrue();   // ~20 tokens, well under
        assertThat(counter(5).fits(sample)).isFalse();      // tiny budget rejects it
    }
}
