package com.morawski.dev.aidevs.tasks.task24goingthere;

/**
 * Which side the rock occupies in the <em>next</em> column, expressed in the rocket's command frame —
 * i.e. directly which move command would fly the rocket <em>into</em> the rock and must be avoided.
 *
 * <p>This is the key simplification that makes collision avoidance independent of the board's
 * row-number orientation: a radio hint of "rock to port/left" means "don't send {@code left}", "rock
 * dead ahead" means "don't send {@code go}", regardless of whether {@code left} maps to a higher or
 * lower row number.
 *
 * <ul>
 *   <li>{@link #LEFT} — rock is on the rocket's left ⇒ avoid {@link Command#LEFT}.</li>
 *   <li>{@link #RIGHT} — rock is on the rocket's right ⇒ avoid {@link Command#RIGHT}.</li>
 *   <li>{@link #FRONT} — rock is straight ahead ⇒ avoid {@link Command#GO}.</li>
 * </ul>
 */
enum AvoidSide {
    LEFT, RIGHT, FRONT;

    /** The single move command that this rock position forbids. */
    Command forbidden() {
        return switch (this) {
            case LEFT -> Command.LEFT;
            case RIGHT -> Command.RIGHT;
            case FRONT -> Command.GO;
        };
    }
}
