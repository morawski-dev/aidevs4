package com.morawski.dev.aidevs.tasks.task19filesystem;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link TradeModel} into the ordered list of {@code /verify} actions that build the required
 * filesystem. Pure logic, no I/O — unit-tested in {@code FsBuilderTest}.
 *
 * <p>Layout required by the task:
 * <ul>
 *   <li>{@code /miasta/<city>} — JSON of the goods the city needs and how many (no units),</li>
 *   <li>{@code /osoby/<first_last>} — the manager's name + a markdown link to the city they run,</li>
 *   <li>{@code /towary/<good>} — markdown link(s) to the city/cities that sell that good.</li>
 * </ul>
 *
 * <p>The API constrains names to {@code ^[a-z0-9_]+$} (no Polish letters, lowercase), so every name is
 * {@link #slug(String) slugged} (diacritics folded, spaces → {@code _}). JSON keys and link text are
 * also ASCII-folded. Order matters: directories first, then {@code /miasta} files, then the files that
 * link into them ({@code /osoby}, {@code /towary}) — markdown links must point to <em>existing</em>
 * files, and the API runs a batch sequentially.
 */
final class FsBuilder {

    static final String DIR_MIASTA = "miasta";
    static final String DIR_OSOBY = "osoby";
    static final String DIR_TOWARY = "towary";

    private FsBuilder() {
    }

    /** Build the full ordered action list (directories, then city files, then person and goods files). */
    static List<Map<String, Object>> build(TradeModel model) {
        var actions = new ArrayList<Map<String, Object>>();
        actions.add(createDirectory("/" + DIR_MIASTA));
        actions.add(createDirectory("/" + DIR_OSOBY));
        actions.add(createDirectory("/" + DIR_TOWARY));

        var cities = model.cities() == null ? List.<TradeModel.City>of() : model.cities();

        // /miasta/<city> — JSON of needs. Created first so later markdown links resolve.
        for (var city : cities) {
            var citySlug = slug(city.name());
            actions.add(createFile("/" + DIR_MIASTA + "/" + citySlug, needsJson(city.needs())));
        }

        // /osoby/<first_last> — manager name + link to the city they manage.
        for (var city : cities) {
            if (city.manager() == null || city.manager().isBlank()) {
                continue;
            }
            var citySlug = slug(city.name());
            var personSlug = slug(city.manager());
            var content = asciiFold(city.manager().trim()) + "\n" + cityLink(citySlug);
            actions.add(createFile("/" + DIR_OSOBY + "/" + personSlug, content));
        }

        // /towary/<good> — invert offers: good -> cities that sell it; link to each seller.
        var sellersByGood = new LinkedHashMap<String, LinkedHashSet<String>>();
        for (var city : cities) {
            var citySlug = slug(city.name());
            var offers = city.offers() == null ? List.<String>of() : city.offers();
            for (var good : offers) {
                if (good == null || good.isBlank()) {
                    continue;
                }
                sellersByGood.computeIfAbsent(slug(good), k -> new LinkedHashSet<>()).add(citySlug);
            }
        }
        for (var entry : sellersByGood.entrySet()) {
            var content = entry.getValue().stream()
                    .map(FsBuilder::cityLink)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            actions.add(createFile("/" + DIR_TOWARY + "/" + entry.getKey(), content));
        }

        return actions;
    }

    /** Markdown link to a city file in {@code /miasta}. */
    static String cityLink(String citySlug) {
        return "[" + citySlug + "](/" + DIR_MIASTA + "/" + citySlug + ")";
    }

    /** JSON object of good (slug) → quantity, insertion-ordered, ASCII keys, integer values. */
    static String needsJson(List<TradeModel.Need> needs) {
        var ordered = new LinkedHashMap<String, Integer>();
        if (needs != null) {
            for (var need : needs) {
                if (need.good() != null && !need.good().isBlank()) {
                    ordered.put(slug(need.good()), need.quantity());
                }
            }
        }
        var sb = new StringBuilder("{");
        var first = true;
        for (var e : ordered.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
            first = false;
        }
        return sb.append("}").toString();
    }

    /**
     * Slug for a file/directory name: fold diacritics to ASCII, lowercase, collapse any run of
     * non-{@code [a-z0-9]} characters to a single {@code _}, and trim leading/trailing {@code _} —
     * satisfying the API's {@code ^[a-z0-9_]+$} pattern. "Rafał Kisiel" -> "rafal_kisiel".
     */
    static String slug(String raw) {
        var folded = asciiFold(raw).toLowerCase();
        var slug = folded.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return slug;
    }

    /** Replace Polish/diacritic letters with their ASCII base, preserving case, spaces and punctuation. */
    static String asciiFold(String raw) {
        if (raw == null) {
            return "";
        }
        var swapped = raw.replace('ł', 'l').replace('Ł', 'L');
        var decomposed = Normalizer.normalize(swapped, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "");
    }

    private static Map<String, Object> createDirectory(String path) {
        var m = new LinkedHashMap<String, Object>();
        m.put("action", "createDirectory");
        m.put("path", path);
        return m;
    }

    private static Map<String, Object> createFile(String path, String content) {
        var m = new LinkedHashMap<String, Object>();
        m.put("action", "createFile");
        m.put("path", path);
        m.put("content", content);
        return m;
    }
}
