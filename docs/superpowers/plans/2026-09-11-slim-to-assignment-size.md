# Pet-Lee Slimming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut Pet-Lee from 11,849 functional lines to roughly 2,800 by removing abstraction the assignment never needs, without removing any feature except the multi-image gallery.

**Architecture:** Three tiers in one WAR — Facelets/CDI beans and Jakarta REST resources both call a CDI service layer, which calls JPA repositories, which talk to PostgreSQL. The JSF tier no longer loops back through its own REST API over HTTP. Services return entities; REST resources map those to record DTOs at the boundary; JSF binds to entities directly.

**Tech Stack:** Jakarta EE 10 (JSF 4, JPA 3.1, Jakarta REST 3.1, CDI 4, Bean Validation 3, JSON-B 3), Java 17, PostgreSQL 18, Payara 6, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-11-slimming-design.md` — read it before starting. This plan argues from it and does not restate its reasoning.

## Global Constraints

- **`pom.xml` has exactly three dependencies:** `jakarta.jakartaee-api` (provided), `postgresql` (provided), `junit-jupiter` (test). Adding a fourth requires a new ADR. (ADR-003)
- **Java 17.** `maven.compiler.release` is 17. Records are available; newer syntax is not.
- **The build does not run in WSL.** Use `bash ~/petlee-winmvn.sh <goals>` (mirrors sources to a native Windows path first — `maven-war-plugin` fails over the `\\wsl.localhost` mount with "Incorrect function").
- **No commit trailers.** No `Co-Authored-By`, no `Claude-Session`.
- **Every `POST`/`PUT`/`DELETE` REST method carries `@Secured` or `@AdminOnly`.** No exceptions.
- **No `printStackTrace()`; no stack trace in any HTTP response.**
- **Authorisation decisions live in the service layer,** taking a caller id as a parameter. A service never reads an `HttpSession` or a thread-local.
- **No `com.petlee.repository` import inside `com.petlee.web` or `com.petlee.rest`.**
- **Services must not import `com.petlee.dto`.** Web must not import `com.petlee.dto`. Records exist only at the REST boundary.
- **Every repository query feeding a JSF view must `LEFT JOIN FETCH` the associations that view touches** — entities are detached once the service transaction ends.
- **Every step must leave the tree compiling.** `bash ~/petlee-winmvn.sh clean compile` must print `BUILD SUCCESS` before any commit.
- **Target band: 2,400–3,000 functional lines.** Under 2,400 is a failure, not a success. The requirement is a floor.

---

## File Structure

**Deleted outright (≈6,400 lines):** all of `src/test/java` (43 files), `web/client/` (2), `mapper/` (3), `session/` (1), `config/` (3), `exception/` (6), `rest/mapper/` (4), `model/PetImage.java`, `repository/AbstractRepository.java`, `repository/PetFilter.java`, `repository/PetImageRepository.java`, `rest/PetImageResource.java`, `rest/HealthResource.java`, `rest/RestParams.java`, `service/ImageStorageService.java`, `utilities/PasswordHasherCli.java`, `web/BootstrapCheckBean.java`, `src/main/resources/messages.properties`, `docs/testing/demo-data.sql`, `.idea/`.

**Final tree:**

| File | Responsibility | Target LOC |
|---|---|---:|
| `model/User.java` | User entity + `Role` enum | 75 |
| `model/Pet.java` | Pet entity + 3 enums, `imageUrl`, `@Version` | 100 |
| `model/Category.java` | Category entity | 35 |
| `repository/UserRepository.java` | find by id/username, existence checks, save | 45 |
| `repository/PetRepository.java` | dynamic gallery query, find by id/owner, save, delete | 55 |
| `repository/CategoryRepository.java` | find all, find by id, existence check | 30 |
| `service/UserService.java` | register, authenticate, find | 90 |
| `service/PetService.java` | CRUD + the §5 authorisation rules | 120 |
| `service/CategoryService.java` | list, require-by-id | 40 |
| `service/ImageStore.java` | store/delete an uploaded `Part` | 50 |
| `service/AppException.java` | one exception carrying an HTTP status | 25 |
| `dto/*.java` | 8 records, REST boundary only | 85 |
| `rest/RestApplication.java` | `@ApplicationPath("/api")` | 6 |
| `rest/AuthResource.java` | login, logout | 40 |
| `rest/UserResource.java` | register, me | 30 |
| `rest/PetResource.java` | pet CRUD + image upload | 80 |
| `rest/CategoryResource.java` | list categories | 20 |
| `rest/AdminResource.java` | admin list, status, delete | 45 |
| `rest/ErrorMapper.java` | one `ExceptionMapper` | 40 |
| `rest/security/{Secured,AdminOnly}.java` | two name-binding annotations | 16 |
| `rest/security/SecurityFilter.java` | one request filter for both | 40 |
| `rest/security/SessionUser.java` | session principal record | 12 |
| `rest/security/CurrentUser.java` | session read/establish/terminate | 25 |
| `web/UserBean.java` | `@SessionScoped` — auth, registration | 85 |
| `web/PetBean.java` | `@ViewScoped` — gallery, filters, my listings | 95 |
| `web/PetDetailBean.java` | `@ViewScoped` — one pet, contact gating | 55 |
| `web/PetFormBean.java` | `@ViewScoped` — add/edit + upload | 95 |
| `web/AdminBean.java` | `@ViewScoped` — moderation | 90 |
| `web/ImageServlet.java` | serve uploads from outside the WAR | 60 |
| `web/PageAccessFilter.java` | guard authenticated pages | 45 |
| `util/PasswordHasher.java` | PBKDF2 hash/verify — **kept verbatim** | 55 |
| `scripts/count-loc.py` | reproducible line count (not counted itself) | — |

---

### Task 1: Delete the test suite and restore the three-dependency build

**Files:**
- Delete: all of `src/test/`
- Modify: `pom.xml`
- Create: `scripts/count-loc.py`, `.gitignore` (append `.idea/`)

**Interfaces:**
- Consumes: nothing.
- Produces: `scripts/count-loc.py`, invoked as `python3 scripts/count-loc.py` from the repo root, printing a per-extension table and a `TOTAL` line. Every later task uses it.

- [ ] **Step 1: Record the baseline count**

```bash
cd /home/RoyStabinski/PetLee
git rev-parse --abbrev-ref HEAD   # must print refactor/slim-to-assignment-size
```

- [ ] **Step 2: Create the line counter**

Create `scripts/count-loc.py`. It strips comments before counting, which is the whole point — a raw `wc -l` is off by 40% on this repo.

```python
#!/usr/bin/env python3
"""Count functional lines: no comments, no blanks. Run from the repo root."""
import subprocess, collections

def strip_c_like(t):
    out, i, n = [], 0, len(t)
    while i < n:
        c, nxt = t[i], t[i+1] if i+1 < n else ''
        if c == '/' and nxt == '/':
            while i < n and t[i] != '\n': i += 1
            continue
        if c == '/' and nxt == '*':
            i += 2
            while i < n and not (t[i] == '*' and i+1 < n and t[i+1] == '/'):
                if t[i] == '\n': out.append('\n')
                i += 1
            i += 2
            continue
        if c in '"\'':
            q = c; out.append(c); i += 1
            while i < n:
                if t[i] == '\\': out.append(t[i:i+2]); i += 2; continue
                out.append(t[i])
                if t[i] == q: i += 1; break
                i += 1
            continue
        out.append(c); i += 1
    return ''.join(out)

def strip_xml(t):
    out, i, n = [], 0, len(t)
    while i < n:
        if t.startswith('<!--', i):
            i += 4
            while i < n and not t.startswith('-->', i):
                if t[i] == '\n': out.append('\n')
                i += 1
            i += 3
            continue
        out.append(t[i]); i += 1
    return ''.join(out)

def strip_sql(t):
    res = []
    for ln in strip_c_like(t).split('\n'):
        idx = ln.find('--')
        if idx != -1 and ln[:idx].count("'") % 2 == 0: ln = ln[:idx]
        res.append(ln)
    return '\n'.join(res)

def strip_hash(t):
    return '\n'.join('' if ln.strip()[:1] in ('#', '!') else ln for ln in t.split('\n'))

STRIP = {'java': strip_c_like, 'css': strip_c_like,
         'xml': strip_xml, 'xhtml': strip_xml, 'html': strip_xml,
         'sql': strip_sql, 'properties': strip_hash}

totals = collections.Counter()
for path in subprocess.check_output(['git', 'ls-files']).decode().split():
    ext = path.rsplit('.', 1)[-1].lower()
    fn = STRIP.get(ext)
    if not fn: continue
    text = open(path, encoding='utf-8', errors='replace').read()
    totals[ext] += len([l for l in fn(text).split('\n') if l.strip()])

for ext, n in totals.most_common():
    print(f'{ext:<12}{n:>7}')
print('-' * 19)
print(f'{"TOTAL":<12}{sum(totals.values()):>7}')
```

- [ ] **Step 3: Run it to confirm the baseline**

Run: `python3 scripts/count-loc.py`
Expected: `TOTAL` of `11849`.

- [ ] **Step 4: Delete the test tree**

```bash
git rm -r --quiet src/test
```

- [ ] **Step 5: Cut `pom.xml` back to three dependencies**

Remove these four blocks entirely:
- the `eclipselink` dependency (test scope)
- the `jersey-client` dependency (test scope)
- the `jersey-hk2` dependency (test scope)
- the `jacoco-maven-plugin` `<plugin>` block and its `<jacoco.version>` property

Also remove the `maven-failsafe-plugin` block if present — there are no integration tests any more. Keep `maven-compiler-plugin`, `maven-war-plugin` and `maven-surefire-plugin`.

- [ ] **Step 6: Ignore the IDE directory**

```bash
git rm -r --cached --quiet .idea
printf '\n.idea/\n' >> .gitignore
```

- [ ] **Step 7: Verify the build still compiles**

Run: `bash ~/petlee-winmvn.sh clean compile`
Expected: `BUILD SUCCESS`, `Compiling 70 source files`.

- [ ] **Step 8: Verify the dependency count**

Run: `grep -c '<scope>' pom.xml`
Expected: `3`.

- [ ] **Step 9: Count and commit**

Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `6520`.

```bash
git add -A
git commit -m "test: remove the integration suite and restore the three-dependency build

The verification phase added 5,163 lines of tests and three test-scope
dependencies (EclipseLink, jersey-client, jersey-hk2), which contradicted
ADR-003's central claim that the dependency list is exactly three entries.

Four focused unit tests replace all of it in a later commit, written
against the final shape of the code rather than the current one."
```

---

### Task 2: Remove the loopback — JSF beans call services directly

**Files:**
- Delete: `src/main/java/com/petlee/web/client/ApiClient.java`, `src/main/java/com/petlee/web/client/ApiException.java`, `src/main/java/com/petlee/web/BootstrapCheckBean.java`
- Modify: all six beans in `src/main/java/com/petlee/web/bean/`
- Modify: `src/main/java/com/petlee/session/SessionLifecycle.java` (deleted), `rest/security/CurrentUser.java`

**Interfaces:**
- Consumes: the existing service API, unchanged in this task — `PetService.findGallery(PetFilter)`, `findDetail(Long, Long)`, `create(PetForm, Long)`, `update(Long, PetForm, Long)`, `delete(Long, Long, boolean)`, `findByOwner(Long)`, `findAllForAdmin(PetFilter)`, `changeStatus(Long, String)`; `UserService.register(RegisterForm)`, `authenticate(String, String)`, `findById(Long)`; `CategoryService.findAll()`.
- Produces: beans that inject services with `@Inject` and hold DTOs (unchanged types). `UserBean.isLoggedIn()`, `UserBean.getCurrentUser()`, `UserBean.isAdmin()` are relied on by Task 5's views.

- [ ] **Step 1: Delete the client package**

```bash
git rm --quiet src/main/java/com/petlee/web/client/ApiClient.java \
               src/main/java/com/petlee/web/client/ApiException.java \
               src/main/java/com/petlee/web/BootstrapCheckBean.java \
               src/main/java/com/petlee/session/SessionLifecycle.java
```

- [ ] **Step 2: Simplify `CurrentUser.terminate`**

`SessionLifecycle.discardIsDeferred` existed only because a loopback call could invalidate the session it was riding on. In `rest/security/CurrentUser.java`, replace the body of `terminate` with:

```java
public static void terminate(HttpServletRequest request) {
    if (request == null) return;
    HttpSession session = request.getSession(false);
    if (session == null) return;
    try {
        session.invalidate();
    } catch (IllegalStateException alreadyGone) {
        // already invalidated by another request; nothing to do
    }
}
```

Remove the `import com.petlee.session.SessionLifecycle;` line.

- [ ] **Step 3: Rewrite `PetManagedBean` as `PetBean`**

This is the pattern every other bean follows: `@Inject` the service, call it directly, catch `AppException`/`PetLeeException` rather than `ApiException`.

```java
package com.petlee.web.bean;

import com.petlee.dto.CategoryDTO;
import com.petlee.dto.PetDTO;
import com.petlee.exception.PetLeeException;
import com.petlee.model.Pet;
import com.petlee.repository.PetFilter;
import com.petlee.service.CategoryService;
import com.petlee.service.PetService;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Named("petBean")
@ViewScoped
public class PetBean implements Serializable {

    private static final long serialVersionUID = 1L;
    static final String PLACEHOLDER_IMAGE = "/resources/images/placeholder-pet.png";

    @Inject private transient PetService petService;
    @Inject private transient CategoryService categoryService;

    private List<PetDTO> pets = List.of();
    private List<CategoryDTO> categories = List.of();
    private Integer selectedCategoryId;
    private String selectedSize;
    private String selectedGender;

    @PostConstruct
    void init() {
        categories = categoryService.findAll();
        load();
    }

    public void applyFilter() { load(); }

    public void clearFilters() {
        selectedCategoryId = null;
        selectedSize = null;
        selectedGender = null;
        load();
    }

    private void load() {
        try {
            pets = petService.findGallery(PetFilter.builder()
                    .categoryId(selectedCategoryId)
                    .size(selectedSize == null ? null : Pet.PetSize.valueOf(selectedSize))
                    .gender(selectedGender == null ? null : Pet.PetGender.valueOf(selectedGender))
                    .build());
        } catch (PetLeeException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    public String viewDetails(Long petId) {
        return "/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId;
    }

    public String imageUrlOf(PetDTO pet) {
        String url = pet == null ? null : pet.getMainImageUrl();
        return url == null || url.isBlank() ? PLACEHOLDER_IMAGE : url;
    }

    public List<SelectItem> getSizeOptions() {
        return options("Any size", "SMALL", "MEDIUM", "LARGE");
    }

    public List<SelectItem> getGenderOptions() {
        return options("Any gender", "MALE", "FEMALE");
    }

    private static List<SelectItem> options(String anyLabel, String... values) {
        List<SelectItem> items = new ArrayList<>();
        items.add(new SelectItem(null, anyLabel));
        for (String v : values) {
            items.add(new SelectItem(v, v.charAt(0) + v.substring(1).toLowerCase()));
        }
        return items;
    }

    public List<PetDTO> getPets() { return pets; }
    public List<CategoryDTO> getCategories() { return categories; }
    public boolean isEmpty() { return pets.isEmpty(); }
    public Integer getSelectedCategoryId() { return selectedCategoryId; }
    public void setSelectedCategoryId(Integer v) { this.selectedCategoryId = v; }
    public String getSelectedSize() { return selectedSize; }
    public void setSelectedSize(String v) { this.selectedSize = v; }
    public String getSelectedGender() { return selectedGender; }
    public void setSelectedGender(String v) { this.selectedGender = v; }
}
```

Note the `transient` on the injected services — a `@ViewScoped` bean is serialised, and CDI proxies for `@ApplicationScoped` beans are serialisable, but marking them `transient` and letting CDI re-inject is the portable choice. `git mv` the file to `PetBean.java` and delete the `bundle()` helper and the `msg` lookups entirely.

- [ ] **Step 4: Rewrite the remaining four beans to the same shape**

Apply the identical transformation — swap `@Inject ApiClient api` for the services below, drop the `ApiException` catch in favour of `PetLeeException`, drop every `LOGGER` field, drop every `msg` bundle lookup in favour of literal English:

| Bean | New name | Injects | Replaces these `api.*` calls |
|---|---|---|---|
| `UserManagedBean` | `UserBean` | `UserService` | `api.login`, `api.logout`, `api.register`, `api.me` → `userService.authenticate`, `CurrentUser.terminate`, `userService.register`, `userService.findById` |
| `PetDetailBean` | unchanged | `PetService` | `api.getPet(id)` → `petService.findDetail(id, callerId)` |
| `MyListingsBean` | folded into `PetBean` as `getMyListings()` | `PetService` | `api.getMyPets()` → `petService.findByOwner(userBean.getCurrentUser().getId())` |
| `PetFormBean` | unchanged | `PetService`, `CategoryService` | `api.createPet`, `api.updatePet`, `api.uploadImage` → `petService.create`, `petService.update` |
| `AdminBean` | unchanged | `PetService`, `CategoryService` | `api.adminPets`, `api.setStatus`, `api.adminDelete` → `petService.findAllForAdmin`, `petService.changeStatus`, `petService.delete` |

`UserBean` must establish the session itself, since there is no longer an HTTP call to do it:

```java
public String login() {
    try {
        UserDTO user = userService.authenticate(username, password);
        HttpServletRequest request = (HttpServletRequest) FacesContext.getCurrentInstance()
                .getExternalContext().getRequest();
        CurrentUser.establish(request, SessionUser.of(user));
        this.currentUser = user;
        return "/index.xhtml?faces-redirect=true";
    } catch (PetLeeException e) {
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        return null;
    }
}
```

This is what makes the shared-session claim in spec §3 true: the JSF login writes the same `petlee.user` session attribute that `SecurityFilter` reads on `/api/*`.

- [ ] **Step 5: Verify the build**

Run: `bash ~/petlee-winmvn.sh clean compile`
Expected: `BUILD SUCCESS`. If it fails on a missing `ApiClient` import, a bean was missed.

- [ ] **Step 6: Confirm the loopback is gone**

Run: `grep -rn "ApiClient\|ApiException\|localhost:8080" src/main/java || echo CLEAN`
Expected: `CLEAN`.

- [ ] **Step 7: Count and commit**

Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `5900`.

```bash
git add -A
git commit -m "refactor: call services directly from the JSF tier

ADR-001 had the JSF beans call the application's own REST API over HTTP on
localhost, forwarding the session cookie by hand. ApiClient cost 361 lines
and bought nothing the assignment asks for: the three tiers are still three
tiers, and the REST API is still complete and independently exercisable,
because both entry points share one HttpSession.

Superseded by ADR-006, written in the final commit of this branch."
```

---

### Task 3: One photo per pet

**Files:**
- Modify: `src/main/resources/db/schema.sql`, `model/Pet.java`, `service/PetService.java`, `rest/PetResource.java`, `web/bean/PetFormBean.java`, `src/main/webapp/addPet.xhtml`, `petDetails.xhtml`, `index.xhtml`
- Delete: `model/PetImage.java`, `repository/PetImageRepository.java`, `rest/PetImageResource.java`, `service/ImageStorageService.java`, `dto/PetImageDTO.java`, `config/StorageConfig.java`, `config/MultipartConfigurator.java`, `docs/testing/demo-data.sql`
- Create: `service/AppException.java`, `service/ImageStore.java`

**Interfaces:**
- Consumes: `Pet.getImageUrl()` / `setImageUrl(String)` (added in this task).
- Produces: `AppException(int status, String message)` with `getStatus()`; `ImageStore.store(Part part) -> String` (returns the public URL, e.g. `/images/3f2a….jpg`); `ImageStore.delete(String url) -> void`. `PetService.attachImage(Long petId, Part part, Long callerUserId) -> Pet`.

- [ ] **Step 0: Create `AppException` first**

`ImageStore` in Step 4 throws it, so it must exist before that step. The six-class hierarchy
in `com.petlee.exception` keeps working alongside it until Task 6 deletes it — two exception
types coexisting for three tasks is the price of keeping every commit compiling.

```java
package com.petlee.service;

/** One exception for the whole application, carrying the HTTP status the boundary should use. */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int status;

    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }

    public AppException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
