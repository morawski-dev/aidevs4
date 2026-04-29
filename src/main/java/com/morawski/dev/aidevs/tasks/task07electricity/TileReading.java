package com.morawski.dev.aidevs.tasks.task07electricity;

/**
 * Structured output of the vision model for a single tile: whether the cable reaches each of the
 * four borders of the square. Mapped to {@link Edge}s by {@link BoardVision}.
 */
record TileReading(boolean top, boolean right, boolean bottom, boolean left) {
}
