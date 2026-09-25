package com.securevaultx.backend.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.securevaultx.backend.config.StorageProperties;
import com.securevaultx.backend.crypto.ChunkedFileCipher;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/** Mode B: Encrypt &amp; Download, then decrypt the uploaded .enc. */
class TransferApiTest extends AbstractApiTest {

    private static final int OVER_VAULT_LIMIT = 6 * 1024 * 1024;

    @Autowired
    StorageProperties storageProps;

    private MvcResult encrypt(MockHttpSession s, String name, byte[] data) throws Exception {
        return mvc.perform(post("/api/files/encrypt").with(csrf()).session(s)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Filename", encodeName(name)).content(data)).andReturn();
    }

    private MvcResult decrypt(MockHttpSession s, byte[] enc) throws Exception {
        return mvc.perform(post("/api/files/decrypt").with(csrf()).session(s)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(enc)).andReturn();
    }

    @Test
    void largeFileEncryptsDownloadsAndIsNotKeptInTheVault() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] data = randomBytes(OVER_VAULT_LIMIT); // > 5 MB: only Encrypt & Download is allowed

        // vault storage is refused for this size ...
        assertEquals(413, uploadToVault(session, "big.bin", data).getResponse().getStatus());

        // ... but Encrypt & Download works
        MvcResult enc = encrypt(session, "big.bin", data);
        assertEquals(200, enc.getResponse().getStatus());
        byte[] encBytes = enc.getResponse().getContentAsByteArray();
        assertEquals(ChunkedFileCipher.encryptedLength(data.length, ChunkedFileCipher.DEFAULT_CHUNK_SIZE),
                encBytes.length);
        assertEquals(String.valueOf(encBytes.length), enc.getResponse().getHeader("Content-Length"));
        assertTrue(enc.getResponse().getHeader("Content-Disposition").contains("big.bin.enc"));

        // the server kept nothing in the vault
        assertEquals("[]", getAs(session, "/api/vault/files").getResponse().getContentAsString());

        // upload the .enc later and get identical bytes back
        MvcResult dec = decrypt(session, encBytes);
        assertEquals(200, dec.getResponse().getStatus());
        assertArrayEquals(data, dec.getResponse().getContentAsByteArray());
    }

    @Test
    void downloadOnlyModeNeverWritesEncryptedFilesToTheVaultDirectory() throws Exception {
        MockHttpSession session = newLoggedInUser();
        Path vault = storageProps.root().toAbsolutePath().normalize().resolve("vault");
        long before;
        try (Stream<Path> s = Files.list(vault)) {
            before = s.count();
        }
        assertEquals(200, encrypt(session, "a.txt", randomBytes(2000)).getResponse().getStatus());
        try (Stream<Path> s = Files.list(vault)) {
            assertEquals(before, s.count());
        }
    }

    @Test
    void tamperedEncryptedFileIsRejectedWithoutReleasingPlaintext() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] enc = encrypt(session, "t.bin", randomBytes(200_000)).getResponse().getContentAsByteArray();
        enc[enc.length / 2] ^= 0x01;
        MvcResult r = decrypt(session, enc);
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("INTEGRITY_CHECK_FAILED"));
    }

    @Test
    void truncatedEncryptedFileIsRejected() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] enc = encrypt(session, "t.bin", randomBytes(200_000)).getResponse().getContentAsByteArray();
        MvcResult r = decrypt(session, Arrays.copyOf(enc, enc.length - 70_000));
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("INTEGRITY_CHECK_FAILED")
                || r.getResponse().getContentAsString().contains("ENCRYPTED_FILE_INVALID"));
    }

    @Test
    void garbageAndEmptyUploadsAreRejectedAsInvalidFiles() throws Exception {
        MockHttpSession session = newLoggedInUser();
        MvcResult garbage = decrypt(session, randomBytes(500));
        assertEquals(400, garbage.getResponse().getStatus());
        assertTrue(garbage.getResponse().getContentAsString().contains("ENCRYPTED_FILE_INVALID"));
        assertEquals(400, decrypt(session, new byte[0]).getResponse().getStatus());
    }

    @Test
    void unsupportedFormatVersionIsReportedClearly() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] enc = encrypt(session, "v.bin", randomBytes(100)).getResponse().getContentAsByteArray();
        enc[4] = 2; // version byte
        MvcResult r = decrypt(session, enc);
        assertEquals(400, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("UNSUPPORTED_FORMAT_VERSION"));
    }

    @Test
    void anotherUserCannotDecryptSomeoneElsesEncFile() throws Exception {
        MockHttpSession owner = newLoggedInUser();
        MockHttpSession intruder = newLoggedInUser();
        byte[] data = randomBytes(5000);
        byte[] enc = encrypt(owner, "private.bin", data).getResponse().getContentAsByteArray();

        MvcResult r = decrypt(intruder, enc);
        assertEquals(404, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("RECORD_NOT_FOUND"));
        assertArrayEquals(data, decrypt(owner, enc).getResponse().getContentAsByteArray());
    }

    @Test
    void vaultFilesDownloadedAsEncCanAlsoBeDecryptedThroughTheUploadEndpoint() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] data = randomBytes(70_000);
        String id = uploadToVaultOk(session, "v.bin", data);
        byte[] enc = getAs(session, "/api/vault/files/" + id + "/encrypted").getResponse().getContentAsByteArray();
        assertArrayEquals(data, decrypt(session, enc).getResponse().getContentAsByteArray());
    }

    @Test
    void encryptionRequiresAuthenticationAndCsrf() throws Exception {
        MvcResult anon = mvc.perform(post("/api/files/encrypt").with(csrf())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[5])).andReturn();
        assertEquals(401, anon.getResponse().getStatus());

        MockHttpSession session = newLoggedInUser();
        MvcResult noCsrf = mvc.perform(post("/api/files/encrypt").session(session)
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[5])).andReturn();
        assertEquals(403, noCsrf.getResponse().getStatus());
    }

    @Test
    void sameFileEncryptedTwiceGivesDifferentCiphertext() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] data = randomBytes(1000);
        byte[] a = encrypt(session, "x.bin", data).getResponse().getContentAsByteArray();
        byte[] b = encrypt(session, "x.bin", data).getResponse().getContentAsByteArray();
        assertTrue(!Arrays.equals(a, b));
    }
}
