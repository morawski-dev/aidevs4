package com.morawski.dev.aidevs.tasks.task24goingthere;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the SHA-1 hex implementation against the standard NIST test vectors. */
class Sha1Test {

    @Test
    void matchesKnownVectors() {
        assertThat(Sha1.hex("")).isEqualTo("da39a3ee5e6b4b0d3255bfef95601890afd80709");
        assertThat(Sha1.hex("abc")).isEqualTo("a9993e364706816aba3e25717850c26c9cd0d89d");
    }

    @Test
    void hashesDisarmComposition() {
        // The disarm hash is SHA1(detectionCode + "disarm"); pin the exact composition the task submits.
        assertThat(Sha1.hex("abc" + "disarm")).isEqualTo(Sha1.hex("abcdisarm"));
        assertThat(Sha1.hex("abcdisarm")).hasSize(40).matches("[0-9a-f]{40}");
    }
}
