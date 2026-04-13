package com.morawski.dev.aidevs.tasks.task02findhim;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record Coordinate(
        @JsonAlias({"lat", "latitude"}) double lat,
        @JsonAlias({"lng", "lon", "longitude"}) double lng) {
}
