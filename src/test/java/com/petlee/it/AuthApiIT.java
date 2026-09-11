package com.petlee.it;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code api-contract.md} § USER / AUTH — {@code POST /api/users/register},
 * {@code POST /api/auth/login}, {@code POST /api/auth/logout}.
 *
 * <p>Success and failure for each, plus the two security properties the contract implies rather
 * than states: a login must rotate the session id (T-18), and no response may carry password
 * material.
 */
class AuthApiIT {

    private ApiTestClient api;

    @BeforeEach
    void openClient() {
        api = new ApiTestClient();
    }

    @AfterEach
    void closeClient() {
        api.close();
    }

    @Test
    @DisplayName("POST /api/users/register — 200 and a UserDTO whose role is USER")
    void registersAUser() {
        String username = ApiTestClient.uniqueName("reg");

        Response response = api.post("/api/users/register", ApiTestClient.registration(username, "12345678"));

        assertEquals(200, response.getStatus());
        JsonObject user = ApiTestClient.readObject(response);
        assertEquals(username, user.getString("username"));
        assertEquals("USER", user.getString("role"), "registration cannot produce an ADMIN");
        assertNotNull(user.getJsonNumber("id"));
    }

    @Test
    @DisplayName("POST /api/users/register — 409 for a username already taken")
    void refusesADuplicateUsername() {
        String username = ApiTestClient.uniqueName("dup");
        api.post("/api/users/register", ApiTestClient.registration(username, "12345678"));

        Response second = api.post("/api/users/register", ApiTestClient.registration(username, "12345678"));

        assertEquals(409, second.getStatus());
        // USERNAME_TAKEN, not USER_EXISTS: the service distinguishes the check it made itself
        // from USER_EXISTS, which is the constraint violation when two registrations race.
        assertEquals("USERNAME_TAKEN", ApiTestClient.readObject(second).getString("code"));
    }

    @Test
    @DisplayName("POST /api/users/register — 400 for a password under eight characters")
    void refusesAnInvalidRegistration() {
        JsonObject tooShort = Json.createObjectBuilder(
                        ApiTestClient.registration(ApiTestClient.uniqueName("short"), "1234567"))
                .build();

        Response response = api.post("/api/users/register", tooShort);

        assertEquals(400, response.getStatus());
        // The body is {code, message} — ErrorDTO has no "field", and the code names the rule.
        assertEquals("PASSWORD_TOO_SHORT", ApiTestClient.readObject(response).getString("code"));
    }

    @Test
    @DisplayName("POST /api/auth/login — 200, the same UserDTO, and a session cookie")
    void logsIn() {
        String username = ApiTestClient.uniqueName("login");
        api.post("/api/users/register", ApiTestClient.registration(username, "12345678"));

        Response response = api.login(username, "12345678");

        assertEquals(200, response.getStatus());
        assertEquals(username, ApiTestClient.readObject(response).getString("username"));
        assertNotNull(api.sessionCookie(), "the server must open a session");
    }

    @Test
    @DisplayName("POST /api/auth/login — 401 for a wrong password")
    void refusesWrongCredentials() {
        String username = ApiTestClient.uniqueName("badpw");
        api.post("/api/users/register", ApiTestClient.registration(username, "12345678"));

        Response response = api.login(username, "not-the-password");

        assertEquals(401, response.getStatus());
    }

    /**
     * T-18: the session id must change at login, or an attacker who fixed a victim's cookie
     * beforehand owns the authenticated session afterwards.
     */
    @Test
    @DisplayName("login rotates the session id — session fixation defence")
    void loginRotatesTheSessionId() {
        String username = ApiTestClient.uniqueName("fix");
        api.post("/api/users/register", ApiTestClient.registration(username, "12345678"));

        // A session exists before login: the register call above already got one.
        api.get("/api/categories");
        String before = api.sessionCookie();

        api.login(username, "12345678");
        String after = api.sessionCookie();

        assertNotNull(after);
        assertNotEquals(before, after, "the session id must not survive a login");
    }

    @Test
    @DisplayName("POST /api/auth/logout — 204 with no body, and the session stops working")
    void logsOut() {
        String username = ApiTestClient.uniqueName("out");
        api.registerAndLogin(username, "12345678");

        Response response = api.post("/api/auth/logout", Json.createObjectBuilder().build());

        assertEquals(204, response.getStatus());
        assertFalse(response.hasEntity() && response.readEntity(String.class).length() > 0,
                "204 means no body");
        assertEquals(401, api.get("/api/pets/mine").getStatus(), "the session is gone");
    }

    @Test
    @DisplayName("POST /api/auth/logout — 401 with no session")
    void logoutWithoutASessionIs401() {
        api.forgetSession();

        assertEquals(401, api.post("/api/auth/logout", Json.createObjectBuilder().build()).getStatus());
    }

    @Test
    @DisplayName("no auth response carries password material")
    void noResponseLeaksPasswordMaterial() {
        String username = ApiTestClient.uniqueName("leak");

        String registered = api.post("/api/users/register",
                ApiTestClient.registration(username, "12345678")).readEntity(String.class);
        String loggedIn = api.login(username, "12345678").readEntity(String.class);

        for (String body : new String[]{registered, loggedIn}) {
            String lower = body.toLowerCase();
            assertFalse(lower.contains("password"), "a response body mentions a password: " + body);
            assertFalse(lower.contains("pbkdf2"), "a response body carries a digest: " + body);
        }
    }

    @Test
    @DisplayName("an error body is JSON with code and message, never HTML or a stack trace")
    void errorsAreJson() {
        Response conflict = api.post("/api/users/register",
                ApiTestClient.registration("admin", "12345678"));

        assertEquals(409, conflict.getStatus());
        assertTrue(conflict.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE),
                "error responses must be application/json, was " + conflict.getMediaType());
        JsonObject error = ApiTestClient.readObject(conflict);
        assertNotNull(error.getString("code"));
        assertNotNull(error.getString("message"));
        assertFalse(error.toString().contains("at com.petlee"), "no stack trace may reach a client");
    }
}
