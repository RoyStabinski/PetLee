# T-42 · Deployment runbook and production configuration

| Field | Value |
|---|---|
| **Phase** | 10 — Handover |
| **Depends on** | T-40 |
| **Blocks** | none |
| **Estimate** | 4h |

## Goal
Make the system deployable by someone other than its authors, on either operating system, from a
written procedure.

## Scope — files to create / modify
- `docs/deployment/runbook.md`
- `docs/deployment/datasource-setup.md` (modify — fold in T-02's draft)
- `docs/deployment/asadmin-commands.txt`

## Requirements
1. The runbook is a numbered procedure covering: prerequisites with exact versions (JDK 17,
   Maven 3.9+, PostgreSQL 15+, **Payara 6**); creating the database and role; applying `schema.sql`
   then `seed.sql` in that order; installing the PostgreSQL JDBC driver into the server; creating
   the JDBC connection pool and the `jdbc/petlee` JNDI resource; setting environment variables;
   building the WAR; deploying it; and a smoke test.
2. **A Jakarta EE 10 server is required, not a servlet container.** Deploying to Tomcat fails
   because Tomcat provides no JPA, no CDI, no JTA and no Jakarta REST implementation — and the WAR
   deliberately bundles none of them (ADR-003). The error does not explain itself, so the runbook
   must call this out in bold. Payara 6 is the reference target; GlassFish 7 and WildFly 31 are
   documented as drop-in alternatives.
3. **Connection pooling is configured on the server** (specification §4), not in the application.
   Document the pool: name, JNDI name `jdbc/petlee`, datasource class
   `org.postgresql.ds.PGSimpleDataSource`, initial size 2, maximum 10, validation on borrow,
   and a leak-detection interval. Give both admin-console steps and `asadmin` commands.
4. Environment variables and server properties, each documented with purpose, default and whether
   it is required: `PETLEE_UPLOAD_DIR`, `PETLEE_PROJECT_STAGE`, `PETLEE_SECURE_COOKIE`.
   Database credentials are **not** environment variables — they live in the server's JDBC pool
   definition (T-02 requirement 6).
5. `asadmin-commands.txt` carries the exact commands with **placeholder** credentials only. A
   committed password is a permanent leak; say so in a comment at the top of the file.
6. A production checklist that must be ticked before going live:
   - `PETLEE_PROJECT_STAGE=Production` — `Development` leaks internals into rendered pages.
   - `PETLEE_SECURE_COOKIE=true` behind TLS.
   - `PETLEE_UPLOAD_DIR` set **outside** the server's `applications` directory, or a redeploy
     destroys every uploaded photograph.
   - The seeded admin password changed from `Admin123!`.
   - SQL logging disabled on the JDBC pool.
   - The upload directory writable by the server's user, and backed up.
7. A rollback procedure: undeploy, redeploy the previous WAR, and note that the database schema is
   not versioned, so a schema change requires a manual reverse script.
8. A troubleshooting table for the failures this stack actually produces, each row naming the task
   that owns the fix:

   | Symptom | Cause | Owner |
   |---|---|---|
   | `ClassNotFoundException: jakarta.persistence.*` | Deployed to a servlet container, not a Jakarta EE server | T-42 |
   | `LinkageError` / duplicate provider at deploy | A platform library was bundled into the WAR | T-01 |
   | Blank page showing `#{...}` literally | Missing `beans.xml`, so CDI never activated | T-17 |
   | 401 on every JSF action | `JSESSIONID` forwarding broken | T-24 |
   | `PersistenceException` naming an unknown column | Schema and entities disagree | T-03 / T-04 |
   | Images 404 after redeploy | Upload directory inside the deployment folder | T-16 |
   | `NameNotFoundException: jdbc/petlee` | JNDI resource not created before deployment | T-02 |

9. Portability (specification §4): the procedure is written for both Windows and Linux, with both
   command forms given, and **executed on both** during T-40.
10. A smoke test at the end: `GET /api/health` returns `{"status":"UP"}`, the home page renders the
    gallery, and a login succeeds. Three checks that together prove all three tiers are alive.

## Out of scope
- No Docker, CI/CD, or infrastructure-as-code — not required by the specification.
- No clustering, load balancing, or TLS certificate issuance (only the flag that assumes TLS).
- No monitoring stack.

## Acceptance criteria
1. A person who has not deployed this application before follows the runbook on a clean machine and
   reaches a working system. **This must actually be attempted**, not assumed.
2. The same is done on the other operating system, satisfying the portability requirement.
3. Every command in the runbook is executed and produces the documented output.
4. The smoke test passes on a fresh deployment.
5. Deliberately deploying to Tomcat produces the failure described in the troubleshooting table,
   and the table's guidance resolves it.
6. Setting `PETLEE_UPLOAD_DIR` inside the deployment directory, then redeploying, reproduces the
   image-loss failure the checklist warns about — proving the warning is real and not folklore.
7. Deploying the same WAR to GlassFish 7 or WildFly 31, with only the JDBC resource redefined,
   works with no code change (T-02 requirement 6).
8. `asadmin-commands.txt` contains no real credentials.
9. The production checklist is complete and every item is verifiable.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated; criteria 1 and 2 signed off by the person who
      performed the deployment, with the date.
- [ ] No credentials committed.
- [ ] The runbook has been followed start to finish at least once on each operating system.
