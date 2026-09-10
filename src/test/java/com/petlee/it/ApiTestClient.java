package com.petlee.it;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The HTTP client the {@code *IT} suites share — one session, cookie carried between calls, and
 * every response read as JSON so key sets can be asserted rather than fields.
 *
 * <h2>The same API the application uses</h2>
 * {@code jakarta.ws.rs.client}, which is specification §7's own technology and what {@code ApiClient}
 * (T-24) calls in production. ADR-003 rules out REST Assured; ADR-005 adds the implementation,
 * at test scope, because outside a container nobody supplies one.
 *
 * <h2>The session</h2>
 * {@link #login} captures {@code Set-Cookie} and every later request replays it, exactly as a
 * browser does and as T-24 does. Without that, every authenticated test would be a 401. Each
 * instance is one browser: a test that needs an owner and a stranger at once builds two.
 */
final class ApiTestClient implements AutoCloseable {

    /** Where the deployment is. Overridden with {@code -Dpetlee.baseUrl=…}. */
    static final String BASE_URL =
            System.getProperty("petlee.baseUrl", "http://localhost:8080/pet-lee");

    /**
     * Makes every fixture this run creates unique, so a second run does not collide with the first.
     *
     * <p>Short on purpose: T-13 limits a username to 20 characters, and a longer id silently turned
     * every registration into a 400 — which then looked like a session bug three tests later.
     */
    private static final String RUN_ID = Long.toHexString(System.currentTimeMillis() & 0xFFFFFF);

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final Client client = ClientBuilder.newClient();

    /** The session cookie, as {@code name=value}, or {@code null} while nobody is logged in. */
    private String sessionCookie;

    /** @return a username no other test or run is using */
    static String uniqueName(String prefix) {
        return prefix + RUN_ID + SEQUENCE.incrementAndGet();
    }

    /** @return the run's marker, so a test can recognise its own fixtures */
    static String runId() {
        return RUN_ID;
    }

    // ----------------------------------------------------------------------------- requests

    Response get(String path) {
        return request(path).get();
    }

    Response post(String path, JsonObject body) {
        return request(path).post(Entity.entity(body.toString(), MediaType.APPLICATION_JSON));
    }

    Response put(String path, JsonObject body) {
        return request(path).put(Entity.entity(body.toString(), MediaType.APPLICATION_JSON));
    }

    Response delete(String path) {
        return request(path).delete();
    }

    /**
     * Posts a {@code multipart/form-data} body assembled by hand — boundary, part headers, payload.
     *
     * <p>Thirty lines instead of a dependency: ADR-003 rules out Jersey's {@code MultiPartFeature},
     * and T-24's {@code ApiClient} builds its upload exactly the same way, so this test exercises
     * the shape production actually sends.
     *
     * @param path     the endpoint
     * @param file     the bytes to upload
     * @param filename the filename to claim
     * @param isMain   the {@code isMain} form field
     * @return the response
     */
    Response upload(String path, byte[] file, String filename, boolean isMain) {
        String boundary = "PetLeeIT" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipartBody(boundary, file, filename, isMain);
        MediaType type = new MediaType("multipart", "form-data", Map.of("boundary", boundary));

        return request(path).post(Entity.entity(body, type));
    }

    // ------------------------------------------------------------------------------- session

    /**
     * Logs in and keeps the session.
     *
     * @param username the account
     * @param password its password
     * @return the login response, so a test can assert on its status and body
     */
    Response login(String username, String password) {
        Response response = post("/api/auth/login", Json.createObjectBuilder()
                .add("username", username)
                .add("password", password)
                .build());
        captureSession(response);
        return response;
    }

    /**
     * Registers an ordinary member and logs in as them.
     *
     * <p>Both calls are checked. A fixture that fails quietly here surfaces as an unrelated 401 in
     * whichever test uses it next, and the hour spent finding that is the reason for the assertion.
     *
     * @param username the account to create
     * @param password its password
     * @return the created user
     */
    JsonObject registerAndLogin(String username, String password) {
        Response registered = post("/api/users/register", registration(username, password));
        if (registered.getStatus() != 200) {
            throw new AssertionError("fixture registration failed with " + registered.getStatus()
                    + ": " + registered.readEntity(String.class));
        }
        JsonObject created = readObject(registered);

        Response loggedIn = login(username, password);
        if (loggedIn.getStatus() != 200 || sessionCookie == null) {
            throw new AssertionError("fixture login failed with " + loggedIn.getStatus());
        }
        return created;
    }

    /** @return a registration body the contract would accept, for this username */
    static JsonObject registration(String username, String password) {
        return Json.createObjectBuilder()
                .add("username", username)
                .add("password", password)
                .add("fullName", "Test " + username)
                .add("email", username + "@example.com")
                .add("phone", "050-1234567")
                .add("region", "Tel Aviv")
                .build();
    }

    /** @return the current session cookie value, or {@code null} — the session-fixation check reads it */
    String sessionCookie() {
        return sessionCookie;
    }

    /** Forgets the session without telling the server — for the "no session" failure cases. */
    void forgetSession() {
        sessionCookie = null;
    }

    private void captureSession(Response response) {
        List<Object> setCookie = response.getHeaders().get("Set-Cookie");
        if (setCookie == null) {
            return;
        }
        for (Object header : setCookie) {
            String value = String.valueOf(header);
            if (value.startsWith("JSESSIONID=")) {
                sessionCookie = value.split(";", 2)[0];
            }
        }
    }

    private Invocation.Builder request(String path) {
        WebTarget target = client.target(BASE_URL + path);
        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON);
        if (sessionCookie != null) {
            builder.header("Cookie", sessionCookie);
        }
        return builder;
    }

    // --------------------------------------------------------------------------------- JSON

    /**
     * @param response the response, consumed here
     * @return its body as a JSON object
     */
    static JsonObject readObject(Response response) {
        return read(response).asJsonObject();
    }

    /**
     * @param response the response, consumed here
     * @return its body as a JSON array
     */
    static JsonArray readArray(Response response) {
        return read(response).asJsonArray();
    }

    private static JsonStructure read(Response response) {
        String body = response.readEntity(String.class);
        try (JsonReader reader = Json.createReader(new StringReader(body))) {
            return reader.read();
        } catch (RuntimeException notJson) {
            throw new AssertionError("expected JSON but got: " + body, notJson);
        }
    }

    /**
     * @param object a JSON object
     * @return its keys, sorted — what a contract-shape assertion compares
     */
    static List<String> keysOf(JsonObject object) {
        List<String> keys = new ArrayList<>(object.keySet());
        keys.sort(String::compareTo);
        return keys;
    }

    /**
     * @param object a JSON object
     * @param key    a key that must be present
     * @return whether its value is JSON null — the contact-masking assertion
     */
    static boolean isJsonNull(JsonObject object, String key) {
        return object.get(key) == null || object.get(key).getValueType() == JsonValue.ValueType.NULL;
    }

    /**
     * @return a one-pixel PNG — a real header, so an upload is judged on the rules rather than on
     *         whether the bytes parse
     */
    static byte[] onePixelPng() {
        return new byte[]{
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
                0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0,
                0x1F, 0x15, (byte) 0xC4, (byte) 0x89,
                0, 0, 0, 0x0A, 'I', 'D', 'A', 'T',
                0x78, (byte) 0x9C, 0x63, 0, 1, 0, 0, 5, 0, 1,
                0x0D, 0x0A, 0x2D, (byte) 0xB4,
                0, 0, 0, 0, 'I', 'E', 'N', 'D', (byte) 0xAE, 0x42, 0x60, (byte) 0x82};
    }

    static byte[] multipartBody(String boundary, byte[] file, String filename, boolean isMain) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ascii(out, "--" + boundary + "\r\n");
            ascii(out, "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n");
            ascii(out, "Content-Type: " + contentTypeFor(filename) + "\r\n\r\n");
            out.write(file);
            ascii(out, "\r\n--" + boundary + "\r\n");
            ascii(out, "Content-Disposition: form-data; name=\"isMain\"\r\n\r\n");
            ascii(out, String.valueOf(isMain));
            ascii(out, "\r\n--" + boundary + "--\r\n");
        } catch (IOException impossibleOnAByteArray) {
            throw new UncheckedIOException(impossibleOnAByteArray);
        }
        return out.toByteArray();
    }

    private static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        return "image/jpeg";
    }

    private static void ascii(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() {
        client.close();
    }
}
