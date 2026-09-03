# Pet-Lee Implementation Backlog

42 tasks that take the repository from its current state — four JPA entities and an empty
`PasswordHasher` — to the complete system described in `pet_lee_system_specification.txt`.

## How to use this backlog

1. **Work in numeric order.** The numbering is a topological sort of the dependency graph: no task
   depends on a higher-numbered one. Where tasks in the same phase are independent (T-06, T-07,
   T-09) they may be done in any order or in parallel.
2. **A task is not started until its dependencies are Done.** "Done" means the Definition of Done
   checklist is fully ticked — not "the code is written".
3. **The acceptance criteria are the specification.** They are written to be checkable by someone
   who did not write the code. If a criterion cannot be demonstrated, the task is not done.
4. **`api-contract.md` is frozen.** Field names, HTTP status codes and paths are copied from it
   verbatim. A deviation requires a row in `docs/decisions/ADR-002-contract-deviations.md`, agreed
   before the code is written.
5. **Read `docs/decisions/ADR-001-loopback-rest.md` before touching anything in `com.petlee.web`.**
   It explains why the JSF tier calls the REST tier over HTTP, and the rule that enforces it.
6. **The dependency list is closed — read `docs/decisions/ADR-003-technology-constraint.md`.**
   Only the technologies in specification §7 (JSF, JPA, Jakarta REST) plus PostgreSQL are used.
   `pom.xml` has exactly three entries: `jakarta.jakartaee-api` (provided), the PostgreSQL driver
   (runtime), and JUnit (test). Everything else — CDI, JTA, JSON-B, connection pooling, multipart —
   comes from the Jakarta EE server. **Adding a dependency requires a new ADR.**

## Phases

| Phase | Tasks | Theme | Gate to pass before moving on |
|---|---|---|---|
| 0 | T-01 … T-04 | Foundation: build, JPA config, schema, entity fixes | WAR deploys to a Jakarta EE server carrying no bundled platform library |
| 1 | T-05 … T-09 | Data access: transactions and repositories | Repository tests green; no N+1 on the gallery query |
| 2 | T-10 | Password hashing | Digest round-trips; no plaintext reaches the database |
| 3 | T-11 | DTOs and mappers matching the frozen contract | JSON key sets diffed against `api-contract.md` |
| 4 | T-12 … T-16 | Business logic and authorisation rules | Every specification §5 rule has a passing test |
| 5 | T-17 … T-23 | REST tier, session security, error mapping | Every contract endpoint answers with the documented status |
| 6 | T-24 | Server communication layer (`ApiClient`) | An authenticated call from a managed bean succeeds |
| 7 | T-25 … T-33 | JSF presentation: five screens plus access control | Specification §12's screens all render and work |
| 8 | T-34, T-35 | Administration | Specification §10 scenario 4 completes |
| 9 | T-36 … T-40 | Verification | `mvn verify` green; all four §10 scenarios pass |
| 10 | T-41, T-42 | Handover: documentation and deployment | A stranger can deploy it from the runbook |

## Task index

| ID | Task | Phase | Depends on | Est. |
|---|---|---|---|---|
| T-01 | Build configuration and dependency set | 0 | — | 3h |
| T-02 | JPA persistence unit & server data source | 0 | T-01 | 3h |
| T-03 | PostgreSQL schema and seed data | 0 | T-01 | 3h |
| T-04 | Entity remediation and model finalisation | 0 | T-03 | 3h |
| T-05 | Persistence context & repository base | 1 | T-02, T-04 | 2h |
| T-06 | UserRepository | 1 | T-05 | 2h |
| T-07 | CategoryRepository | 1 | T-05 | 1.5h |
| T-08 | PetRepository with dynamic filtering | 1 | T-05 | 6h |
| T-09 | PetImageRepository | 1 | T-05 | 2h |
| T-10 | PasswordHasher implementation | 2 | T-01 | 3h |
| T-11 | DTOs and mappers | 3 | T-04 | 5h |
| T-12 | Domain exception hierarchy | 4 | T-01 | 1.5h |
| T-13 | UserService — registration and authentication | 4 | T-06, T-10, T-11, T-12 | 4h |
| T-14 | CategoryService | 4 | T-07, T-11, T-12 | 1.5h |
| T-15 | PetService — rules and concurrency | 4 | T-08, T-11, T-12, T-14 | 8h |
| T-16 | ImageStorageService | 4 | T-09, T-11, T-12, T-15 | 5h |
| T-17 | Jakarta REST bootstrap and `web.xml` | 5 | T-01, T-02 | 3h |
| T-18 | Session authentication filter | 5 | T-13, T-17 | 5h |
| T-19 | Exception mappers | 5 | T-12, T-17 | 3h |
| T-20 | AuthResource and UserResource | 5 | T-13, T-18, T-19 | 4h |
| T-21 | CategoryResource | 5 | T-14, T-19 | 1h |
| T-22 | PetResource | 5 | T-15, T-18, T-19 | 5h |
| T-23 | Image upload (Servlet `Part`) & serving | 5 | T-16, T-18, T-19 | 4h |
| T-24 | ApiClient — server communication layer | 6 | T-11, T-20…T-23 | 7h |
| T-25 | Facelets template and navigation shell | 7 | T-17 | 5h |
| T-26 | UserManagedBean | 7 | T-24, T-25 | 4h |
| T-27 | Login and registration pages | 7 | T-25, T-26 | 3h |
| T-28 | PetManagedBean — gallery and filters | 7 | T-21, T-22, T-24 | 4h |
| T-29 | Home page — gallery and filter panel | 7 | T-25, T-28 | 4h |
| T-30 | Pet details page with gated contact info | 7 | T-25, T-26, T-28 | 4h |
| T-31 | Add-pet form with image upload | 7 | T-23, T-24, T-26 | 6h |
| T-32 | User profile / personal dashboard | 7 | T-24, T-26, T-31 | 4h |
| T-33 | Page access control and error pages | 7 | T-25, T-26 | 4h |
| T-34 | Admin REST endpoints | 8 | T-14, T-15, T-18, T-19 | 5h |
| T-35 | Admin panel page | 8 | T-24, T-26, T-33, T-34 | 5h |
| T-36 | Test infrastructure | 9 | T-01, T-02, T-03 | 5h |
| T-37 | Unit tests | 9 | T-36 | 8h |
| T-38 | Repository and persistence integration tests | 9 | T-36 | 6h |
| T-39 | REST API integration tests | 9 | T-20…T-23, T-34, T-36 | 8h |
| T-40 | Scripted end-to-end walkthrough | 9 | T-27…T-33, T-35, T-39 | 5h |
| T-41 | Documentation refresh | 10 | T-40 | 3h |
| T-42 | Deployment runbook (Payara 6) | 10 | T-40 | 4h |

