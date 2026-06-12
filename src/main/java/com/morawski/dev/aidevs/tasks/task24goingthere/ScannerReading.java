package com.morawski.dev.aidevs.tasks.task24goingthere;

/**
 * Outcome of one frequency-scanner read.
 *
 * <ul>
 *   <li>{@link Kind#CLEAR} — the scanner reported {@code "It's clear!"}: no radar lock, safe to move.</li>
 *   <li>{@link Kind#DETECTED} — a radar lock was reported and both the {@code frequency} and
 *       {@code detectionCode} could be recovered from the (often mangled) body — disarm before moving.</li>
 *   <li>{@link Kind#CORRUPT} — the body was neither a recognisable "clear" nor parseable into a
 *       detection (jamming garbled it): re-request the scan.</li>
 * </ul>
 *
 * <p>{@code frequency} is kept as the raw numeric token (not a primitive) so the integer/decimal form
 * read off the wire is preserved exactly when it is echoed back in the disarm POST.
 */
record ScannerReading(Kind kind, String frequency, String detectionCode) {

    enum Kind { CLEAR, DETECTED, CORRUPT }

    static ScannerReading clear() {
        return new ScannerReading(Kind.CLEAR, null, null);
    }

    static ScannerReading detected(String frequency, String detectionCode) {
        return new ScannerReading(Kind.DETECTED, frequency, detectionCode);
    }

    static ScannerReading corrupt() {
        return new ScannerReading(Kind.CORRUPT, null, null);
    }

    boolean isClear() {
        return kind == Kind.CLEAR;
    }

    boolean isDetected() {
        return kind == Kind.DETECTED;
    }
}
