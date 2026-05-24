package com.morawski.dev.aidevs.tasks.task18domatowo;

/** Builds a structurally faithful {@code getMap} body from a compact symbol grid, for the tests. */
final class CityMapTestSupport {

    private CityMapTestSupport() {
    }

    /** Parse a symbol grid (rows of '|'-separated 2-char symbols) into a {@link CityMap}. */
    static CityMap parse(String[] rows) {
        return CityMap.parse(toGetMapJson(rows));
    }

    static String toGetMapJson(String[] rows) {
        var grid = new StringBuilder("[");
        for (int r = 0; r < rows.length; r++) {
            grid.append(r == 0 ? "" : ",").append("[");
            String[] symbols = rows[r].split("\\|", -1);
            for (int c = 0; c < symbols.length; c++) {
                grid.append(c == 0 ? "" : ",").append('"').append(keyForSymbol(symbols[c])).append('"');
            }
            grid.append("]");
        }
        grid.append("]");

        return """
                {"code":80,"message":"Map loaded.","map":{"name":"Domatowo","size":11,"tiles":{
                  "road":{"label":"Ulica","symbol":"UL"},
                  "tree":{"label":"Drzewa","symbol":"DR"},
                  "house":{"label":"Dom","symbol":"DM"},
                  "empty":{"label":"Pusta przestrzen","symbol":"  "},
                  "block1":{"label":"Blok 1p","symbol":"B1"},
                  "block2":{"label":"Blok 2p","symbol":"B2"},
                  "block3":{"label":"Blok 3p","symbol":"B3"},
                  "church":{"label":"Kosciol","symbol":"KS"},
                  "school":{"label":"Szkola","symbol":"SZ"},
                  "parking":{"label":"Parking","symbol":"PK"},
                  "field":{"label":"Boisko","symbol":"BS"}
                },"grid":%s}}""".formatted(grid);
    }

    private static String keyForSymbol(String symbol) {
        return switch (symbol) {
            case "UL" -> "road";
            case "DR" -> "tree";
            case "DM" -> "house";
            case "B1" -> "block1";
            case "B2" -> "block2";
            case "B3" -> "block3";
            case "KS" -> "church";
            case "SZ" -> "school";
            case "PK" -> "parking";
            case "BS" -> "field";
            default -> "empty";
        };
    }
}
