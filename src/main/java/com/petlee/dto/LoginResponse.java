package com.petlee.dto;

import java.io.Serializable;

/**
 * The body {@code POST /api/auth/login} returns: the user, and a token to send back as
 * {@code Authorization: Bearer <token>} in place of the session cookie.
 */
public record LoginResponse(UserDTO user, String token) implements Serializable { }
