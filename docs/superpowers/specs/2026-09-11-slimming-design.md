# Design — Slimming Pet-Lee to its assignment size

**Date:** 2026-09-11 · **Status:** Proposed · **Supersedes:** ADR-001 · **Amends:** ADR-002, ADR-003

## 1. Why

The assignment requires a working JSF + JPA + Jakarta REST application over PostgreSQL,
sized at **at least 2000–2500 lines of code**. The repository currently holds **11,849**
functional lines — roughly five times the system that was asked for.

The code is not bad. It is careful, well-commented, consistently styled enterprise code.
That is precisely the problem: every tier pays for abstraction the assignment never
needs, and the result is a system a reader cannot hold in their head. A grader opening
`AbstractRepository` finds JPA-metamodel reflection to decide persist-versus-merge across
four entity types. A grader opening `PetManagedBean` finds the JSF tier making an HTTP
call to its own REST API on localhost.

This design cuts the project to **≈2,865 lines** — comfortably clear of the 2,000-line
floor, and simple enough to explain end to end in a viva.

### The floor matters more than the ceiling

The requirement is a **minimum**. Undershooting it is the only outcome that fails.
Every cut below is therefore checked against the floor, and §9 makes "≥2,400 lines"
an explicit acceptance criterion alongside the functional ones. If cuts land us under
2,400, we stop cutting — not because bigger is better, but because the requirement says so.

## 2. Decisions taken

Agreed before this document was written:

| # | Decision | Consequence |
|---|---|---|
| D1 | The line count covers **everything** — Java, XHTML, CSS, SQL, XML, tests | Views and stylesheet are in scope, not just Java |
| D2 | **Drop the loopback REST call.** JSF beans inject services directly | Deletes `ApiClient`; REST tier remains a real, working API |
| D3 | **One photo per pet**, not a gallery | Drops the `pet_image` table and its entity, repository and resource |
| D4 | **A small unit-test suite**, no integration tests | ~350 lines of tests; restores ADR-003's three-dependency list |
| D5 | **Bean Validation replaces hand-written validation** | Platform API, no new dependency; ~65 lines of manual checks removed |

## 3. Target architecture

Three tiers, each genuinely using one of the three named technologies.

```
Browser
   │
   ├─ /*.xhtml ──────► JSF (Facelets + CDI beans) ─┐
   │                                               │
   └─ /api/* ────────► Jakarta REST resources ─────┤
                          (JSON, @Secured)         │
                                                   ▼
                                        Service layer (CDI, @Transactional)
                                                   │
                                                   ▼
                                        Repositories (JPA, @PersistenceContext)
                                                   │
                                                   ▼
                                             PostgreSQL
```

**Both entry points share one `HttpSession`.** They are the same WAR, so a browser that
logs in through `login.xhtml` is simultaneously authenticated against `/api/*`, and the
REST API can be exercised with `curl -b cookies.txt` exactly as it is from the UI. This
is what makes D2 safe: dropping the loopback removes a *call*, not a tier. The REST layer
is still the only way in from outside the application, still carries the authorisation
filters, and is still independently demonstrable.

### What each tier may import

- `com.petlee.web` (JSF) may import `service` and `model`. It must not import `repository`
  or `dto`.
- `com.petlee.rest` may import `service`, `model` and `dto`. It must not import `repository`.
- `com.petlee.service` may import `repository` and `model`. It must not import `dto`,
  `web` or `rest`.
- Entities never cross the REST boundary. DTO records only. (Retained from the old ADR-001.)

### Where the mapping happens, and why

**Services return entities. REST resources map them to records. JSF binds to entities.**

This is not the obvious arrangement, and it is chosen for one concrete reason: Jakarta
Expression Language resolves properties through `java.beans.Introspector`, which requires
JavaBean getters. A record's accessor is `name()`, not `getName()`, so `#{pet.name}` in a
Facelets view bound to a record is at best server-dependent. Payara 6 is not installed in
this environment, so that cannot be tested here — and a design whose correctness we cannot
check is not one to ship. Entities have real getters and work on every server.

The arrangement pays for itself twice over: the service layer stops knowing about DTOs at
all, and there is exactly one mapping site (the resource method) rather than two.

