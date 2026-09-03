# T-34 · Admin REST endpoints

| Field | Value |
|---|---|
| **Phase** | 8 — Administration |
| **Depends on** | T-14, T-15, T-18, T-19 |
| **Blocks** | T-35 |
| **Estimate** | 5h |

## Goal
Satisfy specification §3 — *"The system shall provide administration management capabilities for
system administrators"* — and §2's supervisory role: *"maintaining platform order, managing
categories, and preventing offensive or duplicate listings."*

> **Contract note.** `api-contract.md` defines no admin endpoints. This task **extends** it; no
> existing endpoint changes, so no caller breaks. Record every addition in ADR-002 and update
> `api-contract.md` in T-41.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/AdminResource.java`
- `src/main/java/com/petlee/rest/CategoryResource.java` (modify — add write methods)
- `src/main/java/com/petlee/service/CategoryService.java` (modify — add write methods)
- `src/main/java/com/petlee/service/PetService.java` (modify — add admin listing and status change)

## Requirements
1. New endpoints, all `@AdminOnly` (T-18):

   | Method | Path | Success | Notes |
   |---|---|---|---|
   | `GET` | `/api/admin/pets` | 200 `List<PetDTO>` | all statuses, same optional filters as `/api/pets` |
   | `PUT` | `/api/admin/pets/{id}/status` | 200 `PetDTO` | body `{"status":"REMOVED"}` |
   | `POST` | `/api/categories` | 200 `CategoryDTO` | body `{"name":"Birds"}` |
   | `DELETE` | `/api/categories/{id}` | 204 | |

2. Admin **delete** of a pet reuses the existing `DELETE /api/pets/{id}` — T-15 already permits
   owner-or-admin. Do not add a second deletion path; two ways to delete means two sets of rules to
   keep in step.
3. `PUT /api/admin/pets/{id}/status` sets `REMOVED` (or back to `AVAILABLE`), giving an admin a
   reversible way to hide a policy-violating listing. Specification §2 asks admins to prevent
   offensive listings; hard deletion is the irreversible option, this is the recoverable one.
4. `POST /api/categories` rejects a duplicate name with **409** code `CATEGORY_EXISTS`, checked via
   `existsByName` (T-07) **and** guarded by the UNIQUE constraint for the race.
5. `DELETE /api/categories/{id}` returns **409** code `CATEGORY_IN_USE` when
   `countPetsInCategory > 0`. The FK is `ON DELETE RESTRICT` (T-03), so without this check the user
   would see a raw 500. Specification §5 requires every pet to have a category, and this is what
   protects that invariant.
6. `GET /api/admin/pets` returns every status, using `findAllForAdmin` (T-08). This is the only
   endpoint that exposes `REMOVED` listings to someone who does not own them.
7. Every method carries `@AdminOnly`. A `USER` receives **403** code `NOT_ADMIN`; an anonymous
   caller **401**. Both are enforced by the filter, not by hand-written checks.
8. No endpoint here exposes user records. Specification §2 gives admins authority over listings and
   categories — not over browsing personal data — and there is no requirement for user management.

## Out of scope
- No user listing, banning, or role editing.
- No admin UI (T-35).
- No changes to any existing contract endpoint.

## Acceptance criteria
1. `GET /api/admin/pets` as an admin returns pets of every status; as a `USER` returns 403; with no
   session returns 401.
2. `PUT /api/admin/pets/{id}/status` with `REMOVED` hides the pet from `GET /api/pets` while it
   remains in `GET /api/admin/pets`.
3. Setting it back to `AVAILABLE` makes it public again.
4. `POST /api/categories` creates a category that then appears in `GET /api/categories`.
5. Posting a duplicate name returns 409 `CATEGORY_EXISTS`.
6. `DELETE /api/categories/{id}` for an unused category returns 204.
7. `DELETE` for a category holding pets returns **409 `CATEGORY_IN_USE`**, not 500.
8. An admin deleting another user's pet via `DELETE /api/pets/{id}` returns 204 (existing endpoint,
   unchanged).
9. Every new endpoint returns 401 with no session and 403 for a `USER`.
10. `api-contract.md` and ADR-002 both record the four additions.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated with `curl -i` output.
- [ ] No existing endpoint's behaviour changed — confirm by re-running T-39's suite.
- [ ] Every new endpoint documented in `api-contract.md` in the existing style.
