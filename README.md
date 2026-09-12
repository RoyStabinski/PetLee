# Pet-Lee

Pet-Lee is a pet adoption and rehoming platform: guests browse a public gallery of listings,
registered users post pets for adoption and inquire about others' listings, and an administrator
moderates the catalogue. It is a coursework-scale Jakarta EE application built to a fixed
specification (three tiers — presentation, business logic, persistence — talking to one
PostgreSQL database).

## Stack

- **Presentation:** Jakarta Server Faces (JSF) / Facelets
- **Business logic:** Jakarta RESTful Web Services (Jakarta REST / Jersey), plus CDI services the
  JSF tier calls directly (see `docs/decisions/ADR-006-direct-service-calls.md`)
- **Persistence:** Jakarta Persistence (JPA)
- **Database:** PostgreSQL 18
- **Runtime:** a Jakarta EE 10 application server — the reference target is **Payara 6**;
  GlassFish 7 and WildFly 31 are drop-in alternatives (`docs/decisions/ADR-003-technology-constraint.md`)

The build adds no runtime libraries of its own: `pom.xml` has exactly three dependencies (the
Jakarta EE 10 platform API, the PostgreSQL JDBC driver, and JUnit 5 for tests), and the packaged
WAR's `WEB-INF/lib` is empty. Everything JSF, JPA and Jakarta REST need is supplied by the server.

## Building

Requires Java 17 and Maven.

```bash
mvn clean package
```

This produces `target/pet-lee.war`.

## Database setup

Create an empty `petlee` database, then apply the two scripts in order:

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

`schema.sql` creates the `users`, `category` and `pet` tables, their constraints and indexes.
`seed.sql` inserts the six categories from the specification and one administrator account
(`admin` / `Admin123!`). Both scripts are idempotent — safe to re-run against an already-migrated
database. See `src/main/resources/db/README.md` for details, including how to add the
application's own least-privileged database role (`petlee_app`).

## Deploying

1. Create a JDBC connection pool and resource named `jdbc/petlee` on the server, pointing at the
   `petlee` database — `docs/deployment/datasource-setup.md` has the exact `asadmin` commands for
   Payara/GlassFish and the WildFly equivalent. The application never opens its own JDBC
   connection or names a driver class; it only looks up `jdbc/petlee` by name.
2. Deploy `target/pet-lee.war` to the server (Payara's autodeploy directory, `asadmin deploy`, or
   the admin console). The context path is `/pet-lee`.
3. Open `http://localhost:8080/pet-lee/`.

`docs/deployment/upload-directory.md` covers where uploaded photographs are stored on disk — never
inside the deployment itself, since a redeploy would wipe them.

## Accounts

| Username | Password | Role | Source |
|---|---|---|---|
| `admin` | `Admin123!` | ADMIN | `seed.sql` — created by the script above on any fresh database |

Register a new account through `/register.xhtml` to try the flow as a first-time user.

## Architecture

The application is one WAR containing all three tiers. JSF managed beans (`com.petlee.web`) inject
CDI services (`com.petlee.service`) directly rather than calling the REST API over HTTP; the REST
API at `/api/*` (`com.petlee.rest`) is a complete, independently usable surface documented in
`api-contract.md`, exercised the same way a non-browser client would (`curl -b cookies.txt
http://localhost:8080/pet-lee/api/pets`). Both tiers share one `HttpSession`, so a browser login
also authenticates that session's `/api` calls — see
`docs/decisions/ADR-006-direct-service-calls.md` for exactly which direction that sharing goes, and
what it costs in place of a single authorisation chokepoint. The rest of the reasoning behind the
project's shape — why the dependency list is closed, why entities rather than records cross into
JSF views, where every deviation from the frozen `api-contract.md` came from — is recorded in
`docs/decisions/`.

## Known limitations

- **Photo upload is available through the web UI only, not the REST API.** `POST
  /api/pets/{id}/images` was removed entirely rather than repaired: Jersey cannot inject a Servlet
  `Part` as a `@FormParam`, and the two ways to fix that (`jersey-media-multipart`, or multipart
  configuration on the JAX-RS servlet) are both closed off by
  `docs/decisions/ADR-003-technology-constraint.md`. Uploading a photo works through
  `addPet.xhtml`'s `<h:inputFile>`, posted to the Faces servlet. See ADR-002, deviation #10.
- **The container-level cap lives in `web.xml`, not in a servlet declaration.** The surviving
  upload path is the Faces servlet, so `jakarta.faces.UPLOADER_MAX_FILE_SIZE` (5 MB, matching
  `ImageStore.MAX_BYTES`) and `jakarta.faces.UPLOADER_MAX_REQUEST_SIZE` (6 MB) are plain
  context-params — no `@MultipartConfig`, no RESTEasy risk, because the JAX-RS upload endpoint
  that made multipart configuration look expensive was removed entirely (ADR-002 #10).
