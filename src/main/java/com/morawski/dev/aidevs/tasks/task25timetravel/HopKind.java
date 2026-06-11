package com.morawski.dev.aidevs.tasks.task25timetravel;

/**
 * Rodzaj przemieszczenia w czasie i wynikający z niego układ przełączników {@code PT-A}/{@code PT-B}
 * (ustawianych ręcznie w preview, nie przez API):
 *
 * <ul>
 *   <li>{@link #PAST_JUMP} — skok w przeszłość: {@code PT-A=ON, PT-B=OFF}</li>
 *   <li>{@link #FUTURE_JUMP} — skok w przyszłość: {@code PT-A=OFF, PT-B=ON}</li>
 *   <li>{@link #TUNNEL} — tunel czasowy: {@code PT-A=ON, PT-B=ON} (wymaga ≥60% baterii,
 *       zużywa wielokrotnie więcej energii niż zwykły skok)</li>
 * </ul>
 */
enum HopKind {

    PAST_JUMP(true, false, "skok w przeszłość"),
    FUTURE_JUMP(false, true, "skok w przyszłość"),
    TUNNEL(true, true, "tunel czasowy");

    private final boolean ptA;
    private final boolean ptB;
    private final String label;

    HopKind(boolean ptA, boolean ptB, String label) {
        this.ptA = ptA;
        this.ptB = ptB;
        this.label = label;
    }

    boolean ptA() {
        return ptA;
    }

    boolean ptB() {
        return ptB;
    }

    boolean isTunnel() {
        return this == TUNNEL;
    }

    String label() {
        return label;
    }

    /** Krótka instrukcja przełączników dla operatora, np. {@code "PT-A = OFF, PT-B = ON  (skok w przyszłość)"}. */
    String switchesInstruction() {
        return "PT-A = %s, PT-B = %s  (%s)".formatted(onOff(ptA), onOff(ptB), label);
    }

    private static String onOff(boolean on) {
        return on ? "ON" : "OFF";
    }
}
