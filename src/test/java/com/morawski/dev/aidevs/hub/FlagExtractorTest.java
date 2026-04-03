package com.morawski.dev.aidevs.hub;

import com.morawski.dev.aidevs.hub.dto.HubResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlagExtractorTest {

    @Test
    void extractsFlag() {
        assertThat(FlagExtractor.extract("{FLG:SOME_SECRET_FLAG}"))
                .hasValue("SOME_SECRET_FLAG");
    }

    @Test
    void extractsFlagEmbeddedInText() {
        assertThat(FlagExtractor.extract("Correct! {FLG:ABC123} well done"))
                .hasValue("ABC123");
    }

    @Test
    void returnsEmptyWhenNoFlag() {
        assertThat(FlagExtractor.extract("No flag here")).isEmpty();
    }

    @Test
    void returnsEmptyForBlank() {
        assertThat(FlagExtractor.extract("")).isEmpty();
    }

    @Test
    void returnsEmptyForNull() {
        assertThat(FlagExtractor.extract((String) null)).isEmpty();
    }

    @Test
    void extractsFirstFlagWhenMultiplePresent() {
        assertThat(FlagExtractor.extract("{FLG:FIRST} and {FLG:SECOND}"))
                .hasValue("FIRST");
    }

    @Test
    void extractsFromHubResponse() {
        assertThat(FlagExtractor.extract(new HubResponse(0, "Done! {FLG:RESPONSE_FLAG}")))
                .hasValue("RESPONSE_FLAG");
    }

    @Test
    void returnsEmptyForNullHubResponse() {
        assertThat(FlagExtractor.extract((HubResponse) null)).isEmpty();
    }
}
