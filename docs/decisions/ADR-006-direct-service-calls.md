# ADR-006 — JSF injects services directly; the loopback HTTP call is gone

**Status:** Accepted · **Date:** 2026-09-12 · **Supersedes ADR-001**

## Context
ADR-001 put a real HTTP request between the JSF tier and the REST tier — `ApiClient` (361 lines)
called `/api` over loopback so that "the layer boundary is physically enforced" and no bean could
reach a service or a repository directly. That bought compliance with specification §4's
prohibition on the presentation layer touching the database directly, at the cost of a
serialize/deserialize round trip on every page action and the session-fixation machinery ADR-001's
amendment describes (`CurrentUser.establish` calling `changeSessionId()`, `SessionLifecycle`
deciding who tears the session down).

Slimming the project removed `ApiClient` and the beans that called it. The remaining question is
what replaced it, and what that costs.

## Decision
**JSF managed beans inject `@ApplicationScoped` services directly** — `UserBean` calls
`UserService`, `PetFormBean` calls `PetService`, and so on. No HTTP request, no JSON body, no
loopback socket. The three tiers (Faces servlet, Jakarta REST application, JPA) still exist in one
WAR, deployed once, and the REST API at `/api/*` still answers every contract endpoint in
`api-contract.md` on its own — it is not a façade kept alive only for appearances. `curl -b
cookies.txt http://localhost:8080/pet-lee/api/pets` returns JSON with no browser involved, which is
what makes the claim "a complete REST API" checkable rather than asserted.

DTOs stay at the REST boundary only (`com.petlee.dto`, mapped in the resource classes). JSF still
binds to entities, for the reason ADR-001's design note already gave: Jakarta EL resolves
`#{pet.name}` through `java.beans.Introspector`, which needs a `getName()` accessor a record does
not have.

### What is actually shared, and in which direction

ADR-001 described a session shared symmetrically by two live requests. That is no longer the
shape, and the honest description is asymmetric:

- **`UserBean.login()`** calls `userService.authenticate(...)` and then
  `CurrentUser.establish(request, SessionUser.of(user))` — the same call ADR-001's `ApiClient`
  used to make from inside the loopback request. It writes the session attribute
  `petlee.user`, in the one `HttpSession` the browser's own request owns.
- **`SecurityFilter`**, the REST tier's `ContainerRequestFilter`, reads exactly that attribute
  (`CurrentUser.from(request)`) to decide `@Secured`/`@AdminOnly` outcomes.
- **`PageAccessFilter`**, the JSF tier's page guard, does **not** read that attribute. It reads
  `UserBean.isLoggedIn()` / `.isAdmin()` directly — deliberately, per its own class comment, so
  that `com.petlee.web` never imports `com.petlee.rest`.

The sharing is therefore **one-directional**: a browser that logs in through `/login.xhtml`
authenticates against `/api/*` for the rest of that session, because both filters ultimately trace
back to the one `HttpSession` and the attribute `UserBean.login()` wrote into it. The reverse never
happens in this application — nothing calls `POST /api/auth/login` from outside a browser that then
tries to load a `.xhtml` page — but if it did, `PageAccessFilter` would not see it, because it
never looks at the session attribute at all.

### Verified, not assumed

This was checked on a running Payara 6 deployment (Task 11's end-to-end verification) rather than
inferred from reading the filters:

1. An ordinary form login at `/login.xhtml` — `loginForm:username`/`loginForm:password` posted
   with the view state, exactly as a browser would, cookie jar preserved across requests.
2. `GET /pet-lee/api/pets/mine` (a `@Secured` endpoint) with that same cookie jar and no other
   authentication of any kind.

Result: **200**, with the JSON list of the logged-in user's own pets — not 401:

```
[{"age":2,"categoryName":"Dogs","gender":"MALE","id":353,
  "imageUrl":"/images/....png","name":"Buddy", ...}]
```

The identical request with a fresh cookie jar that never logged in anywhere returns:

```
401  {"code":"NOT_AUTHENTICATED","message":"You must be logged in to do that."}
```

The one-directional claim above is demonstrated, not asserted.

## Consequences

- **`ApiClient` (361 lines) and `SessionLifecycle`'s login-time role are gone.** A page action is
  now one in-JVM method call, not a loopback HTTP round trip with its own JSON encode/decode.
- **What was given up: a single enforcement point for authorisation.** ADR-001 made every page
  action pass through `SecurityFilter` on its way to the service layer, so one filter was the
  entire authorisation story. That is no longer true. Authorisation for the JSF tier now rests on
  two separate mechanisms that must each be right on their own:
  - `PageAccessFilter`, an allow-list of public views, blocking guests and non-admins from pages
    they should not load; and
  - the service layer's own checks (e.g. `PetService` verifying the caller owns a pet before an
    edit), which fire regardless of which tier called them.

  `PageAccessFilter`'s own class comment already says this plainly: *"this filter is convenience
  and clarity, not enforcement."* The REST tier still has its one chokepoint, `SecurityFilter` —
  that claim from ADR-001 is unaffected — but the JSF tier does not, and did not before this ADR
  either; ADR-001's "physically enforced" boundary was about beans not being able to *compile*
  against a service or a repository, not about a shared authorisation gate the JSF tier passed
  through. What changes here is only that the loopback call — and the fixation/teardown machinery
  built to make two simultaneous holders of one `HttpSession` safe — is gone, because there is now
  only ever one request touching the session at a time.
- **`com.petlee.web` may import `com.petlee.service` and `com.petlee.model`.** It must not import
  `com.petlee.repository` or `com.petlee.dto` — the layer boundary is still enforced by which
  packages compile against which, just at one join instead of two (JSF→service, service→
  repository) instead of three.
- **One build, one deploy, one database configuration** — unchanged from ADR-001.

## Rejected alternatives
- **Keep the loopback call.** Simplest way to preserve ADR-001's "physically enforced" framing
  literally, but every action pays a JSON round trip for no reader of this codebase at this scale,
  and the whole session-fixation/`SessionLifecycle` apparatus exists only to make that round trip
  safe. Removing the round trip removes the reason for the apparatus.
- **Two WARs**, considered and rejected already in ADR-001 for the same reasons — doubled deploy
  and a second `JSESSIONID` to manage — and nothing about slimming the project changes that
  calculus.
