package com.securevaultx.backend.api;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.securevaultx.backend.config.StorageProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class VaultApiTest extends AbstractApiTest {

    private static final int MB5 = 5 * 1024 * 1024;

    @Autowired
    StorageProperties storageProps;

    private long vaultFileCountOnDisk() throws Exception {
        Path vault = storageProps.root().toAbsolutePath().normalize().resolve("vault");
        try (Stream<Path> s = Files.list(vault)) {
            return s.count();
        }
    }

    @Test
    void storeListDownloadDeleteRoundTrip() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] data = randomBytes(300_000);
        String id = uploadToVaultOk(session, "report ü.pdf", data);

        String list = getAs(session, "/api/vault/files").getResponse().getContentAsString();
        assertTrue(list.contains(id));
        assertTrue(list.contains("report ü.pdf"));

        MvcResult content = getAs(session, "/api/vault/files/" + id + "/content");
        assertEquals(200, content.getResponse().getStatus());
        assertArrayEquals(data, content.getResponse().getContentAsByteArray());
        assertEquals(String.valueOf(data.length), content.getResponse().getHeader("Content-Length"));

        MvcResult enc = getAs(session, "/api/vault/files/" + id + "/encrypted");
        byte[] encBytes = enc.getResponse().getContentAsByteArray();
        assertEquals("SVXF", new String(encBytes, 0, 4, StandardCharsets.US_ASCII));
        assertFalse(Arrays.equals(data, encBytes));

        mvc.perform(delete("/api/vault/files/" + id).with(csrf()).session(session))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent());
        assertEquals(404, getAs(session, "/api/vault/files/" + id).getResponse().getStatus());
    }

    @Test
    void emptyFileCanBeStoredAndRestored() throws Exception {
        MockHttpSession session = newLoggedInUser();
        String id = uploadToVaultOk(session, "empty.txt", new byte[0]);
        assertEquals(0, getAs(session, "/api/vault/files/" + id + "/content").getResponse().getContentAsByteArray().length);
    }

    @Test
    void fileNamesCannotEscapeOrPolluteMetadata() throws Exception {
        MockHttpSession session = newLoggedInUser();
        String id = uploadToVaultOk(session, "../../etc/evil.txt", randomBytes(10));
        String meta = getAs(session, "/api/vault/files/" + id).getResponse().getContentAsString();
        assertTrue(meta.contains("\"filename\":\"evil.txt\""), meta);
    }

    // ---- 5 MB policy ---------------------------------------------------------------------------------------

    @Test
    void exactlyFiveMegabytesIsAllowedInTheVault() throws Exception {
        MockHttpSession session = newLoggedInUser();
        String id = uploadToVaultOk(session, "limit.bin", randomBytes(MB5));
        assertEquals(MB5, getAs(session, "/api/vault/files/" + id + "/content").getResponse().getContentAsByteArray().length);
    }

    @Test
    void oneByteOverTheLimitIsRejectedAndNothingIsStored() throws Exception {
        MockHttpSession session = newLoggedInUser();
        long before = vaultFileCountOnDisk();
        MvcResult r = uploadToVault(session, "too-big.bin", randomBytes(MB5 + 1));
        assertEquals(413, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("VAULT_SIZE_LIMIT_EXCEEDED"));
        assertEquals(before, vaultFileCountOnDisk(), "rejected upload must leave no file behind");
        assertEquals("[]", getAs(session, "/api/vault/files").getResponse().getContentAsString());
    }

    // ---- authorization ----------------------------------------------------------------------------------------

    @Test
    void anotherUserCannotSeeDownloadDecryptOrDelete() throws Exception {
        MockHttpSession owner = newLoggedInUser();
        MockHttpSession intruder = newLoggedInUser();
        String id = uploadToVaultOk(owner, "secret.txt", randomBytes(1000));

        assertEquals("[]", getAs(intruder, "/api/vault/files").getResponse().getContentAsString());
        assertEquals(404, getAs(intruder, "/api/vault/files/" + id).getResponse().getStatus());
        assertEquals(404, getAs(intruder, "/api/vault/files/" + id + "/content").getResponse().getStatus());
        assertEquals(404, getAs(intruder, "/api/vault/files/" + id + "/encrypted").getResponse().getStatus());
        MvcResult del = mvc.perform(delete("/api/vault/files/" + id).with(csrf()).session(intruder)).andReturn();
        assertEquals(404, del.getResponse().getStatus());

        // the owner is unaffected
        assertEquals(200, getAs(owner, "/api/vault/files/" + id + "/content").getResponse().getStatus());
    }

    @Test
    void unknownAndMalformedIdsAreHandledCleanly() throws Exception {
        MockHttpSession session = newLoggedInUser();
        assertEquals(404, getAs(session, "/api/vault/files/" + java.util.UUID.randomUUID()).getResponse().getStatus());
        assertEquals(400, getAs(session, "/api/vault/files/not-a-uuid").getResponse().getStatus());
    }

    @Test
    void uploadRequiresOctetStreamAndAuthentication() throws Exception {
        MockHttpSession session = newLoggedInUser();
        MvcResult wrongType = mvc.perform(post("/api/vault/files").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn();
        assertEquals(415, wrongType.getResponse().getStatus());

        MvcResult anon = mvc.perform(post("/api/vault/files").with(csrf())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).content(new byte[3])).andReturn();
        assertEquals(401, anon.getResponse().getStatus());
    }

    @Test
    void encryptedVaultFileOnDiskContainsNoPlaintext() throws Exception {
        MockHttpSession session = newLoggedInUser();
        byte[] marker = "TOP-SECRET-MARKER-TOP-SECRET-MARKER".getBytes(StandardCharsets.US_ASCII);
        String id = uploadToVaultOk(session, "m.txt", marker);
        Path onDisk = storageProps.root().toAbsolutePath().normalize().resolve("vault").resolve(id + ".enc");
        byte[] raw = Files.readAllBytes(onDisk);
        assertFalse(new String(raw, StandardCharsets.ISO_8859_1).contains("TOP-SECRET-MARKER"));
    }
}
