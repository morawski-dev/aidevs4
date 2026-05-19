package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.morawski.dev.aidevs.tasks.task14negotiations.Catalog.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the deterministic, network-free pieces of the negotiations task: the diacritics-insensitive
 * tokeniser, the lexical shortlist ranking that pre-filters the ~2000-item catalog, and the 500-byte reply
 * fitting. The LLM disambiguation and live Hub loop are verified by running the task, not here.
 */
class ItemMatcherTest {

    private static Item item(String code, String name) {
        return new Item(code, name, Tokens.of(name));
    }

    private static final List<Item> CATALOG = List.of(
            item("BWST28", "Rezystor metalizowany 1 ohm 0.125 W 1%"),
            item("2GF4VO", "Rezystor SMD 10 ohm 0402 1% niski szum"),
            item("QQAPOK", "Kondensator ceramiczny 10 pF 16 V X7R"),
            item("NDJLGW", "Dioda LED czerwona 3 mm standard"),
            item("0ZZF5M", "Tranzystor NPN BC547 TO-92 niskoszumny")
    );

    @Test
    void tokeniserStripsPolishDiacriticsAndNonAlnum() {
        assertThat(Tokens.of("Łopatki 1 Ω niskoszumny")).containsExactly("lopatki", "1", "niskoszumny");
        assertThat(Tokens.of("Grudziądz")).containsExactly("grudziadz");
    }

    @Test
    void shortlistRanksTheExactItemFirst() {
        var result = ItemMatcher.shortlist("rezystor 1 ohm 0.125W", CATALOG, 5);

        assertThat(result).isNotEmpty();
        assertThat(result.getFirst().code()).isEqualTo("BWST28");
    }

    @Test
    void shortlistMatchesDespitePolishDiacritics() {
        var result = ItemMatcher.shortlist("dioda LED czerwoną", CATALOG, 5);

        assertThat(result.getFirst().code()).isEqualTo("NDJLGW");
    }

    @Test
    void shortlistDropsItemsWithNoOverlapAndRespectsSize() {
        var result = ItemMatcher.shortlist("rezystor", CATALOG, 1);

        assertThat(result).hasSize(1);
        assertThat(result).allMatch(i -> i.name().toLowerCase().contains("rezystor"));
    }

    @Test
    void shortlistIsEmptyWhenNothingOverlaps() {
        assertThat(ItemMatcher.shortlist("samochód osobowy", CATALOG, 5)).isEmpty();
        assertThat(ItemMatcher.shortlist("   ", CATALOG, 5)).isEmpty();
    }

    @Test
    void fitJoinsCitiesWhenWithinBudget() {
        var output = NegotiationsService.fit(List.of("Warszawa", "Krakow", "Lodz"), 450);

        assertThat(output).isEqualTo("Warszawa, Krakow, Lodz");
        assertThat(output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(450);
    }

    @Test
    void fitTruncatesToStayUnderBudget() {
        var output = NegotiationsService.fit(List.of("Warszawa", "Krakow", "Lodz"), 12);

        // "Warszawa" (8) fits; ", Krakow" would push past 12 bytes, so it stops there.
        assertThat(output).isEqualTo("Warszawa");
        assertThat(output.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThanOrEqualTo(12);
    }
}