**Total: ~182 hours.**

## Traceability — every requirement maps to a task

### Functional requirements (specification §3)
| Requirement | Tasks |
|---|---|
| User registration | T-13, T-20, T-26, T-27 |
| Authentication / login | T-10, T-13, T-18, T-20, T-26, T-27 |
| View pets in the repository | T-08, T-15, T-22, T-28, T-29 |
| Filter by category | T-08, T-21, T-22, T-28, T-29 |
| View pet details | T-08, T-15, T-22, T-30 |
| Add and remove a pet | T-15, T-22, T-31, T-32 |
| Administration capabilities | T-34, T-35 |

### Non-functional requirements (specification §4)
| Requirement | Tasks |
|---|---|
| Data encryption (password hashing) | T-10, T-13 |
| Endpoint security on modifying requests | T-18, T-20, T-22, T-23, T-34 |
| Connection pooling | T-02 (server-managed JNDI pool) |
| Concurrency control | T-04 (`@Version`), T-15, T-22, T-38 |
| Complete layer separation (MVC, 3-tier) | ADR-001, T-11, T-24 |
| Portability (Windows and Linux) | T-40, T-42 |

### Business rules (specification §5)
| Rule | Tasks |
|---|---|
| Only registered users can post | T-15, T-18, T-22 |
| Only the poster or an admin can remove | T-15, T-22, T-34 |
| Every pet belongs to a predefined category | T-03 (FK), T-14, T-15 |
| Each listing has exactly one owner | T-03, T-15 |
| Unregistered users cannot post or remove | T-18, T-22, T-33 |
| Guests cannot see contact details (§6) | T-11, T-15, T-22, T-30 |

### Database schema (specification §11)
| Table | Tasks |
|---|---|
| User | T-03, T-04, T-06 |
| Pet | T-03, T-04, T-08 |
| Category | T-03, T-04, T-07 |
| PetImage | T-03, T-04, T-09, T-16 |

### Screens (specification §12)
| Screen | Tasks |
|---|---|
| Home page (catalog/gallery) | T-28, T-29 |
| Pet details page | T-30 |
| Registration / login page | T-27 |
| Add pet listing form | T-31 |
| User profile / dashboard | T-32 |
| Admin screens (§8) | T-35 |

### Use-case scenarios (specification §10)
| Scenario | Verified by |
|---|---|
| 1 — Guest browses pets | T-29, T-40 |
| 2 — Registered user posts a pet | T-31, T-40 |
| 3 — Registered user inquires about adoption | T-30, T-40 |
| 4 — Administrator deletes a listing | T-35, T-40 |

### Contract endpoint coverage
Every endpoint in `api-contract.md` is implemented by exactly one Phase-5 task and tested by T-39:

| Endpoint | Implemented | Tested |
|---|---|---|
| `POST /api/users/register` | T-20 | T-39 |
| `POST /api/auth/login` | T-20 | T-39 |
| `POST /api/auth/logout` | T-20 | T-39 |
| `GET /api/categories` | T-21 | T-39 |
| `GET /api/pets` | T-22 | T-39 |
| `GET /api/pets/{id}` | T-22 | T-39 |
| `POST /api/pets` | T-22 | T-39 |
| `PUT /api/pets/{id}` | T-22 | T-39 |
| `DELETE /api/pets/{id}` | T-22 | T-39 |
| `POST /api/pets/{id}/images` | T-23 | T-39 |
| *admin endpoints (contract extension)* | T-34 | T-39 |

## Standing rules

- **No JPA entity crosses the REST boundary.** DTOs only (T-11).
- **No `com.petlee.service` or `com.petlee.repository` import inside `com.petlee.web`.** ADR-001.
- **Every POST/PUT/DELETE resource method carries `@Secured` or `@AdminOnly`.** No exceptions (T-18).
- **Authorisation decisions live in the service layer**, using a caller id passed in — never read
  from a session or a thread-local inside a service (T-15).
- **No `printStackTrace()`, no stack trace in any HTTP response** (T-19).
- **UI checks are never the security boundary.** The REST tier is (T-26, T-33).
