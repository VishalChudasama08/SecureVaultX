package com.securevaultx.backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class LimitedInputStreamTest {

    @Test
    void allowsExactlyTheLimit() throws IOException {
        LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(new byte[100]), 100,
                () -> new IllegalStateException("too big"));
        assertEquals(100, in.readAllBytes().length);
        assertEquals(100, in.bytesRead());
    }

    @Test
    void failsOneByteOverTheLimit() {
        LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(new byte[101]), 100,
                () -> new IllegalStateException("too big"));
        assertThrows(IllegalStateException.class, in::readAllBytes);
    }

    @Test
    void singleByteReadsAreCountedToo() throws IOException {
        LimitedInputStream in = new LimitedInputStream(new ByteArrayInputStream(new byte[3]), 2,
                () -> new IllegalStateException("too big"));
        in.read();
        in.read();
        assertThrows(IllegalStateException.class, in::read);
    }
}
