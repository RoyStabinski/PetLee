# T-01 · Build configuration and dependency set

| Field | Value |
|---|---|
| **Phase** | 0 — Foundation |
| **Depends on** | none |
| **Blocks** | every other task |
| **Estimate** | 3h |

## Goal
Reduce `pom.xml` to the closed dependency set of ADR-003 and produce a WAR that deploys to a
Jakarta EE 10 server, which supplies every technology specification §7 names.

## Scope — files to create / modify
- `pom.xml` (modify)
- `.gitignore` (modify — append `/uploads/` and `*.log`)

## Requirements
1. **Remove** `hibernate-core`, `jersey-container-servlet`, `jersey-hk2`,
   `jersey-media-json-binding` and `jakarta.faces` from the dependency list. On a Jakarta EE server
   the JPA provider, the Jakarta REST implementation, JSON-B and JSF are **platform services**.
   Bundling a second copy inside the WAR causes classloader conflicts at deployment — this is the
   most likely failure in this task, and it presents as a confusing `LinkageError` rather than a
   duplicate-jar warning.
2. The dependency list becomes exactly three entries:
   - `jakarta.platform:jakarta.jakartaee-api:10.0.0` — scope **`provided`**. Compiles against JSF,
     JPA, Jakarta REST, CDI, JTA and the Servlet API in one artifact.
   - `org.postgresql:postgresql:42.6.0` — scope **`runtime`**. Specification §8. It is also
     installed into the server as a JDBC driver (T-42); the `runtime` scope here is for tests.
   - `org.junit.jupiter:junit-jupiter:5.10.0` — scope **`test`**. The single exception permitted by
     ADR-003.
3. Keep `<packaging>war</packaging>` and `<finalName>pet-lee</finalName>` — the context path
   `/pet-lee` is assumed by T-24 and T-42.
4. Raise `maven.compiler.source` / `target` from `16` to **`17`**. Jakarta EE 10 requires Java 11
   or later; 17 is the current LTS.
5. Plugins: `maven-war-plugin` 3.4.0 with `<failOnMissingWebXml>false</failOnMissingWebXml>`,
   `maven-surefire-plugin` 3.1.2 (unit tests, `*Test.java`), `maven-failsafe-plugin` 3.1.2
   (integration tests, `*IT.java`).
6. Add **no** other dependency. Per ADR-003, adding one requires a new ADR. If a later task appears
   to need a library, the first question is which platform capability already does the job.
7. Record above each dependency, as an XML comment, why it is present and which scope it carries.

## Out of scope
- No Java source changes.
- No `persistence.xml` — that is T-02.
- No server installation or JDBC resource configuration — that is T-42.

## Acceptance criteria
1. `mvn -q clean package` exits 0 and produces `target/pet-lee.war`.
2. `unzip -l target/pet-lee.war | grep -E 'hibernate|jersey|faces|weld|hikari|h2|mockito|junit'`
   returns **nothing**. The WAR carries no platform library and no test library — this is the
   definitive check that ADR-003 holds.
3. `mvn dependency:tree` lists exactly three direct dependencies.
4. The WAR deploys to Payara 6 with no `LinkageError`, `ClassCastException` or duplicate-provider
   warning in the server log.
5. `mvn -q test` runs and reports zero tests without failing.
6. The compiled classes reference only `jakarta.*` packages — no `javax.*` and no `org.hibernate.*`.

## Definition of Done
- [ ] `mvn clean package` succeeds with zero warnings introduced by this task.
- [ ] All six acceptance criteria demonstrated; criterion 2 with the command output pasted in.
- [ ] Every dependency carries a comment stating its purpose and scope.
- [ ] No dependency added beyond the three in ADR-003.
