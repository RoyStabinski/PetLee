package com.petlee.rest.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a resource class or method that requires a logged-in user whose role is
 * {@code ADMIN} — specification §8's administration capabilities, used by T-34.
 *
 * <h2>It implies {@link Secured}</h2>
 * Not by meta-annotation — Jakarta REST name binding does not follow one — and not by
 * {@link SecurityFilter} being name-bound to both annotations either, which would only fire for a
 * resource carrying <em>both</em> (name binding is an AND, not an OR; see that class's javadoc).
 * Instead {@code SecurityFilter} carries no binding at all: it runs for every request and reads
 * {@code @AdminOnly}'s presence with reflection, treating it as requiring authentication in the
 * same step as the role check. So {@code @AdminOnly} alone is enough, and a guest calling an
 * {@code @AdminOnly} endpoint gets <strong>401</strong>, not 403: "who are you" is answered before
 * "are you allowed", and answering them in the other order would tell an anonymous caller which
 * endpoints exist for administrators.
 *
 * <h2>Why the role check lives in a filter and not in the service</h2>
 * It is the one authorisation decision that needs no domain knowledge. Everything else — is this
 * the pet's owner, may this listing be removed — depends on data the service has and the filter
 * does not, and the standing rule keeps those in the service layer with the caller's id passed in.
 * A role read from the session, compared to a constant, has no such dependency.
 *
 * <p>Note what this does <em>not</em> replace: an admin deleting someone else's pet still reaches
 * {@code PetService}, which is told the caller is an administrator rather than working it out. The
 * filter decides whether the request may enter; the service decides what it may do.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AdminOnly {
}
