package com.lanmessenger.util;

import org.bouncycastle.crypto.generators.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Password hashing using bcrypt via Bouncy Castle.
 * We never store plain passwords — only the hash + salt.
 */
public class PasswordUtil {

    private static final int BCRYPT_COST = 12; // 2^12 = 4096 iterations
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    /** Generates a random 16-byte salt, Base64-encoded. */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with the given salt using bcrypt.
     * Returns a Base64-encoded hash string safe to store in the DB.
     */
    public static String hash(String password, String saltBase64) {
        byte[] salt     = Base64.getDecoder().decode(saltBase64);
        byte[] passBytes = password.getBytes(StandardCharsets.UTF_8);

        // BCrypt needs exactly 16 bytes of salt
        byte[] salt16 = new byte[16];
        System.arraycopy(salt, 0, salt16, 0, Math.min(salt.length, 16));

        byte[] hash = BCrypt.generate(passBytes, salt16, BCRYPT_COST);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verifies a plain password against a stored hash + salt.
     * Uses constant-time comparison to prevent timing attacks.
     */
    public static boolean verify(String plainPassword, String storedHash, String storedSalt) {
        String computedHash = hash(plainPassword, storedSalt);
        return constantTimeEquals(computedHash, storedHash);
    }

    /** Compares two strings in constant time — prevents timing-based attacks. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int diff = 0;
        for (int i = 0; i < aBytes.length; i++) {
            diff |= (aBytes[i] ^ bBytes[i]);
        }
        return diff == 0;
    }
}
