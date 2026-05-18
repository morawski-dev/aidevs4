package com.morawski.dev.aidevs.tasks.task14negotiations;

import com.morawski.dev.aidevs.common.CsvReader;
import com.morawski.dev.aidevs.config.NegotiationsProperties;
import com.morawski.dev.aidevs.hub.HubClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * The brains behind the tool endpoint: given the agent's natural-language item request, returns the names
 * of the cities that sell that item.
 *
 * <p>The three S03E04 files are downloaded once from the <b>public</b> Hub space ({@code /dane/...} via
 * {@link HubClient#downloadPublic}) and indexed into a {@link Catalog}. Loading is <b>lazy</b> (first
 * lookup, or an explicit {@link #warmUp()} at task start) rather than in a {@code @PostConstruct} — so the
 * context-load smoke test never touches the network. A lookup resolves the request to one item via
 * {@link ItemMatcher} and formats the offering cities to fit the tool's strict 4..500-byte reply budget.
 */
@Service
class NegotiationsService {

    private static final Logger log = LoggerFactory.getLogger(NegotiationsService.class);

    private final HubClient hub;
    private final ItemMatcher matcher;
    private final NegotiationsProperties props;

    private volatile Catalog catalog;

    NegotiationsService(HubClient hub, ItemMatcher matcher, NegotiationsProperties props) {
        this.hub = hub;
        this.matcher = matcher;
        this.props = props;
    }

    /** Download + index the catalog if it isn't loaded yet (idempotent, thread-safe). */
    void warmUp() {
        if (catalog == null) {
            synchronized (this) {
                if (catalog == null) {
                    catalog = load();
                }
            }
        }
    }

    private Catalog load() {
        log.info("Loading negotiations catalog: cities={}, items={}, connections={}",
                props.citiesFile(), props.itemsFile(), props.connectionsFile());
        var cities = CsvReader.read(hub.downloadPublic(props.citiesFile()));
        var items = CsvReader.read(hub.downloadPublic(props.itemsFile()));
        var connections = CsvReader.read(hub.downloadPublic(props.connectionsFile()));
        var built = Catalog.build(cities, items, connections);
        log.info("Catalog ready: {} cities, {} items, {} items-with-offers, maxCitiesPerItem={}",
                cities.size(), built.items().size(), built.citiesByItem().size(), built.maxCitiesPerItem());
        return built;
    }

    /** Resolve a free-text item request to the comma-separated city names that offer it (≤ budget bytes). */
    String lookup(String params) {
        if (!StringUtils.hasText(params)) {
            return "Podaj nazwe lub opis jednego przedmiotu.";
        }
        warmUp();
        var code = matcher.match(params, catalog);
        if (code.isEmpty()) {
            return "Nie znaleziono takiego przedmiotu w katalogu.";
        }
        var cities = catalog.citiesFor(code.get());
        if (cities.isEmpty()) {
            return "Brak miast oferujacych ten przedmiot.";
        }
        return fit(cities, props.maxOutputBytes());
    }

    /**
     * Join city names with ", " while the UTF-8 length stays within {@code maxBytes}. Truncation would
     * make the agent's cross-item intersection drop a valid city, so a trimmed list is logged loudly — but
     * the items a wind turbine needs are niche (a few cities each), so in practice everything fits.
     */
    static String fit(Collection<String> cities, int maxBytes) {
        var sb = new StringBuilder();
        int included = 0;
        for (var city : cities) {
            var addition = sb.isEmpty() ? city : ", " + city;
            if (utf8Len(sb.toString() + addition) > maxBytes) {
                break;
            }
            sb.append(addition);
            included++;
        }
        if (included < cities.size()) {
            log.warn("City list for an item exceeded {} bytes; sent {}/{} cities — intersection may be incomplete.",
                    maxBytes, included, cities.size());
        }
        // Guarantee the ≥4-byte floor even in the pathological case of one very long city name.
        return sb.length() >= 4 ? sb.toString() : "Brak.";
    }

    private static int utf8Len(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }
}
