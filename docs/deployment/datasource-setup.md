# JDBC data source setup — `jdbc/petlee`

The persistence unit `petlee-pu` (`src/main/resources/META-INF/persistence.xml`) is JTA and names
one resource: `jdbc/petlee`. That resource does not exist until you create it on the application
server. This page is how.

Connection pooling is a **server** responsibility here, not an application one — specification §4
requires pooling, not a pooling library, and the platform already provides it (ADR-003). The
database URL, user and password are configured **only** in the pool below. They never appear in
`persistence.xml`, in `pom.xml`, or anywhere else in this repository.

Reference target is **Payara 6**; the console paths and `asadmin` commands below are Payara's and
apply unchanged to GlassFish 7. WildFly's equivalent is at the end.

Everything on this page was run against Payara 6.2025.11 on JDK 21. That combination prints
`WARNING: You are running the product on an unsupported JDK version` at `start-domain` and then
works normally; JDK 17 is the version Payara 6 states support for.

## What gets created

| Setting | Value |
|---|---|
| Connection pool name | `petlee-pool` |
| JNDI name of the resource | `jdbc/petlee` |
| Resource type | `javax.sql.DataSource` |
| Datasource class | `org.postgresql.ds.PGSimpleDataSource` |
| Database | `petlee` on `localhost:5432` |
| Initial / minimum pool size | 2 |
| Maximum pool size | 10 |
| Connection validation | on borrow, table validation against `pg_catalog.pg_class` |
| On failed validation | close all connections and reconnect |

Pool sizes: two connections are kept open so the first request after an idle period does not pay
connection setup, and ten is the ceiling a single-instance deployment of this application reaches
under the load specification §4 describes. Both are pool attributes, so they are tuned on the
server without rebuilding the WAR.

Validation matters because PostgreSQL, a firewall or a container restart can drop a pooled
connection while the pool still believes it is good. Without validation that stale connection is
handed to a request and the request fails; with it, the pool discards and replaces the connection
first.

## Step 0 — install the PostgreSQL JDBC driver into the server

The driver is a `provided` dependency in `pom.xml`, so it is on the compile and test classpaths but
is **not** packaged into the WAR. The *server* needs its own copy — it is the server's pool, not the
application, that opens connections.

```bash
# Payara 6 / GlassFish 7 — then restart the domain
mkdir -p "$PAYARA_HOME/glassfish/domains/domain1/lib/ext"
cp postgresql-42.6.0.jar "$PAYARA_HOME/glassfish/domains/domain1/lib/ext/"
asadmin restart-domain
```

`lib/ext` is **not** present in the Payara 6 distribution — the domain ships `lib/applibs`,
`lib/classes`, `lib/databases` and `lib/warlibs` only. Create it, or the copy silently becomes a
file named `ext` and the driver is never found. (Verified on Payara 6.2025.11: the domain starts
and the jar loads from `lib/ext` once the directory exists.)

The jar is in your local Maven repository at
`~/.m2/repository/org/postgresql/postgresql/42.6.0/postgresql-42.6.0.jar`, or from
<https://jdbc.postgresql.org/download/>. Use the same version as `${postgresql.version}` in
`pom.xml`.

## Step 1 — keep the password out of the shell history: create a password alias

`asadmin` commands are stored in shell history and printed by `asadmin get`. An alias keeps the
password out of both.

```bash
asadmin create-password-alias petlee-db-password
# prompts twice for the value; nothing is echoed
```

For a scripted setup, the same command reads the value from a file instead of prompting — delete
the file afterwards:

```bash
printf 'AS_ADMIN_ALIASPASSWORD=<the password>\n' > alias.txt
asadmin --passwordfile alias.txt create-password-alias petlee-db-password
rm alias.txt
```

Refer to it later as `${ALIAS=petlee-db-password}`. If you skip this step, substitute the literal
password for that token below — and accept that it is then readable in the domain configuration.

