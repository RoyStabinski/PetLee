package com.petlee.web.client;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The presentation tier's only way into the logic tier: JSON over HTTP to {@code /api}, with the
 * caller's bearer token from {@link ApiCredentials} when there is one.
 *
 * <p>One {@link Client} for the whole application, created at startup and closed at shutdown,
 * never one per request. A {@code Client} is heavyweight — it owns a connection pool and its
 * provider configuration — so sharing it lets every call reuse open keep-alive connections instead
 * of paying a TCP handshake each time, and closing it is what releases those sockets. Sharing is
 * safe because a built {@code Client} and its {@code WebTarget}s are thread-safe for issuing
 * requests; on WildFly, RESTEasy backs it with a pooling connection manager (50 connections by
 * default), so concurrent request threads each borrow their own connection.
 */
@ApplicationScoped
public class ApiClient {

    private static final Logger LOGGER = Logger.getLogger(ApiClient.class.getName());

    /** System property that overrides the base URL, e.g. {@code http://localhost:8080/pet-lee/api}. */
    public static final String BASE_URL_PROPERTY = "petlee.api.url";

    private Client client;

    @Inject
    private ApiCredentials credentials;

    /** The browser's request being served, to derive the base URL from. A CDI proxy. */
    @Inject
    private HttpServletRequest request;

    @PostConstruct
    void open() {
        client = ClientBuilder.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    void close() {
        client.close();
    }

    /**
     * {@code GET} a JSON resource.
     *
     * @param path the path under {@code /api}, such as {@code /pets/7}
     * @param type the type to read the body into
     * @return the body
     * @throws ApiException on a non-2xx response or when the API cannot be reached
     */
    public <T> T get(String path, Class<T> type) {
        return call("GET", path, null, new GenericType<>(type));
    }

    /**
     * {@code GET} a JSON resource of a generic type, such as {@code List<PetDTO>}.
     *
     * @param path the path under {@code /api}
     * @param type the type to read the body into
     * @return the body
     * @throws ApiException on a non-2xx response or when the API cannot be reached
     */
    public <T> T get(String path, GenericType<T> type) {
        return call("GET", path, null, type);
    }

    /**
     * {@code POST} a JSON body.
     *
     * @param path the path under {@code /api}
     * @param body the body, or null to send none
     * @param type the type to read the response into, or {@code Void.class} to ignore it
     * @return the response body, or null for {@code Void.class} or an empty response
     * @throws ApiException on a non-2xx response or when the API cannot be reached
     */
    public <T> T post(String path, Object body, Class<T> type) {
        return call("POST", path, body, new GenericType<>(type));
    }

    /**
     * {@code PUT} a JSON body.
     *
     * @param path the path under {@code /api}
     * @param body the body, or null to send none
     * @param type the type to read the response into, or {@code Void.class} to ignore it
     * @return the response body, or null for {@code Void.class} or an empty response
     * @throws ApiException on a non-2xx response or when the API cannot be reached
     */
    public <T> T put(String path, Object body, Class<T> type) {
        return call("PUT", path, body, new GenericType<>(type));
    }

    /**
     * {@code DELETE} a resource.
     *
     * @param path the path under {@code /api}
     * @throws ApiException on a non-2xx response or when the API cannot be reached
     */
    public void delete(String path) {
        call("DELETE", path, null, new GenericType<>(Void.class));
    }

    private <T> T call(String method, String path, Object body, GenericType<T> type) {
        Invocation.Builder builder = client.target(baseUrl()).path(path)
                .request(MediaType.APPLICATION_JSON_TYPE);
        String token = credentials.getToken();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        try (Response response = body == null
                ? builder.method(method)
                : builder.method(method, Entity.json(body))) {

            if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
                throw ApiException.from(response);
            }
            if (type.getRawType() == Void.class || !response.hasEntity()) {
                return null;
            }
            return response.readEntity(type);

        } catch (ProcessingException unreachable) {
            // Connection refused, a timeout, or a 2xx body that would not parse.
            LOGGER.log(Level.WARNING, method + " " + path + " failed", unreachable);
            throw new ApiException(503, "API_UNAVAILABLE",
                    "The service is not available right now. Please try again.", unreachable);
        }
    }

    /**
     * @return the {@code petlee.api.url} system property if set, otherwise
     *         {@code http://localhost:<local port><context path>/api} for the request being served
     */
    private String baseUrl() {
        String configured = System.getProperty(BASE_URL_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        try {
            return "http://localhost:" + request.getLocalPort() + request.getContextPath() + "/api";
        } catch (ContextNotActiveException noRequest) {
            throw new IllegalStateException("No request to derive the API URL from; set the "
                    + BASE_URL_PROPERTY + " system property", noRequest);
        }
    }
}
