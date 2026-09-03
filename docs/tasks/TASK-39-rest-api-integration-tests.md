# T-39 · REST API integration tests

| Field | Value |
|---|---|
| **Phase** | 9 — Verification |
| **Depends on** | T-20…T-23, T-34, T-36 |
| **Blocks** | none |
| **Estimate** | 8h |

## Goal
Verify the deployed API against `api-contract.md` line by line — status codes, JSON keys, and the
authorisation matrix — using the Jakarta REST Client API.

## Scope — files to create / modify
- `src/test/java/com/petlee/it/ApiTestClient.java` (shared client helper)
- `src/test/java/com/petlee/it/AuthApiIT.java`
- `src/test/java/com/petlee/it/PetApiIT.java`
- `src/test/java/com/petlee/it/CategoryApiIT.java`
- `src/test/java/com/petlee/it/ImageApiIT.java`
- `src/test/java/com/petlee/it/AdminApiIT.java`
- `src/test/java/com/petlee/it/ContractShapeIT.java`

## Requirements
1. Tests use the **Jakarta REST Client API** (`jakarta.ws.rs.client.ClientBuilder`) — specification
   §7's own technology, already on the compile path from T-01. ADR-003 forbids REST Assured.
   Base URL from a system property (`-Dpetlee.baseUrl=…`, default
   `http://localhost:8080/pet-lee`). Run by failsafe in `mvn verify`.
2. `ApiTestClient` wraps the client and **carries the session cookie between calls**: capture
   `Set-Cookie` from the login response and replay it on subsequent requests, exactly as a browser
   and as T-24 do. Without this every authenticated test returns 401.
3. Responses are read as `jakarta.json.JsonObject` (JSON-P, part of the platform) so key sets can be
   asserted directly, rather than deserialising into DTOs — a DTO would quietly ignore an extra
   field, and detecting extra fields is the point of requirement 5.
4. **Every endpoint in `api-contract.md` gets at least one success test and one failure test:**

   | Endpoint | Success | Failures to assert |
   |---|---|---|
   | `POST /api/users/register` | 200 + `UserDTO` | 409 duplicate, 400 invalid |
   | `POST /api/auth/login` | 200 + session cookie | 401 bad credentials |
   | `POST /api/auth/logout` | 204 empty body | 401 no session |
   | `GET /api/categories` | 200 array | — |
   | `GET /api/pets` | 200 array, filters | 400 invalid enum |
   | `GET /api/pets/{id}` | 200 both auth states | 404 unknown |
   | `POST /api/pets` | 200 | 401 anonymous, 400 invalid, 404 bad category |
   | `PUT /api/pets/{id}` | 200 | 401, 403 non-owner, 409 stale |
   | `DELETE /api/pets/{id}` | 204 owner, 204 admin | 401, 403 stranger |
   | `POST /api/pets/{id}/images` | 200 | 401, 403 non-owner, 400 bad type/size |
   | admin endpoints (T-34) | 2xx as admin | 401 anonymous, 403 as USER |

5. `ContractShapeIT` asserts **exact JSON key sets** for `UserDTO`, `PetDTO`, `PetDetailDTO`,
   `CategoryDTO` and `PetImageDTO` against the contract's example bodies. It must fail both when a
   key is missing and when an **extra** key appears — an accidentally exposed field is how a
   password leaks.
6. The single most important test in this task: `GET /api/pets/{id}` **without** a session returns
   `ownerFullName`, `ownerEmail` and `ownerPhone` as JSON null, and **with** a session returns them
   populated. Both assertions, in the same test class, adjacent. Specification §6.
7. Multipart upload tests build the `multipart/form-data` body by hand — boundary, part headers,
   payload — and POST it as a byte array with the matching `Content-Type`. About thirty lines in
   `ApiTestClient`, and it keeps the dependency list closed (ADR-003).
8. A test asserting no response body from any endpoint contains `password` or `password_hash`.
9. Optimistic locking over HTTP: `GET` a pet twice, `PUT` the first, `PUT` the second, assert **409**.
10. Session fixation: capture `JSESSIONID` before and after login and assert they differ (T-18).
11. Error bodies are JSON with `message` and `code` — never HTML, never a stack trace. Assert
    `Content-Type: application/json` on a 403, a 404 and a 409.
12. Tests create their own fixtures through the API and clean up after themselves. The suite must be
    runnable repeatedly against the same deployment without accumulating junk or failing the second
    time.

## Out of scope
- No UI/browser testing (T-40 covers the screens manually).
- No load or performance testing.
- No HTTP client library beyond the platform's (ADR-003).

## Acceptance criteria
1. `mvn verify -Dpetlee.baseUrl=…` runs the whole suite green against a deployed WAR.
2. Every row of the requirement-4 matrix has passing tests, success and failure.
3. `ContractShapeIT` fails when a field is added to `UserDTO` — perform the mutation and revert it.
4. The contact-masking test fails if `includeContact` is hard-coded to `true` — likewise mutate and
   revert.
5. The 409 concurrency test passes reliably over ten runs.
6. Running the suite twice in a row against the same deployment passes both times.
7. No response body in any test contains password material.
8. Error responses on 403/404/409 are `application/json`.
9. `grep -rE "io.restassured|rest_assured" src/test/` returns nothing.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated; 3 and 4 are mutation checks, performed then
      reverted.
- [ ] A traceability comment on each test class names the `api-contract.md` section it covers.
- [ ] The suite leaves the database in its pre-run state.
