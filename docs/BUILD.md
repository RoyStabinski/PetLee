# Building Pet-Lee

## Prerequisites

| Tool | Version | Note |
|---|---|---|
| JDK | 17 | Jakarta EE 10 needs Java 11+; 17 is the current LTS and the compile target |
| Maven | 3.8+ | |
| Jakarta EE 10 server | Payara 6 (reference) | GlassFish 7 and WildFly 31 also work |
| PostgreSQL | 14+ | To run the application. Not needed to build or test — the four unit tests use hand-written stubs and open no connection |

## Commands

```bash
mvn clean package   # compile + build target/pet-lee.war
mvn test            # the four unit tests (*Test.java, Surefire); no live database or server needed
```

There is no `mvn verify` step: the `*IT.java` integration test suite and the Failsafe plugin that
ran it were both deleted in the 2026-09-11 slimming refactor (see ADR-003's 2026-09-12 amendment).
`mvn test` is the whole test run.

Deploy `target/pet-lee.war`; the context path is `/pet-lee`, fixed by `<finalName>`.

## Dependencies

`pom.xml` has exactly three entries, and the list is closed —
see [ADR-003](decisions/ADR-003-technology-constraint.md).

| Dependency | Scope | In the WAR? |
|---|---|---|
| `jakarta.platform:jakarta.jakartaee-api` | `provided` | No — the server supplies it |
| `org.postgresql:postgresql` | `provided` | No — `WEB-INF/lib` is empty; the driver is installed server-side as a JDBC resource instead (see ADR-003's 2026-09-04 amendment) |
| `org.junit.jupiter:junit-jupiter` | `test` | No |

JSF, JPA, Jakarta REST, CDI, JTA, JSON-B and connection pooling are **platform services**. Bundling
a second copy of any of them inside the WAR conflicts with the server's own copy at deployment, and
it surfaces as a `LinkageError` rather than a duplicate-jar warning.

**Adding a dependency requires a new ADR.** If a task appears to need a library, the first question
is which platform capability already does the job.

## Verifying the WAR is clean

`WEB-INF/lib` must be empty — every one of the three dependencies above is `provided` or `test`
scope, so none of them is packaged:

```bash
jar tf target/pet-lee.war | grep '^WEB-INF/lib/'
```

An empty result is correct, not a mistake.
