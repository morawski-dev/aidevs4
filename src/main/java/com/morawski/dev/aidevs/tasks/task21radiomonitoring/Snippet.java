package com.morawski.dev.aidevs.tasks.task21radiomonitoring;

/**
 * One piece of useful, model-ready text extracted from the intercept stream, tagged with its
 * provenance (e.g. {@code "listen#3 image"}). Noise/skipped chunks never produce a snippet. The
 * synthesizer reasons over the concatenation of all snippets.
 */
record Snippet(String source, String text) {
}