```

The old hierarchy mapped one-to-one onto status codes anyway: `ValidationException` → 400,
`UnauthorizedException` → 401, `ForbiddenException` → 403, `NotFoundException` → 404,
`ConflictException` → 409.

- [ ] **Step 1: Migrate the schema**

Append to `src/main/resources/db/schema.sql` (the file must stay re-runnable):

```sql
-- One photo per pet. The pet_image table and its partial unique index are gone:
-- a single nullable column carries what a whole table used to.
ALTER TABLE pet ADD COLUMN IF NOT EXISTS image_url VARCHAR(512);
DROP TABLE IF EXISTS pet_image;
```

While in this file, delete `CREATE INDEX IF NOT EXISTS idx_pet_status` — at assignment data volumes the planner does not need it — and cut the explanatory comment blocks down to one line each. Keep every `CONSTRAINT`, both `LOWER()` unique indexes, `ON DELETE RESTRICT` on `category_id` and `ON DELETE CASCADE` on `owner_id`.

- [ ] **Step 2: Apply it to the live database**

```bash
WSLENV=PGPASSWORD PGPASSWORD=petlee-dev \
  /mnt/c/Windows/System32/cmd.exe /c "C:\\Users\\royst\\AppData\\Local\\Temp\\petlee-psql.bat -U postgres -d petlee -f schema.sql"
