# ADR-004 — A JPA provider on the test classpath

**Status:** Superseded · **Date:** 2026-09-08 · **Amends ADR-003's dependency list**

> **This is a historical record, not the current dependency set.** The 2026-09-11 slimming
> refactor deleted `DatabaseTest`, T-38's repository integration tests, and the EclipseLink
> dependency this ADR added. See ADR-003's 2026-09-12 amendment.

## Context
T-36 requires `DatabaseTest` to build an application-managed `EntityManagerFactory` against a
`RESOURCE_LOCAL` persistence unit, and T-38's repository tests run on it. Both run **outside** a
container, by design: they are ordinary JUnit tests, not a deployment.

`jakarta.jakartaee-api` is an API-only artifact. It contains `jakarta.persistence.Persistence` but
no implementation of it, so outside the server there is no provider to find and
`Persistence.createEntityManagerFactory("petlee-test-pu")` fails with
*"No Persistence provider for EntityManager named petlee-test-pu"* before the first assertion runs.

In production this is not a problem — Payara supplies EclipseLink and WildFly supplies Hibernate,
which is exactly why `persistence.xml` names no `<provider>` (specification §4, portability). The
gap exists only for tests.

The alternatives were: run the data-layer tests inside the deployed server as `*IT`s through a probe
WAR, or reduce T-38 to plain JDBC assertions about the schema. The first makes the slowest,
hardest-to-debug tests out of the ones most likely to fail; the second drops precisely the coverage
the task exists for — lazy loading, the N+1 bound, and real optimistic locking.

## Decision
Add **one** dependency, at `test` scope:

| Dependency | Scope | Why |
|---|---|---|
| `org.eclipse.persistence:eclipselink` | `test` | A JPA provider for out-of-container tests |

EclipseLink rather than Hibernate because it is the provider **Payara runs**, the reference target
in ADR-003. The tests therefore exercise the same provider as the deployment, and provider-specific
behaviour — EclipseLink's collection caching, its `OptimisticLockException` timing — is exercised
rather than assumed.

`pom.xml` now has four dependencies. Two are `provided`, two are `test`, and **`WEB-INF/lib` is
still empty**: `test` scope cannot reach the WAR, which T-01's acceptance criterion checks
independently.

## What does not change
- **`src/main/resources/META-INF/persistence.xml` still names no provider.** The deployed unit stays
  portable across Payara, GlassFish and WildFly. The provider is named only in
  `src/test/resources/META-INF/persistence.xml`, which is never packaged.
- **No provider-specific API is used in application code.** `org.eclipse.persistence.*` appears in no
  class under `src/main/java`, and must not — that would defeat specification §4.
- **The standing rule stands.** This ADR is the mechanism ADR-003 prescribes for an addition, not an
  exception to it. The next dependency needs the next ADR.

## Consequences
- `mvn test` needs a reachable PostgreSQL database (`petlee_test`), as ADR-003 already anticipated
  when it removed H2. Tests that need no database still need none.
- A provider mismatch becomes possible in principle: a query that EclipseLink accepts and Hibernate
  rejects would pass the tests and fail on WildFly. The mitigation is the one already in place —
  application code uses only JPA-standard API, and T-02 criterion 6 deploys to WildFly to prove it.
