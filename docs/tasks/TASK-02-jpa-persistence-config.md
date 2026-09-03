# T-02 · JPA persistence unit and server-managed data source

| Field | Value |
|---|---|
| **Phase** | 0 — Foundation |
| **Depends on** | T-01 |
| **Blocks** | T-05…T-09, T-36 |
| **Estimate** | 3h |

## Goal
Configure a JTA persistence unit backed by a server-managed, pooled data source, satisfying
specification §4's connection-pooling requirement without adding a pooling library.

## Scope — files to create / modify
- `src/main/resources/META-INF/persistence.xml`
- `docs/deployment/datasource-setup.md` (the JDBC resource definition T-42 will fold in)

## Requirements
1. `persistence.xml` declares persistence unit **`petlee-pu`** with
   `transaction-type="JTA"` and `<jta-data-source>jdbc/petlee</jta-data-source>`.
   JTA, not `RESOURCE_LOCAL`: the server then owns transactions and connection handling, which is
   what removes the need for the hand-rolled helper the earlier draft carried.
2. **No `<provider>` element.** Omitting it uses the server's own JPA provider — EclipseLink on
   Payara and GlassFish, Hibernate on WildFly. Naming one would tie the application to a single
   server and contradict specification §4's portability requirement.
3. List all four entity classes explicitly with `<class>` elements plus
   `<exclude-unlisted-classes>true</exclude-unlisted-classes>`, so a forgotten entity is a startup
   failure rather than a runtime mystery.
4. Properties are **JPA-standard `jakarta.persistence.*` only** — no `hibernate.*` or
   `eclipselink.*` keys:
   - `jakarta.persistence.schema-generation.database.action=none`. The schema is owned by
     `schema.sql` (T-03); the provider must never create or alter it.
   - `jakarta.persistence.validation.mode=NONE` — validation lives in the service layer (T-13, T-15)
     so it can produce contract-shaped error responses.
5. **Connection pooling is configured on the server, not in the application** (specification §4).
   `datasource-setup.md` records the JDBC connection pool and JDBC resource definition: pool name,
   JNDI name `jdbc/petlee`, PostgreSQL driver class, initial size 2, maximum 10, and a validation
   query. Give both the admin-console steps and the equivalent `asadmin` commands.
6. Database URL, user and password live **in the server's JDBC pool definition**, never in
   `persistence.xml` and never in the repository. This is what keeps credentials out of version
   control.
7. Because requirement 4 removes provider-specific schema validation, an entity/table mismatch is
   no longer caught at deployment. That safety net moves to T-38, whose integration tests run
   against the real schema. State this trade-off in a comment in `persistence.xml` so the next
   engineer knows where the check went.

## Out of scope
- No repository classes (T-05…T-09).
- Do not create the database or tables (T-03).
- No `EntityManagerFactory` bootstrap code, no `ServletContextListener`, no transaction helper —
  the container owns all three. Writing any of them is a review blocker (ADR-003).

## Acceptance criteria
1. With the JDBC resource configured and the T-03 schema applied, the WAR deploys and the server
   log shows the persistence unit starting with no error.
2. `asadmin ping-connection-pool petlee-pool` (or the console equivalent) succeeds.
3. With PostgreSQL stopped, deployment fails with a message naming the JNDI resource, and no
   password appears in the log.
4. A trivial injected `@PersistenceContext EntityManager` in a test resource returns a live
   connection.
5. `grep -riE "hibernate\.|eclipselink\.|HikariCP" src/main/resources/META-INF/persistence.xml`
   returns nothing.
6. Deploying the same WAR to a second Jakarta EE server (GlassFish or WildFly) works with only the
   JDBC resource redefined — no code or `persistence.xml` change. This proves requirement 2 and
   specification §4's portability claim.

## Definition of Done
- [ ] All six acceptance criteria demonstrated; criterion 6 is the portability proof and must
      actually be performed.
- [ ] No credentials committed anywhere in the repository.
- [ ] `datasource-setup.md` gives both console and command-line steps.
