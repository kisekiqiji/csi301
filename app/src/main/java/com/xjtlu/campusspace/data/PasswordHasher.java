package com.xjtlu.campusspace.data;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Pure Java password hashing; no password or credential is logged or retained. */
public final class PasswordHasher {
    public static final String SHA256 = "PBKDF2WithHmacSHA256";
    public static final String SHA1 = "PBKDF2WithHmacSHA1";
    public static final int SHA256_ITERATIONS = 600_000;
    public static final int SHA1_ITERATIONS = 1_400_000;
    private static final int MAX_ITERATIONS = 2_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private PasswordHasher() { }

    public static final class Encoded {
        public final String hash, salt, algorithm;
        public final int iterations;
        private Encoded(String hash, String salt, String algorithm, int iterations) {
            this.hash = hash; this.salt = salt; this.algorithm = algorithm; this.iterations = iterations;
        }
    }

    /** API 24/25 lack the SHA256 factory; retain a recorded SHA1 compatibility format. */
    public static Encoded hash(char[] password) throws GeneralSecurityException {
        String algorithm = preferredAlgorithm();
        int iterations = SHA256.equals(algorithm) ? SHA256_ITERATIONS : SHA1_ITERATIONS;
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = null;
        try {
            derived = derive(password, salt, algorithm, iterations, HASH_BYTES);
            return new Encoded(hex(derived), hex(salt), algorithm, iterations);
        } finally {
            Arrays.fill(salt, (byte) 0);
            if (derived != null) Arrays.fill(derived, (byte) 0);
        }
    }

    public static boolean verify(char[] password, String saltHex, String expectedHex,
                                 String algorithm, int iterations) throws GeneralSecurityException {
        if (password == null || (!SHA256.equals(algorithm) && !SHA1.equals(algorithm))
                || iterations < 1 || iterations > MAX_ITERATIONS) return false;
        byte[] salt = unhex(saltHex, SALT_BYTES), expected = unhex(expectedHex, HASH_BYTES);
        if (salt == null || expected == null) return false;
        byte[] derived = null;
        try {
            derived = derive(password, salt, algorithm, iterations, HASH_BYTES);
            return MessageDigest.isEqual(expected, derived);
        } finally {
            Arrays.fill(salt, (byte) 0);
            Arrays.fill(expected, (byte) 0);
            if (derived != null) Arrays.fill(derived, (byte) 0);
        }
    }

    private static String preferredAlgorithm() throws NoSuchAlgorithmException {
        try { SecretKeyFactory.getInstance(SHA256); return SHA256; }
        catch (NoSuchAlgorithmException unavailable) { SecretKeyFactory.getInstance(SHA1); return SHA1; }
    }

    static byte[] derive(char[] password, byte[] salt, String algorithm, int iterations, int bytes)
            throws GeneralSecurityException {
        if (password == null || salt == null || iterations < 1 || bytes < 1) {
            throw new IllegalArgumentException("Invalid password derivation parameters");
        }
        PBEKeySpec specification = new PBEKeySpec(password, salt, iterations, bytes * 8);
        try { return SecretKeyFactory.getInstance(algorithm).generateSecret(specification).getEncoded(); }
        finally { specification.clearPassword(); }
    }

    static String hex(byte[] value) {
        char[] result = new char[value.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < value.length; i++) {
            result[2 * i] = digits[(value[i] & 255) >>> 4];
            result[2 * i + 1] = digits[value[i] & 15];
        }
        return new String(result);
    }

    private static byte[] unhex(String text, int bytes) {
        if (text == null || text.length() != bytes * 2) return null;
        byte[] result = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            int high = Character.digit(text.charAt(2 * i), 16), low = Character.digit(text.charAt(2 * i + 1), 16);
            if (high < 0 || low < 0) return null;
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }
}
