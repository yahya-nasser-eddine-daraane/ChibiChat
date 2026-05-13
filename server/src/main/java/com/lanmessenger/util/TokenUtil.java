package com.lanmessenger.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and hashes session tokens and OTP codes.
 *
 * We never store raw tokens in the database — only their SHA-256 hash.
 * This way, even if the DB is leaked, tokens are useless to an attacker.
 */
public class TokenUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenUtil() {}

    /**
     * Generates a cryptographically secure random token.
     * Returns a URL-safe Base64 string (no padding).
     * Example: "aB3xQ7mN2kLpRtYw..."  (43 chars for 32 bytes)
     */
    public static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generates a 6-digit numeric OTP code.
     * e.g. "483921"
     */
    public static String generateOtp() {
        int code = 100_000 + RANDOM.nextInt(900_000); // always 6 digits
        return String.valueOf(code);
    }

    /**
     * Hashes a token or OTP with SHA-256.
     * Store this hash in the DB; compare hashes when verifying.
     */
    public static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
