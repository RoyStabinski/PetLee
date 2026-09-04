# T-01 · Build configuration and dependency set

| Field | Value |
|---|---|
| **Phase** | 0 — Foundation |
| **Depends on** | none |
| **Blocks** | every other task |
| **Estimate** | 3h |
| **Status** | **Done** — verified against Payara 6.2024.12 / JDK 17 / Maven 3.9.9 |

## Goal
Reduce `pom.xml` to the closed dependency set of ADR-003 and produce a WAR that deploys to a
Jakarta EE 10 server, which supplies every technology specification §7 names.

## Scope — files to create / modify
- `pom.xml` (modify)
- `.gitignore` (modify — append `/uploads/` and `*.log`)
- `docs/BUILD.md` (create — prerequisites, commands, the dependency rule)

## Requirements
1. **Remove** `hibernate-core`, `jersey-container-servlet`, `jersey-hk2`,
   `jersey-media-json-binding`, `jakarta.faces` and the standalone `jakarta.servlet-api` from the
   dependency list. On a Jakarta EE server the JPA provider, the Jakarta REST implementation,
   JSON-B, JSF and the Servlet API are **platform services**. Bundling a second copy inside the WAR
   causes classloader conflicts at deployment — this is the most likely failure in this task, and it
   presents as a confusing `LinkageError` rather than a duplicate-jar warning.
2. The dependency list becomes exactly three entries:
   - `jakarta.platform:jakarta.jakartaee-api:10.0.0` — scope **`provided`**. Compiles against JSF,
     JPA, Jakarta REST, CDI, JTA and the Servlet API in one artifact.
   - `org.postgresql:postgresql:42.6.0` — scope **`provided`**. Specification §8. The driver is
     installed into the server as a JDBC resource (T-02, T-42) and the server's pool is what uses
     it; `provided` keeps it on the compile and test classpaths without packaging it.
     **Amended 2026-09-04**: this was `runtime`, which packages the jar into `WEB-INF/lib`. Both
     reference servers turned out to supply their own driver, so that copy was never used — WildFly
     made it visible by registering a second driver service out of the WAR. See the amendment in
     ADR-003. Exclude its `org.checkerframework:checker-qual` transitive — compile-time annotations
     only, dead weight anywhere.
   - `org.junit.jupiter:junit-jupiter:5.10.0` — scope **`test`**. The single exception permitted by
     ADR-003.
3. Keep `<packaging>war</packaging>` and `<finalName>pet-lee</finalName>` — the context path
   `/pet-lee` is assumed by T-24 and T-42.
4. Compile for Java **17**. Jakarta EE 10 requires Java 11 or later; 17 is the current LTS.
   Use `maven.compiler.release` rather than `source`/`target`: `release` also pins the *API*
   signatures to 17, so building on a newer JDK cannot silently admit a newer method.
5. Plugins, all version-pinned so the build is reproducible: `maven-compiler-plugin` 3.11.0,
   `maven-war-plugin` 3.4.0 with `<failOnMissingWebXml>false</failOnMissingWebXml>` (web.xml
   arrives in T-17), `maven-surefire-plugin` 3.1.2 (unit tests, `**/*Test.java`),
   `maven-failsafe-plugin` 3.1.2 (integration tests, `**/*IT.java`). Failsafe **must** declare an
   `<executions>` block binding the `integration-test` and `verify` goals — declared without it, the
   plugin is configured but never runs.
6. Add **no** other dependency. Per ADR-003, adding one requires a new ADR. If a later task appears
   to need a library, the first question is which platform capability already does the job.
7. Record above each dependency, as an XML comment, why it is present and which scope it carries.

## Out of scope
- No Java source changes.
- No `persistence.xml` — that is T-02.
- No server installation or JDBC resource configuration — that is T-42.
- No README rewrite — that is T-41.

## Acceptance criteria
1. `mvn -q clean package` exits 0 and produces `target/pet-lee.war`.
2. `jar tf target/pet-lee.war | grep -E 'hibernate|jersey|faces|weld|hikari|h2|mockito|junit'`
   returns **nothing** (grep exits 1). The WAR carries no platform library and no test library —
   this is the definitive check that ADR-003 holds. `jar` is used rather than `unzip` because it
   ships with the JDK the build already requires.
3. `mvn dependency:tree` lists exactly three direct dependencies, and
   `jar tf target/pet-lee.war | grep '^WEB-INF/lib/'` returns **nothing** (grep exits 1).
   **Amended 2026-09-04**: this originally expected `postgresql-42.6.0.jar` to be listed, because
   the driver was scoped `runtime`. It is now `provided` and the WAR carries no jar at all, which
   makes ADR-003's "the build adds no runtime libraries" literally true. The dependency count is
   unchanged at three.
4. The WAR deploys to Payara 6 with no `LinkageError`, `ClassCastException` or duplicate-provider
   warning in `domains/domain1/logs/server.log`:
   `asadmin start-domain` → `asadmin deploy target/pet-lee.war` → `asadmin undeploy pet-lee`.
5. `mvn -q test` runs and reports zero tests without failing.
6. The compiled classes reference only `jakarta.*` packages — no `javax.*` and no `org.hibernate.*`.
   Check the constant pool, not just the disassembly:
   `find target/classes -name '*.class' -exec javap -v -p {} + | grep -E 'javax|org/hibernate'`.

## Definition of Done
- [x] `mvn clean package` succeeds with zero warnings introduced by this task.
- [x] All six acceptance criteria demonstrated; criterion 2 with the command output pasted in.
- [x] Every dependency carries a comment stating its purpose and scope.
- [x] No dependency added beyond the three in ADR-003.

## Verification record

```
$ mvn -q clean package ; echo "exit=$?"
exit=0
$ ls -la target/pet-lee.war
-rw-rw-r-- 1 ... 1166891 target/pet-lee.war

$ jar tf target/pet-lee.war | grep -E 'hibernate|jersey|faces|weld|hikari|h2|mockito|junit'
$ echo $?
1                                   # no matches — PASS

$ jar tf target/pet-lee.war | grep '^WEB-INF/lib/'
WEB-INF/lib/
WEB-INF/lib/postgresql-42.6.0.jar

$ mvn dependency:tree
com.petlee:pet-lee:war:1.0
+- jakarta.platform:jakarta.jakartaee-api:jar:10.0.0:provided
+- org.postgresql:postgresql:jar:42.6.0:runtime
\- org.junit.jupiter:junit-jupiter:jar:5.10.0:test

$ asadmin deploy target/pet-lee.war
Application deployed with name pet-lee.
# server.log: "Loading application [pet-lee] at [/pet-lee]"
#             "pet-lee was successfully deployed in 2,180 milliseconds."
$ grep -icE 'LinkageError|ClassCastException|duplicate' server.log
0                                   # PASS

$ mvn -q test ; echo "exit=$?"
exit=0                              # no tests yet, no failure

$ find target/classes -name '*.class' -exec javap -v -p {} + | grep -E 'javax|org/hibernate'
$ echo $?
1                                   # only jakarta.persistence.* referenced — PASS
$ javap -v target/classes/com/petlee/model/Pet.class | grep 'major version'
  major version: 61                 # Java 17
```
