package com.lms.tracker.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lms.tracker.entity.RoleName;
import com.lms.tracker.entity.User;
import com.lms.tracker.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTestSupport {

    private static final String STRONG_PASSWORD = "Str0ng!Pass";

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andReturn();
    }

    // ------------------------------------------------------------------ registration

    @Test
    @DisplayName("student registration: 201, role STUDENT, password stored as BCrypt hash and never returned")
    void registerStudent() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "New Student", "email", "New.Student@Test.local", "password", STRONG_PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.email", is("new.student@test.local")))
                .andExpect(jsonPath("$.role", is("STUDENT")))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByEmail("new.student@test.local").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo(STRONG_PASSWORD).startsWith("$2");
        assertThat(passwordEncoder.matches(STRONG_PASSWORD, saved.getPasswordHash())).isTrue();
        assertThat(saved.getRoleName()).isEqualTo(RoleName.STUDENT);
    }

    @Test
    @DisplayName("privilege escalation: registering with \"role\":\"ADMIN\" is rejected (400) and no user is created")
    void registerCannotChooseRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Sneaky User", "email", "sneaky@test.local",
                                "password", STRONG_PASSWORD, "role", "ADMIN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role", notNullValue()));
        assertThat(userRepository.findByEmail("sneaky@test.local")).isEmpty();
    }

    @Test
    @DisplayName("duplicate email gives 409")
    void registerDuplicateEmail() throws Exception {
        createUser("Existing", "existing@test.local", RoleName.STUDENT);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "Other Person", "email", "EXISTING@test.local", "password", STRONG_PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", is(409)));
    }

    @Test
    @DisplayName("invalid registration input gives 400 with a message per field")
    void registerInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fullName", "<script>", "email", "not-an-email", "password", "weak"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", is("Validation failed")))
                .andExpect(jsonPath("$.fieldErrors.fullName", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("login returns a bearer token that works on protected endpoints")
    void loginSuccess() throws Exception {
        createUser("Login Student", "login@test.local", RoleName.STUDENT);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "login@test.local", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.expiresAt", notNullValue()))
                .andExpect(jsonPath("$.user.role", is("STUDENT")))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andReturn();

        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("login@test.local")));
    }

    @Test
    @DisplayName("wrong password and unknown email give the same 401 (no user enumeration)")
    void loginFailures() throws Exception {
        createUser("Login Student", "login2@test.local", RoleName.STUDENT);

        MvcResult wrongPassword = login("login2@test.local", "Wrong!Pass1");
        MvcResult unknownUser = login("nobody@test.local", "Wrong!Pass1");

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownUser.getResponse().getStatus()).isEqualTo(401);
        JsonNode a = objectMapper.readTree(wrongPassword.getResponse().getContentAsString());
        JsonNode b = objectMapper.readTree(unknownUser.getResponse().getContentAsString());
        assertThat(a.get("message").asText()).isEqualTo("Invalid email or password").isEqualTo(b.get("message").asText());
    }

    @Test
    @DisplayName("brute force: after 5 failures the account+IP is locked (429), even with the right password")
    void loginLockout() throws Exception {
        createUser("Locked Student", "lockme@test.local", RoleName.STUDENT);
        for (int i = 0; i < 5; i++) {
            assertThat(login("lockme@test.local", "Wrong!Pass" + i).getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult blocked = login("lockme@test.local", PASSWORD);
        assertThat(blocked.getResponse().getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("invalid login input gives 400")
    void loginInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "", "password", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    @DisplayName("disabled account cannot log in and its existing tokens stop working")
    void disabledAccount() throws Exception {
        User user = createUser("Disabled Student", "disabled@test.local", RoleName.STUDENT);
        String token = bearer(user);
        user.setEnabled(false);
        userRepository.save(user);

        assertThat(login("disabled@test.local", PASSWORD).getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ logout

    @Test
    @DisplayName("logout revokes the token: the same token then gets 401")
    void logoutRevokesToken() throws Exception {
        User user = createUser("Logout Student", "logout@test.local", RoleName.STUDENT);
        String token = bearer(user);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("revoked")));
    }

    @Test
    @DisplayName("logout without a token gets 401")
    void logoutUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ headers / info leakage

    @Test
    @DisplayName("security headers are present and errors never leak stack traces")
    void securityHeadersAndNoLeakage() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, bearer(createUser("Header Check", "headers@test.local", RoleName.STUDENT))))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "at com.", "trace");
    }
}
