package com.petlee.service;

import com.petlee.StubRepositories.StubUserRepository;
import com.petlee.dto.RegisterForm;
import com.petlee.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Specification §3/§9.3, exercised through {@code register(RegisterForm)} and
 * {@code authenticate(String, String)} — never the scalar overloads, which delegate through the
 * {@code self} proxy that CDI would normally inject and that is null under {@code new
 * UserService(...)}. {@code @Valid} does not fire outside a container, so these tests cover the
 * duplicate-account and credential-check logic, not field validation.
 */
class UserServiceTest {

    private StubUserRepository users;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = new StubUserRepository();
        service = new UserService(users);
    }

    private static RegisterForm form(String username, String email) {
        return new RegisterForm(username, "password1", "Some Name", email, null, null);
    }

    @Test
    void registrationRejectsADuplicateUsername() {
        service.register(form("alice", "alice@example.com"));

        AppException ex = assertThrows(AppException.class,
                () -> service.register(form("alice", "alice2@example.com")));

        assertEquals(409, ex.getStatus());
    }

    @Test
    void registrationRejectsADuplicateEmailDifferingOnlyInCase() {
        service.register(form("alice", "Alice@Example.com"));

        AppException ex = assertThrows(AppException.class,
                () -> service.register(form("bob", "alice@example.com")));

        assertEquals(409, ex.getStatus());
    }

    @Test
    void authenticateRejectsAWrongPassword() {
        service.register(form("alice", "alice@example.com"));

        AppException ex = assertThrows(AppException.class,
                () -> service.authenticate("alice", "wrong-password"));

        assertEquals(401, ex.getStatus());
    }

    @Test
    void authenticateReturnsTheUserOnSuccess() {
        service.register(form("alice", "alice@example.com"));

        User authenticated = service.authenticate("alice", "password1");

        assertEquals("alice", authenticated.getUserName());
    }
}