```

Expected: `ALTER TABLE`, `DROP TABLE`. If `psql.exe` reports "could not find own program executable", the `.bat` is missing its `cd /d "C:\Program Files\PostgreSQL\18\bin"` first line.

- [ ] **Step 3: Change the entity**

In `model/Pet.java`, delete the `images` field and its accessors, and add:

```java
@Column(name = "image_url", length = 512)
private String imageUrl;

public String getImageUrl() { return imageUrl; }
public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
```

Remove `import java.util.ArrayList;` and `import java.util.List;`.

- [ ] **Step 4: Replace `ImageStorageService` with `ImageStore`**

Create `src/main/java/com/petlee/service/ImageStore.java`:

```java
package com.petlee.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ImageStore {

    public static final String URL_PREFIX = "/images/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/gif", "gif", "image/webp", "webp");

    private final Path root = Path.of(Optional.ofNullable(System.getProperty("petlee.upload.dir"))
            .or(() -> Optional.ofNullable(System.getenv("PETLEE_UPLOAD_DIR")))
            .orElse(System.getProperty("user.home") + "/petlee-uploads"));

    public String store(Part part) {
        if (part == null || part.getSize() == 0) {
            throw new AppException(400, "A photo file is required");
        }
        if (part.getSize() > MAX_BYTES) {
            throw new AppException(400, "The photo must be 5 MB or smaller");
        }
        String extension = EXTENSIONS.get(
                String.valueOf(part.getContentType()).toLowerCase(Locale.ROOT));
        if (extension == null) {
            throw new AppException(400, "The photo must be a JPEG, PNG, GIF or WebP image");
        }
        String fileName = UUID.randomUUID() + "." + extension;
        try (InputStream in = part.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(in, root.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new AppException(500, "The photo could not be saved");
        }
        return URL_PREFIX + fileName;
    }

    public void delete(String url) {
        resolve(url).ifPresent(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // an orphaned file is not worth failing the request over
            }
        });
    }

    public Optional<Path> resolve(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return Optional.empty();
        }
        Path candidate = root.resolve(url.substring(URL_PREFIX.length())).normalize();
        return candidate.startsWith(root) ? Optional.of(candidate) : Optional.empty();
    }
}
```

The `candidate.startsWith(root)` check is the path-traversal guard and must not be dropped — it is the one piece of `StorageConfig` that was doing real work.

- [ ] **Step 5: Move the multipart config onto the servlet**

`MultipartConfigurator` existed because naming the REST servlet in `web.xml` made RESTEasy skip registering the application. Delete it and put the limits on `PetResource`'s upload method instead — Jakarta REST reads `Part` from a `@Consumes(MediaType.MULTIPART_FORM_DATA)` method on Payara 6 without extra configuration. Add to `rest/PetResource.java`:

```java
@POST
@Path("{id: \\d+}/image")
@Secured
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces(MediaType.APPLICATION_JSON)
public PetDTO uploadImage(@PathParam("id") Long id,
                          @FormParam("file") Part file) {
    return PetDTO.of(pets.attachImage(id, file, caller().getUserId()));
}
```

Delete `rest/PetImageResource.java` and `dto/PetImageDTO.java`.

- [ ] **Step 6: Add `attachImage` to `PetService`**

```java
@Transactional
public Pet attachImage(Long petId, Part file, Long callerUserId) {
    Pet pet = requireById(petId);
    if (!isSameUser(pet.getOwner(), callerUserId)) {
        throw new AppException(403, "Only the owner of a listing can change its photo");
    }
    String previous = pet.getImageUrl();
    pet.setImageUrl(images.store(file));
    Pet saved = pets.save(pet);
    if (previous != null) {
        images.delete(previous);
    }
    return saved;
}
```

In `delete(...)`, replace the image-collection cleanup with `images.delete(pet.getImageUrl())` after `pets.delete(pet)`.

- [ ] **Step 7: Update the add-pet form**

In `src/main/webapp/addPet.xhtml`, replace the multi-file upload with one `<h:inputFile>` bound to `#{petFormBean.photo}` (type `Part`), and in `PetFormBean.save()` call `petService.attachImage(...)` after `petService.create(...)` when `photo != null`. In `index.xhtml` and `petDetails.xhtml`, replace the images loop with a single `<h:graphicImage value="#{petBean.imageUrlOf(pet)}"/>`.

- [ ] **Step 8: Delete what is now unreferenced**

```bash
git rm --quiet src/main/java/com/petlee/model/PetImage.java \
               src/main/java/com/petlee/repository/PetImageRepository.java \
               src/main/java/com/petlee/service/ImageStorageService.java \
               src/main/java/com/petlee/config/StorageConfig.java \
               src/main/java/com/petlee/config/MultipartConfigurator.java \
               docs/testing/demo-data.sql
```

`ImageServlet` must now use `ImageStore.resolve(...)` instead of `StorageConfig.fileFor(...)`; update its single call site and its `@Inject` field type.

- [ ] **Step 9: Remove `PetImage` from `persistence.xml`**

Delete the `<class>com.petlee.model.PetImage</class>` line from `src/main/resources/META-INF/persistence.xml`.

- [ ] **Step 10: Verify and commit**

