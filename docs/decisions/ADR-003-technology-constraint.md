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
| `postgresql` (JDBC driver) | `provided` | Specification §8. Installed into the server as a JDBC resource |
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

## Amendment, 2026-09-04 — the driver's scope

This table originally gave the PostgreSQL driver scope `runtime`, which packages it into
`WEB-INF/lib`. Deploying to both reference servers showed that copy is never used: connections come
from the server's pool behind `jdbc/petlee`, made with the driver installed into the server. WildFly
made the redundancy visible by registering a second driver service out of the WAR
(`Started Driver service with driver-name = pet-lee.war_org.postgresql.Driver_42_6`).

The scope is now `provided`, so `WEB-INF/lib` is empty and the decision above — "the build adds *no*
runtime libraries at all" — is literally true rather than nearly true. `provided` still puts the
driver on the compile and test classpaths, so T-38's tests, which open their own JDBC connection,
are unaffected. The dependency list is still exactly three entries; nothing was added or removed.

## Amendment, 2026-09-12 — the three-dependency claim is literally true again

ADR-004 and ADR-005 added four `test`-scope dependencies (EclipseLink, `jersey-client`,
`jersey-hk2`, `parsson`) so the integration test suite could bootstrap a JPA provider and a REST
client outside a container. Slimming the project deleted that suite. All four dependencies went
with it — nothing in the surviving four unit tests (`PetServiceTest`, `UserServiceTest`,
`PetDetailDTOTest`, `PasswordHasherTest`) touches a database or an HTTP client; they run against
hand-written test doubles.

`grep -c '<scope>' pom.xml` returns **3**. This decision's opening claim — "`pom.xml` contains
exactly three dependencies" — was true when written, stopped being true for the two ADR-004/005
amendments, and is true again now that the code that needed them is gone. ADR-004 and ADR-005
remain accurate historical records of why those four dependencies existed for a time; this
amendment does not retract them, it records that their subject no longer exists.

**Bean Validation is not a fourth dependency.** `jakarta.validation.constraints.*` — used on
`RegisterForm`, `PetForm` and the other records in `com.petlee.dto` — comes from
`jakarta.jakartaee-api`, the same single artifact that already supplies JSF, JPA, Jakarta REST,
CDI and JTA. It needs no separate line in the dependency list any more than CDI does; it was never
absent, and was never counted as an addition.

### Where Bean Validation actually runs

Recording this because it is not obvious from the annotations alone. `persistence.xml` sets
`jakarta.persistence.validation.mode=NONE`:

```
<property name="jakarta.persistence.validation.mode" value="NONE"/>
```

which means the JPA provider never validates an entity on `@PrePersist`/`@PreUpdate` — the
constraints on `User` or `Pet`, if any were placed there, would simply never fire. They are not
placed there. Validation is declared instead on the form records in `com.petlee.dto`
(`RegisterForm`, `PetForm`, `LoginForm`) and enforced by `@Valid` on the CDI-managed service
methods that accept them (`UserService.register(@Valid RegisterForm form, ...)` and the
equivalent on `PetService`). Bean Validation is a CDI interceptor: it only runs when the call
arrives through the CDI proxy of the bean, never when a method runs directly on the plain Java
instance behind it — which is exactly what happens when one method on a bean calls another method
on `this`.

That is why `UserService` and `PetService` each hold an injected reference to **themselves**
(commonly named `self`, injected the same way any other collaborator is) and route their
JSF-facing scalar overloads — the ones that take plain `String`/`Long` parameters rather than a
`@Valid` form record — back through `self.register(...)` or `self.create(...)`/`self.update(...)`
instead of calling the validated method directly. Without `self`, a call from `UserBean.register()`
to `userService.register(username, password, ...)` would run on the raw instance, the validation
interceptor would never be invoked, and a blank username or an 8-character-short password would
sail straight through to the database and fail there instead — or not fail at all, if the column
happens to allow it. The javadoc on each `self` field says this explicitly, so the next reader
does not delete it as apparent dead code.

`attachImage` is not part of this: it takes a Servlet `Part`, not a `@Valid` form record, so it has
no scalar overload and nothing to delegate through `self` for — the JSF form calls it directly.

## Standing rule
**Adding any dependency to `pom.xml` requires a new ADR.** "It would be convenient" is not
sufficient justification. If a task appears to need a library, the first question is which platform
capability already does the job.