**The consequence is a rule, not a suggestion:** every repository query whose result reaches
a JSF view must `LEFT JOIN FETCH` the associations that view touches. Entities returned from
a `@Transactional` service method are detached, and a lazy association touched after that
throws `LazyInitializationException`. In practice this means `category` and `owner` on every
`Pet` query — which is what the existing code already does, and which §5.2 preserves.

### Package layout after the change

```
com.petlee
├── model/        User · Pet · Category                          (3 entities)
├── repository/   UserRepository · PetRepository · CategoryRepository
├── service/      UserService · PetService · CategoryService · ImageStore
├── dto/          PetDTO · PetDetailDTO · CategoryDTO · UserDTO
│                 PetForm · RegisterForm · LoginForm · ErrorDTO   (records)
├── rest/         RestApplication · AuthResource · UserResource
│                 PetResource · CategoryResource · AdminResource · ErrorMapper
│   └── security/ Secured · AdminOnly · SecurityFilter · SessionUser · CurrentUser
├── web/          UserBean · PetBean · PetDetailBean · PetFormBean · AdminBean
│                 ImageServlet · PageAccessFilter
└── util/         PasswordHasher
```

Gone entirely: `mapper/`, `web/client/`, `session/`, `config/`, `exception/` (collapsed to
one class in `service/`), `rest/mapper/` (collapsed to one `ErrorMapper`).

## 4. Line budget

| Area | Now | Target | How |
|---|---:|---:|---|
| `model` | 407 | 210 | 3 entities; drop `equals`/`hashCode`/`toString` triplets |
| `repository` | 368 | 130 | Drop `AbstractRepository` reflection and the `PetFilter` builder; JPQL instead of Criteria |
| `service` | 601 | 300 | Bean Validation; no logging; `ImageStore` replaces `ImageStorageService` |
| `dto` + `mapper` | 595 | 85 | Java 17 records with static factories |
| `exception` | 117 | 25 | One `AppException` carrying an HTTP status |
| `rest` + `rest/mapper` | 596 | 240 | 5 resources, one error mapper; `RestParams` folded in |
| `rest/security` | 229 | 90 | One filter, a `SessionUser` record, `CurrentUser` helpers |
| `web/bean` + `web/client` | 1,359 | 420 | 5 beans injecting services; `ApiClient` deleted |
| `web` servlet + filter | 206 | 105 | Simplified image serving and page guard |
| `config` + `session` + `utilities` | 299 | 80 | `PasswordHasher` kept; the rest deleted or inlined |
| **Java (main)** | **4,777** | **≈1,685** | |
| XHTML views | 842 | 480 | 9 views + template; shared field include kept |
| CSS | 491 | 170 | One stylesheet, no unused rules |
| `messages.properties` | 143 | 0 | Single-language project; text inlined in the views |
| SQL | 183 | 60 | `schema.sql` + `seed.sql`; demo data dropped |
| XML (`web.xml`, faces, beans, persistence) | 85 | 60 | |
| `pom.xml` | 138 | 60 | Three dependencies again |
| `.idea` | 27 | 0 | Gitignored |
| **Resources** | **1,909** | **≈830** | |
| Tests | 5,163 | 350 | See §8 |
| **Total** | **11,849** | **≈2,865** | |

## 5. Tier-by-tier changes

### 5.1 Model (407 → 210)

Delete `PetImage.java`. `Pet` gains `private String imageUrl` with the usual accessors and
loses its `List<PetImage> images` and the `@OneToMany` cascade.

Remove `equals`, `hashCode` and `toString` from all three entities. Nothing in the
application puts an entity in a `HashSet` or a `HashMap`; the implementations exist only to
be correct in a situation that never arises, and each carries a six-line comment justifying
a constant hash code.

Keep, unchanged and for stated reasons:

