package com.morawski.dev.aidevs.tasks.task19filesystem;

import java.util.List;

/**
 * Structured trade model extracted from Natan's notes by the LLM. Everything is in Polish nominative,
 * singular for goods (e.g. {@code ziemniak}, not {@code ziemniaki}); {@code FsBuilder} transliterates
 * to the ASCII lowercase names the API requires and assembles the three directories from this.
 *
 * @param cities one entry per city Natan describes (the trade participants).
 */
record TradeModel(List<City> cities) {

    /**
     * @param name    city name in nominative (Polish ok — transliterated downstream), e.g. {@code Domatowo}.
     * @param manager full name "First Last" of the person responsible for trade in this city.
     * @param needs   goods this city <em>needs</em> with quantities (no units) → {@code /miasta/<city>} JSON.
     * @param offers  goods this city <em>sells</em> (singular nominative) → linked from {@code /towary/<good>}.
     */
    record City(String name, String manager, List<Need> needs, List<String> offers) {
    }

    /**
     * @param good     good name, singular nominative (Polish ok — transliterated downstream).
     * @param quantity how much of it the city needs, as a bare number (no units).
     */
    record Need(String good, int quantity) {
    }
}
