package com.morawski.dev.aidevs.common;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvReader {

    private CsvReader() {
    }

    public static List<CSVRecord> read(byte[] data) {
        try (var reader = new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8);
             var parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            return parser.getRecords();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse CSV", e);
        }
    }
}
