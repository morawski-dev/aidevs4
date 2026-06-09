package com.morawski.dev.aidevs.tasks.task22phonecall;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RoadParserTest {

    @Test
    void picksTheSingleOpenRoad() {
        assertThat(RoadParser.findPassable("RD224 zamknięta. RD472 przejezdna. RD820 zablokowana."))
                .contains("RD472");
    }

    @Test
    void negatedOpenWordCountsAsBlocked() {
        // "RD224 nie jest przejezdna" must NOT be read as passable
        assertThat(RoadParser.findPassable("RD224 nie jest przejezdna. RD472 jest przejezdna. RD820 uszkodzona."))
                .contains("RD472");
    }

    @Test
    void gluedNegationNieprzejezdnaIsBlocked() {
        assertThat(RoadParser.findPassable("RD224 nieprzejezdna, RD472 nieprzejezdna, RD820 przejezdna."))
                .contains("RD820");
    }

    @Test
    void roadCodesMayContainASpace() {
        assertThat(RoadParser.findPassable("RD 472 jest przejezdna; RD 224 i RD 820 są zamknięte."))
                .contains("RD472");
    }

    @Test
    void roadCodesMayContainAHyphen() {
        // STT renders the operator's spoken codes as "RD-472" etc.
        assertThat(RoadParser.findPassable("RD-472 zamknięta, RD-224 zablokowana, RD-820 przejezdna."))
                .contains("RD820");
    }

    @Test
    void variousClosedSynonymsAreBlocked() {
        assertThat(RoadParser.findPassable("RD224 zawalona, RD472 zasypana, RD820 otwarta i przejezdna."))
                .contains("RD820");
    }

    @Test
    void allBlockedYieldsNothing() {
        assertThat(RoadParser.findPassable("RD224 zamknięta, RD472 zablokowana, RD820 nieprzejezdna."))
                .isEmpty();
    }

    @Test
    void twoOpenRoadsAreAmbiguous() {
        assertThat(RoadParser.findPassable("RD224 przejezdna, RD472 przejezdna, RD820 zamknięta."))
                .isEmpty();
    }

    @Test
    void emptyOrBlankTranscriptYieldsNothing() {
        assertThat(RoadParser.findPassable("")).isEmpty();
        assertThat(RoadParser.findPassable(null)).isEmpty();
        assertThat(RoadParser.findPassable("Dzień dobry, w czym mogę pomóc?")).isEmpty();
    }

    @Test
    void statusesReflectNegation() {
        var statuses = RoadParser.statuses("RD224 jest przejezdna, RD472 nie jest przejezdna, RD820 zamknięta.");
        assertThat(statuses.get("RD224")).isEqualTo(RoadParser.Status.PASSABLE);
        assertThat(statuses.get("RD472")).isEqualTo(RoadParser.Status.BLOCKED);
        assertThat(statuses.get("RD820")).isEqualTo(RoadParser.Status.BLOCKED);
    }

    @Test
    void contradictorySignalsForSameRoadAreAmbiguous() {
        // a single road mention whose clause carries both an open and a closed signal → not decidable
        Optional<String> result = RoadParser.findPassable("RD472 była zamknięta, ale teraz jest przejezdna.");
        assertThat(result).isEmpty();
    }
}
