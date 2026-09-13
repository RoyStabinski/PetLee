# ADR-001 — JSF reaches the business layer over loopback HTTP

**Status:** Superseded by [ADR-006](ADR-006-direct-service-calls.md) · **Date:** 2026-09-03

> **This is a historical record, not the current architecture.** The 2026-09-11 slimming refactor
> deleted `ApiClient` and the loopback HTTP hop this ADR decided on; `com.petlee.web` now injects
> `com.petlee.service` classes directly. See ADR-006 for the current decision and why.

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

## Amendment — 2026-09-07 (T-24): two requests, one session

Sharing a session manager is what makes the loopback call an authenticated call, and it has a
consequence this ADR did not anticipate: during a login or a logout, **two live requests hold the
same `HttpSession` object** — the browser's Faces request and the loopback REST request. Whichever
one destroys it leaves the other standing on an object the container has already torn down, and the
next `@SessionScoped` bean that request touches fails with
`IllegalStateException: getAttribute: Session already invalidated`.

Reproduced during T-24, one statement after `ApiClient.login` returned — which is exactly where
T-26's `UserManagedBean` stores the signed-in user.

Two rules follow, and both are enforced in code rather than left to memory:

1. **Login changes the identifier; it does not replace the session.**
   `CurrentUser.establish` calls `HttpServletRequest.changeSessionId()`. The fixation defence is
   unchanged — the pre-login identifier is worthless afterwards — but the session object survives,
   so nothing holding a reference to it is harmed. Attributes carry over, which is the accepted
   trade: an attacker who fixates an identifier knows that string and nothing else, and never had
   a way to read or write the session's server-side attributes.

2. **Logout is performed by the request the browser is waiting on.**
   `ApiClient.logout` declares this through `com.petlee.session.SessionLifecycle` before it calls,
   and destroys the session itself afterwards, so the container's session listeners fire on that
   thread. `CurrentUser.terminate` honours the declaration by clearing only its own attribute. The
   session dies either way; what changes is which thread ends it. The flag is a **server-side
   session attribute**, so no external client can set it and every external client still takes the
   ordinary path where the REST tier invalidates the session itself.

`com.petlee.session` exists so that neither tier has to import the other: `com.petlee.web`
depending on `com.petlee.rest` would invert the layering as surely as the service import this ADR
bans.

## Rejected alternatives
- **Two WARs** — strictest separation, but the web tier would have to store and replay a second,
  independent `JSESSIONID` for the API, and every environment needs two deploys. Complexity
  without a matching benefit at this scale.
- **Direct service injection** — simplest and fastest, but deletes the Server Communication Layer
  the specification explicitly requires. Rejected on compliance grounds.
