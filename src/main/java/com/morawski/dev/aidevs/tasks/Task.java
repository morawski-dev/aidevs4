package com.morawski.dev.aidevs.tasks;

public interface Task {
    String name();
    Object solve();

    /**
     * Whether the task drives its own Hub communication inside {@link #solve()} (multi-step,
     * resilient) and therefore must NOT be submitted again by the {@code TaskRunner}.
     *
     * <p>Default {@code false}: the runner submits {@link #solve()}'s result to {@code /verify}
     * and extracts the flag. Self-submitting tasks (e.g. {@code railway}) log their own flag.
     */
    default boolean selfSubmitting() {
        return false;
    }
}
