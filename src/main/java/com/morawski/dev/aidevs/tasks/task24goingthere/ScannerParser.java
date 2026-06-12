package com.morawski.dev.aidevs.tasks.task24goingthere;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lenient, <b>key-agnostic</b> parser for the frequency-scanner response. The OKO jamming doesn't just
 * garble formatting — it <em>scrambles the JSON key names and characters</em>. A real lock observed in
 * recon looked like:
 *
 * <pre>{ "FrEPueNcy": 193, "beInGTracKEb": true, "bata": { "BeTeCtI0nC0be": "DqmfAb",
 *       "WEAp0ntYPe": "surface-to-air missile" } }</pre>
 *
 * i.e. {@code frequency}/{@code detectionCode}/{@code data}/{@code weaponType} are unreadable. So we
 * cannot key on field names; instead we recover the two things we need <b>by structure</b>:
 * <ul>
 *   <li><b>frequency</b> = the numeric value (a number appearing after a {@code :} / {@code =});</li>
 *   <li><b>detectionCode</b> = the alphanumeric quoted <em>value</em> (after a {@code :} / {@code =}).
 *       Matching only values — not keys (which precede the colon) — keeps a long scrambled key from
 *       being mistaken for the code; the weapon-type value is excluded because it contains spaces.</li>
 * </ul>
 *
 * <p>Decision order (detection FIRST — safety): a real lock always carries those two values, a "clear"
 * never does, so checking detection before "clear" prevents a stray "clear"-looking substring from
 * masking a lock we must disarm. A blank or otherwise unrecoverable body ⇒ CORRUPT ⇒ caller re-requests.
 * The plain "clear" text is itself stretched/spaced by jamming ("Its cleeeeeeeear", "Its   clear"), so
 * repeated letters and whitespace are collapsed before the contains-check.
 */
final class ScannerParser {

    // A numeric VALUE: first number that follows a ':' or '=' (keys precede the separator, so are skipped).
    private static final Pattern FREQUENCY = Pattern.compile("[:=]\\s*\"?(-?\\d+(?:\\.\\d+)?)");

    // An alphanumeric quoted VALUE (no spaces ⇒ excludes the "surface-to-air missile" weapon type).
    private static final Pattern CODE_VALUE = Pattern.compile("[:=]\\s*\"([A-Za-z0-9]+)\"");

    private ScannerParser() {
    }

    static ScannerReading parse(String body) {
        if (body == null || body.isBlank()) {
            return ScannerReading.corrupt();
        }

        String frequency = firstGroup(FREQUENCY, body);
        String detectionCode = longestGroup(CODE_VALUE, body);
        if (frequency != null && detectionCode != null) {
            return ScannerReading.detected(frequency, detectionCode);
        }

        if (isClear(body)) {
            return ScannerReading.clear();
        }
        return ScannerReading.corrupt();
    }

    /** Collapse runs of the same char and all whitespace, then look for "clear" (handles jamming). */
    private static boolean isClear(String body) {
        String normalised = body.toLowerCase()
                .replaceAll("\\s+", "")
                .replaceAll("(.)\\1+", "$1");
        return normalised.contains("clear");
    }

    private static String firstGroup(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** The longest captured value across all matches — the detection code dominates any short noise. */
    private static String longestGroup(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        String best = null;
        while (m.find()) {
            String g = m.group(1);
            if (best == null || g.length() > best.length()) {
                best = g;
            }
        }
        return best;
    }
}
