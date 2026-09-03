# ADR-003 — Only the technologies named in the specification may be used

**Status:** Accepted · **Date:** 2026-09-03 · **Supersedes earlier dependency choices in T-01**

## Context
Specification §7 names the implementation technologies exhaustively:

- **Presentation:** JSF (Jakarta Server Faces)
- **Persistence:** JPA (Jakarta Persistence API)
- **Service layer:** RESTful Web Services (Jakarta REST / JAX-RS)

and §8 names **PostgreSQL** as the storage tier. Nothing else is named.

An earlier draft of this backlog prescribed six libraries outside that list — HikariCP, H2,
REST Assured, Mockito, JSTL and `jersey-media-multipart` — plus a CDI implementation (Weld) needed
to run Jakarta Faces 4.0 on plain Tomcat. Each was defensible in isolation and none was necessary.

## Decision
**The dependency list is closed.** The application is deployed to a **Jakarta EE 10 application
server**, which provides JSF, JPA, Jakarta REST, CDI, JTA and connection pooling as platform
services. The build therefore adds *no* runtime libraries at all.

`pom.xml` contains exactly three dependencies:

| Dependency | Scope | Why |
|---|---|---|
| `jakarta.jakartaee-api` | `provided` | Compile against the platform; the server supplies it at runtime |
| `postgresql` (JDBC driver) | `runtime` | Specification §8. Installed into the server as a JDBC resource |
| `junit-jupiter` | `test` | See "The single exception" below |

**Reference target: Payara 6.** GlassFish 7 and WildFly 31 are drop-in alternatives — all three
implement Jakarta EE 10. Application code is written **provider-agnostically** so any of them
works, which also serves specification §4's portability requirement.

### Replacements
| Removed | Replaced by | Named in |
|---|---|---|
| HikariCP | Server-managed JDBC connection pool, bound via JNDI | §4 requires *pooling*, not a library |
| H2 | A real PostgreSQL database for tests | §8 |
| REST Assured | Jakarta REST Client API (`jakarta.ws.rs.client`) | §7 |
| Mockito | Hand-written test doubles (plain Java classes) | — |
| JSTL | Facelets tags | §7 (JSF) |
| `jersey-media-multipart` | Servlet `Part` via `@MultipartConfig` | Servlet API, part of the platform |
| Weld + `jakarta.el` | CDI and EL provided by the application server | — |
| Hibernate-specific configuration | JPA-standard `jakarta.persistence.*` properties | §7 (JPA) |

### The single exception
**JUnit 5**, at `test` scope only. Automated tests are required scope, and Java has no way to run
them without a test framework. It never appears in the deployed WAR — confirmed by an explicit
acceptance criterion in T-01. This is the only library in the project not named in §7, and it is
recorded here so the exception is visible rather than assumed.

## Consequences
- **Transactions become container-managed.** With a JTA persistence unit, services use
  `@Transactional` and repositories use an injected `@PersistenceContext EntityManager`. The
  hand-rolled `TxRunner`/`JpaUtil` from the earlier draft is deleted — the platform does it, and
  does it better.
- **No provider-specific schema validation.** `hibernate.hbm2ddl.auto=validate` is a Hibernate
  property and is replaced by the JPA-standard
  `jakarta.persistence.schema-generation.database.action=none`. The safety it provided moves to
  T-38, whose integration tests run against the real schema and fail on any entity/table mismatch.
  This is a genuine trade-off: the mismatch is caught by the test suite rather than at deployment.
- **Tests need a real PostgreSQL database.** Slower than in-memory H2, but they now exercise the
  actual dialect, the `ON DELETE RESTRICT` behaviour and the partial unique index — none of which
  H2 could model faithfully. The earlier draft already had to carve out `@Tag("postgres")` tests
  for exactly this reason; that split disappears.
- **`pom.xml` shrinks substantially.** The committed file bundles Hibernate, Jersey and Mojarra
  into the WAR; on a Jakarta EE server all three are platform services and must not be bundled, or
  they will conflict with the server's own copies at deployment.

## Standing rule
**Adding any dependency to `pom.xml` requires a new ADR.** "It would be convenient" is not
sufficient justification. If a task appears to need a library, the first question is which platform
capability already does the job.