## Step 2 — create the connection pool

### Command line

```bash
asadmin create-jdbc-connection-pool \
  --datasourceclassname org.postgresql.ds.PGSimpleDataSource \
  --restype javax.sql.DataSource \
  --steadypoolsize 2 \
  --maxpoolsize 10 \
  --isconnectvalidatereq=true \
  --validationmethod=table \
  --validationtable=pg_catalog.pg_class \
  --failconnection=true \
  --property "serverName=localhost:portNumber=5432:databaseName=petlee:user=petlee_app:password=\${ALIAS=petlee-db-password}" \
  petlee-pool
```

Note the `--property` syntax: pairs are separated by `:` and a literal `:` inside a value must be
escaped as `\:`. This is the usual reason a first attempt fails.

### Admin console

1. Open <http://localhost:4848>.
2. **Resources → JDBC → JDBC Connection Pools → New…**
3. Page 1 — Pool Name `petlee-pool`, Resource Type `javax.sql.DataSource`, Database Driver Vendor
   `PostgreSQL`. **Next**.
4. Page 2 — Datasource Classname `org.postgresql.ds.PGSimpleDataSource`.
5. Same page, **Pool Settings**: Initial and Minimum Pool Size `2`, Maximum Pool Size `10`.
6. Same page, **Connection Validation**: tick *Required*, Validation Method `table`, Table Name
   `pg_catalog.pg_class`, and tick *On Any Failure → Close All Connections*.
7. Same page, **Additional Properties** — delete the generated defaults you do not need and set
   exactly these four:

   | Name | Value |
   |---|---|
   | `serverName` | `localhost` |
   | `portNumber` | `5432` |
   | `databaseName` | `petlee` |
   | `user` | `petlee_app` |
   | `password` | `${ALIAS=petlee-db-password}` |

8. **Finish**.

## Step 3 — create the JDBC resource

The pool holds connections; the resource is the JNDI name `persistence.xml` looks up.

```bash
asadmin create-jdbc-resource --connectionpoolid petlee-pool jdbc/petlee
```

Console: **Resources → JDBC → JDBC Resources → New…**, JNDI Name `jdbc/petlee`, Pool Name
`petlee-pool`, **OK**.

## Step 4 — verify

```bash
asadmin ping-connection-pool petlee-pool
# Command ping-connection-pool executed successfully.
```

Console: **JDBC Connection Pools → petlee-pool → Ping** (top right).

A successful ping proves the driver is installed, the credentials are right and the `petlee`
database is reachable. It is the check to run *before* deploying, because a deployment failure
caused by the data source is much harder to read than this one line.

Then deploy and confirm the persistence unit starts:

```bash
asadmin deploy --force=true target/pet-lee.war
asadmin list-domains          # domain1 running
```

The server log should show `petlee-pu` starting with no error.

### Negative check

Make the database unreachable and redeploy. Deployment must fail, and the log must not contain the
password — that is what Step 1's alias buys you.

```bash
sudo systemctl stop postgresql          # Linux
# net stop postgresql-x64-18            # Windows, matching your installed major version
asadmin deploy --force=true target/pet-lee.war   # expected to fail
grep -ri "<the password>" "$PAYARA_HOME/glassfish/domains/domain1/logs/server.log"   # expect no match
sudo systemctl start postgresql
```

Without stopping the service, pointing the pool at a closed port produces the same failure and
touches nothing else:

```bash
asadmin set resources.jdbc-connection-pool.petlee-pool.property.portNumber=5433
asadmin ping-connection-pool petlee-pool          # fails
asadmin deploy --force=true target/pet-lee.war    # fails
asadmin set resources.jdbc-connection-pool.petlee-pool.property.portNumber=5432
```

**What the failure actually says.** Expect the JPA provider's exception, naming the host and port —
not the JNDI name:

```
Error occurred during deployment: Exception [EclipseLink-4002] ... DatabaseException
Internal Exception: java.sql.SQLException: Error in allocating a connection. Cause: Connection
could not be allocated because: Connection to localhost:5433 refused.
```

