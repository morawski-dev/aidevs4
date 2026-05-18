package com.morawski.dev.aidevs.tasks.task14negotiations;

import org.apache.commons.csv.CSVRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * In-memory index built once from the three S03E04 files:
 * <ul>
 *   <li>{@code cities.csv} — {@code name,code} (city name ↔ code),</li>
 *   <li>{@code items.csv} — {@code name,code} (~2000 component names ↔ code),</li>
 *   <li>{@code connections.csv} — {@code itemCode,cityCode} (which item is sold where).</li>
 * </ul>
 *
 * <p>A lookup resolves a natural-language request to one item {@link #items}, then to the set of city
 * names offering it via {@link #citiesFor(String)} — already joined item→city→name so callers only see
 * human-readable city names (which the agent then intersects across its three items).
 *
 * @param items        catalog items with pre-tokenised names (for the lexical pre-filter)
 * @param citiesByItem itemCode → sorted set of city names offering that item
 */
record Catalog(List<Item> items, Map<String, TreeSet<String>> citiesByItem) {

    /** One catalog entry: short {@code code}, full {@code name}, and its normalised token set. */
    record Item(String code, String name, Set<String> tokens) {
    }

    /** Sorted city names offering the given item code (empty if none / unknown code). */
    TreeSet<String> citiesFor(String itemCode) {
        return citiesByItem.getOrDefault(itemCode, new TreeSet<>());
    }

    /** Largest number of cities any single item is offered in — sanity-checks the 500-byte reply budget. */
    int maxCitiesPerItem() {
        return citiesByItem.values().stream().mapToInt(Set::size).max().orElse(0);
    }

    static Catalog build(List<CSVRecord> cities, List<CSVRecord> items, List<CSVRecord> connections) {
        Map<String, String> cityNameByCode = cities.stream()
                .collect(Collectors.toMap(r -> r.get("code"), r -> r.get("name"), (a, b) -> a));

        List<Item> catalogItems = items.stream()
                .map(r -> {
                    var name = r.get("name");
                    return new Item(r.get("code"), name, Tokens.of(name));
                })
                .toList();

        Map<String, TreeSet<String>> citiesByItem = new java.util.HashMap<>();
        for (var c : connections) {
            var itemCode = c.get("itemCode");
            var cityName = cityNameByCode.get(c.get("cityCode"));
            if (cityName != null) {
                citiesByItem.computeIfAbsent(itemCode, k -> new TreeSet<>()).add(cityName);
            }
        }
        return new Catalog(catalogItems, citiesByItem);
    }
}
