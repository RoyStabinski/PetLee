package com.petlee.rest.security;

import com.petlee.dto.UserDTO;
import com.petlee.model.User;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-18 criteria 7 and 8 are the two tests the task file requires to be automated: session
 * fixation defence, and "no anonymous request creates a session".
 */
class CurrentUserTest {

    private static SessionUser user(long id, User.Role role) {
        return new SessionUser(id, "donaldt", "Donald Trump", role);
    }

    @Nested
    @DisplayName("reading the session")
    class Reading {

        @Test
        @DisplayName("criterion 8 — a request without a session is not given one")
        void anonymousRequestCreatesNoSession() {
            TestRequest request = TestRequest.withoutSession();

            assertTrue(CurrentUser.from(request.asServletRequest()).isEmpty());
            assertNull(CurrentUser.userIdOrNull(request.asServletRequest()));

            // The assertion that matters: had either call used getSession(true), the container
            // would emit a Set-Cookie for every guest browsing the gallery, and the filter could
            // no longer tell "logged in" from "has been here".
            assertEquals(0, request.sessionsCreated());
            assertNull(request.session());
        }

        @Test
        @DisplayName("a session with no user attribute reads as not logged in")
        void sessionWithoutUser() {
            TestRequest request = TestRequest.withSession();

            assertTrue(CurrentUser.from(request.asServletRequest()).isEmpty());
            assertEquals(0, request.sessionsCreated());
        }

        @Test
        @DisplayName("a session holding a user reads it back")
        void sessionWithUser() {
            SessionUser stored = user(7L, User.Role.USER);
            TestRequest request = TestRequest.loggedInAs(stored);

            Optional<SessionUser> read = CurrentUser.from(request.asServletRequest());

            assertTrue(read.isPresent());
            assertSame(stored, read.get());
            assertEquals(7L, CurrentUser.userIdOrNull(request.asServletRequest()));
        }

        @Test
        @DisplayName("criterion 6 — an expired session reads as not logged in, it does not throw")
        void expiredSessionDoesNotThrow() {
            TestRequest request = TestRequest.loggedInAs(user(7L, User.Role.USER));
            request.session().invalidate();

            // A 500 here would be the difference between "please log in again" and an error page.
            assertTrue(CurrentUser.from(request.asServletRequest()).isEmpty());
            assertNull(CurrentUser.userIdOrNull(request.asServletRequest()));
        }

        @Test
        @DisplayName("a null request is absent, not an exception")
        void nullRequest() {
            assertTrue(CurrentUser.from(null).isEmpty());
            assertNull(CurrentUser.userIdOrNull(null));
        }

        @Test
        @DisplayName("an attribute of another type is ignored rather than cast")
        void foreignAttribute() {
            TestRequest request = TestRequest.withSession();
            request.session().setAttribute(CurrentUser.SESSION_ATTRIBUTE, "not a SessionUser");

            assertTrue(CurrentUser.from(request.asServletRequest()).isEmpty());
        }
    }

    @Nested
    @DisplayName("establishing a session")
    class Establishing {

        @Test
        @DisplayName("criterion 7 — login replaces the session identifier it arrived with")
        void loginRotatesTheSessionId() {
            TestRequest request = TestRequest.withSession();
            FakeHttpSession beforeLogin = request.session();
            String idBeforeLogin = beforeLogin.getId();

            CurrentUser.establish(request.asServletRequest(), user(7L, User.Role.USER));

            assertNotEquals(idBeforeLogin, request.session().getId(),
                    "a fixed session id must not survive login");
            assertEquals(0, request.sessionsCreated(),
                    "the identifier changes; a second session is not created");
            assertEquals(7L, CurrentUser.userIdOrNull(request.asServletRequest()));
        }

        /**
         * The regression behind the ADR-001 amendment. Under ADR-001 the Faces tier reaches login
         * over loopback HTTP and the two requests share one {@code HttpSession}, so destroying it
         * here left the browser's request holding a torn-down object: the next line of Faces code
         * to touch a {@code @SessionScoped} bean died with
         * "getAttribute: Session already invalidated". Changing the identifier achieves the same
         * defence without destroying anything.
         */
        @Test
        @DisplayName("login leaves the session object alive, because the Faces tier is standing on it")
        void loginDoesNotDestroyTheSharedSession() {
            TestRequest request = TestRequest.withSession();
            FakeHttpSession shared = request.session();
            shared.setAttribute("faces.viewState", "whatever Faces put there");

            CurrentUser.establish(request.asServletRequest(), user(7L, User.Role.USER));

            assertTrue(shared.isValid(), "the session the other tier holds must survive login");
            assertSame(shared, request.session(), "and it must still be the same session");
            assertEquals("whatever Faces put there", shared.getAttribute("faces.viewState"),
                    "its attributes belong to the user and survive with it");
        }

        @Test
        @DisplayName("login works for a caller who arrived with no session at all")
        void loginWithoutPriorSession() {
            TestRequest request = TestRequest.withoutSession();

            CurrentUser.establish(request.asServletRequest(), user(3L, User.Role.ADMIN));

            assertEquals(1, request.sessionsCreated());
            assertTrue(CurrentUser.from(request.asServletRequest()).orElseThrow().isAdmin());
        }

        @Test
        @DisplayName("logout invalidates the session, not just the attribute")
        void logoutInvalidates() {
            TestRequest request = TestRequest.loggedInAs(user(7L, User.Role.USER));
            FakeHttpSession session = request.session();

            CurrentUser.terminate(request.asServletRequest());

            assertFalse(session.isValid());
            assertTrue(CurrentUser.from(request.asServletRequest()).isEmpty());
        }

        @Test
        @DisplayName("logout without a session is a no-op, and creates none")
        void logoutWithoutSession() {
            TestRequest request = TestRequest.withoutSession();

            CurrentUser.terminate(request.asServletRequest());
            CurrentUser.terminate(null);

            assertEquals(0, request.sessionsCreated());
        }
    }

    @Nested
    @DisplayName("SessionUser")
    class Payload {

        @Test
        @DisplayName("is built from the DTO the login endpoint already has")
        void fromDto() {
            UserDTO dto = new UserDTO();
            dto.setId(42L);
            dto.setUsername("admin");
            dto.setFullName("Site Administrator");
            dto.setRole("ADMIN");

            SessionUser user = SessionUser.of(dto);

            assertEquals(42L, user.getUserId());
            assertEquals("admin", user.getUsername());
            assertEquals("Site Administrator", user.getFullName());
            assertTrue(user.isAdmin());
        }

        @Test
        @DisplayName("an unrecognised role is not an administrator")
        void unknownRoleIsNotAdmin() {
            UserDTO dto = new UserDTO();
            dto.setId(42L);
            dto.setUsername("mallory");
            dto.setRole("SUPERUSER");

            assertEquals(User.Role.USER, SessionUser.of(dto).getRole());
        }

        @Test
        @DisplayName("carries no password material — there is nowhere to put any")
        void holdsNoSecrets() {
            String rendered = user(7L, User.Role.USER).toString();

            // toString is what reaches a log line, so it is asserted on directly.
            assertEquals("SessionUser[donaldt, USER]", rendered);
        }
    }
}
