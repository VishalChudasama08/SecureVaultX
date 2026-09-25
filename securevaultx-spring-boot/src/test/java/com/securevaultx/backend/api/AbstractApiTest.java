package com.securevaultx.backend.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractApiTest {

    static final String PASSWORD = "correct-horse-1";
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"");
    private final SecureRandom random = new SecureRandom();

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void buildMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    static String encodeName(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    void register(String email) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    /** Registers a fresh user and returns an authenticated session. */
    MockHttpSession newLoggedInUser() throws Exception {
        String email = uniqueEmail();
        register(email);
        return login(email, PASSWORD);
    }

    MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("email", email).param("password", password))
                .andExpect(status().isNoContent()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    MvcResult uploadToVault(MockHttpSession session, String filename, byte[] data) throws Exception {
        return mvc.perform(post("/api/vault/files").with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Filename", encodeName(filename)).content(data)).andReturn();
    }

    String uploadToVaultOk(MockHttpSession session, String filename, byte[] data) throws Exception {
        MvcResult r = uploadToVault(session, filename, data);
        org.junit.jupiter.api.Assertions.assertEquals(201, r.getResponse().getStatus());
        return extractId(r.getResponse().getContentAsString());
    }

    static String extractId(String json) {
        Matcher m = ID.matcher(json);
        org.junit.jupiter.api.Assertions.assertTrue(m.find(), "no id in: " + json);
        return m.group(1);
    }

    MvcResult getAs(MockHttpSession session, String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn();
    }
}
