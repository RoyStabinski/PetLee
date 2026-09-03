# ADR-001 — JSF reaches the business layer over loopback HTTP

**Status:** Accepted · **Date:** 2026-09-03

## Context
Specification §4 ("Complete Layer Separation") states: *"Direct access from the presentation
layer (JSF) to the database without passing through the business logic layer (Web Services) is
strictly prohibited."* §8 further requires a *"Server Communication Layer"* that *"executes HTTP
client calls (GET, POST, PUT, DELETE) to RESTful Web Services."*

Taken literally this means the JSF tier must speak HTTP, not Java method calls, to the service
tier. Three deployments satisfy the spec to varying degrees: two separate WARs, one WAR with an
internal HTTP hop, or one WAR with direct service injection.

## Decision
**One `pet-lee.war` deployed to a Jakarta EE 10 server** (reference target Payara 6; see ADR-003).
It contains both the Faces servlet and the Jakarta REST application at `/api/*`, both provided by
the platform. JSF managed beans never call a `*Service` class and never
touch an `EntityManager`. They call `ApiClient` (T-24), which performs a real HTTP request to
`/api` on the same server.

`ApiClient` copies the browser's `JSESSIONID` cookie from the inbound `HttpServletRequest` onto
the outbound API request, using the Jakarta REST Client API. Because both servlets live in the
same web application, they share one session manager, so the REST tier resolves **the same
`HttpSession`** the browser owns. Login state therefore works with no token plumbing.

## Consequences
- The layer boundary is physically enforced: a bean that skips `ApiClient` cannot compile against
  anything useful, because service and repository classes are not exposed to the web package.
- One build, one deploy, one database configuration.
- Cost: an extra localhost HTTP round trip and JSON serialize/deserialize per page action.
  Accepted — this is a coursework-scale system, and the architectural clarity is the point.
- **Hard rule for reviewers:** any `import com.petlee.service.*` or `import
  com.petlee.repository.*` inside `com.petlee.web` is a review blocker.

## Rejected alternatives
- **Two WARs** — strictest separation, but the web tier would have to store and replay a second,
  independent `JSESSIONID` for the API, and every environment needs two deploys. Complexity
  without a matching benefit at this scale.
- **Direct service injection** — simplest and fastest, but deletes the Server Communication Layer
  the specification explicitly requires. Rejected on compliance grounds.
