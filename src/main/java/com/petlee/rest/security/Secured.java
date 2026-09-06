package com.petlee.rest.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource class or method that requires a logged-in user. Every POST, PUT and DELETE
 * in this project carries this or {@link AdminOnly}; the annotation is the only protection there
 * is, since nothing in {@code web.xml} guards {@code /api}.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Secured {
}
