package com.petlee.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;

/**
 * {@code GET /api/health} — "is the REST tier alive?", answered without a database, a session or a
 * deployed page.
 *
 * <h2>What it is for</h2>
 * When a call fails there are four candidate culprits: the server, the deployment, the REST
 * application, and the code behind the endpoint. This resource eliminates the first three in one
 * request. T-42's smoke test uses it as the deployment's success signal, and every later debugging
 * session starts here.
 *
 * <h2>Deliberately unauthenticated</h2>
 * It carries no {@code @Secured} (T-18) and touches no user data. A health check that can fail
 * because the caller is not logged in cannot answer the question it exists to answer. It reveals
 * nothing beyond the fact that the application is deployed, which anyone can already tell by
 * requesting a page.
 *
 * <h2>Deliberately not a database check</h2>
 * It opens no connection. Separating "the REST tier is up" from "the database is reachable" is the
 * point: when a page breaks, this endpoint says which of the two halves to look at. The datasource
 * has its own check, {@code asadmin ping-connection-pool} (T-02).
 */
@Path("health")
@RequestScoped
public class HealthResource {

    /**
     * @return {@code {"status":"UP"}}, serialised by the platform's JSON-B. A single-entry
     *         {@code Map} rather than a DTO because the shape is the whole payload and nothing
     *         else in the system reads it; {@code api-contract.md} does not list this endpoint,
     *         so no client depends on a stable type here.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
