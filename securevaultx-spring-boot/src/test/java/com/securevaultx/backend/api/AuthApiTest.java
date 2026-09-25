package com.securevaultx.backend.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthApiTest extends AbstractApiTest {

    private String registerJson(String name, String email, String password) {
        return "{\"fullName\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registrationCreatesAccountWithoutLeakingSecrets() throws Exception {
        String email = uniqueEmail();
        MvcResult r = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Vishal", email, PASSWORD))).andExpect(status().isCreated()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains(email));
        assertFalse(body.toLowerCase().contains("password"));
        assertFalse(body.contains("$2"), "no BCrypt hash may be returned");
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult r = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Other", email.toUpperCase(), PASSWORD))).andReturn();
        assertEquals(409, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("", "not-an-email", "short"))).andReturn();
        assertEquals(400, r.getResponse().getStatus());
        String body = r.getResponse().getContentAsString();
        assertTrue(body.contains("VALIDATION_FAILED"));
        assertTrue(body.contains("fullName") && body.contains("email") && body.contains("password"));
    }

    @Test
    void loginSucceedsAndMeReturnsProfile() throws Exception {
        String email = uniqueEmail();
        register(email);
        MockHttpSession session = login(email, PASSWORD);
        MvcResult me = getAs(session, "/api/auth/me");
        assertEquals(200, me.getResponse().getStatus());
        String body = me.getResponse().getContentAsString();
        assertTrue(body.contains(email));
        assertFalse(body.contains("$2"));
    }

    @Test
    void loginFailuresAreUniform() throws Exception {
        String email = uniqueEmail();
        register(email);
        MvcResult wrongPassword = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", email).param("password", "wrong-password-1")).andReturn();
        MvcResult unknownUser = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", uniqueEmail()).param("password", PASSWORD)).andReturn();
        assertEquals(401, wrongPassword.getResponse().getStatus());
        assertEquals(401, unknownUser.getResponse().getStatus());
        assertEquals(wrongPassword.getResponse().getContentAsString(), unknownUser.getResponse().getContentAsString());
        assertTrue(wrongPassword.getResponse().getContentAsString().contains("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        for (String path : new String[]{"/api/auth/me", "/api/vault/files", "/api/files/policy"}) {
            MvcResult r = mvc.perform(get(path)).andReturn();
            assertEquals(401, r.getResponse().getStatus(), path);
            assertTrue(r.getResponse().getContentAsString().contains("UNAUTHENTICATED"));
        }
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("A", uniqueEmail(), PASSWORD))).andReturn();
        assertEquals(403, r.getResponse().getStatus());
        assertTrue(r.getResponse().getContentAsString().contains("CSRF_INVALID"));
    }

    @Test
    void csrfEndpointIssuesAUsableToken() throws Exception {
        MockHttpSession anon = new MockHttpSession();
        MvcResult t = mvc.perform(get("/api/auth/csrf").session(anon)).andExpect(status().isOk()).andReturn();
        String json = t.getResponse().getContentAsString();
        assertTrue(json.contains("headerName"));
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        assertTrue(m.find());
        mvc.perform(post("/api/auth/register").session(anon).header("X-CSRF-TOKEN", m.group(1))
                .contentType(MediaType.APPLICATION_JSON).content(registerJson("A", uniqueEmail(), PASSWORD)))
                .andExpect(status().isCreated());
    }

    @Test
    void logoutInvalidatesTheSession() throws Exception {
        MockHttpSession session = newLoggedInUser();
        assertEquals(200, getAs(session, "/api/auth/me").getResponse().getStatus());
        mvc.perform(post("/api/auth/logout").with(csrf()).session(session)).andExpect(status().isNoContent());
        assertEquals(401, getAs(session, "/api/auth/me").getResponse().getStatus());
    }

    @Test
    void policyEndpointExposesTheConfiguredVaultLimit() throws Exception {
        MockHttpSession session = newLoggedInUser();
        String body = getAs(session, "/api/files/policy").getResponse().getContentAsString();
        assertTrue(body.contains("\"maxVaultFileSizeBytes\":" + 5 * 1024 * 1024));
    }
}
