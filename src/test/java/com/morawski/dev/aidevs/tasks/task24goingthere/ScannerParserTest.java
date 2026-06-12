package com.morawski.dev.aidevs.tasks.task24goingthere;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for the lenient frequency-scanner parser. Covers the clean "clear" / JSON paths
 * plus the jammed/corrupted variants the parser must still recover the two fields from.
 */
class ScannerParserTest {

    @Test
    void recognisesClear() {
        assertThat(ScannerParser.parse("It's clear!").kind()).isEqualTo(ScannerReading.Kind.CLEAR);
        assertThat(ScannerParser.parse("...nothing here, ALL CLEAR...").kind()).isEqualTo(ScannerReading.Kind.CLEAR);
    }

    @Test
    void parsesWellFormedDetection() {
        var r = ScannerParser.parse("{\"frequency\":123,\"detectionCode\":\"abc123def\"}");
        assertThat(r.kind()).isEqualTo(ScannerReading.Kind.DETECTED);
        assertThat(r.frequency()).isEqualTo("123");
        assertThat(r.detectionCode()).isEqualTo("abc123def");
    }

    @Test
    void recoversFieldsFromCorruptedJson() {
        String mangled = "##@@!! \"frequency\" :  4567 ~~garbage~~ \"detectionCode\": \"XYZ789abc\" }}}";
        var r = ScannerParser.parse(mangled);
        assertThat(r.kind()).isEqualTo(ScannerReading.Kind.DETECTED);
        assertThat(r.frequency()).isEqualTo("4567");
        assertThat(r.detectionCode()).isEqualTo("XYZ789abc");
    }

    @Test
    void keepsDecimalFrequency() {
        var r = ScannerParser.parse("{ \"frequency\": 88.5, \"detectionCode\": \"q1w2e3\" }");
        assertThat(r.frequency()).isEqualTo("88.5");
    }

    @Test
    void recoversFromScrambledKeys() {
        // Real recon body: the jamming scrambles the KEY names, so extraction must be key-agnostic —
        // frequency = the numeric value, detectionCode = the alphanumeric quoted value (weapon type
        // has spaces and is excluded; the long scrambled keys precede the colon so aren't values).
        String jammed = "{ \"FrEPueNcy\": 193, \"beInGTracKEb\": true, \"bata\": { "
                + "\"BeTeCtI0nC0be\": \"DqmfAb\", \"WEAp0ntYPe\": \"surface-to-air missile\" } }";
        var r = ScannerParser.parse(jammed);
        assertThat(r.kind()).isEqualTo(ScannerReading.Kind.DETECTED);
        assertThat(r.frequency()).isEqualTo("193");
        assertThat(r.detectionCode()).isEqualTo("DqmfAb");
    }

    @Test
    void treatsUnparseableNonClearAsCorrupt() {
        assertThat(ScannerParser.parse("%%$$ garbled noise $$%%").kind()).isEqualTo(ScannerReading.Kind.CORRUPT);
        assertThat(ScannerParser.parse("").kind()).isEqualTo(ScannerReading.Kind.CORRUPT);
        assertThat(ScannerParser.parse(null).kind()).isEqualTo(ScannerReading.Kind.CORRUPT);
        // Frequency present but no detection code -> not enough to disarm -> corrupt.
        assertThat(ScannerParser.parse("{\"frequency\":42}").kind()).isEqualTo(ScannerReading.Kind.CORRUPT);
    }
}
