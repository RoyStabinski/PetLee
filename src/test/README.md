# Tests

Two tiers, told apart by filename so they run independently.

| Tier | Files | Goal | Needs |
|---|---|---|---|
| Unit and repository | `*Test.java` (surefire) | `mvn test` | A PostgreSQL `petlee_test` database — for the `DatabaseTest` subclasses only |
| API integration | `*IT.java` (failsafe) | `mvn verify` | The above, plus a deployed WAR |

Pure unit tests — services on the fakes, mappers, `PasswordHasher` — need neither. They are the
majority and run in milliseconds.

## Create the test database

It is a **separate database** from the application's, so a test run can truncate every table
without touching anything anyone cares about. The schema is the same file production uses.

```bash
createdb -U postgres petlee_test
psql -U postgres -d petlee_test -f src/main/resources/db/schema.sql
```

On Windows:

```bat
"C:\Program Files\PostgreSQL\18\bin\createdb.exe" -U postgres petlee_test
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d petlee_test -f src\main\resources\db\schema.sql
```

`seed.sql` is **not** applied: every test builds the rows it needs with `TestData` and starts from
an empty database. Seeded categories would be six rows every test had to work around.

To reset it, drop and recreate — `schema.sql` is `IF NOT EXISTS` throughout, so re-running it on a
drifted database changes nothing (`src/main/resources/db/README.md` explains why).

## Run

```bash
mvn test                                  # unit + repository tests
mvn verify -Dpetlee.baseUrl=http://localhost:8080/pet-lee   # + the API tests, against a deployment
```

### Coverage

```bash
mvn test jacoco:report      # target/site/jacoco/index.html
```

JaCoCo is a build **plugin**, not a dependency: it runs as a test-time agent and adds nothing to the
compile classpath or the WAR, so ADR-003's closed dependency list is untouched.

### System properties

| Property | Default | Used by |
|---|---|---|
| `petlee.test.db.url` | `jdbc:postgresql://localhost:5432/petlee_test` | `DatabaseTest` |
| `petlee.test.db.user` | `postgres` | `DatabaseTest` |
| `petlee.test.db.password` | `postgres` | `DatabaseTest` |
| `petlee.baseUrl` | `http://localhost:8080/pet-lee` | the `*IT` suites |

No credentials are committed: the defaults are a local developer's, and any of them can be
overridden on the command line.

```bash
mvn test -Dpetlee.test.db.user=petlee_test -Dpetlee.test.db.password=…
```

## What is where

| Class | For |
|---|---|
| `com.petlee.test.DatabaseTest` | Base class: one `EntityManagerFactory` per JVM, a fresh `EntityManager` and an **empty database** per test method, plus `inTransaction`, `persist` and `backdate` |
| `com.petlee.test.TestData` | Fluent entity builders — `aUser()`, `anAdmin()`, `aCategory()`, `aPet()`, `aPetImage()` |
| `com.petlee.test.Fakes` | Hand-written repository doubles: `Fakes.Users`, `Fakes.Categories`, `Fakes.Pets`, `Fakes.Images` |

## The rules these tests are written to

- **A real PostgreSQL, never an in-memory stand-in.** ADR-003 removed H2, so the suite exercises the
  real dialect, `ON DELETE RESTRICT` and the partial unique index on `is_main` — none of which H2
  models faithfully.
- **No mocking library, no assertion library.** JUnit 5 and hand-written doubles (ADR-003).
- **A JPA provider is on the test classpath only** — EclipseLink, `test` scope, ADR-004. The
  deployed `persistence.xml` still names no provider; the one in `src/test/resources` does.
- **No test depends on another.** `DatabaseTest` truncates before each test method, so order and
  leftovers cannot matter. To prove it, run the suite in a random order:

  ```bash
  mvn surefire:test -Djunit.jupiter.testclass.order.default=org.junit.jupiter.api.ClassOrderer\$Random \
                    -Djunit.jupiter.testmethod.order.default=org.junit.jupiter.api.MethodOrderer\$Random
  ```
