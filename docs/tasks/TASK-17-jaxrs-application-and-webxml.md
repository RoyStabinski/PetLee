# T-17 · Jakarta REST bootstrap and web.xml

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-01, T-02 |
| **Blocks** | T-18…T-23, T-25 |
| **Estimate** | 3h |

## Goal
Make the WAR deployable on a Jakarta EE server: Jakarta REST serving `/api/*`, Faces serving the
pages, CDI active, sessions configured.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/JakartaRestApplication.java`
- `src/main/webapp/WEB-INF/web.xml`
- `src/main/webapp/WEB-INF/beans.xml`

## Requirements
1. `JakartaRestApplication extends jakarta.ws.rs.core.Application`, annotated
   `@ApplicationPath("/api")` — matching the contract's `Base path: /api`. **Leave the class body
   empty**: with an `Application` subclass present the server discovers `@Path` and `@Provider`
   classes automatically. No feature registration is needed, and none may be added, since any
   provider-specific `Feature` would tie the application to one server (ADR-003).
2. **The Faces servlet needs no `<servlet>` declaration.** A Jakarta EE server registers it
   automatically when Faces artifacts are present. Declaring it by hand risks a duplicate mapping.
   Set `jakarta.faces.PROJECT_STAGE` from a context parameter (`Development` locally, `Production`
   when deployed — T-42 documents the switch); `Development` leaks internal detail into rendered
   pages, so it must not be hard-coded.
3. `beans.xml` (CDI 4.0, `bean-discovery-mode="annotated"`) in `WEB-INF`. CDI is a platform service
   here — no implementation is bundled — but the archive still declares itself a bean archive.
   Without this file every `#{userBean}` expression silently resolves to null.
4. Session configuration in `web.xml`: 30-minute timeout, session cookie marked `HttpOnly`, and
   `Secure` driven by a context parameter so local HTTP development still works while T-42 can turn
   it on for TLS. `HttpOnly` blocks JavaScript from reading `JSESSIONID`, which is the credential
   the entire auth model (T-18, T-24) rests on.
5. Welcome file: `index.xhtml`.
6. A `/api/health` resource returning `200` and `{"status":"UP"}`, no auth. It gives T-42's smoke
   test and every later debugging session a definitive "is the REST tier alive" answer independent
   of the database.
7. JSON serialisation is JSON-B, provided by the platform. Add no JSON library and no custom
   `MessageBodyWriter`.
8. Error pages are **not** configured here; T-33 owns them, so JSF and REST error handling stay in
   one place each.

## Out of scope
- No resource classes beyond `/api/health` (T-20…T-23).
- No authentication filter (T-18).
- No XHTML pages (T-25).
- No CDI implementation, no EL implementation, no Jakarta REST implementation — all platform
  services (ADR-003).

## Acceptance criteria
1. `mvn clean package` produces `target/pet-lee.war`; deploying it to Payara 6 logs no exception.
2. `curl -i http://localhost:8080/pet-lee/api/health` returns `200` with
   `Content-Type: application/json` and body `{"status":"UP"}`.
3. The `Set-Cookie` header for `JSESSIONID` contains `HttpOnly`.
4. A trivial `index.xhtml` containing `#{1+1}` renders `2`, proving Faces and EL are wired.
5. A trivial `@Named @RequestScoped` bean resolves in an EL expression, proving CDI is active.
6. `web.xml` contains no `<servlet>` element for Faces or for Jakarta REST.
7. The same WAR deploys unchanged to GlassFish 7 or WildFly 31.

## Definition of Done
- [ ] All seven acceptance criteria demonstrated against a real Jakarta EE server.
- [ ] `web.xml` has a comment above each block explaining what depends on it.
- [ ] `PROJECT_STAGE` and the `Secure` cookie flag are both parameterised, not hard-coded.
