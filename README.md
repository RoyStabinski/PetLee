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
Follow them in order. A new installation needs only `schema.sql` and `seed.sql`; the
`migrate-00N` scripts are only for a database created by an earlier version (see
[Upgrading an existing database](#upgrading-an-existing-database)).

### 1. Requirements

- **JDK** 17 or later (tested with 21)
- **Maven** 3.x (or IntelliJ IDEA's bundled Maven)
- **PostgreSQL** 17 or later
- **WildFly** 27 or later (tested with 41.0.0.Final) — any Jakarta EE 10 server works, see
  [Alternative servers](#alternative-servers)
- The **PostgreSQL JDBC driver** jar, `postgresql-42.7.x.jar`, from
  <https://jdbc.postgresql.org/download/> or Maven Central

### 2. PostgreSQL tools on the PATH

The PostgreSQL installer does not put `psql` and `createdb` on the PATH. Add its `bin` folder for
the current terminal session, in the terminal you will run the next two steps from:

```powershell
# PowerShell
$env:Path += ";C:\Program Files\PostgreSQL\17\bin"
```

```bat
:: cmd
set PATH=%PATH%;C:\Program Files\PostgreSQL\17\bin
```

The version number in the path must match the installed version (`C:\Program Files\PostgreSQL\`
has one folder per installed version). The setting lasts until the terminal is closed.

### 3. Database

Create an empty `petlee` database, then apply the two scripts in order, from the project root:

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

Both are idempotent. `src/main/resources/db/README.md` covers the schema in more detail.

### 4. The application's database role

The server's connection pool authenticates as a least-privileged `petlee_app` role, not as
`postgres`. Create it and grant it access, replacing `<chosen>` with a password of your choice:

```bash
psql -U postgres -d petlee -c "CREATE ROLE petlee_app LOGIN PASSWORD '<chosen>';"
psql -U postgres -d petlee -c "GRANT CONNECT ON DATABASE petlee TO petlee_app; GRANT USAGE ON SCHEMA public TO petlee_app; GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO petlee_app; GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO petlee_app;"
```

The `GRANT`s must run while connected to `petlee` (`-d petlee`), not to the default `postgres`
database: `ON ALL TABLES IN SCHEMA public` means the tables of the database you are connected to,
so run anywhere else they grant nothing on Pet-Lee's tables, and every request then fails with
`permission denied`. For the same reason they must run after `schema.sql`, once the tables exist.
A role belongs to the whole server, so if `petlee_app` already exists the first command fails
with `role "petlee_app" already exists`; keep its existing password and run the second command
anyway.

The role has no DDL rights, so the application cannot alter the schema even by accident. Keep
the password: the DataSource command in the next step needs it.

### 5. WildFly: JDBC driver and DataSource

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

Then raise Undertow's request-size limit, so the add-pet form can send five full-size
photographs in one request (see [Uploaded photographs](#uploaded-photographs) for why), and
reload the server:

```
/subsystem=undertow/server=default-server/http-listener=default:write-attribute(name=max-post-size,value=27262976)
reload
```

`--min-pool-size=8 --max-pool-size=32` is the connection pool: WildFly keeps at least 8 open
connections to PostgreSQL ready and never opens more than 32, and every request borrows one from
the pool instead of opening its own connection.

These settings are saved in `standalone/configuration/standalone.xml`, so this step is done once;
they survive server restarts and redeploys.

### 6. Build and deploy

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

### 7. Redeploying after a code change

Package again (`mvn clean package`), then run the same command:

```
deploy <path>/target/pet-lee.war --force
```

`--force` replaces the deployed version in place. The DataSource from step 5 does not need to be
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

Two limits apply to uploads. `ImageStore` refuses any single photograph over 5 MB
with a 400. Undertow, WildFly's web server, refuses a whole request body
over its `max-post-size`, 10 MB unless configured, before the application sees it. The add-pet
form sends all its photographs in one request, up to five of up to 5 MB each, so step 5 raises
`max-post-size` to 26 MB (27262976 bytes). Nothing in `web.xml` limits uploads: Mojarra ignores
the `jakarta.faces.UPLOADER_*` parameters.

The API itself receives one photograph per request, so it is not affected.

### Troubleshooting

- **`Required services that are not installed: jboss.naming.context.java.jdbc.petlee`** on deploy
  means the DataSource does not exist. Run step 5, check that
  `test-connection-in-pool` succeeds, then deploy again.
- **Deploy with the CLI rather than copying the WAR into `standalone/deployments`.** The deployment
  scanner leaves marker files there, and a stale `pet-lee.war.failed` from an earlier attempt
  hides the real deployment state. `deploy ... --force` reports success or the actual error
  directly. If you did use the folder, delete any `pet-lee.war.*` marker files it contains.

## Upgrading an existing database

A fresh install needs no migration: `schema.sql` already has every table and column. A database
created by an earlier version of `schema.sql` needs the migrations it has not had yet, in number
order, as described in `src/main/resources/db/README.md#migrations`:

- `migrate-001-pet-image.sql` moves each pet's single `pet.image_url` into the `pet_image` table
  and drops the column. Back up first and stop the application while it runs.
- `migrate-002-user-version.sql` adds `users.version`, the optimistic lock behind the
  edit-profile form. It only adds a column with a default and needs no new grant. A build with
  edit profile fails on every query that loads a user until it has been run.

```bash
psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-001-pet-image.sql
psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-002-user-version.sql
```

`psql` must be on the PATH, as in [step 2](#2-postgresql-tools-on-the-path).

## Running the tests

From the project root:

```bash
mvn test
```

(add `-q` for quiet output, or run **test** from IntelliJ's Maven Lifecycle). The JUnit tests need
no database, server or network, and cover:

- `PasswordHasherTest`: hashing and verifying, wrong passwords, a fresh random salt per hash, the
  `pbkdf2_sha256$` format, and null, empty or malformed stored values failing without an exception.
- `PhoneNumbersTest`: valid mobile, landline and 07X numbers in many spellings (spaces, dashes,
  parentheses, `+972`, `972`) and their stored form; too short, too long, wrong-prefix and
  lettered numbers refused.
- `TokenStoreTest`: issuing, resolving and revoking tokens, `revokeAllExcept`, the 30-minute idle
  expiry and its sliding renewal (on a test clock, so nothing waits), and 32 threads sharing one
  store without losing or reviving a token.
- `PetRepositoryTest`: the search text's LIKE pattern, lower-cased with `\`, `%` and `_` escaped.

`mvn package` runs the same tests before it builds the WAR, and builds nothing if one fails.

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
beans call `com.petlee.web.client` (`AuthApi`, `PetApi`, `CategoryApi`
and `AdminApi` over one shared `ApiClient`), exchange the records in `com.petlee.dto`, and authenticate with the bearer
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
| POST | `/api/users/register` | open — 201 Created with `Location: /api/users/me` (where the new member reads the account once logged in) and the created user as the body |
| GET | `/api/users/me` | auth — the caller's own account, with its `version` |
| PUT | `/api/users/me?version=N` | auth — body `{fullName, email, phone, region}`; the username cannot change. `N` is the `version` from `GET /api/users/me`; a stale or missing one is 409 (`code` `STALE_VERSION`), and so is an email address another account has |
| PUT | `/api/users/me/password` | auth — body `{currentPassword, newPassword}`; a wrong current password is 400, a new one under 8 characters is 400. Revokes every other bearer token of the caller and keeps the one sent; 204 |
| POST | `/api/auth/login` | open |
| POST | `/api/auth/logout` | auth — 204 |
| GET | `/api/categories` | open |
| POST | `/api/categories` | admin — body `{name}`; 201 Created with `Location: /api/categories/{id}` and the created category as the body |
| DELETE | `/api/categories/{id}` | admin — 204; 409 while listings still use it |
| GET | `/api/pets` | open — filters: `categoryId`, `size`, `gender`, `minAge`, `maxAge`, `q` |
| GET | `/api/pets/{id}` | open — owner contact fields (`ownerName`, `ownerPhone`, `ownerEmail`) only when logged in; `ownedByCaller` is true only for the owner; `images` lists every photograph |
| GET | `/api/pets/mine` | auth |
| POST | `/api/pets` | auth — 201 Created with `Location: /api/pets/{id}` and the created listing as the body |
| PUT | `/api/pets/{id}?version=N` | owner only — `N` is the `version` from `GET /api/pets/{id}`; a stale or missing one is 409 |
| DELETE | `/api/pets/{id}` | owner or admin — 204 |
| PUT | `/api/pets/{id}/status?status=ADOPTED\|AVAILABLE&version=N` | owner only — marks a listing adopted or available again; `N` is the `version` from the pet as last read |
| POST | `/api/pets/{id}/images` | owner only — multipart/form-data, one part named `file`; adds one photograph (at most 5); 201 Created with `Location: /api/pets/{id}/images/{imageId}` and `{id, url, main}` as the body |
| DELETE | `/api/pets/{id}/images/{imageId}` | owner only — deletes the photograph and its file; 204 |
| PUT | `/api/pets/{id}/images/{imageId}/main` | owner only — makes it the main photograph; 204 |
| GET | `/api/admin/pets` | admin — every status, with `ownerName` and `createdAt`; filters: `categoryId`, `size`, `gender` |
| PUT | `/api/admin/pets/{id}/status` | admin — `?status=REMOVED\|AVAILABLE` |
| GET | `/api/admin/category-counts` | admin — listings per category id, e.g. `{"1": 4}`; unused categories are absent |

Enum strings are exact: size `SMALL\|MEDIUM\|LARGE`, gender `MALE\|FEMALE`, status
`AVAILABLE\|ADOPTED\|REMOVED`, role `USER\|ADMIN`. Errors come back as
`{"code": "...", "message": "..."}`.

The endpoints that create something (`POST /api/users/register`, `POST /api/pets`,
`POST /api/pets/{id}/images` and `POST /api/categories`) answer 201 Created, with a `Location`
header naming the new resource and the same body as before. Every other success is 200 with a
body or 204 without one.

`minAge` and `maxAge` are whole numbers from 0 to 50, and either may be given alone; a listing
whose age is unknown is left out once either is given. A value outside that range, or `minAge`
greater than `maxAge`, is a 400 naming the problem.

`q` is free-text search: a pet matches when its name or its breed contains the text, ignoring
case. It is trimmed and ignored when blank, and more than 50 characters is a 400. `%` and `_`
match only themselves, and the value is bound as a query parameter, never pasted into the query:

```bash
curl "http://localhost:8080/pet-lee/api/pets?q=shep&size=LARGE"
```

`PUT /api/pets/{id}/status` accepts only AVAILABLE to ADOPTED and back. It is a 409 when an
administrator has withdrawn the listing (only an administrator can restore it), when the
listing already has that status, or when `version` is missing or stale. Every pet in a list
carries its `version`, so the owner's dashboard can send it back:

```bash
curl -X PUT -H "Authorization: Bearer <token>" \
     "http://localhost:8080/pet-lee/api/pets/7/status?status=ADOPTED&version=3"
```

A listing has up to five photographs, and once it has any, exactly one of them is main. The
main one is the `imageUrl` of every pet the API returns, and the one the gallery, the owner's
listings and the admin table show. `GET /api/pets/{id}` lists all of them in `images`, oldest
first and with their ids, for the details and edit pages. `imageUrl` is null, and `images` empty,
for a pet with no photographs.

`POST /api/pets/{id}/images` adds one photograph per request. The part must be a JPEG, PNG, GIF
or WebP image of at most 5 MB, sent with its own `Content-Type`; anything else is a 400, and a
sixth photograph is a 409. A listing's first photograph becomes its main one:

```bash
curl -H "Authorization: Bearer <token>" \
     -F "file=@rex.jpg;type=image/jpeg" \
     http://localhost:8080/pet-lee/api/pets/7/images
```

Every `/api/users/me` endpoint acts for whoever the token or session belongs to; none takes a
user id, so a member can neither read nor change another's account. A user carries a `version`,
like a pet: send back the one `GET /api/users/me` returned, and a save based on an older read is
a 409 whose `code` is `STALE_VERSION`. A taken email address is also a 409, with the ordinary
`ERROR` code. A password change does not move the `version`, so a profile form open at the same
time can still be saved, and it signs the member out everywhere else: every other bearer token
of theirs is revoked, and the token the request carried stays valid.

```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/pet-lee/api/users/me
curl -X PUT -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
     -d '{"fullName":"Dana Levi","email":"dana@example.com","phone":"050-1234567","region":"Haifa"}' \
     "http://localhost:8080/pet-lee/api/users/me?version=0"
```

**Telephone numbers** must be Israeli, at registration and on every profile save. Accepted:
mobile numbers, 05X and seven digits; landlines 02, 03, 04, 08 and 09 and seven digits; and the
07X numbers, 07X and seven digits. Spaces, dashes and parentheses are ignored, and the leading 0
may be written `+972` or `972`. The number is stored normalised, `0XX-XXXXXXX` for mobile and
07X numbers and `0X-XXXXXXX` for the other landlines, so `+972 (54) 123-4567` is stored as
`054-1234567`. The field stays optional; a non-empty number that does not fit is a 400, "Enter a
valid Israeli phone number, for example 050-1234567". The rule lives in
`com.petlee.util.PhoneNumbers`; the register and edit-profile pages carry a matching regular
expression for a quicker answer, and the server still decides.

A browser login through the JSF pages authenticates the pages, not the browser's own `/api`
calls: the server-side client logs in with a bearer token, and the browser session never holds
the REST tier's session user. To call `/api` from a browser, log in through
`POST /api/auth/login`.

Authorisation is decided in the service layer, from a caller id passed in as an argument — no
service reads a session. `@Secured` and `@AdminOnly` guard the REST endpoints; `PageAccessFilter`
keeps guests off the pages that are not for them, which is convenience rather than enforcement.
