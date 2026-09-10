# ADR-005 — A Jakarta REST client and a JSON-P provider on the test classpath

**Status:** Accepted · **Date:** 2026-09-08 · **Extends ADR-004**

## Context
T-39 tests the deployed API over HTTP with the **Jakarta REST Client API**
(`jakarta.ws.rs.client.ClientBuilder`) and reads the responses as `jakarta.json.JsonObject`, so that
key sets can be asserted directly — a DTO would quietly ignore an extra field, and detecting an
extra field is the whole point of `ContractShapeIT`.

Both are specification §7 technologies, and both are API-only in `jakarta.jakartaee-api`:

- `ClientBuilder.newClient()` fails with *"No client implementation found"*.
- `Json.createReader(…)` fails with *"Provider org.eclipse.parsson.JsonProviderImpl not found"*.

In the deployment neither is a problem — Payara supplies Jersey and Parsson, which is why
`ApiClient` (T-24) uses exactly this API in production code and needs nothing added. The gap is the
same one ADR-004 described for JPA, and it exists only outside the container.

## Decision
Add three dependencies, all at `test` scope:

| Dependency | Scope | Why |
|---|---|---|
| `org.glassfish.jersey.core:jersey-client` | `test` | The Jakarta REST client implementation Payara ships |
| `org.glassfish.jersey.inject:jersey-hk2` | `test` | Jersey's injection provider; the client will not bootstrap without one |
| `org.eclipse.parsson:parsson` | `test` | The JSON-P provider, for reading responses as `JsonObject` |

REST Assured remains excluded (ADR-003): these are implementations of the API the specification
already names, not a different testing API layered on top of it.

`pom.xml` now has seven dependencies. Three are `provided` or `test` implementations of platform
services, and **`WEB-INF/lib` is still empty** — `test` scope cannot reach the WAR, and T-01's
acceptance criterion checks that independently.

## Consequences
- `mvn verify` needs a deployed WAR to test against; `mvn test` still does not.
- The test client and the production `ApiClient` use the same API against the same server, so a
  behaviour that works in one and not the other is a real difference rather than a tooling artefact.
- The standing rule stands. This is ADR-003's mechanism being used, not bypassed.
