package com.morawski.dev.aidevs.tasks.task06categorize;

/** One row of {@code categorize.csv}: an identifier and a free-text description of the cargo item. */
record Item(String id, String description) {
}
