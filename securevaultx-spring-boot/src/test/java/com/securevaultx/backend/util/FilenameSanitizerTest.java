package com.securevaultx.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilenameSanitizerTest {

    @Test
    void stripsPathComponents() {
        assertEquals("passwd", FilenameSanitizer.sanitize("../../etc/passwd"));
        assertEquals("secret.txt", FilenameSanitizer.sanitize("C:\\Users\\me\\secret.txt"));
        assertEquals("a.pdf", FilenameSanitizer.sanitize("/abs/path/a.pdf"));
    }

    @Test
    void removesControlCharactersAndNullBytes() {
        assertEquals("evil.txt", FilenameSanitizer.sanitize("evil\u0000.txt"));
        assertEquals("ab.txt", FilenameSanitizer.sanitize("a\u0000b\r\n.txt"));
    }

    @Test
    void neverReturnsEmptyOrHiddenOrDotNames() {
        assertEquals("file", FilenameSanitizer.sanitize(null));
        assertEquals("file", FilenameSanitizer.sanitize(""));
        assertEquals("file", FilenameSanitizer.sanitize(".."));
        assertEquals("file", FilenameSanitizer.sanitize("   "));
        assertEquals("hidden", FilenameSanitizer.sanitize(".hidden"));
    }

    @Test
    void keepsUnicodeAndLimitsLength() {
        assertEquals("résumé 日本語.docx", FilenameSanitizer.sanitize("résumé 日本語.docx"));
        assertTrue(FilenameSanitizer.sanitize("x".repeat(1000)).length() <= FilenameSanitizer.MAX_LENGTH);
    }
}
