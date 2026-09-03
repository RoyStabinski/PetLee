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

The driver is a `runtime` dependency in `pom.xml` for the test classpath, but the *server* needs its
own copy to open connections on the application's behalf.

```bash
# Payara 6 / GlassFish 7 — then restart the domain
cp postgresql-42.6.0.jar "$PAYARA_HOME/glassfish/domains/domain1/lib/ext/"
asadmin restart-domain
```

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

Stop PostgreSQL and redeploy. Deployment must fail with a message naming `jdbc/petlee`, and the log
must not contain the password — that is what Step 1's alias buys you.

```bash
sudo systemctl stop postgresql       # Linux
# net stop postgresql-x64-14         # Windows
asadmin deploy --force=true target/pet-lee.war   # expected to fail
grep -ri "<the password>" "$PAYARA_HOME/glassfish/domains/domain1/logs/server.log"   # expect no match
sudo systemctl start postgresql
```

## Removing it again

```bash
asadmin delete-jdbc-resource jdbc/petlee
asadmin delete-jdbc-connection-pool petlee-pool
asadmin delete-password-alias petlee-db-password
```

## WildFly 31

The same WAR deploys unchanged; only this resource is redefined, which is the portability claim
`persistence.xml` makes by omitting `<provider>`. Add the driver as a module, then:

```bash
jboss-cli.sh --connect --command="data-source add \
  --name=petlee-pool \
  --jndi-name=java:/jdbc/petlee \
  --driver-name=postgresql \
  --connection-url=jdbc:postgresql://localhost:5432/petlee \
  --user-name=petlee_app --password=<password> \
  --min-pool-size=2 --max-pool-size=10 \
  --validate-on-match=true \
  --valid-connection-checker-class-name=org.jboss.jca.adapters.jdbc.extensions.postgres.PostgreSQLValidConnectionChecker"
```

WildFly resolves the unqualified name `jdbc/petlee` in `persistence.xml` against `java:/`, so no
application change is needed. Use a vault expression rather than a literal password for anything
beyond a local machine.

## Related

- `src/main/resources/META-INF/persistence.xml` — the unit that consumes this resource (T-02)
- `docs/tasks/TASK-03-database-schema-and-seed.md` — creates the `petlee` database and its schema
- `docs/decisions/ADR-003-technology-constraint.md` — why pooling is a server service, not a library
- T-42 folds this page into the full deployment runbook
