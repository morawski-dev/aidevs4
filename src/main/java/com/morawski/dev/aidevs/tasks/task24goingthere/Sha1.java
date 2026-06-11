package com.morawski.dev.aidevs.tasks.task24goingthere;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-1 helper. The {@code goingthere} radar-disarm hash is computed client-side as
 * {@code SHA1(detectionCode + "disarm")} and submitted as a lowercase hex string — unlike
 * {@code foodwarehouse}, whose signature is generated server-side. Kept local to the task because no
 * other task needs SHA-1.
 */
final class Sha1 {

    private Sha1() {
    }

    /** Lowercase hex SHA-1 of {@code input} (UTF-8 bytes). */
    static String hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(input.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 is guaranteed present on every JVM.
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
