package com.petlee.rest;

import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.service.UserService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code /api/users} — registration (specification §3, first bullet).
 *
 * <h2>Thin on purpose</h2>
 * The method below parses, delegates and returns. Every rule — the username pattern, the password
 * length, the duplicate check, the forced {@code USER} role — is {@link UserService}'s, and every
 * error status is T-19's. There is no {@code try}/{@code catch} here and there must not be one: a
 * resource that shapes its own errors is a second, divergent copy of the status table.
 *
 * <h2>Registration does not log you in</h2>
 * No session is created here. {@code api-contract.md} creates one only at
 * {@code POST /api/auth/login}, and T-27's flow sends a newly registered user to the login form.
 * Logging them in as a side effect would also mean a failed login attempt straight after
 * registering silently kept the earlier session.
 */
@Path("users")
@RequestScoped
public class UserResource {

    private UserService users;

    /**
     * For the container. It is {@code public}, not {@code protected}: CDI only needs something it
     * can proxy, but the Jakarta REST specification requires a root resource class to have a
     * public constructor, and RESTEasy enforces it — a protected one deploys on Payara and fails
     * on WildFly with "could not find constructor for class".
     */
    public UserResource() {
    }

    @Inject
    public UserResource(UserService users) {
        this.users = users;
    }

    /**
     * {@code POST /api/users/register} — open.
     *
     * <p>The contract: <em>"Response 200 (UserDTO — never includes password)"</em>, and
     * <em>"Errors: 409 if username/email already exists"</em>. <strong>200, not 201</strong> —
     * the status is frozen, and a 201 with a {@code Location} header would be a deviation
     * requiring a row in ADR-002 rather than an improvement.
     *
     * <p>The request body holds a plaintext password, so it is never logged, here or anywhere
     * downstream. {@link UserDTO} has no password field at all, which is what stops one being
     * echoed back.
     *
     * @param form the registration body
     * @return the created user
     */
    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO register(RegisterForm form) {
        return users.register(form);
    }
}
