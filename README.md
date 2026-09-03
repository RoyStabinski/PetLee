# Pet-Lee

A pet adoption and rehoming platform: guests browse a public gallery of listings, registered users
post pets for adoption and contact other owners, and an administrator moderates the catalogue.
One WAR, three tiers, one PostgreSQL database.

## Stack

- **Presentation:** Jakarta Server Faces (JSF) / Facelets
- **Business logic:** Jakarta RESTful Web Services at `/api`, plus CDI services the JSF tier calls
  directly
- **Persistence:** Jakarta Persistence (JPA)
- **Database:** PostgreSQL 18
- **Runtime:** a Jakarta EE 10 application server — Payara 6 is the reference target; GlassFish 7
  and WildFly 31 are drop-in alternatives

`pom.xml` declares two dependencies, both `provided`: the Jakarta EE 10 platform API and the
PostgreSQL JDBC driver. The packaged WAR's `WEB-INF/lib` is empty — JSF, JPA, Jakarta REST, CDI,
JTA and JSON-B are all supplied by the server.

## Building

Requires Java 17 and Maven.

```bash
mvn clean package
```

Produces `target/pet-lee.war`.

## Database setup

Create an empty `petlee` database, then apply the two scripts in order:

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

Both are idempotent. `src/main/resources/db/README.md` covers the schema in more detail, including
the least-privileged `petlee_app` role the server's connection pool should authenticate as.

## Deploying

1. Create a JDBC connection pool and resource named `jdbc/petlee` on the server, pointing at the
   `petlee` database. The application never opens its own JDBC connection and names no driver
   class; it looks up `jdbc/petlee` by name.

   On Payara or GlassFish:

   ```bash
   asadmin create-jdbc-connection-pool --datasourceclassname org.postgresql.ds.PGSimpleDataSource \
       --restype javax.sql.DataSource \
       --property user=petlee_app:password=<chosen>:serverName=localhost:portNumber=5432:databaseName=petlee \
       petlee-pool
   asadmin create-jdbc-resource --connectionpoolid petlee-pool jdbc/petlee
   asadmin ping-connection-pool petlee-pool
   ```

   On WildFly the equivalent is a `<datasource jndi-name="java:/jdbc/petlee">` entry in
   `standalone.xml`, with the PostgreSQL driver installed as a module.

2. Deploy `target/pet-lee.war`. The context path is `/pet-lee`.
3. Open `http://localhost:8080/pet-lee/`.

### Uploaded photographs

Photographs are written outside the deployment, so a redeploy cannot wipe them. The directory is
chosen in this order: the `petlee.upload.dir` system property, the `PETLEE_UPLOAD_DIR` environment
variable, then `~/petlee-uploads`. The server's user needs write permission on it; it is created
on the first upload if it does not exist.

```bash
asadmin create-jvm-options "-Dpetlee.upload.dir=/var/lib/petlee/uploads"
```

## Accounts

| Username | Password | Role | Source |
|---|---|---|---|
| `admin` | `Admin123!` | ADMIN | `seed.sql` |

Register through `/register.xhtml` to try the flow as a first-time user.

## Architecture

The JSF managed beans in `com.petlee.web` inject the CDI services in `com.petlee.service` directly
rather than calling the REST API over HTTP. The REST API at `/api/*` (`com.petlee.rest`) is a
complete, independently usable surface, exercisable the way any non-browser client would:

```bash
curl -b cookies.txt http://localhost:8080/pet-lee/api/pets
```

### Endpoints

All bodies are JSON. "auth" means a valid session cookie, created by `POST /api/auth/login`.

| Method | Path | Access |
|---|---|---|
| POST | `/api/users/register` | open |
| POST | `/api/auth/login` | open |
| POST | `/api/auth/logout` | auth |
| GET | `/api/categories` | open |
| POST | `/api/categories` | admin |
| DELETE | `/api/categories/{id}` | admin |
| GET | `/api/pets` | open — filters: `categoryId`, `size`, `gender` |
| GET | `/api/pets/{id}` | open — owner contact fields only when logged in |
| GET | `/api/pets/mine` | auth |
| POST | `/api/pets` | auth |
| PUT | `/api/pets/{id}` | owner only |
| DELETE | `/api/pets/{id}` | owner or admin |
| GET | `/api/admin/pets` | admin — every status |
| PUT | `/api/admin/pets/{id}/status` | admin — `?status=REMOVED\|AVAILABLE` |

Enum strings are exact: size `SMALL\|MEDIUM\|LARGE`, gender `MALE\|FEMALE`, status
`AVAILABLE\|ADOPTED\|REMOVED`, role `USER\|ADMIN`. Errors come back as
`{"code": "...", "message": "..."}`.

Both tiers share one `HttpSession`, so a browser login also authenticates that session's `/api`
calls.

Authorisation is decided in the service layer, from a caller id passed in as an argument — no
service reads a session. `@Secured` and `@AdminOnly` guard the REST endpoints; `PageAccessFilter`
keeps guests off the pages that are not for them, which is convenience rather than enforcement.

## Known limitation

Photo upload is available through the web UI only, not the REST API. Jersey cannot inject a Servlet
`Part` as a `@FormParam`, and both ways around that would add a runtime dependency, so
`POST /api/pets/{id}/image` does not exist. Uploading works through `addPet.xhtml`'s
`<h:inputFile>`, posted to the Faces servlet, capped at 5 MB by the
`jakarta.faces.UPLOADER_MAX_FILE_SIZE` context-param in `web.xml`.
