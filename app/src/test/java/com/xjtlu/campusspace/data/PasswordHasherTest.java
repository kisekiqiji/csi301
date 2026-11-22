package com.xjtlu.campusspace.data;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class PasswordHasherTest {
    @Test public void pbkdf2MatchesIndependentPublishedVectors() throws Exception {
        byte[] salt = "salt".getBytes(StandardCharsets.US_ASCII);
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b",
                PasswordHasher.hex(PasswordHasher.derive("password".toCharArray(), salt, PasswordHasher.SHA256, 1, 32)));
        // RFC 6070 PBKDF2-HMAC-SHA1 test case 1.
        assertEquals("0c60c80f961f0e71f3a9b524af6012062fe037a6",
                PasswordHasher.hex(PasswordHasher.derive("password".toCharArray(), salt, PasswordHasher.SHA1, 1, 20)));
    }

    @Test public void randomSaltRoundTripAndWrongPassword() throws Exception {
        char[] password = "校园 password🙂".toCharArray();
        char[] original = password.clone();
        try {
            PasswordHasher.Encoded first = PasswordHasher.hash(password), second = PasswordHasher.hash(password);
            assertEquals(32, first.salt.length());
            assertEquals(64, first.hash.length());
            assertNotEquals(first.salt, second.salt);
            assertNotEquals(first.hash, second.hash);
            assertTrue(PasswordHasher.verify(password, first.salt, first.hash, first.algorithm, first.iterations));
            assertFalse(PasswordHasher.verify("incorrect password".toCharArray(), first.salt, first.hash,
                    first.algorithm, first.iterations));
            assertArrayEquals("The caller owns its password buffer", original, password);
        } finally { Arrays.fill(password, '\0'); Arrays.fill(original, '\0'); }
    }

    @Test public void malformedOrUnboundedParametersAreRejected() throws Exception {
        char[] password = "password".toCharArray();
        String salt = "00112233445566778899aabbccddeeff";
        String hash = "0000000000000000000000000000000000000000000000000000000000000000";
        assertFalse(PasswordHasher.verify(password, null, hash, PasswordHasher.SHA256, 1));
        assertFalse(PasswordHasher.verify(password, salt, "zz", PasswordHasher.SHA256, 1));
        assertFalse(PasswordHasher.verify(password, salt, hash, "MD5", 1));
        assertFalse(PasswordHasher.verify(password, salt, hash, PasswordHasher.SHA256, 0));
        assertFalse(PasswordHasher.verify(password, salt, hash, PasswordHasher.SHA256, Integer.MAX_VALUE));
        assertFalse(PasswordHasher.verify(null, salt, hash, PasswordHasher.SHA256, 1));
    }
}
