package com.petlee.rest.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource class or method that requires an administrator. Implies {@link Secured}:
 * {@link SecurityFilter} treats it as requiring a session too, so a guest gets 401, not 403.
 * The filter decides whether a request may enter; the services still decide what it may do.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AdminOnly {
}
