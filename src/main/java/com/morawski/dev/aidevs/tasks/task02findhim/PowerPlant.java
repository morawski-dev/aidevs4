package com.morawski.dev.aidevs.tasks.task02findhim;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record PowerPlant(
        @JsonAlias({"code", "id"}) String code,
        @JsonAlias({"name", "nazwa"}) String name,
        @JsonAlias({"location", "address", "city", "lokalizacja"}) String location,
        @JsonAlias({"lat", "latitude"}) Double lat,
        @JsonAlias({"lng", "lon", "longitude"}) Double lng) {

    boolean hasCoords() {
        return lat != null && lng != null;
    }

    PowerPlant withCoords(double lat, double lng) {
        return new PowerPlant(code, name, location, lat, lng);
    }
}
