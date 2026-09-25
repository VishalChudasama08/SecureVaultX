package com.securevaultx.backend.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class MasterKeyParserTest {

    private static byte[] random32() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return b;
    }

    @Test
    void acceptsStandardAndUrlSafeBase64() {
        byte[] raw = random32();
        SecretKey a = MasterKeyParser.parse(Base64.getEncoder().encodeToString(raw));
        SecretKey b = MasterKeyParser.parse(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        assertArrayEquals(raw, a.getEncoded());
        assertArrayEquals(raw, b.getEncoded());
        assertEquals("AES", a.getAlgorithm());
    }

    @Test
    void missingOrBlankFailsWithClearMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MasterKeyParser.parse(""));
        assertEquals(true, e.getMessage().contains("SECUREVAULTX_MASTER_KEY"));
        assertThrows(IllegalArgumentException.class, () -> MasterKeyParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> MasterKeyParser.parse("   "));
    }

    @Test
    void wrongLengthOrNonBase64Fails() {
        assertThrows(IllegalArgumentException.class,
                () -> MasterKeyParser.parse(Base64.getEncoder().encodeToString(new byte[16])));
        assertThrows(IllegalArgumentException.class,
                () -> MasterKeyParser.parse(Base64.getEncoder().encodeToString(new byte[33])));
        assertThrows(IllegalArgumentException.class, () -> MasterKeyParser.parse("not base64 !!!"));
    }

    @Test
    void rejectsObviousPlaceholder() {
        assertThrows(IllegalArgumentException.class,
                () -> MasterKeyParser.parse(Base64.getEncoder().encodeToString(new byte[32])));
    }

    @Test
    void errorMessagesNeverEchoTheSecret() {
        String bad = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4, 5});
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> MasterKeyParser.parse(bad));
        assertFalse(e.getMessage().contains(bad));
    }
}