Run: `bash ~/petlee-winmvn.sh clean compile` — expected `BUILD SUCCESS`.
Run: `grep -rn "PetImage" src/main || echo CLEAN` — expected `CLEAN`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `5400`.

```bash
git add -A
git commit -m "feat: one photo per pet instead of an image gallery

A whole table, entity, repository, resource, DTO and 203-line storage
service existed to model a collection the UI showed one member of. Pet
gains a nullable image_url column and loses the relationship.

Deviates from api-contract.md: POST /api/pets/{id}/images becomes
/image and answers with a PetDTO. Recorded in ADR-002 in the final commit."
```

---

### Task 4: Simplify the repositories

**Files:**
- Delete: `repository/AbstractRepository.java`, `repository/PetFilter.java`
- Rewrite: `repository/UserRepository.java`, `repository/PetRepository.java`, `repository/CategoryRepository.java`
- Modify: every caller of `PetFilter` (`PetService`, `PetResource`, `AdminResource`, `PetBean`, `AdminBean`)

**Interfaces:**
- Consumes: the three entities.
- Produces:
  - `PetRepository.find(Integer categoryId, Pet.PetSize size, Pet.PetGender gender, Long ownerId, boolean availableOnly) -> List<Pet>`
  - `PetRepository.findById(Long) -> Optional<Pet>`, `save(Pet) -> Pet`, `delete(Pet) -> void`
  - `UserRepository.findById(Long) -> Optional<User>`, `findByUsername(String) -> Optional<User>`, `existsByUsername(String) -> boolean`, `existsByEmail(String) -> boolean`, `save(User) -> User`
  - `CategoryRepository.findAll() -> List<Category>`, `findById(Integer) -> Optional<Category>`

- [ ] **Step 1: Rewrite `PetRepository`**

This is the one with real logic. Replace the whole file:

```java
package com.petlee.repository;

import com.petlee.model.Pet;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PetRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    /**
     * The gallery query. LEFT JOIN FETCH on category and owner is load-bearing: the result
     * is detached when the service transaction ends, and the views read both.
     */
    public List<Pet> find(Integer categoryId, Pet.PetSize size, Pet.PetGender gender,
                          Long ownerId, boolean availableOnly) {
        StringBuilder jpql = new StringBuilder(
                "SELECT DISTINCT p FROM Pet p"
                + " LEFT JOIN FETCH p.category LEFT JOIN FETCH p.owner WHERE 1 = 1");
        List<Object[]> params = new ArrayList<>();
        if (availableOnly) {
            jpql.append(" AND p.status = :status");
            params.add(new Object[]{"status", Pet.PetStatus.AVAILABLE});
        }
        if (ownerId != null) {
            jpql.append(" AND p.owner.userId = :ownerId");
            params.add(new Object[]{"ownerId", ownerId});
        }
        if (categoryId != null) {
            jpql.append(" AND p.category.categoryId = :categoryId");
            params.add(new Object[]{"categoryId", categoryId});
        }
        if (size != null) {
            jpql.append(" AND p.size = :size");
            params.add(new Object[]{"size", size});
        }
        if (gender != null) {
            jpql.append(" AND p.gender = :gender");
            params.add(new Object[]{"gender", gender});
        }
        jpql.append(" ORDER BY p.createdAt DESC");

        TypedQuery<Pet> query = em.createQuery(jpql.toString(), Pet.class);
        params.forEach(p -> query.setParameter((String) p[0], p[1]));
        return query.getResultList();
    }

    public Optional<Pet> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return em.createQuery(
                        "SELECT p FROM Pet p LEFT JOIN FETCH p.category LEFT JOIN FETCH p.owner"
                        + " WHERE p.petId = :id", Pet.class)
                .setParameter("id", id)
                .getResultStream().findFirst();
    }

    public Pet save(Pet pet) {
        if (pet.getPetId() == null) {
            em.persist(pet);
            em.flush();
            return pet;
        }
        Pet merged = em.merge(pet);
        em.flush();
        return merged;
    }

    public void delete(Pet pet) {
        em.remove(em.contains(pet) ? pet : em.merge(pet));
        em.flush();
    }
}
```

- [ ] **Step 2: Rewrite `UserRepository`**

```java
package com.petlee.repository;

import com.petlee.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class UserRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    public Optional<User> findById(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(User.class, id));
    }

    public Optional<User> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return em.createQuery("SELECT u FROM User u WHERE u.userName = :name", User.class)
                .setParameter("name", username)
                .getResultStream().findFirst();
    }

    // LOWER() on both sides, matching ux_users_email_lower: one mailbox, one account,
    // whatever case it is typed in.
    public boolean existsByEmail(String email) {
        return email != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE LOWER(u.email) = LOWER(:email)", Long.class)
                .setParameter("email", email).getSingleResult() > 0;
    }

    public boolean existsByUsername(String username) {
        return username != null && em.createQuery(
                        "SELECT COUNT(u) FROM User u WHERE u.userName = :name", Long.class)
                .setParameter("name", username).getSingleResult() > 0;
    }

    public User save(User user) {
        if (user.getUserId() == null) {
            em.persist(user);
            em.flush();
            return user;
        }
        User merged = em.merge(user);
        em.flush();
        return merged;
    }
}
```

- [ ] **Step 3: Rewrite `CategoryRepository`**

```java
package com.petlee.repository;

import com.petlee.model.Category;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CategoryRepository {

    @PersistenceContext(unitName = "petlee-pu")
    private EntityManager em;

    public List<Category> findAll() {
        return em.createQuery(
                "SELECT c FROM Category c ORDER BY c.categoryName", Category.class).getResultList();
    }

    public Optional<Category> findById(Integer id) {
        return id == null ? Optional.empty() : Optional.ofNullable(em.find(Category.class, id));
    }
}
```

- [ ] **Step 4: Delete the base class and the filter**

```bash
git rm --quiet src/main/java/com/petlee/repository/AbstractRepository.java \
               src/main/java/com/petlee/repository/PetFilter.java
```

- [ ] **Step 5: Update every `PetFilter` call site**

`PetService.findGallery(PetFilter)` becomes `findGallery(Integer categoryId, Pet.PetSize size, Pet.PetGender gender)`, and `findAllForAdmin(PetFilter)` becomes `findAllForAdmin(Integer categoryId)`. Update `PetResource.findGallery`, `AdminResource`, `PetBean.load()` and `AdminBean` to pass the three values positionally instead of building a filter object.

- [ ] **Step 6: Verify and commit**

Run: `bash ~/petlee-winmvn.sh clean compile` — expected `BUILD SUCCESS`.
Run: `grep -rn "PetFilter\|AbstractRepository" src/main || echo CLEAN` — expected `CLEAN`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `5150`.

```bash
git add -A
git commit -m "refactor: plain JPQL repositories without a reflective base class

AbstractRepository read entity identifiers through the JPA metamodel and
reflection to choose between persist and merge, across four entity types.
Three repositories now call getId() on a known type.

PetFilter was a builder with a private constructor, a static NONE and a
nested Builder class, carrying three nullable fields. They are three
parameters."
```

---

### Task 5: The entity/record boundary

**Files:**
- Rewrite: all of `src/main/java/com/petlee/dto/` as records
- Delete: `mapper/PetMapper.java`, `mapper/UserMapper.java`, `mapper/CategoryMapper.java`, `dto/AdminPetDTO.java`, `dto/StatusForm.java`
- Modify: all four services (return entities), all five resources (map at the boundary), all five beans and their views (bind entities)

**Interfaces:**
- Consumes: the repositories from Task 4.
- Produces:
  - Services return `Pet`, `User`, `Category`, `List<Pet>`, `List<Category>` — never a DTO.
  - `PetDTO.of(Pet) -> PetDTO`; `PetDetailDTO.of(Pet, boolean authenticated) -> PetDetailDTO`; `UserDTO.of(User) -> UserDTO`; `CategoryDTO.of(Category) -> CategoryDTO`.
  - Input records: `PetForm(String name, String breed, Integer age, String size, String gender, String shortDesc, String longDesc, Integer categoryId)`; `RegisterForm(String username, String password, String fullName, String email, String phone, String region)`; `LoginForm(String username, String password)`; `ErrorDTO(String code, String message)`.

- [ ] **Step 1: Write the output records**

Create `src/main/java/com/petlee/dto/PetDTO.java`:

```java
package com.petlee.dto;

import com.petlee.model.Pet;

public record PetDTO(Long id, String name, String shortDesc, Integer age, String size,
                     String gender, String status, String categoryName, String imageUrl) {

    public static PetDTO of(Pet p) {
        return new PetDTO(p.getPetId(), p.getPetName(), p.getShortDesc(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getStatus().name(),
                p.getCategory().getCategoryName(), p.getImageUrl());
    }
}
```

Create `src/main/java/com/petlee/dto/PetDetailDTO.java` — this one carries the §6 contact-gating rule and is covered by a unit test in Task 10:

```java
package com.petlee.dto;

import com.petlee.model.Pet;

public record PetDetailDTO(Long id, String name, String breed, Integer age, String size,
                           String gender, String shortDesc, String longDesc, String status,
                           String categoryName, String imageUrl,
                           String ownerName, String ownerPhone, String ownerEmail) {

    /**
     * Contact details are populated only for an authenticated caller (specification §6).
     * A guest receives the same shape with three nulls, not a different shape.
     */
    public static PetDetailDTO of(Pet p, boolean authenticated) {
        return new PetDetailDTO(p.getPetId(), p.getPetName(), p.getBreed(), p.getAge(),
                p.getSize().name(), p.getGender().name(), p.getShortDesc(), p.getLongDesc(),
                p.getStatus().name(), p.getCategory().getCategoryName(), p.getImageUrl(),
                authenticated ? p.getOwner().getFullName() : null,
                authenticated ? p.getOwner().getPhoneNumber() : null,
                authenticated ? p.getOwner().getEmail() : null);
    }
}
```

Create `UserDTO`, `CategoryDTO` and `ErrorDTO` in the same shape:

```java
public record UserDTO(Long id, String username, String fullName, String email,
                      String phone, String role) {
    public static UserDTO of(User u) {
        return new UserDTO(u.getUserId(), u.getUserName(), u.getFullName(), u.getEmail(),
                u.getPhoneNumber(), u.getRole().name());
    }
}

public record CategoryDTO(Integer id, String name) {
    public static CategoryDTO of(Category c) {
        return new CategoryDTO(c.getCategoryId(), c.getCategoryName());
    }
}

public record ErrorDTO(String code, String message) { }
```

- [ ] **Step 2: Write the input records with Bean Validation**

```java
package com.petlee.dto;

import jakarta.validation.constraints.*;

public record RegisterForm(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                message = "Username must be 3-20 characters: letters, digits and underscore")
        String username,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters")
        String password,
        @NotBlank @Size(max = 50) String fullName,
        @NotBlank @Email @Size(max = 100) String email,
        @Size(max = 20) String phone,
        @Size(max = 100) String region) { }
```

```java
public record PetForm(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String breed,
        @Min(0) @Max(50) Integer age,
        @NotNull @Pattern(regexp = "SMALL|MEDIUM|LARGE") String size,
        @NotNull @Pattern(regexp = "MALE|FEMALE") String gender,
        @Size(max = 255) String shortDesc,
        String longDesc,
        @NotNull Integer categoryId) { }
```

```java
public record LoginForm(@NotBlank String username, @NotBlank String password) { }
```

- [ ] **Step 3: Change every service signature to return entities**

