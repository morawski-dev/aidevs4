package com.morawski.dev.aidevs.tasks.task19filesystem;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FsBuilderTest {

    private static final Pattern NAME = Pattern.compile("^[a-z0-9_]+$");

    @Test
    void slugFoldsPolishLettersLowercasesAndUnderscoresSpaces() {
        assertThat(FsBuilder.slug("Grudziądz")).isEqualTo("grudziadz");
        assertThat(FsBuilder.slug("Rafał Kisiel")).isEqualTo("rafal_kisiel");
        assertThat(FsBuilder.slug("łopata")).isEqualTo("lopata");
        assertThat(FsBuilder.slug("  Karlinkowo  ")).isEqualTo("karlinkowo");
    }

    @Test
    void asciiFoldKeepsCaseAndSpacesButDropsDiacritics() {
        assertThat(FsBuilder.asciiFold("Rafał Kisiel")).isEqualTo("Rafal Kisiel");
        assertThat(FsBuilder.asciiFold("Marta Frantz")).isEqualTo("Marta Frantz");
    }

    @Test
    void needsJsonIsAsciiKeyedIntegerValuedAndOrdered() {
        var json = FsBuilder.needsJson(List.of(
                new TradeModel.Need("chleb", 45),
                new TradeModel.Need("woda", 120),
                new TradeModel.Need("młotek", 6)));
        assertThat(json).isEqualTo("{\"chleb\":45,\"woda\":120,\"mlotek\":6}");
    }

    @Test
    void cityLinkIsMarkdownIntoMiasta() {
        assertThat(FsBuilder.cityLink("domatowo")).isEqualTo("[domatowo](/miasta/domatowo)");
    }

    @Test
    void buildEmitsDirectoriesFirstThenCityThenPersonAndGoods() {
        var model = new TradeModel(List.of(
                new TradeModel.City("Domatowo", "Natan Rams",
                        List.of(new TradeModel.Need("makaron", 60)), List.of("chleb")),
                new TradeModel.City("Opalino", "Iga Kapecka",
                        List.of(new TradeModel.Need("chleb", 45)), List.of("makaron"))));

        var actions = FsBuilder.build(model);

        // first three are the directories
        assertThat(actions).first().satisfies(a -> {
            assertThat(a).containsEntry("action", "createDirectory").containsEntry("path", "/miasta");
        });
        assertThat(actions.subList(0, 3))
                .extracting(a -> a.get("path"))
                .containsExactly("/miasta", "/osoby", "/towary");

        // city files come before the person/goods files that link into them
        int firstMiasta = indexOfPathPrefix(actions, "/miasta/");
        int firstOsoby = indexOfPathPrefix(actions, "/osoby/");
        int firstTowary = indexOfPathPrefix(actions, "/towary/");
        assertThat(firstMiasta).isLessThan(firstOsoby);
        assertThat(firstMiasta).isLessThan(firstTowary);

        // person file: name + link to the managed city
        var natan = actionFor(actions, "/osoby/natan_rams");
        assertThat(natan.get("content")).isEqualTo("Natan Rams\n[domatowo](/miasta/domatowo)");

        // goods file: link to the selling city
        var chleb = actionFor(actions, "/towary/chleb");
        assertThat(chleb.get("content")).isEqualTo("[domatowo](/miasta/domatowo)");

        // city file holds the needs JSON
        var domatowo = actionFor(actions, "/miasta/domatowo");
        assertThat(domatowo.get("content")).isEqualTo("{\"makaron\":60}");
    }

    @Test
    void everyNameMatchesTheApiPattern() {
        var model = new TradeModel(List.of(
                new TradeModel.City("Darżlubie", "Rafał Kisiel",
                        List.of(new TradeModel.Need("łopata", 8)), List.of("ziemniaki"))));
        for (var action : FsBuilder.build(model)) {
            var path = (String) action.get("path");
            for (var segment : path.split("/")) {
                if (!segment.isEmpty()) {
                    assertThat(NAME.matcher(segment).matches())
                            .as("path segment '%s' in %s", segment, path)
                            .isTrue();
                }
            }
        }
    }

    @Test
    void offersFromMultipleCitiesAreCollectedIntoOneGoodFile() {
        var model = new TradeModel(List.of(
                new TradeModel.City("Domatowo", "A B", List.of(), List.of("chleb")),
                new TradeModel.City("Celbowo", "C D", List.of(), List.of("chleb"))));
        var chleb = actionFor(FsBuilder.build(model), "/towary/chleb");
        assertThat(chleb.get("content"))
                .isEqualTo("[domatowo](/miasta/domatowo)\n[celbowo](/miasta/celbowo)");
    }

    private static int indexOfPathPrefix(List<Map<String, Object>> actions, String prefix) {
        for (int i = 0; i < actions.size(); i++) {
            if (((String) actions.get(i).get("path")).startsWith(prefix)
                    && actions.get(i).get("action").equals("createFile")) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, Object> actionFor(List<Map<String, Object>> actions, String path) {
        return actions.stream()
                .filter(a -> path.equals(a.get("path")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no action for " + path));
    }
}