- `@Version` on `Pet` — the concurrency-control non-functional requirement.
- `created_at` as `updatable = false` — the gallery's ordering key (ADR-002 #4).
- `getSize()`/`setSize()` naming — JSON-B and EL derive the property name from the
  accessor (ADR-002 #2).
- `Long` rather than `long` identifiers (ADR-002 #3, #8).
- The `region` column (ADR-002 #1).

Bean Validation annotations move onto the entity fields alongside the JPA ones:
`@NotBlank @Size(max = 100)` on `petName`, `@Min(0) @Max(50)` on `age`, `@Email` on
`User.email`, and so on. These are the same limits `schema.sql` already states; the point
is that they are now stated **twice** (database and entity) instead of four times
(database, entity, service constants, service checks).

### 5.2 Repository (368 → 130)

Delete `AbstractRepository` and `PetFilter`. Each repository becomes a small
`@ApplicationScoped` class with its own `@PersistenceContext EntityManager` and only the
queries it actually needs.

`PetRepository.findPets` takes three nullable parameters instead of a builder object, and
builds a JPQL string:

```java
public List<Pet> find(Integer categoryId, Pet.PetSize size, Pet.PetGender gender,
                      Long ownerId, boolean availableOnly) {
    StringBuilder jpql = new StringBuilder(
            "SELECT DISTINCT p FROM Pet p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.owner WHERE 1=1");
    if (availableOnly)      jpql.append(" AND p.status = :status");
    if (ownerId != null)    jpql.append(" AND p.owner.userId = :ownerId");
    ...
}
```

The `LEFT JOIN FETCH` on `category` and `owner` stays — it is what keeps the gallery off an
N+1 query, and it is now simpler because there is no `images` collection to fetch. This
also sidesteps the EclipseLink stale-collection-cache trap recorded in the project notes:
with no collection to cache, a `Pet` cached with an empty `images` list cannot exist.

`save` becomes `em.persist` or `em.merge` chosen on `getId() == null` — a direct call on a
known entity type, not reflection over the metamodel.

### 5.3 Service (601 → 300)

Four classes. `UserService`, `PetService` and `CategoryService` keep their current
responsibilities and authorisation rules verbatim — those rules are the assignment's §5
business logic and are not negotiable:

- Only a registered user may post a listing.
- Only the poster or an administrator may remove one.
- Every pet belongs to a predefined category.
- Each listing has exactly one owner.
- Authorisation decisions stay **in the service layer**, taking a caller id as a parameter.
  A service never reads a session or a thread-local. (Retained from the old standing rules.)

**Every service method returns an entity or a list of entities, never a DTO** (see §3). This
is a change from the current code, where `PetService.findGallery` returns `List<PetDTO>`.
The DTO imports disappear from the whole tier.

What goes:

- All `Logger` fields and every `LOGGER.log(...)` call (~40 lines across the tier).
- `UserService.validateForRegistration` and its seven `*_MAX` constants — replaced by
  `@Valid` on the resource and bean methods.
- `PetService.applyForm`'s length and range checks — same replacement. The enum parsing
  stays, because `PetForm` carries `size` and `gender` as strings from JSON and HTML form
  fields, but collapses into a two-line helper without the `legalValues` parameter.
- Fine-grained error codes (`NAME_INVALID`, `BREED_TOO_LONG`, `SHORT_DESC_TOO_LONG`,
  `AGE_OUT_OF_RANGE`, …). Bean Validation produces the message; the mapper reports it.

`ImageStorageService` (203) becomes `ImageStore` (~50): validate the content type and size,
write the `Part` under a UUID filename, delete a stored file. No main-image flag, no
per-pet collection management, no partial unique index to maintain.

### 5.4 DTOs (595 → 85)

**These exist only at the REST boundary.** Nothing in `service` or `web` imports this
package; a resource method is the only place a record is constructed. See §3 for why.

Every DTO becomes a record with a static factory. `PetDTO` in full:

```java
public record PetDTO(Long id, String name, String shortDesc, Integer age, String size,
                     String gender, String status, String categoryName, String imageUrl) {
    public static PetDTO of(Pet p) {
        return new PetDTO(p.getPetId(), p.getPetName(), p.getShortDesc(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getImageUrl());
    }
}
```

That is 10 lines replacing 70 lines of `PetDTO` plus its share of `PetMapper`. The whole
`mapper` package disappears into these factories.

`PetDetailDTO.of(Pet, boolean callerIsAuthenticated)` keeps the **guest contact-gating
rule**: owner name, phone and email are populated only when the caller is authenticated.
This is a §6 requirement and gets one of the four surviving unit tests (§8).

The rule is enforced **twice, deliberately**, because the two tiers reach it by different
routes: `PetDetailDTO.of` gates the JSON, and `petDetails.xhtml` wraps the contact block in
`rendered="#{userBean.loggedIn}"`. These are not redundant — the REST gate is the security
boundary and the view gate is presentation. Removing the REST gate would leak contact
details to any unauthenticated `GET /api/pets/{id}`; removing the view gate would render an
empty contact panel to guests.

`AdminPetDTO`, `PetImageDTO` and `StatusForm` are deleted — the admin list uses `PetDTO`
plus an owner name, images are a single URL on `PetDTO`, and the status change takes a
query parameter rather than a one-field body.

JSON-B in Jakarta EE 10 (Yasson 3) serialises and deserialises records, so this needs no
configuration.

### 5.5 REST tier (825 → 330)

Five resources — `AuthResource`, `UserResource`, `PetResource`, `CategoryResource`,
`AdminResource` — plus `RestApplication`. `HealthResource` is deleted.

Endpoints, after the D3 image change:

| Method | Path | Auth | Note |
|---|---|---|---|
| `POST` | `/api/users/register` | — | |
| `POST` | `/api/auth/login` | — | |
| `POST` | `/api/auth/logout` | — | |
| `GET` | `/api/users/me` | `@Secured` | |
| `GET` | `/api/categories` | — | |
| `GET` | `/api/pets` | — | `categoryId`, `size`, `gender` query filters |
| `GET` | `/api/pets/{id}` | — | contact details gated on session |
| `GET` | `/api/pets/mine` | `@Secured` | |
| `POST` | `/api/pets` | `@Secured` | |
| `PUT` | `/api/pets/{id}` | `@Secured` | owner only |
| `DELETE` | `/api/pets/{id}` | `@Secured` | owner or admin |
| `POST` | `/api/pets/{id}/image` | `@Secured` | multipart, **replaces** `/images` |
| `GET` | `/api/admin/pets` | `@AdminOnly` | |
| `PUT` | `/api/admin/pets/{id}/status` | `@AdminOnly` | |
| `DELETE` | `/api/admin/pets/{id}` | `@AdminOnly` | |

Two contract deviations need ADR-002 rows: `POST /api/pets/{id}/images` becomes
`/image` and returns a `PetDTO` rather than a `PetImageDTO`; the admin status change moves
from a JSON body to a query parameter.

**Security** collapses from six files to five smaller ones. `SessionUser` becomes a record.
`CurrentUser` keeps `from`, `userIdOrNull`, `establish` and `terminate`, losing the
`SessionLifecycle.discardIsDeferred` branch — that existed only because a loopback call
could invalidate the session it was riding on, and D2 removes the loopback.
`AuthenticationFilter` and `AdminOnlyFilter` merge into one `SecurityFilter` that reads
`@AdminOnly` off the matched method via `@Context ResourceInfo`.

**The standing rule survives:** every `POST`/`PUT`/`DELETE` resource method carries
`@Secured` or `@AdminOnly`. No exceptions. The UI is never the security boundary.

**Error handling** collapses from three mappers plus `ErrorResponses` (183) to one
`ErrorMapper` (~40) handling `AppException`, `ConstraintViolationException` and everything
else. No stack trace reaches an HTTP response; no `printStackTrace()` anywhere.

### 5.6 JSF tier (1,565 → 525 Java, 842 → 480 views)

`ApiClient` (361) and `ApiException` (43) are deleted. Six beans become five, each
injecting services directly:

| Bean | Scope | Responsibility | Target |
|---|---|---|---:|
| `UserBean` | `@SessionScoped` | login, logout, register, current user | 85 |
| `PetBean` | `@ViewScoped` | gallery, filters, my listings | 95 |
| `PetDetailBean` | `@ViewScoped` | one pet, contact gating | 55 |
| `PetFormBean` | `@ViewScoped` | add / edit, `<h:inputFile>` upload | 95 |
| `AdminBean` | `@ViewScoped` | all listings, status change, delete | 90 |

`PetManagedBean`'s `bundle()` helper — which reaches into the resource bundle through
`evaluateExpressionGet(context, "#{msg['" + key + "']}")` to label a dropdown — goes with
`messages.properties`. Filter options become a plain `List<SelectItem>` with literal labels.

Views: nine pages (`index`, `login`, `register`, `petDetails`, `addPet`, `editPet`,
`profile`, `admin`, plus the four error pages consolidated to two), one template, one
shared `petFields.xhtml` include. All `#{msg.*}` expressions become literal English text.

`ImageServlet` keeps its job — serving uploaded files from outside the WAR — at about half
the size, since `StorageConfig`'s three-app-server domain probing collapses to:

```java
Path root = Path.of(Optional.ofNullable(System.getProperty("petlee.upload.dir"))
        .or(() -> Optional.ofNullable(System.getenv("PETLEE_UPLOAD_DIR")))
        .orElse(System.getProperty("user.home") + "/petlee-uploads"));
```

The path-traversal guard (`candidate.startsWith(root)`) is **kept** — it is a real security
control, not ceremony.

## 6. Database change

One migration, appended to `schema.sql` so the file stays re-runnable:

```sql
ALTER TABLE pet ADD COLUMN IF NOT EXISTS image_url VARCHAR(512);
DROP TABLE IF EXISTS pet_image;
```

Dropped with the table: `ux_pet_image_main` (the partial unique index enforcing one main
image per pet) and `idx_pet_status` (the gallery filters on status, but at assignment data
volumes the three remaining indexes are already more than the query planner needs).

Kept, with their reasons intact: `ux_users_email_lower` and `ux_category_name_lower` — the
case-insensitive uniqueness indexes that make one mailbox mean one account and one label
mean one category even when two requests race; `ON DELETE RESTRICT` on `pet.category_id`;
`ON DELETE CASCADE` on `pet.owner_id`; the `version` column; and all four `CHECK`
constraints.

`docs/testing/demo-data.sql` (112) is deleted along with the end-to-end scenarios it fed.

## 7. Decision records

Three documents change, in the same commit as the code that makes them true:

- **ADR-006 (new) — the JSF tier calls services directly.** Supersedes ADR-001. States
  what D2 costs and what it keeps: three tiers remain, the REST API remains complete and
  independently exercisable over the shared session, and `ApiClient`'s 361 lines of
  cookie-forwarding buy nothing the assignment asks for.
- **ADR-002 — two new deviation rows** for the image endpoint and the admin status
  parameter (§5.5).
- **ADR-003 — amendment.** The test-scope additions (EclipseLink, jersey-client,
  jersey-hk2) are removed with the integration tests, and the "exactly three dependencies"
  claim becomes literally true again. Bean Validation is recorded as a platform service —
  provided by the server, no dependency added — in the same category as CDI and JTA.

`docs/tasks/` (42 task files) is left as the historical record of how the system was built.
It is documentation, not code, and is outside the line count.

**`README.md` is rewritten.** It currently describes a React / Node / Express / MongoDB /
Firebase / Cloudinary application and has nothing to do with what was built. It is the
first thing a grader reads.

## 8. Test scope

Delete 43 test files (5,163 lines), including the `CountingDriver`/`CountingConnection`
JDBC wrapper pair written to assert query counts, all seven `*IT.java` integration tests,
`Fakes`, `TestData`, `DatabaseTest` and the bean tests.

Keep four unit tests, ~350 lines, no database and no HTTP:

| Test | Proves |
|---|---|
| `PasswordHasherTest` | Hash round-trips; a wrong password fails; a tampered digest fails; no plaintext is stored |
| `UserServiceTest` | Registration rejects a duplicate username and a duplicate email; authentication rejects bad credentials |
| `PetServiceTest` | The §5 rules: a non-owner cannot edit; a non-owner non-admin cannot delete; an admin can |
| `PetDetailDtoTest` | The §6 rule: contact details are absent for a guest and present for a logged-in caller |

These use hand-written stub repositories — plain Java classes implementing the same method
signatures, as ADR-003 already requires in place of Mockito.

## 9. Acceptance criteria

1. `mvn clean verify` passes with **exactly three dependencies** in `pom.xml`.
2. The four unit tests pass.
3. `WEB-INF/lib` is empty in the built WAR.
4. The WAR deploys to Payara 6 without error.
5. All four specification §10 scenarios work by hand: a guest browses and filters;
   a registered user posts a listing with a photo; a logged-in user sees contact details a
   guest cannot; an administrator deletes a listing.
6. Every `POST`/`PUT`/`DELETE` REST method carries `@Secured` or `@AdminOnly`.
7. No `com.petlee.repository` import inside `com.petlee.web` or `com.petlee.rest`.
8. No stack trace in any HTTP response; no `printStackTrace()` in the source.
9. **Total functional lines between 2,400 and 3,000.** Under 2,400 is a failure of this
   design, not a success.

## 10. Execution order

**Every step must leave the tree compiling.** A naive bottom-up order (model, then
repository, then service, …) does not: deleting `PetImage` breaks four higher tiers at
once, and the tree stays broken for six consecutive steps, so a failure at step 8 cannot
be attributed to anything. The order below is therefore by **vertical slice** — each step
is one complete semantic change through however many tiers it touches, and
`petlee-winmvn.sh clean compile` must pass before it is committed.

| # | Step | Why here |
|---|---|---|
| 1 | Tests deleted; `pom.xml` back to three dependencies | They reference everything and would block every later step |
| 2 | Loopback removed — delete `ApiClient`/`ApiException`, beans inject services | Taking `ApiClient` out first removes it as a complication from every step after |
| 3 | One photo per pet — schema migration, `PetImage` gone, `ImageStore`, `/image` endpoint | Self-contained vertical slice |
| 4 | Repositories — delete `AbstractRepository` and `PetFilter`, JPQL queries | Nothing above depends on their internals |
| 5 | Entity/record boundary — services return entities, DTOs become records, resources map, beans and views bind entities | One atomic change; splitting it breaks compilation either side |
| 6 | Services slimmed — Bean Validation, logging removed, exceptions collapsed | Needs the boundary from step 5 settled |
| 7 | REST — merged `SecurityFilter`, single `ErrorMapper` | |
| 8 | Entities slimmed — `equals`/`hashCode`/`toString` removed, constraints added | Last, so earlier steps aren't chasing entity churn |
| 9 | Views and CSS — text inlined, `messages.properties` deleted, unused rules cut | |
| 10 | The four unit tests | Written against the final shape, not a moving one |
| 11 | Docs — ADR-006, the ADR-002 and ADR-003 amendments, new `README.md`; final count | |

Step 5 is deliberately the largest. It is the one change that cannot be decomposed without
leaving the tree broken between commits: the moment a service returns `Pet` instead of
`PetDTO`, its resource, its bean and its view must all move with it.

Each step is one commit on `refactor/slim-to-assignment-size`.

## 11. Risks

**The §10 scenarios are verified by hand, not by tests.** D4 removes the integration suite
that currently checks them, so step 11 is the only thing standing between a regression and
the submission. It is a real reduction in safety, accepted deliberately: the suite costs
4,800 lines against a 2,500-line target, and the scenarios take about ten minutes to walk
through in a browser.

**Payara 6 is not installed in this environment** (per the project handoff notes), so
criteria 4 and 5 cannot be checked here. They must be run on the Windows-side installation
before submission. The build itself runs through `~/petlee-winbuild.sh`, which mirrors
sources to a native Windows path — `maven-war-plugin` fails over the `\\wsl.localhost`
mount.

**Bean Validation must be triggered, not merely declared.** Annotations on an entity do
nothing unless the bean is validated. `@Valid` goes on the REST resource method parameters
(where Jakarta REST enforces it automatically) and JPA validates on `@PrePersist`/`@PreUpdate`
by default. The JSF tier gets validation through `<h:message>` on the form fields. If a
form submits an invalid value and no message appears, this is the cause.

**Records in JSON-B.** Yasson 3 handles records in both directions, but if the target
server ships an older provider, `PetForm` deserialisation would fail at runtime rather
than at compile time. Verified by the first `POST /api/pets` in step 11. This risk is
confined to the REST tier: §3 keeps records out of the JSF tier entirely, where the
equivalent failure — Jakarta EL not resolving a record accessor — would break every page
rather than one endpoint.

**Detached entities in the view.** Because services now return entities (§3), a lazy
association touched inside a Facelets page throws `LazyInitializationException` — after
the response has already begun, so it surfaces as a half-rendered page rather than a clean
error. The guard is the `LEFT JOIN FETCH` rule in §3, and the symptom to watch for during
step 11 is a pet card rendering its name but not its category.

## 12. Out of scope

- Rewriting `docs/tasks/` to match the new design. It is the build history.
- Any new feature. Nothing in this document adds behaviour; the only functional change is
  D3, one photo per pet instead of a gallery.
- Reformatting or restyling code that is already the right size — `PasswordHasher` is kept
  essentially verbatim.
