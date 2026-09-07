package com.petlee.web.client;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.ErrorDTO;
import com.petlee.dto.LoginForm;
import com.petlee.dto.PetDTO;
import com.petlee.dto.PetDetailDTO;
import com.petlee.dto.PetForm;
import com.petlee.dto.PetImageDTO;
import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.session.SessionLifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The Server Communication Layer — the one door between the JSF tier and the business tier.
 *
 * <h2>Why an HTTP hop exists at all (ADR-001)</h2>
 * Specification §4 forbids the presentation layer from reaching the database without passing
 * through the business layer, and §8 requires a communication layer that <em>"executes HTTP client
 * calls (GET, POST, PUT, DELETE) to RESTful Web Services"</em>. So a managed bean never calls a
 * {@code *Service} class and never touches an {@code EntityManager}: it calls a method here, and
 * this class makes a genuine HTTP request to {@code /api} on the same server.
 *
 * <p><strong>Do not "optimise" the round trip away.</strong> Replacing a call here with
 * {@code @Inject PetService} would be faster and would delete the layer the specification asks
 * for. ADR-001 makes an import of {@code com.petlee.service} or {@code com.petlee.repository}
 * inside {@code com.petlee.web} a review blocker — T-24 criterion 8 greps for exactly that, so the
 * sentence is worded to keep the check clean — and this is the class the rule exists to protect.
 *
 * <h2>How the session survives the hop</h2>
 * Both servlets live in one WAR, so they share one session manager. Every outbound request copies
 * the browser's session cookie ({@link #forwardSessionCookie}), which is what lets T-18 resolve
 * <strong>the same {@code HttpSession}</strong> the browser owns and see the logged-in user.
 * Without that copy every authenticated call returns 401 — by some distance the most likely way
 * for this class to be broken.
 *
 * <p>{@link #login} is the mirror image: T-18 rotates the session id as a fixation defence, so the
 * API's {@code Set-Cookie} is copied back onto the browser's response. Omit that and a user is
 * logged out again the instant they log in, because their browser still holds the id of a session
 * that no longer exists.
 *
 * <h2>The base URI is derived, never configured</h2>
 * Scheme, host, port and context path all come from the inbound request, so the same WAR works on
 * {@code localhost:8080}, behind a different port, or under a different context path with no code
 * change and no property to forget.
 *
 * <h2>Failures</h2>
 * Any non-2xx becomes an {@link ApiException} carrying T-19's status, code and message; so does a
 * timeout or a refused connection. Managed beans render the message and never see a status number.
 *
 * @see ApiException
 */
@ApplicationScoped
public class ApiClient {

    private static final Logger LOGGER = Logger.getLogger(ApiClient.class.getName());

    /** Where {@code JakartaRestApplication} is mounted, relative to the context path. */
    static final String API_PATH = "api";

    /** The Servlet default, used when {@code web.xml} does not rename the session cookie. */
    static final String DEFAULT_SESSION_COOKIE = "JSESSIONID";

    /**
     * Long enough for a loopback connection on a busy server, short enough that a wedged API
     * cannot pin a rendering thread. An unbounded wait is the difference between one slow page and
     * a server with no free threads.
     */
    private static final long CONNECT_TIMEOUT_SECONDS = 5;

    /** Read timeout. Longer than the connect timeout: an upload legitimately takes a while. */
    private static final long READ_TIMEOUT_SECONDS = 10;

    private static final GenericType<List<CategoryDTO>> CATEGORY_LIST = new GenericType<>() {
    };

    private static final GenericType<List<PetDTO>> PET_LIST = new GenericType<>() {
    };

    private static final String CRLF = "\r\n";

    /**
     * The browser's request, re-resolved on every call. It is a {@link Provider} and not a plain
     * injection point because this bean is {@code @ApplicationScoped} and the request is not: a
     * directly injected instance would be whichever request happened to be in flight when the bean
     * was first created, and every later call would forward a stale cookie.
     */
    @Inject
    private Provider<HttpServletRequest> browserRequest;

    /**
     * One {@link Client} for the application. Building one per call would open a fresh connection
     * pool each time — slow, and a steady leak of sockets under load.
     */
    private Client client;

    @PostConstruct
    void open() {
        client = ClientBuilder.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        LOGGER.log(Level.FINE, () -> "ApiClient ready; connect " + CONNECT_TIMEOUT_SECONDS
                + "s, read " + READ_TIMEOUT_SECONDS + "s");
    }

    @PreDestroy
    void close() {
        if (client != null) {
            client.close();
        }
    }

    // ---------------------------------------------------------------- users and authentication

    /**
     * {@code POST /api/users/register} — open.
     *
     * @param form the registration fields; its password is sent and never logged
     * @return the created user, without a password field ({@code UserDTO} has none)
     * @throws ApiException 400 for invalid fields, 409 {@code USERNAME_TAKEN} or {@code EMAIL_TAKEN}
     */
    public UserDTO register(RegisterForm form) {
        return send("POST", target("users").path("register"), Entity.json(form), UserDTO.class);
    }

    /**
     * {@code POST /api/auth/login} — open, and the one call that changes the browser's cookie.
     *
     * <p>T-18 invalidates the old session and creates a new one, so the API's response carries a
     * {@code Set-Cookie} with a new session id. That header is copied onto the response the browser
     * is about to receive; without it the browser keeps presenting the id of a session that was
     * just destroyed and the very next page renders as a guest.
     *
     * @param form username and password
     * @return the signed-in user
     * @throws ApiException 401 {@code INVALID_CREDENTIALS}
     */
    public UserDTO login(LoginForm form) {
        Response response = exchange("POST", target("auth").path("login"), Entity.json(form));
        List<String> rotated = response.getStringHeaders().get(HttpHeaders.SET_COOKIE);
        UserDTO user = read(response, UserDTO.class);
        applyToBrowser(rotated);
        return user;
    }

    /**
     * {@code POST /api/auth/logout} — auth. Ends the browser's session, so every later call is a
     * guest call again.
     *
     * <h2>Why the session is destroyed here and not by the API</h2>
     * Both requests hold the same {@code HttpSession} (ADR-001). If the loopback request destroys
     * it, this one is left standing on a torn-down object and the next {@code @SessionScoped} bean
     * the caller touches — T-26's {@code UserManagedBean} clears its user one statement later —
     * fails with {@code IllegalStateException: getAttribute: Session already invalidated}. So this
     * method declares the destruction its own through {@link SessionLifecycle}, and performs it on
     * the request the browser is waiting on, where the container's listeners fire on this thread.
     *
     * <p>The {@code finally} is deliberate: whatever the server said, and even if it said nothing
     * at all, this browser is finished with its session. Failing closed is the only safe direction
     * for a logout.
     *
     * @throws ApiException 401 when nobody was logged in
     */
    public void logout() {
        HttpServletRequest browser = currentRequest();
        SessionLifecycle.deferDiscardToCaller(browser);
        try {
            send("POST", target("auth").path("logout"), null, (Class<Void>) null);
        } finally {
            SessionLifecycle.discard(browser);
        }
    }

    // ------------------------------------------------------------------------------ categories

    /**
     * {@code GET /api/categories} — open. The six-row vocabulary behind every filter menu.
     *
     * @return the categories, alphabetically
     */
    public List<CategoryDTO> getCategories() {
        return send("GET", target("categories"), null, CATEGORY_LIST);
    }

    // ------------------------------------------------------------------------------------ pets

    /**
     * {@code GET /api/pets} — open. Filtering happens on the server; a {@code null} argument means
     * "no restriction" and is simply not sent.
     *
     * @param categoryId a category id, or {@code null}
     * @param size       {@code SMALL}, {@code MEDIUM}, {@code LARGE}, or {@code null}
     * @param gender     {@code MALE}, {@code FEMALE}, or {@code null}
     * @return the gallery, newest first; empty when nothing matches
     * @throws ApiException 400 {@code INVALID_FILTER} for an unknown enum value
     */
    public List<PetDTO> getPets(Integer categoryId, String size, String gender) {
        WebTarget target = target("pets");
        if (categoryId != null) {
            target = target.queryParam("categoryId", categoryId);
        }
        if (isPresent(size)) {
            target = target.queryParam("size", size.trim());
        }
        if (isPresent(gender)) {
            target = target.queryParam("gender", gender.trim());
        }
        return send("GET", target, null, PET_LIST);
    }

    /**
     * {@code GET /api/pets/{id}} — open*. The owner's contact fields come back {@code null} unless
     * the forwarded session says somebody is logged in; that decision is the server's, and this
     * method neither asks for it nor knows it was made.
     *
     * @param id the pet
     * @return the full listing
     * @throws ApiException 404 when no pet has that id
     */
    public PetDetailDTO getPet(Long id) {
        return send("GET", target("pets").path(String.valueOf(id)), null, PetDetailDTO.class);
    }

    /**
     * {@code GET /api/pets/mine} — auth. The signed-in user's own listings, every status included,
     * which is what T-32's dashboard shows.
     *
     * <p>Not in {@code api-contract.md}: it extends the frozen contract and is recorded as
     * deviation #11 in ADR-002.
     *
     * @return this user's listings, newest first
     * @throws ApiException 401 when nobody is logged in
     */
    public List<PetDTO> getMyPets() {
        return send("GET", target("pets").path("mine"), null, PET_LIST);
    }

    /**
     * {@code POST /api/pets} — auth. The owner is taken from the session by the server;
     * {@link PetForm} has no owner field and must not gain one.
     *
     * @param form the listing
     * @return the created pet
     * @throws ApiException 401 when nobody is logged in, 400 for invalid fields
     */
    public PetDTO createPet(PetForm form) {
        return send("POST", target("pets"), Entity.json(form), PetDTO.class);
    }

    /**
     * {@code PUT /api/pets/{id}} — auth + owner.
     *
     * @param id   the pet
     * @param form the new values
     * @return the updated pet
     * @throws ApiException 403 {@code NOT_OWNER}, 404, or 409 {@code STALE_PET} when somebody else
     *         edited the listing first
     */
    public PetDTO updatePet(Long id, PetForm form) {
        return send("PUT", target("pets").path(String.valueOf(id)), Entity.json(form), PetDTO.class);
    }

    /**
     * {@code DELETE /api/pets/{id}} — owner or admin. Answers 204, so there is nothing to return.
     *
     * @param id the pet
     * @throws ApiException 403 for anyone who is neither, 404 for an unknown id
     */
    public void deletePet(Long id) {
        send("DELETE", target("pets").path(String.valueOf(id)), null, (Class<Void>) null);
    }

    // ---------------------------------------------------------------------------------- images

    /**
     * {@code POST /api/pets/{petId}/images} — auth + owner, {@code multipart/form-data}.
     *
     * <p>The body is assembled by hand in {@link #multipartBody}. ADR-003 closes the dependency
     * list to the platform, which rules out Jersey's {@code MultiPartFeature} and RESTEasy's
     * equivalent — and those are implementation modules anyway, so a WAR built against one would
     * stop working on the other server. Thirty lines of {@code ByteArrayOutputStream} deploy
     * everywhere.
     *
     * <p>The stream is read fully into memory before it is sent, because the length has to be known
     * to write the body. The size limit is the server's business, not this tier's: T-16 rejects
     * anything over 5&nbsp;MB and answers 400.
     *
     * @param petId    the pet the photograph belongs to
     * @param data     the file's bytes; consumed and closed here
     * @param filename the browser's filename, used for the part header and for nothing else
     * @param isMain   whether this should become the listing's thumbnail
     * @return the stored image
     * @throws ApiException 400 for an unsupported or oversized file, 403 for a non-owner, 404 for
     *         an unknown pet
     */
    public PetImageDTO uploadImage(Long petId, InputStream data, String filename, boolean isMain) {
        byte[] file = readFully(data);
        String boundary = "PetLeeBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipartBody(boundary, file, filename, isMain);
        MediaType type = new MediaType("multipart", "form-data", Map.of("boundary", boundary));

        return send("POST", target("pets").path(String.valueOf(petId)).path("images"),
                Entity.entity(body, type), PetImageDTO.class);
    }

    // ------------------------------------------------------------------------------- transport

    /** A target under the derived base URI. */
    private WebTarget target(String path) {
        return client.target(baseUri(currentRequest())).path(path);
    }

    /**
     * The API's base URI, built from the request that is being served.
     *
     * <p>There is no host and no port literal here and there must never be one: a hard-coded
     * {@code localhost:8080} works exactly until the first deployment that is not this laptop.
     *
     * @param request the inbound browser request
     * @return {@code {scheme}://{host}:{port}{contextPath}/api}
     */
    static URI baseUri(HttpServletRequest request) {
        // java.net.URI rather than jakarta.ws.rs.core.UriBuilder: UriBuilder resolves a
        // RuntimeDelegate on its first call, which exists only inside a Jakarta REST
        // implementation. That is fine at runtime and fatal in a unit test, and ADR-003 closes the
        // dependency list, so there is no implementation to put on the test classpath. The
        // multi-argument constructor also escapes the components for us.
        String path = request.getContextPath() + "/" + API_PATH;
        try {
            return new URI(request.getScheme(), null, request.getServerName(),
                    request.getServerPort(), path, null, null);
        } catch (URISyntaxException impossible) {
            // Every component came from the container's own view of the request it is serving.
            throw new IllegalStateException("the server's own address is not a URI: " + path, impossible);
        }
    }

    /**
     * One request, one response, one log line. Nothing about the body is logged: a login body
     * carries a plaintext password, and a log file is the last place it should end up.
     */
    private Response exchange(String method, WebTarget target, Entity<?> entity) {
        Invocation.Builder builder = target.request(MediaType.APPLICATION_JSON_TYPE);
        forwardSessionCookie(builder);

        String path = target.getUri().getPath();
        long startedAt = System.nanoTime();
        try {
            Response response = entity == null
                    ? builder.method(method)
                    : builder.method(method, entity);
            long millis = elapsedMillis(startedAt);
            LOGGER.log(Level.FINE,
                    () -> method + " " + path + " -> " + response.getStatus() + " in " + millis + " ms");
            return response;
        } catch (ProcessingException unreachable) {
            long millis = elapsedMillis(startedAt);
            LOGGER.log(Level.WARNING,
                    () -> method + " " + path + " -> no response after " + millis + " ms: "
                            + unreachable.getMessage());
            throw new ApiException(ApiException.TRANSPORT_FAILURE, ApiException.API_UNREACHABLE,
                    "The service is not responding. Please try again in a moment.", unreachable);
        }
    }

    private <T> T send(String method, WebTarget target, Entity<?> entity, Class<T> type) {
        return read(exchange(method, target, entity), type);
    }

    private <T> T send(String method, WebTarget target, Entity<?> entity, GenericType<T> type) {
        return read(exchange(method, target, entity), type);
    }

    /**
     * Turns a response into a DTO, or into an {@link ApiException}. This and its {@link
     * GenericType} twin are the only places where an HTTP status is examined; everything above them
     * deals in DTOs and exceptions.
     *
     * @param type the expected body, or {@code null} for the calls that answer 204
     */
    private <T> T read(Response response, Class<T> type) {
        try {
            if (!successful(response)) {
                throw failure(response);
            }
            return type == null || isEmpty(response) ? null : response.readEntity(type);
        } finally {
            response.close();
        }
    }

    /** As {@link #read(Response, Class)}, for the two calls that return a {@code List}. */
    private <T> T read(Response response, GenericType<T> type) {
        try {
            if (!successful(response)) {
                throw failure(response);
            }
            return isEmpty(response) ? null : response.readEntity(type);
        } finally {
            response.close();
        }
    }

    private static boolean successful(Response response) {
        return response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL;
    }

    private static boolean isEmpty(Response response) {
        return response.getStatus() == Response.Status.NO_CONTENT.getStatusCode()
                || !response.hasEntity();
    }

    /**
     * Reads T-19's {@link ErrorDTO} out of a failed response.
     *
     * <p>It falls back rather than throwing if the body is not one: a 502 from a proxy or a
     * container-generated 404 page is HTML, and a parse failure there would replace a useful "not
     * found" with an unrelated {@code JsonbException}.
     */
    private ApiException failure(Response response) {
        int status = response.getStatus();
        String code = null;
        String message = null;

        try {
            if (response.hasEntity()) {
                ErrorDTO error = response.readEntity(ErrorDTO.class);
                if (error != null) {
                    code = error.getCode();
                    message = error.getMessage();
                }
            }
        } catch (RuntimeException notAnErrorDto) {
            LOGGER.log(Level.FINE, notAnErrorDto,
                    () -> "response " + status + " carried no ErrorDTO; falling back");
        }

        if (code == null || code.isBlank()) {
            code = "HTTP_" + status;
        }
        if (message == null || message.isBlank()) {
            message = "The service could not complete that request (" + status + ").";
        }
        return new ApiException(status, code, message);
    }

    // ------------------------------------------------------------------------------- the seam

    /**
     * Copies the browser's session cookie onto the outbound request. This one method is what makes
     * the loopback call an authenticated call.
     *
     * <p>The cookie's name is read from the deployment rather than assumed: {@code JSESSIONID} is
     * only the default, and a {@code <cookie-config><name>} in {@code web.xml} would silently break
     * a hard-coded copy.
     */
    private void forwardSessionCookie(Invocation.Builder builder) {
        HttpServletRequest request = currentRequest();
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        String name = sessionCookieName(request.getServletContext());
        for (Cookie cookie : cookies) {
            if (name.equalsIgnoreCase(cookie.getName())) {
                builder.cookie(name, cookie.getValue());
                return;
            }
        }
    }

    /**
     * @param context the deployment's context, or {@code null} when there is none
     * @return the configured session cookie name, or the Servlet default when it is left alone
     */
    static String sessionCookieName(ServletContext context) {
        String configured = context == null ? null : context.getSessionCookieConfig().getName();
        return configured == null || configured.isBlank() ? DEFAULT_SESSION_COOKIE : configured;
    }

    /**
     * Puts the API's {@code Set-Cookie} headers onto the response the browser will receive.
     *
     * <p>The header is forwarded verbatim, attributes and all, so {@code HttpOnly}, {@code Secure}
     * and {@code Path} arrive exactly as the container wrote them; re-building the cookie by hand
     * would be one place to drop a flag.
     *
     * <p>The browser's response comes from {@code FacesContext}, because a login is always a Faces
     * action. Outside a Faces request there is nothing to write to, which is logged and ignored
     * rather than being an error — the call itself still succeeded.
     */
    private void applyToBrowser(List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return;
        }
        HttpServletResponse browserResponse = currentResponse();
        if (browserResponse == null) {
            LOGGER.log(Level.FINE,
                    "the API rotated the session cookie but there is no Faces response to write it to");
            return;
        }
        for (String header : setCookieHeaders) {
            browserResponse.addHeader(HttpHeaders.SET_COOKIE, header);
        }
    }

    private HttpServletRequest currentRequest() {
        HttpServletRequest request = browserRequest.get();
        if (request == null) {
            throw new IllegalStateException(
                    "ApiClient was called outside a request; it derives the API's address from one");
        }
        return request;
    }

    private static HttpServletResponse currentResponse() {
        FacesContext faces = FacesContext.getCurrentInstance();
        if (faces == null) {
            return null;
        }
        Object response = faces.getExternalContext().getResponse();
        return response instanceof HttpServletResponse http ? http : null;
    }

    // ------------------------------------------------------------------------------- multipart

    /**
     * Builds a {@code multipart/form-data} body with two parts, {@code file} and {@code isMain} —
     * exactly the two T-23's {@code PetImageResource} reads back with {@code getPart("file")} and
     * {@code getParameter("isMain")}.
     *
     * <p>Header lines are ASCII and are terminated with CRLF, as RFC 7578 requires; the file's
     * bytes go in untouched.
     *
     * @param boundary the delimiter, which must not occur in the payload — a UUID makes that safe
     * @param file     the image
     * @param filename the browser's filename, sanitised into the part header
     * @param isMain   sent as the string {@code true} or {@code false}
     * @return the encoded body
     */
    static byte[] multipartBody(String boundary, byte[] file, String filename, boolean isMain) {
        String safeName = headerSafe(filename);
        ByteArrayOutputStream body = new ByteArrayOutputStream(file.length + 512);

        ascii(body, "--" + boundary + CRLF);
        ascii(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"" + CRLF);
        ascii(body, "Content-Type: " + contentTypeFor(safeName) + CRLF + CRLF);
        body.writeBytes(file);
        ascii(body, CRLF);

        ascii(body, "--" + boundary + CRLF);
        ascii(body, "Content-Disposition: form-data; name=\"isMain\"" + CRLF + CRLF);
        ascii(body, Boolean.toString(isMain));
        ascii(body, CRLF);

        ascii(body, "--" + boundary + "--" + CRLF);
        return body.toByteArray();
    }

    /**
     * Makes a browser-supplied filename safe to put inside a header.
     *
     * <p>A filename is attacker-controlled text going into a structured header, so a CR, an LF or
     * a quote in it could forge a part boundary or a header of the caller's choosing. They are
     * removed, along with path separators; T-16 generates the stored name from a UUID anyway, so
     * nothing is lost by being blunt here.
     */
    static String headerSafe(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        String stripped = filename.replaceAll("[\\r\\n\"\\\\/]", "").trim();
        if (stripped.isEmpty()) {
            return "upload";
        }
        return stripped.length() > 100 ? stripped.substring(stripped.length() - 100) : stripped;
    }

    /**
     * The declared type of the file part, guessed from the extension.
     *
     * <p>It is a courtesy, not a decision: T-16 identifies an image by its first bytes and logs the
     * declared type only when the two disagree. Sending {@code application/octet-stream} for
     * everything would work and would make that log line useless.
     */
    static String contentTypeFor(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private static void ascii(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.ISO_8859_1));
    }

    private byte[] readFully(InputStream data) {
        try (InputStream source = data) {
            return source.readAllBytes();
        } catch (IOException broken) {
            throw new ApiException(ApiException.TRANSPORT_FAILURE, "UPLOAD_READ_FAILED",
                    "The photograph could not be read; please try again.", broken);
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
