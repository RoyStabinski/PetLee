# Pet-Lee

A pet adoption and rehoming platform: guests browse a public gallery of listings, registered users
post pets for adoption and contact other owners, and an administrator moderates the catalogue.
One WAR, three tiers, one PostgreSQL database.

## Stack

- **Presentation:** Jakarta Server Faces (JSF) / Facelets
- **Business logic:** Jakarta RESTful Web Services at `/api`, plus CDI services the JSF tier calls
  directly
- **Persistence:** Jakarta Persistence (JPA)
- **Database:** PostgreSQL 17+
- **Runtime:** a Jakarta EE 10 application server — WildFly 41 is the tested target; Payara 6 and
  GlassFish 7 are drop-in alternatives

`pom.xml` declares two dependencies, both `provided`: the Jakarta EE 10 platform API and the
PostgreSQL JDBC driver. The packaged WAR's `WEB-INF/lib` is empty — JSF, JPA, Jakarta REST, CDI,
JTA and JSON-B are all supplied by the server.

## Setup

The steps below are the tested path: WildFly 41.0.0.Final, PostgreSQL 17 and JDK 21 on Windows.
Follow them in order.

### 1. Requirements

- **JDK** 17 or later (tested with 21)
- **Maven** 3.x (or IntelliJ IDEA's bundled Maven)
- **PostgreSQL** 17 or later
- **WildFly** 27 or later (tested with 41.0.0.Final) — any Jakarta EE 10 server works, see
  [Alternative servers](#alternative-servers)
- The **PostgreSQL JDBC driver** jar, `postgresql-42.7.x.jar`, from
  <https://jdbc.postgresql.org/download/> or Maven Central

### 2. Database

Create an empty `petlee` database, then apply the two scripts in order:

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

Both are idempotent. `src/main/resources/db/README.md` covers the schema in more detail, including
how to create the least-privileged `petlee_app` role the server's connection pool authenticates as.
Create that role before the next step.

**Upgrading an existing database** instead of creating one: apply the migrations it has not had
yet, in number order, as described in `src/main/resources/db/README.md#migrations`. The latest,
`migrate-002-favorite.sql`, adds the `favorite` table behind the favourites feature; a build
with that feature fails on the favourites pages and hearts until it has been run:

```bash
psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-002-favorite.sql
```

### 3. WildFly: JDBC driver and DataSource

The application never opens its own JDBC connection and names no driver class; it looks up the
`jdbc/petlee` DataSource by name. That DataSource is created once on the server.

Start WildFly (`bin\standalone.bat`), then, with the server running, open the management CLI from
another terminal:

```bat
bin\jboss-cli.bat --connect
```

and run these four commands, replacing `<path>` with the folder holding the driver jar and
`<chosen>` with the `petlee_app` password:

```
module add --name=org.postgresql --resources=<path>/postgresql-42.7.x.jar --dependencies=javax.api,jakarta.transaction.api
/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql,driver-class-name=org.postgresql.Driver)
data-source add --name=PetleeDS --jndi-name=java:/jdbc/petlee --driver-name=postgresql --connection-url=jdbc:postgresql://localhost:5432/petlee --user-name=petlee_app --password=<chosen> --min-pool-size=8 --max-pool-size=32
/subsystem=datasources/data-source=PetleeDS:test-connection-in-pool
```

The last command should answer `"outcome" => "success"`.

`--min-pool-size=8 --max-pool-size=32` is the connection pool: WildFly keeps at least 8 open
connections to PostgreSQL ready and never opens more than 32, and every request borrows one from
the pool instead of opening its own connection.

These settings are saved in `standalone/configuration/standalone.xml`, so this step is done once;
they survive server restarts and redeploys.

### 4. Build and deploy

Build the WAR from the project root:

```bash
mvn clean package
```

(or, in IntelliJ IDEA, run **clean** and then **package** from the Maven tool window's Lifecycle).
This produces `target/pet-lee.war`.

Deploy it from the same `jboss-cli.bat --connect` session, replacing `<path>` with the project
folder:

```
deploy <path>/target/pet-lee.war --force
```

Open <http://localhost:8080/pet-lee/> and log in as `admin` / `Admin123!`.

### 5. Redeploying after a code change

Package again (`mvn clean package`), then run the same command:

```
deploy <path>/target/pet-lee.war --force
```

`--force` replaces the deployed version in place. The DataSource from step 3 does not need to be
recreated.

### Uploaded photographs

Photographs are written outside the deployment, so a redeploy cannot wipe them. The directory is
chosen in this order: the `petlee.upload.dir` system property, the `PETLEE_UPLOAD_DIR` environment
variable, then `~/petlee-uploads`. The server's user needs write permission on it; it is created
on the first upload if it does not exist. The default needs no configuration; to choose another
directory on WildFly, run in the CLI:

```
/system-property=petlee.upload.dir:add(value=C:/petlee/uploads)
```

The add-pet form sends all its photographs in one request, up to five of up to 5 MB each.
WildFly refuses a request body over its `max-post-size`, 10 MB unless configured, before the
application sees it. To allow five full-size photographs at once, raise it in the CLI and reload:

```
/subsystem=undertow/server=default-server/http-listener=default:write-attribute(name=max-post-size,value=27262976)
reload
```

The API itself receives one photograph per request, so it is not affected.

### Troubleshooting

- **`Required services that are not installed: jboss.naming.context.java.jdbc.petlee`** on deploy
  means the DataSource does not exist. Run step 3, check that
  `test-connection-in-pool` succeeds, then deploy again.
- **Deploy with the CLI rather than copying the WAR into `standalone/deployments`.** The deployment
  scanner leaves marker files there, and a stale `pet-lee.war.failed` from an earlier attempt
  hides the real deployment state. `deploy ... --force` reports success or the actual error
  directly. If you did use the folder, delete any `pet-lee.war.*` marker files it contains.

## Alternative servers

Any Jakarta EE 10 server works, as long as it provides a DataSource named `jdbc/petlee`. On Payara 6
or GlassFish 7:

```bash
asadmin create-jdbc-connection-pool --datasourceclassname org.postgresql.ds.PGSimpleDataSource     --restype javax.sql.DataSource     --property user=petlee_app:password=<chosen>:serverName=localhost:portNumber=5432:databaseName=petlee     petlee-pool
asadmin create-jdbc-resource --connectionpoolid petlee-pool jdbc/petlee
asadmin ping-connection-pool petlee-pool
asadmin deploy target/pet-lee.war
```

The PostgreSQL driver jar must be in the domain's `lib` folder. To set the upload directory:

```bash
asadmin create-jvm-options "-Dpetlee.upload.dir=/var/lib/petlee/uploads"
```

The context path is `/pet-lee`, as on WildFly.

## Accounts

| Username | Password | Role | Source |
|---|---|---|---|
| `admin` | `Admin123!` | ADMIN | `seed.sql` |

Register through `/register.xhtml` to try the flow as a first-time user.

## Architecture

The JSF tier (`com.petlee.web`) reaches the logic tier only through the REST API. Its managed
beans call `com.petlee.web.client` (`AuthApi`, `PetApi`, `CategoryApi` and `AdminApi` over one
shared `ApiClient`), exchange the records in `com.petlee.dto`, and authenticate with the bearer
token that login returns, kept in the session-scoped `ApiCredentials`; nothing in the JSF tier
touches a service, an entity or a repository. The same holds for photographs: the pages send
the files to `POST /api/pets/{id}/images` and show the URLs the API returns. This gives security
and business rules a single
enforcement point, the API that every client goes through, and leaves the two tiers free to be
split onto separate servers. The API's base URL is `http://localhost:<port><context path>/api`,
derived from the request being served; set the `petlee.api.url` system property to override it.

The REST API at `/api/*` (`com.petlee.rest`) is a complete, independently usable surface,
exercisable the way any non-browser client would:

```bash
curl -b cookies.txt http://localhost:8080/pet-lee/api/pets
curl -H "Authorization: Bearer <token>" http://localhost:8080/pet-lee/api/pets/mine
```

### Endpoints

All bodies are JSON. "auth" means a logged-in caller, identified by either credential that
`POST /api/auth/login` hands out:

- the session cookie it sets, or
- the `token` in its response body, sent as `Authorization: Bearer <token>` in place of the cookie.

Login answers `{"user": {...}, "token": "..."}`. A token expires after 30 idle minutes, like the
session. When a request carries a bearer header, that header alone decides: an unknown or
expired token is a 401 even if a valid cookie came along, on every endpoint, open ones
included, so a client learns that it has to log in again. A request without the header is
unaffected. `POST /api/auth/logout` revokes the bearer token if one was sent and invalidates the
session if there is one.

| Method | Path | Access |
|---|---|---|
| POST | `/api/users/register` | open |
| POST | `/api/auth/login` | open |
| POST | `/api/auth/logout` | auth |
| GET | `/api/categories` | open |
| POST | `/api/categories` | admin |
| DELETE | `/api/categories/{id}` | admin |
| GET | `/api/pets` | open — filters: `categoryId`, `size`, `gender`, `minAge`, `maxAge` |
| GET | `/api/pets/{id}` | open — owner contact fields only when logged in; `ownedByCaller` is true only for the owner; `images` lists every photograph |
| GET | `/api/pets/mine` | auth |
| POST | `/api/pets` | auth |
| PUT | `/api/pets/{id}?version=N` | owner only — `N` is the `version` from `GET /api/pets/{id}`; a stale or missing one is 409 |
| DELETE | `/api/pets/{id}` | owner or admin |
| PUT | `/api/pets/{id}/status?status=ADOPTED\|AVAILABLE&version=N` | owner only — marks a listing adopted or available again; `N` is the `version` from the pet as last read |
| POST | `/api/pets/{id}/images` | owner only — multipart/form-data, one part named `file`; adds one photograph (at most 5); returns `{id, url, main}` |
| DELETE | `/api/pets/{id}/images/{imageId}` | owner only — deletes the photograph and its file; 204 |
| PUT | `/api/pets/{id}/images/{imageId}/main` | owner only — makes it the main photograph; 204 |
| GET | `/api/admin/pets` | admin — every status, with `ownerName` and `createdAt`; filters: `categoryId`, `size`, `gender` |
| PUT | `/api/admin/pets/{id}/status` | admin — `?status=REMOVED\|AVAILABLE` |
| GET | `/api/admin/category-counts` | admin — listings per category id, e.g. `{"1": 4}`; unused categories are absent |
| GET | `/api/favorites` | auth — the caller's saved pets, newest saved first, each with its `status`; withdrawn (REMOVED) pets are left out, adopted ones kept |
| GET | `/api/favorites/ids` | auth — the ids of the caller's saved pets, e.g. `[7, 12]`, for marking hearts in one call |
| PUT | `/api/favorites/{petId}` | auth — saves the pet; 204. Idempotent: saving it again is also 204. 404 if the pet does not exist or is not AVAILABLE |
| DELETE | `/api/favorites/{petId}` | auth — forgets the pet; 204. Idempotent: 204 even if it was not saved |

Enum strings are exact: size `SMALL\|MEDIUM\|LARGE`, gender `MALE\|FEMALE`, status
`AVAILABLE\|ADOPTED\|REMOVED`, role `USER\|ADMIN`. Errors come back as
`{"code": "...", "message": "..."}`.

`minAge` and `maxAge` are whole numbers from 0 to 50, and either may be given alone; a listing
whose age is unknown is left out once either is given. A value outside that range, or `minAge`
greater than `maxAge`, is a 400 naming the problem.

`PUT /api/pets/{id}/status` accepts only AVAILABLE to ADOPTED and back. It is a 409 when an
administrator has withdrawn the listing (only an administrator can restore it), when the
listing already has that status, or when `version` is missing or stale. Every pet in a list
carries its `version`, so the owner's dashboard can send it back:

```bash
curl -X PUT -H "Authorization: Bearer <token>" \
     "http://localhost:8080/pet-lee/api/pets/7/status?status=ADOPTED&version=3"
```

A listing has up to five photographs, and once it has any, exactly one of them is main. The
main one is the `imageUrl` of every pet the API returns, and the one the owner's listings and
the admin table show. Every pet in a list also carries `imageUrls`, all of them with the main
one first, for the gallery card's carousel; `GET /api/pets/{id}` lists them in `images`, oldest
first, for the details page. `imageUrl` is null, and `imageUrls` empty, for a pet with no
photographs.

`POST /api/pets/{id}/images` adds one photograph per request. The part must be a JPEG, PNG, GIF
or WebP image of at most 5 MB, sent with its own `Content-Type`; anything else is a 400, and a
sixth photograph is a 409. A listing's first photograph becomes its main one:

```bash
curl -H "Authorization: Bearer <token>" \
     -F "file=@rex.jpg;type=image/jpeg" \
     http://localhost:8080/pet-lee/api/pets/7/images
```

A member's favourites are theirs alone: every `/api/favorites` call acts for whoever the token
or session belongs to, and no endpoint takes another user's id. They live in the `favorite`
table, one row per member and pet, keyed by the pair, so saving twice cannot create a duplicate
and two simultaneous saves both succeed. Deleting the member or the pet deletes the row with it.

```bash
curl -X PUT    -H "Authorization: Bearer <token>" http://localhost:8080/pet-lee/api/favorites/7
curl           -H "Authorization: Bearer <token>" http://localhost:8080/pet-lee/api/favorites/ids
curl -X DELETE -H "Authorization: Bearer <token>" http://localhost:8080/pet-lee/api/favorites/7
```

Deleting the main photograph makes the oldest remaining one main. An `imageId` that belongs to
another pet is a 404. Deleting a pet deletes all its photographs, files included. Changing a
listing's photographs does not change its `version`, so an edit form open at the same time can
still be saved.

A browser login through the JSF pages authenticates the pages, not the browser's own `/api`
calls: the server-side client logs in with a bearer token, and the browser session never holds
the REST tier's session user. To call `/api` from a browser, log in through
`POST /api/auth/login`.

Authorisation is decided in the service layer, from a caller id passed in as an argument — no
service reads a session. `@Secured` and `@AdminOnly` guard the REST endpoints; `PageAccessFilter`
keeps guests off the pages that are not for them, which is convenience rather than enforcement.
