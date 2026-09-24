package com.petlee.rest;

import com.petlee.dto.RegisterForm;
import com.petlee.dto.UserDTO;
import com.petlee.model.User;
import com.petlee.service.AppException;
import com.petlee.service.UserService;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * {@code /api/users} — registration. Every rule belongs to {@link UserService} and every error
 * status to the exception mapper, so there is deliberately no try/catch here.
 */
@Path("users")
@RequestScoped
public class UserResource {

    private UserService users;

    /** For the container. Public, as Jakarta REST requires of a root resource class. */
    public UserResource() {
    }

    @Inject
    public UserResource(UserService users) {
        this.users = users;
    }

    /**
     * {@code POST /api/users/register} — open. Creates no session; registration does not log
     * anybody in. {@link UserDTO} has no password field, so none can be echoed back.
     *
     * @param form the registration body
     * @return the created user
     */
    @POST
    @Path("register")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public UserDTO register(RegisterForm form) {
        if (form == null) {
            throw new AppException(400, "A request body is required");
        }
        User user = users.register(form);
        return UserDTO.of(user);
    }
}
