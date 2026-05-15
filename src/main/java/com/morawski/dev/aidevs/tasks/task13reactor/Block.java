package com.morawski.dev.aidevs.tasks.task13reactor;

/**
 * One reactor block: it occupies exactly two vertically-adjacent cells in a single column
 * ({@code topRow} and {@code bottomRow == topRow + 1}) and cycles up/down. {@code dir} is the
 * direction it will move on the next step (the API reports the post-bounce direction, so a block
 * that just reached the bottom is already marked {@link Direction#UP}).
 *
 * <p>Rows are 1-based with row 1 at the top and the bottom row (5 on a 7×5 board) at the bottom, so
 * the block blocks the robot's lane exactly when {@code bottomRow} equals the bottom row.
 */
record Block(int col, int topRow, int bottomRow, Direction dir) {

    enum Direction {
        UP, DOWN;

        static Direction parse(String s) {
            return "up".equalsIgnoreCase(s) ? UP : DOWN;
        }
    }
}