`jdbc/petlee` does not appear in it, so search the log for the host and port rather than the
resource name. The password does not appear anywhere in the log, and `domain.xml` stores
`${ALIAS=petlee-db-password}` rather than the value — both verified on Payara 6.2025.11.

## Removing it again

```bash
asadmin delete-jdbc-resource jdbc/petlee
asadmin delete-jdbc-connection-pool petlee-pool
asadmin delete-password-alias petlee-db-password
```

## WildFly 31

The same WAR deploys unchanged; only this resource is redefined, which is the portability claim
`persistence.xml` makes by omitting `<provider>`. **Verified on WildFly 31.0.1.Final** — see
"What differed" below.

Add the driver as a module first. This runs offline, before the server starts:

```bash
jboss-cli.sh --command="module add --name=org.postgresql \
  --resources=/path/to/postgresql-42.6.0.jar \
  --dependencies=jakarta.transaction.api,java.sql"
```

Then start the server and register the driver and the data source:

```bash
jboss-cli.sh --connect --command="/subsystem=datasources/jdbc-driver=postgresql:add(\
  driver-name=postgresql, driver-module-name=org.postgresql, \
  driver-class-name=org.postgresql.Driver)"

jboss-cli.sh --connect --command="data-source add \
  --name=petlee-pool \
  --jndi-name=java:/jdbc/petlee \
  --driver-name=postgresql \
  --connection-url=jdbc:postgresql://localhost:5432/petlee \
  --user-name=petlee_app --password=<password> \
  --min-pool-size=2 --max-pool-size=10 \
  --validate-on-match=true \
  --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker"

jboss-cli.sh --connect --command="/subsystem=datasources/data-source=petlee-pool:test-connection-in-pool"
jboss-cli.sh --connect --command="deploy --force target/pet-lee.war"
```

WildFly resolves the unqualified name `jdbc/petlee` in `persistence.xml` against `java:/`, so no
application change is needed — confirmed: the log shows `WFLYJCA0001: Bound data source
[java:/jdbc/petlee]` and then `WFLYJPA0002: Read persistence.xml for petlee-pu` with no
configuration change of any kind. Use a vault expression rather than a literal password for
anything beyond a local machine.

Two mechanics if you are following this on Windows or beside a running Payara:

- `jboss-cli.bat` ends with `pause`, so a scripted run appears to hang after each command. Feed it
  a command file and close stdin: `jboss-cli.bat --connect --file=setup.cli < NUL`.
- To run WildFly alongside Payara, offset its ports:
  `standalone.bat -Djboss.socket.binding.port-offset=100` puts HTTP on 8180 and management on
  10090, and `--controller=remote+http://localhost:10090` reaches it.

### What differed

The same WAR ran identically on both servers, but the JPA provider underneath is not the same one —
Payara supplies **EclipseLink 4.0.7**, WildFly supplies **Hibernate ORM 6.4.4**. Two differences
showed up under the same test, and application code must not depend on either:

| | EclipseLink (Payara) | Hibernate (WildFly) |
|---|---|---|
| `@Version` after the first insert | `1` | `0` |
| Duplicate key surfaces as | `PersistenceException` | `ConstraintViolationException` |

Nothing in the codebase assumes a starting version number or a specific wrapper exception, which is
why both passed. T-15 and T-38 should keep it that way: compare versions for *change*, never against
a literal, and match on `jakarta.persistence.OptimisticLockException` — which both providers do
throw as itself.

## Related

- `src/main/resources/META-INF/persistence.xml` — the unit that consumes this resource (T-02)
- `docs/tasks/TASK-03-database-schema-and-seed.md` — creates the `petlee` database and its schema
- `docs/decisions/ADR-003-technology-constraint.md` — why pooling is a server service, not a library
- T-42 folds this page into the full deployment runbook