`PetService`: `findGallery(...) -> List<Pet>`, `findDetail(Long) -> Pet` (the `callerUserId` parameter moves out — gating is the resource's job now), `create(PetForm, Long) -> Pet`, `update(Long, PetForm, Long) -> Pet`, `findByOwner(Long) -> List<Pet>`, `findAllForAdmin(Integer) -> List<Pet>`, `changeStatus(Long, String) -> Pet`.
`UserService`: `register(RegisterForm) -> User`, `authenticate(String, String) -> User`, `findById(Long) -> Optional<User>`.
`CategoryService`: `findAll() -> List<Category>`, `requireById(Integer) -> Category`.

Delete every `import com.petlee.dto.*` and every `import com.petlee.mapper.*` from the `service` package except the two form records, which are still the input type.

- [ ] **Step 4: Map at the resource boundary**

Each resource method wraps its service call. `PetResource`:

```java
@GET
@Produces(MediaType.APPLICATION_JSON)
public List<PetDTO> findGallery(@QueryParam("categoryId") Integer categoryId,
                                @QueryParam("size") String size,
                                @QueryParam("gender") String gender) {
    return pets.findGallery(categoryId, parseSize(size), parseGender(gender))
            .stream().map(PetDTO::of).toList();
}

@GET
@Path("{id: \\d+}")
@Produces(MediaType.APPLICATION_JSON)
public PetDetailDTO findDetail(@PathParam("id") Long id) {
    return PetDetailDTO.of(pets.findDetail(id), CurrentUser.from(request).isPresent());
}
```

`RestParams` folds into two private static helpers on `PetResource` (`parseSize`, `parseGender`), each throwing `new AppException(400, ...)` on an unparseable value. Delete `rest/RestParams.java`.

- [ ] **Step 5: Change the beans and views to bind entities**

Every bean field of type `List<PetDTO>` becomes `List<Pet>`; `PetDTO` becomes `Pet`; `UserDTO` becomes `User`. The EL property names change with them — this is the step that touches the views:

| Old EL | New EL |
|---|---|
| `#{pet.id}` | `#{pet.petId}` |
| `#{pet.name}` | `#{pet.petName}` |
| `#{pet.categoryName}` | `#{pet.category.categoryName}` |
| `#{pet.mainImageUrl}` | `#{pet.imageUrl}` |
| `#{pet.ownerName}` | `#{pet.owner.fullName}` |
| `#{user.username}` | `#{user.userName}` |

Grep for each old expression across `src/main/webapp` and replace. `PetDetailBean` gains `isContactVisible()` returning `userBean.isLoggedIn()`, and `petDetails.xhtml` wraps the contact block in `rendered="#{petDetailBean.contactVisible}"`.

- [ ] **Step 6: Delete the mapper package**

```bash
git rm --quiet -r src/main/java/com/petlee/mapper
git rm --quiet src/main/java/com/petlee/dto/AdminPetDTO.java \
               src/main/java/com/petlee/dto/StatusForm.java \
               src/main/java/com/petlee/rest/RestParams.java
```

- [ ] **Step 7: Move the admin status change to a query parameter**

`StatusForm` was a class holding one field. In `AdminResource`, replace the JSON body with a
query parameter (ADR-002 row 11, recorded in Task 11):

```java
@PUT
@Path("pets/{id: \\d+}/status")
@Produces(MediaType.APPLICATION_JSON)
public PetDTO changeStatus(@PathParam("id") Long id, @QueryParam("status") String status) {
    return PetDTO.of(pets.changeStatus(id, status));
}
```

`PetService.changeStatus` keeps its existing guard — `status` must be `AVAILABLE` or
`REMOVED`, anything else throws 400. Update `AdminBean` to call the service directly (it no
longer goes through HTTP, so there is no body to build either way).

- [ ] **Step 8: Verify the layering rules hold**

Run: `grep -rn "com.petlee.dto" src/main/java/com/petlee/service src/main/java/com/petlee/web || echo CLEAN`
Expected: only `PetForm`/`RegisterForm`/`LoginForm` imports in `service`; nothing at all in `web`.

Run: `grep -rn "com.petlee.repository" src/main/java/com/petlee/web src/main/java/com/petlee/rest || echo CLEAN`
Expected: `CLEAN`. This is acceptance criterion 7 from the spec, and it is easiest to break
here — a bean reaching for a repository to avoid adding a service method.

- [ ] **Step 9: Verify and commit**

Run: `bash ~/petlee-winmvn.sh clean compile` — expected `BUILD SUCCESS`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `4500`.

```bash
git add -A
git commit -m "refactor: records at the REST boundary, entities everywhere else

Eleven DTO classes of getters and setters become eight records with static
factories, and the mapper package disappears into them.

Services now return entities. The JSF tier binds to those directly, because
Jakarta EL resolves properties through Introspector and cannot see a record's
accessor: #{pet.name} against a record is server-dependent at best. Records
stay on the REST side, where JSON-B handles them natively."
```

---

### Task 6: Slim the services

**Files:**
- Rewrite: `service/UserService.java`, `service/PetService.java`, `service/CategoryService.java`
- Delete: all of `src/main/java/com/petlee/exception/`

**Interfaces:**
- Consumes: repositories (Task 4), form records (Task 5), `AppException` (created in Task 3 Step 0).
- Produces: every service throws `AppException` and nothing else.

- [ ] **Step 1: Retire the old exception hierarchy from the services**

`AppException` already exists (Task 3 Step 0). This step replaces the last uses of the six
old classes. Map them by status: `ValidationException` → 400, `UnauthorizedException` → 401,
`ForbiddenException` → 403, `NotFoundException` → 404, `ConflictException` → 409.

- [ ] **Step 2: Rewrite `UserService`**

```java
package com.petlee.service;

import com.petlee.dto.RegisterForm;
import com.petlee.model.User;
import com.petlee.repository.UserRepository;
import com.petlee.util.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.Optional;

@ApplicationScoped
public class UserService {

    @Inject private UserRepository users;

    @Transactional
    public User register(RegisterForm form) {
        if (users.existsByUsername(form.username())) {
            throw new AppException(409, "That username is already taken");
        }
        if (users.existsByEmail(form.email())) {
            throw new AppException(409, "That email address is already registered");
        }
        User user = new User();
        user.setUserName(form.username());
        user.setPassword(PasswordHasher.hash(form.password()));
        user.setFullName(form.fullName());
        user.setEmail(form.email());
        user.setPhoneNumber(form.phone());
        user.setRegion(form.region());
        user.setRole(User.Role.USER);
        try {
            return users.save(user);
        } catch (PersistenceException race) {
            // The LOWER() unique indexes are the real arbiter; two registrations can both
            // pass the checks above and only one can pass this.
            throw new AppException(409, "That username or email address is already registered", race);
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public User authenticate(String username, String password) {
        Optional<User> found = users.findByUsername(username == null ? null : username.trim());
        if (found.isEmpty() || password == null
                || !PasswordHasher.verify(password, found.get().getPassword())) {
            // One message for both cases: a distinct "no such user" tells an attacker
            // which usernames exist.
            throw new AppException(401, "Username or password is incorrect");
        }
        return found.get();
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Optional<User> findById(Long id) {
        return users.findById(id);
    }
}
```

The seven `*_MAX` constants, the two `Pattern` fields and the 30-line `validateForRegistration` are gone — `@Valid RegisterForm` on the resource method does that work now.

- [ ] **Step 3: Rewrite `PetService`**

Keep every authorisation rule exactly as it is today. Replace `applyForm`'s length and range checks with the annotations from Task 5, keeping only the enum parsing and the category lookup:

```java
private void applyForm(PetForm form, Pet pet) {
    pet.setPetName(form.name().trim());
    pet.setBreed(form.breed());
    pet.setAge(form.age());
    pet.setSize(Pet.PetSize.valueOf(form.size()));
    pet.setGender(Pet.PetGender.valueOf(form.gender()));
    pet.setShortDesc(form.shortDesc());
    pet.setLongDesc(form.longDesc());
    pet.setCategory(categories.requireById(form.categoryId()));
}
```

`valueOf` is safe here because `@Pattern(regexp = "SMALL|MEDIUM|LARGE")` has already rejected anything else.

The three rules that must survive unchanged, each with its status:

```java
// update(): only the owner may edit
if (!isSameUser(pet.getOwner(), callerUserId)) {
    throw new AppException(403, "Only the owner of a listing can edit it");
}

// delete(): the owner or an admin
if (!callerIsAdmin && !isSameUser(pet.getOwner(), callerUserId)) {
    throw new AppException(403, "Only the owner of a listing, or an administrator, can remove it");
}

// update(): optimistic locking, from @Version on Pet
catch (OptimisticLockException e) {
    throw new AppException(409, "This listing was changed by someone else; reload it and try again", e);
}
```

Delete every `LOGGER` field and call across all three services.

- [ ] **Step 4: Update the beans' catch clauses**

The five beans written in Task 2 catch `PetLeeException`. Nothing throws it any more, and an
unreachable catch of a type no longer thrown is a compile error once the package is deleted.
In each of `UserBean`, `PetBean`, `PetDetailBean`, `PetFormBean` and `AdminBean`, replace:

```java
} catch (PetLeeException e) {
```

with:

```java
} catch (AppException e) {
```

and swap `import com.petlee.exception.PetLeeException;` for `import com.petlee.service.AppException;`.

Run: `grep -rn "PetLeeException" src/main/java || echo CLEAN` — expected `CLEAN` before the next step.

- [ ] **Step 5: Delete the exception package**

```bash
git rm --quiet -r src/main/java/com/petlee/exception
git mv src/main/java/com/petlee/utilities src/main/java/com/petlee/util
git rm --quiet src/main/java/com/petlee/util/PasswordHasherCli.java
```

Update the `package`/`import` lines for `PasswordHasher` — its body stays byte-for-byte identical.

- [ ] **Step 6: Verify and commit**

Run: `bash ~/petlee-winmvn.sh clean compile` — expected `BUILD SUCCESS`.
Run: `grep -rn "LOGGER\|Logger" src/main/java/com/petlee/service || echo CLEAN` — expected `CLEAN`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `4150`.

```bash
git add -A
git commit -m "refactor: Bean Validation in place of hand-written checks

UserService declared seven length constants and a 30-line validator that
restated limits already in schema.sql and the @Column annotations. PetService
did it again. Both are now annotations on the form records, enforced by the
platform at the boundary.

Six exception classes that mapped one-to-one onto status codes become one
AppException carrying the status."
```

---

### Task 7: One security filter, one error mapper

**Files:**
- Create: `rest/security/SecurityFilter.java`, `rest/ErrorMapper.java`
- Delete: `rest/security/AuthenticationFilter.java`, `rest/security/AdminOnlyFilter.java`, all of `rest/mapper/`
- Rewrite: `rest/security/SessionUser.java` as a record

**Interfaces:**
- Consumes: `AppException` (Task 6), `ErrorDTO` (Task 5).
- Produces: `SessionUser(Long userId, String username, String fullName, boolean admin)`; `SessionUser.of(User)`; `CurrentUser.from(HttpServletRequest) -> Optional<SessionUser>`.

- [ ] **Step 1: Make `SessionUser` a record**

```java
package com.petlee.rest.security;

import com.petlee.model.User;
import java.io.Serializable;

public record SessionUser(Long userId, String username, String fullName, boolean admin)
        implements Serializable {

    public static SessionUser of(User u) {
        return new SessionUser(u.getUserId(), u.getUserName(), u.getFullName(),
                u.getRole() == User.Role.ADMIN);
    }
}
```

This record never reaches a Facelets view — beans expose `userBean.loggedIn` and `userBean.admin` as ordinary boolean getters — so the EL constraint from spec §3 does not apply.

- [ ] **Step 2: Merge the two filters**

```java
package com.petlee.rest.security;

import com.petlee.dto.ErrorDTO;
import jakarta.annotation.Priority;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.util.Optional;

@Provider
@Secured
@Priority(Priorities.AUTHENTICATION)
public class SecurityFilter implements ContainerRequestFilter {

    @Context private HttpServletRequest request;
    @Context private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext context) {
        Optional<SessionUser> caller = CurrentUser.from(request);
        if (caller.isEmpty()) {
            abort(context, 401, "NOT_AUTHENTICATED", "You must be logged in to do that.");
            return;
        }
        if (adminRequired() && !caller.get().admin()) {
            abort(context, 403, "NOT_ADMIN", "Administrator privileges are required.");
        }
    }

    private boolean adminRequired() {
        return resourceInfo.getResourceMethod().isAnnotationPresent(AdminOnly.class)
                || resourceInfo.getResourceClass().isAnnotationPresent(AdminOnly.class);
    }

    private static void abort(ContainerRequestContext context, int status, String code, String message) {
        context.abortWith(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message))
                .build());
    }
}
```

**Admin methods carry both annotations: `@Secured @AdminOnly`.**

The tempting alternative is to meta-annotate `@AdminOnly` with `@Secured` so one annotation
implies the other. Do not: the Jakarta REST specification defines name binding in terms of
annotations present on the resource method, and does not guarantee that a name-binding
annotation is resolved through a meta-annotation chain. Whether it works would depend on the
implementation — and Payara 6 is not available here to find out. Two annotations are four
extra characters and behave identically on every server.

```java
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AdminOnly { }
```

`AdminResource` therefore reads:

```java
@Path("admin")
@RequestScoped
@Secured
@AdminOnly
public class AdminResource { ... }
```

Class-level annotations apply to every method, so this is stated once rather than on each.

- [ ] **Step 3: One error mapper**

```java
package com.petlee.rest;

import com.petlee.dto.ErrorDTO;
import com.petlee.service.AppException;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class ErrorMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(ErrorMapper.class.getName());

    @Override
    public Response toResponse(Throwable failure) {
        if (failure instanceof AppException app) {
            return json(app.getStatus(), "ERROR", app.getMessage());
        }
        if (failure instanceof ConstraintViolationException violations) {
            String message = violations.getConstraintViolations().stream().findFirst()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .orElse("The request is not valid");
            return json(400, "VALIDATION_FAILED", message);
        }
        if (failure instanceof WebApplicationException web) {
            return json(web.getResponse().getStatus(), "ERROR", web.getMessage());
        }
        // The only place an unexpected failure is recorded. The response carries no detail:
        // a stack trace in an HTTP body tells an attacker the framework, the version and the
        // call path.
        LOGGER.log(Level.SEVERE, "Unhandled failure", failure);
        return json(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }

    private static Response json(int status, String code, String message) {
        return Response.status(status).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorDTO(code, message)).build();
    }
}
```

- [ ] **Step 4: Delete the replaced files**

```bash
git rm --quiet -r src/main/java/com/petlee/rest/mapper
git rm --quiet src/main/java/com/petlee/rest/security/AuthenticationFilter.java \
               src/main/java/com/petlee/rest/security/AdminOnlyFilter.java \
               src/main/java/com/petlee/rest/HealthResource.java
```

- [ ] **Step 5: Verify every mutating endpoint is still guarded**

A per-method grep gives a false negative on `AdminResource`, which is guarded at class level.
Check each file in turn instead, and read the result rather than counting it:

```bash
for f in src/main/java/com/petlee/rest/*Resource.java; do
  echo "--- $f"
  if grep -qE '^@(Secured|AdminOnly)' "$f"; then
    echo "    class-level guard: all methods covered"
  else
    grep -B6 -E '^\s+@(POST|PUT|DELETE)' "$f" | grep -E '@(POST|PUT|DELETE)|@Secured|@AdminOnly'
  fi
done
```

Expected: every `@POST`, `@PUT` and `@DELETE` is either in a class-level-guarded file or
immediately preceded by `@Secured`. The two legitimately unguarded `@POST` methods are
`AuthResource.login` and `UserResource.register` — anything else unguarded is a defect.

- [ ] **Step 6: Verify and commit**

Run: `bash ~/petlee-winmvn.sh clean compile` — expected `BUILD SUCCESS`.
Run: `grep -rn "printStackTrace" src/main || echo CLEAN` — expected `CLEAN`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `3950`.

```bash
git add -A
git commit -m "refactor: one security filter and one exception mapper

Two filters differing only in an admin check become one reading @AdminOnly
off the matched method. Three exception mappers plus an ErrorResponses
helper become one ExceptionMapper<Throwable>.

Unexpected failures are logged server-side and answered with a bare 500:
no stack trace reaches an HTTP response."
```

---

### Task 8: Slim the entities

**Files:**
- Rewrite: `model/User.java`, `model/Pet.java`, `model/Category.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: unchanged accessor names — this task removes code, it does not rename anything.

- [ ] **Step 1: Remove the boilerplate triplets**

From all three entities, delete `equals`, `hashCode` and `toString` together with their justification comments. Nothing in the application puts an entity in a `HashSet` or `HashMap`; confirm with:

Run: `grep -rn "HashSet\|HashMap\|\.contains(" src/main/java/com/petlee || echo CLEAN`
Expected: `CLEAN` (or only `em.contains(...)`, which is JPA identity, not `equals`).

- [ ] **Step 2: Add the validation constraints**

On `Pet`: `@NotBlank @Size(max = 100)` on `petName`, `@Size(max = 100)` on `breed`, `@Min(0) @Max(50)` on `age`, `@Size(max = 255)` on `shortDesc`, `@NotNull` on `gender`, `size`, `status`, `category`, `owner`.
On `User`: `@NotBlank @Size(max = 20)` on `userName`, `@NotBlank @Size(max = 50)` on `fullName`, `@NotBlank @Email @Size(max = 100)` on `email`, `@Size(max = 20)` on `phoneNumber`, `@Size(max = 100)` on `region`.
On `Category`: `@NotBlank @Size(max = 50)` on `categoryName`.

These fire on `@PrePersist`/`@PreUpdate` automatically — a second line of defence behind the form records.

- [ ] **Step 3: Keep these, and do not "simplify" them**

- `@Version private Long version;` on `Pet` with a getter and no setter — the concurrency requirement.
- `@Column(name = "created_at", nullable = false, updatable = false)` — ADR-002 #4.
- `getSize()`/`setSize()` naming — ADR-002 #2.
- `Long` rather than `long` identifiers — ADR-002 #3 and #8.
- `region` on `User` — ADR-002 #1.
- The `@PrePersist` hooks setting `createdAt` and defaulting `status`.

- [ ] **Step 4: Verify and commit**

Run: `bash ~/petlee-winmvn.sh clean compile` — expected `BUILD SUCCESS`.
Run: `grep -c "@Version" src/main/java/com/petlee/model/Pet.java` — expected `1`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `3800`.

```bash
git add -A
git commit -m "refactor: drop entity boilerplate, add validation constraints

Around 120 lines of equals/hashCode/toString across four entities, each with
a comment explaining a constant hash code, for a situation that never arises:
nothing here puts an entity in a hash-based collection.

Bean Validation constraints replace them, stating the schema's limits once
more where JPA can enforce them on persist and update."
```

---

### Task 9: Views, stylesheet and the resource bundle

**Files:**
- Modify: all 14 `.xhtml` files, `resources/css/petlee.css`, `WEB-INF/web.xml`, `WEB-INF/faces-config.xml`
- Delete: `src/main/resources/messages.properties`, `webapp/error/expired.xhtml`, `webapp/error/500.xhtml`

**Interfaces:**
- Consumes: the bean property names settled in Task 5.
- Produces: no Java interface. The nine surviving views.

- [ ] **Step 1: Delete the resource bundle and its declaration**

```bash
git rm --quiet src/main/resources/messages.properties
```

Remove the `<resource-bundle>` block from `WEB-INF/faces-config.xml`. Then replace every `#{msg.someKey}` in every view with the literal English string that key held. Work from the deleted file's content:

Run: `git show HEAD:src/main/resources/messages.properties` to read the values while editing.

- [ ] **Step 2: Consolidate the error pages**

Four error pages of near-identical markup become two: `error/404.xhtml` and `error/error.xhtml` (covering 403, 500 and `ViewExpiredException`). Update the four `<error-page>` entries in `web.xml` to point at the two survivors.

- [ ] **Step 3: Trim `web.xml`**

The file is 45 code lines under roughly 60 lines of comment. Keep: `PROJECT_STAGE`, `FACELETS_SKIP_COMMENTS` (load-bearing — without it Facelets evaluates EL inside XML comments and a comment mentioning `#{msg...}` breaks the page), `session-config` with `http-only`, the error pages, the welcome file. Delete the `petlee.session.cookie.secure` context parameter, since `SessionCookieConfigurator` goes with it:

```bash
git rm --quiet src/main/java/com/petlee/config/SessionCookieConfigurator.java
```

- [ ] **Step 4: Cut the stylesheet**

`petlee.css` is 491 code lines. Find the rules no surviving view uses:

```bash
for cls in $(grep -oE '^\.[a-zA-Z0-9_-]+' src/main/webapp/resources/css/petlee.css | sort -u | tr -d '.'); do
  grep -rqF "$cls" src/main/webapp --include=*.xhtml || echo "UNUSED: $cls"
done
```

Delete every rule the loop reports, plus the gallery-specific rules that went with the multi-image carousel. Target 170 lines.

- [ ] **Step 5: Verify and commit**

Run: `bash ~/petlee-winbuild.sh package` — expected `BUILD SUCCESS` and a WAR in `target/`.
Run: `grep -rn "#{msg" src/main/webapp || echo CLEAN` — expected `CLEAN`.
Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `2950`.

```bash
git add -A
git commit -m "refactor: inline the view text and cut the unused stylesheet rules

messages.properties held 143 lines for a single-language project, reached
through evaluateExpressionGet(\"#{msg['...']}\") to label a dropdown. The
strings are now where they are read.

Four near-identical error pages become two."
```

---

### Task 10: The four unit tests

**Files:**
- Create: `src/test/java/com/petlee/util/PasswordHasherTest.java`, `src/test/java/com/petlee/service/UserServiceTest.java`, `src/test/java/com/petlee/service/PetServiceTest.java`, `src/test/java/com/petlee/dto/PetDetailDTOTest.java`, `src/test/java/com/petlee/StubRepositories.java`

**Interfaces:**
- Consumes: everything above.
- Produces: `StubRepositories.users(User...) -> UserRepository`, `StubRepositories.pets(Pet...) -> PetRepository` — hand-written subclasses overriding the query methods, per ADR-003's "hand-written test doubles, no Mockito".

- [ ] **Step 1: Write `PasswordHasherTest`**

```java
package com.petlee.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {

    @Test
    void hashRoundTrips() {
        String hash = PasswordHasher.hash("correct horse battery");
        assertTrue(PasswordHasher.verify("correct horse battery", hash));
    }

    @Test
    void rejectsTheWrongPassword() {
        assertFalse(PasswordHasher.verify("wrong", PasswordHasher.hash("right")));
    }

    @Test
    void neverStoresThePlaintext() {
        assertFalse(PasswordHasher.hash("hunter2").contains("hunter2"));
    }

    @Test
    void saltsEachHashSeparately() {
        assertNotEquals(PasswordHasher.hash("same"), PasswordHasher.hash("same"));
    }

    @Test
    void rejectsAMalformedDigest() {
        assertFalse(PasswordHasher.verify("x", "not-a-digest"));
    }
}
```

- [ ] **Step 2: Run it and watch it pass**

Run: `bash ~/petlee-winmvn.sh clean test -Dtest=PasswordHasherTest`
Expected: `Tests run: 5, Failures: 0`.

- [ ] **Step 3: Write the stub repositories**

```java
package com.petlee;

import com.petlee.model.Pet;
import com.petlee.model.User;
import com.petlee.repository.PetRepository;
import com.petlee.repository.UserRepository;
import java.util.List;
import java.util.Optional;

/** Hand-written doubles. ADR-003 rules out Mockito; these are three overridden methods. */
public final class StubRepositories {

    public static UserRepository users(User... rows) {
        return new UserRepository() {
            @Override public Optional<User> findByUsername(String name) {
                return List.of(rows).stream().filter(u -> u.getUserName().equals(name)).findFirst();
            }
            @Override public boolean existsByUsername(String name) {
                return findByUsername(name).isPresent();
            }
            @Override public boolean existsByEmail(String email) {
                return List.of(rows).stream().anyMatch(u -> u.getEmail().equalsIgnoreCase(email));
            }
            @Override public User save(User user) { return user; }
        };
    }

    public static PetRepository pets(Pet... rows) {
        return new PetRepository() {
            @Override public Optional<Pet> findById(Long id) {
                return List.of(rows).stream().filter(p -> id.equals(p.getPetId())).findFirst();
            }
            @Override public Pet save(Pet pet) { return pet; }
            @Override public void delete(Pet pet) { }
        };
    }

    private StubRepositories() { }
}
```

The repository methods must not be `final` for this to work — confirm Task 4 left them plain `public`.

- [ ] **Step 4: Write `PetServiceTest` — the §5 authorisation rules**

```java
package com.petlee.service;

import com.petlee.StubRepositories;
import com.petlee.model.Pet;
import com.petlee.model.User;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PetServiceTest {

    private static final Long OWNER = 1L;
    private static final Long STRANGER = 2L;

    @Test
    void aStrangerCannotDeleteSomeoneElsesListing() {
        PetService service = serviceOwning(OWNER);
        AppException failure = assertThrows(AppException.class,
                () -> service.delete(10L, STRANGER, false));
        assertEquals(403, failure.getStatus());
    }

    @Test
    void anAdministratorCanDeleteAnyListing() {
        PetService service = serviceOwning(OWNER);
        assertDoesNotThrow(() -> service.delete(10L, STRANGER, true));
    }

    @Test
    void theOwnerCanDeleteTheirOwnListing() {
        PetService service = serviceOwning(OWNER);
        assertDoesNotThrow(() -> service.delete(10L, OWNER, false));
    }

    private static PetService serviceOwning(Long ownerId) {
        User owner = new User();
        owner.setUserId(ownerId);
        Pet pet = new Pet();
        pet.setPetId(10L);
        pet.setOwner(owner);
        return new PetService(StubRepositories.pets(pet), null, null, null);
    }
}
```

This requires `PetService` to keep a constructor taking its four collaborators — Task 6 must not replace it with field injection only. Keep the `@Inject` constructor.

- [ ] **Step 5: Write `UserServiceTest` and `PetDetailDTOTest`**

`UserServiceTest` asserts: a duplicate username throws 409; a duplicate email in different case throws 409; authenticating with a wrong password throws 401; authenticating correctly returns the user.

`PetDetailDTOTest` asserts the §6 rule directly:

```java
@Test
void aGuestSeesNoContactDetails() {
    PetDetailDTO dto = PetDetailDTO.of(petOwnedBy("Ada", "050-1234567", "ada@example.com"), false);
    assertNull(dto.ownerName());
    assertNull(dto.ownerPhone());
    assertNull(dto.ownerEmail());
}

@Test
void aLoggedInCallerSeesThem() {
    PetDetailDTO dto = PetDetailDTO.of(petOwnedBy("Ada", "050-1234567", "ada@example.com"), true);
    assertEquals("Ada", dto.ownerName());
    assertEquals("050-1234567", dto.ownerPhone());
    assertEquals("ada@example.com", dto.ownerEmail());
}
```

- [ ] **Step 6: Run the whole suite**

Run: `bash ~/petlee-winmvn.sh clean test`
Expected: `BUILD SUCCESS`, roughly `Tests run: 14, Failures: 0, Errors: 0`.

- [ ] **Step 7: Commit**

Run: `python3 scripts/count-loc.py` — expected `TOTAL` near `3300`.

```bash
git add -A
git commit -m "test: four unit tests covering the rules that matter

Password hashing round-trips, registration rejects duplicates,
authentication rejects bad credentials, the specification section 5
authorisation rules hold, and contact details are hidden from guests.

No database, no HTTP, no Mockito: hand-written stubs, as ADR-003 requires."
```

---

### Task 11: Decision records, README, final verification

**Files:**
- Create: `docs/decisions/ADR-006-direct-service-calls.md`
- Modify: `docs/decisions/ADR-002-contract-deviations.md`, `docs/decisions/ADR-003-technology-constraint.md`, `README.md`, `api-contract.md`

**Interfaces:** none — documentation only.

- [ ] **Step 1: Write ADR-006**

Create `docs/decisions/ADR-006-direct-service-calls.md` superseding ADR-001. It must state: what changed (JSF beans inject services rather than calling `/api` over HTTP); what is preserved (three tiers, a complete REST API, the shared `HttpSession` that makes it independently exercisable, DTOs at the boundary); and what was given up (a single enforcement point for authorisation — the JSF tier now relies on `PageAccessFilter` and the service-layer checks rather than passing through `SecurityFilter`).

- [ ] **Step 2: Add the two ADR-002 rows**

| # | Contract | Implementation | Reason |
|---|---|---|---|
| 10 | `POST /api/pets/{id}/images` → `PetImageDTO` | `POST /api/pets/{id}/image` → `PetDTO` | One photo per pet; a plural path for a single column is misleading |
| 11 | `PUT /api/admin/pets/{id}/status` with a JSON body | same path with `?status=` | Removes a one-field DTO class |

Update `api-contract.md` to match, so the contract and the code agree.

- [ ] **Step 3: Amend ADR-003**

Add an amendment dated 2026-09-11: the test-scope additions are removed with the integration suite and the three-dependency list is literally true again; Bean Validation is a platform service in the same category as CDI and JTA, not a new dependency.

- [ ] **Step 4: Rewrite `README.md`**

The current file describes a React / Node / Express / MongoDB / Firebase / Cloudinary application and is the first thing a grader reads. Replace it with: what Pet-Lee is, the real stack (Jakarta EE 10, JSF, JPA, Jakarta REST, PostgreSQL 18, Payara 6), how to create and seed the database, how to build the WAR, how to deploy it, the default admin credentials, and a one-paragraph architecture note pointing at the ADRs.

- [ ] **Step 5: Final count**

Run: `python3 scripts/count-loc.py`
Expected: `TOTAL` between `2400` and `3000`. **If it is under 2,400, stop and report** — the target is a floor, and the remedy is to restore detail (a fuller README is not code; restoring a deleted test or a clearer view is), not to declare victory.

- [ ] **Step 6: Full build and package**

Run: `bash ~/petlee-winmvn.sh clean test` — expected `BUILD SUCCESS`.
Run: `bash ~/petlee-winbuild.sh package` — expected `BUILD SUCCESS`.
Run: `unzip -l target/pet-lee-1.0.war | grep "WEB-INF/lib"` — expected no entries: the WAR bundles no platform library.

- [ ] **Step 7: Deploy and walk the four scenarios by hand**

This is the only verification of end-to-end behaviour left, and it cannot be skipped. On the Windows side, deploy `target/pet-lee-1.0.war` to Payara 6, then:

1. **Guest browses.** Open `/`, see the gallery, filter by category, by size, by gender. Open a pet — the contact panel must not appear.
2. **User posts.** Register, log in, add a pet with a photo, see it in the gallery and in the profile dashboard.
3. **User inquires.** Logged in, open another user's pet — the owner's name, phone and email must appear.
4. **Admin deletes.** Log in as the seeded admin, open the admin panel, delete a listing, confirm it leaves the gallery.

Also check `curl -b cookies.txt http://localhost:8080/pet-lee/api/pets` returns JSON after a browser login — this is what proves the REST tier is real and not decorative.

- [ ] **Step 8: Commit and open the pull request**

```bash
git add -A
git commit -m "docs: record the slimming decisions and rewrite the README

ADR-006 supersedes ADR-001. ADR-002 gains the two contract deviations from
the one-photo change. ADR-003's three-dependency claim is true again.

README.md described a React and Node application that was never built."
gh pr create --base master --title "Slim the project to its assignment size" --body "$(cat <<'EOF'
Cuts the project from 11,849 functional lines to roughly 2,800 by removing
abstraction, not features. The only behaviour change is one photo per pet
instead of a gallery.

The large ones: the JSF tier no longer calls its own REST API over HTTP
(ADR-006 supersedes ADR-001), eleven DTO classes become eight records,
AbstractRepository's reflective identifier lookup is gone, and the 5,163-line
test suite becomes four focused unit tests.

Spec: docs/superpowers/specs/2026-09-11-slimming-design.md
Plan: docs/superpowers/plans/2026-09-11-slim-to-assignment-size.md

Verified: mvn clean test green, WAR builds with an empty WEB-INF/lib, and all
four specification section 10 scenarios walked by hand on Payara 6.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Progress tracking

| Task | Expected TOTAL after |
|---|---:|
| — baseline | 11,849 |
| 1 — tests and pom | ~6,520 |
| 2 — loopback removed | ~5,900 |
| 3 — one photo | ~5,400 |
| 4 — repositories | ~5,150 |
| 5 — entity/record boundary | ~4,500 |
| 6 — services | ~4,150 |
| 7 — security and errors | ~3,950 |
| 8 — entities | ~3,800 |
| 9 — views and CSS | ~2,950 |
| 10 — four tests | ~3,300 |
| 11 — docs | ~2,800 |

These are estimates for orientation, not gates. The only hard gate is Task 11 Step 5: the
final total must land between 2,400 and 3,000.
