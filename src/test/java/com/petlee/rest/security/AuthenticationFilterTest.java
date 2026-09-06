package com.petlee.rest.security;

import com.petlee.model.User;

import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The filter's decisions, without a server: who is let through, and what each refusal says.
 *
 * <p>{@link AuthenticationFilter#decide()} is asserted rather than {@code filter(...)} because
 * building a {@code Response} needs a Jakarta REST runtime, and ADR-003 keeps one off the test
 * classpath. The status and code carried by a {@code Rejection} are the ones the client receives;
 * that they arrive as JSON over the wire is T-20's and T-22's criteria, against a deployed server.
 */
class AuthenticationFilterTest {

    /** A resource whose methods stand in for the real ones; only the annotations are read. */
    static class ExampleResource {

        @Secured
        public void createPet() {
        }

        @AdminOnly
        public void deleteAnyPet() {
        }

        public void listPets() {
        }
    }

    @AdminOnly
    static class ExampleAdminResource {

        public void inheritsTheClassAnnotation() {
        }
    }

    private static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static ResourceInfo resource(Class<?> type, String methodName) {
        return new ResourceInfo() {
            @Override
            public Method getResourceMethod() {
                return method(type, methodName);
            }

            @Override
            public Class<?> getResourceClass() {
                return type;
            }
        };
    }

    private static AuthenticationFilter.Rejection decide(TestRequest request, ResourceInfo info) {
        return new AuthenticationFilter(request.asServletRequest(), info).decide();
    }

    private static AuthenticationFilter.Rejection rejection(AuthenticationFilter.Rejection actual,
                                                            Response.Status expectedStatus,
                                                            String expectedCode) {
        assertNotNull(actual, "the request should have been refused");
        assertEquals(expectedStatus, actual.status);
        assertEquals(expectedCode, actual.code);
        return actual;
    }

    @Test
    @DisplayName("criterion 1 — no session on a @Secured endpoint is 401 NOT_AUTHENTICATED")
    void noSessionIsUnauthorised() {
        AuthenticationFilter.Rejection refused = rejection(
                decide(TestRequest.withoutSession(), resource(ExampleResource.class, "createPet")),
                Response.Status.UNAUTHORIZED, "NOT_AUTHENTICATED");

        assertEquals("You must be logged in to perform this action.", refused.message);
        // The message names no user and no endpoint: at this point the server knows neither, and
        // a message that varied would tell a caller which guesses were closer.
        assertEquals("no authenticated session", refused.reason);
    }

    @Test
    @DisplayName("criterion 6 — an expired session is 401, not 500")
    void expiredSessionIsUnauthorised() {
        TestRequest request = TestRequest.loggedInAs(
                new SessionUser(7L, "donaldt", "Donald Trump", User.Role.USER));
        request.session().invalidate();

        rejection(decide(request, resource(ExampleResource.class, "createPet")),
                Response.Status.UNAUTHORIZED, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("criterion 2 — a logged-in user passes a @Secured endpoint")
    void loggedInUserPasses() {
        TestRequest request = TestRequest.loggedInAs(
                new SessionUser(7L, "donaldt", "Donald Trump", User.Role.USER));

        assertNull(decide(request, resource(ExampleResource.class, "createPet")),
                "the request must reach the resource, so nothing is refused");
    }

    @Test
    @DisplayName("criterion 5 — a USER on an @AdminOnly endpoint is 403 NOT_ADMIN")
    void userOnAdminEndpointIsForbidden() {
        TestRequest request = TestRequest.loggedInAs(
                new SessionUser(7L, "donaldt", "Donald Trump", User.Role.USER));

        AuthenticationFilter.Rejection refused = rejection(
                decide(request, resource(ExampleResource.class, "deleteAnyPet")),
                Response.Status.FORBIDDEN, "NOT_ADMIN");

        assertEquals("donaldt is not an administrator", refused.reason);
    }

    @Test
    @DisplayName("criterion 5 — an ADMIN passes an @AdminOnly endpoint")
    void adminOnAdminEndpointPasses() {
        TestRequest request = TestRequest.loggedInAs(
                new SessionUser(3L, "admin", "Site Administrator", User.Role.ADMIN));

        assertNull(decide(request, resource(ExampleResource.class, "deleteAnyPet")));
    }

    @Test
    @DisplayName("@AdminOnly implies @Secured — a guest gets 401 there, not 403")
    void guestOnAdminEndpointIsUnauthorisedNotForbidden() {
        // 403 would tell an anonymous caller that the endpoint exists and is an admin one.
        rejection(decide(TestRequest.withoutSession(),
                        resource(ExampleResource.class, "deleteAnyPet")),
                Response.Status.UNAUTHORIZED, "NOT_AUTHENTICATED");
    }

    @Test
    @DisplayName("@AdminOnly on the resource class covers its methods")
    void classLevelAdminOnly() {
        TestRequest request = TestRequest.loggedInAs(
                new SessionUser(7L, "donaldt", "Donald Trump", User.Role.USER));

        rejection(decide(request,
                        resource(ExampleAdminResource.class, "inheritsTheClassAnnotation")),
                Response.Status.FORBIDDEN, "NOT_ADMIN");
    }

    @Test
    @DisplayName("a rejected request is never given a session")
    void rejectionCreatesNoSession() {
        TestRequest request = TestRequest.withoutSession();

        decide(request, resource(ExampleResource.class, "createPet"));

        assertEquals(0, request.sessionsCreated());
    }
}
