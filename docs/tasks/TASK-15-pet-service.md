# T-15 · PetService — listings, ownership rules, and concurrency

| Field | Value |
|---|---|
| **Phase** | 4 — Business logic |
| **Depends on** | T-08, T-11, T-12, T-14 |
| **Blocks** | T-22, T-34 |
| **Estimate** | 8h |

## Goal
Implement every business rule in specification §5 and the authorisation logic in §2 — this is the
class where the product's rules actually live.

## Scope — files to create / modify
- `src/main/java/com/petlee/service/PetService.java`

## Requirements
1. `List<PetDTO> findGallery(PetFilter filter)` — public catalogue. `AVAILABLE` only, newest
   first (enforced by T-08). Open to everyone, including guests.
2. `PetDetailDTO findDetail(Long id, Long callerUserId)`:
   - `NotFoundException` (404) if the pet does not exist.
   - Calls `PetMapper.toDetailDto(pet, callerUserId != null)`. A `null` caller is a guest, so
     owner contact fields come back `null` — specification §6: *"Unregistered clients can view
     details of pets offered for adoption without seeing private contact information."*
   - **The contact decision is made here and nowhere else.** No REST resource and no managed bean
     may re-derive it.
3. `PetDTO create(PetForm form, Long ownerUserId)`:
   - `ownerUserId` is non-null; a null value is a programming error, not a 401 (T-18 already
     rejected anonymous callers). Fail fast with `IllegalStateException`.
   - Validate: `name` non-blank ≤ 100; `age` null or 0–50; `size` and `gender` non-null and valid
     enum constants; `shortDesc` ≤ 255; `categoryId` non-null. Violations → `ValidationException` (400).
   - Resolve the category through `CategoryService.requireById` → 404 on an unknown id
     (specification §5, "must belong to a predefined category").
   - Set `owner` from `ownerUserId`, `status = AVAILABLE`. Specification §5: *"Each pet listing has
     one, and only one, owner."* The owner is taken **from the session**, never from the request
     body — `PetForm` has no owner field and must not gain one.
   - Return the created `PetDTO`.
4. `PetDTO update(Long petId, PetForm form, Long callerUserId)`:
   - 404 if the pet does not exist.
   - **`ForbiddenException` (403) unless the caller is the owner.** `api-contract.md`: *"403 if not
     the owner"*. Note an admin may **not** edit someone else's listing — the contract grants admins
     delete rights only, and editing another person's advert is a different act from removing a
     policy-violating one.
   - `owner`, `createdAt` and `status` are not editable through this path. Silently ignore any
     attempt; do not throw.
   - An `OptimisticLockException` propagating from T-05 is caught and rethrown as
     `ConflictException` (**409**), code `STALE_PET`. This is specification §4 "Concurrency
     Control" — *"prevent situations where two users update the same pet listing simultaneously and
     overwrite data"* — made visible to the client.
5. `void delete(Long petId, Long callerUserId, boolean callerIsAdmin)`:
   - 404 if absent.
   - Allowed if the caller is the owner **or** an admin; otherwise `ForbiddenException` (403),
     matching *"403 if not owner and not admin"* and specification §5 *"Only the original poster of
     a pet listing can remove it (or an administrator)."*
   - Deletion is a **hard delete**; images cascade (T-03, T-09). The `REMOVED` status exists for
     admin soft-hiding in T-34 and is not used here.
6. `List<PetDTO> findByOwner(Long ownerUserId)` — the T-32 dashboard feed, all statuses.
7. `boolean isOwner(Long petId, Long userId)` — a small helper so the JSF tier can decide whether
   to render an Edit button, without duplicating the rule.
8. Every authorisation decision in this class compares against the **caller id passed in by the
   REST layer from the session**. This class must never read `HttpSession`, a `SecurityContext`, or
   any thread-local; that is what makes it unit-testable and keeps the tier boundary honest.
9. No transport imports.

## Out of scope
- No image upload (T-16) beyond reading already-stored images for mapping.
- No admin-wide listing view (T-34).
- No status transitions (marking a pet `ADOPTED`) — specification §3 does not require it. Do not
  invent it.

## Acceptance criteria
1. `findGallery` excludes `ADOPTED` and `REMOVED` pets and returns newest first.
2. `findDetail(id, null)` returns all three owner contact fields as `null`;
   `findDetail(id, someUserId)` returns them populated. **Both assertions are mandatory** — this is
   the system's core privacy rule.
3. `create` with an unknown `categoryId` throws `NotFoundException`, not a database error.
4. `create` sets the owner to the passed `ownerUserId` even if the caller attempts to inject a
   different owner through the JSON body.
5. `update` by a non-owner throws `ForbiddenException`; by an admin who is not the owner it **also**
   throws `ForbiddenException`.
6. Concurrency: load the same pet in two `EntityManager`s, update through the first, then the
   second — the second throws `ConflictException` with code `STALE_PET`. Write this as an explicit
   test; it is the only proof of specification §4's concurrency requirement.
7. `delete` by the owner succeeds; by an admin who is not the owner succeeds; by an unrelated user
   throws `ForbiddenException`.
8. After `delete`, `pet_image` rows for that pet are gone.
9. `findByOwner` includes the owner's `REMOVED` pets.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All nine acceptance criteria demonstrated, criteria 2, 5, 6 and 7 as automated tests.
- [ ] `grep -E "jakarta.(ws|servlet)" PetService.java` returns nothing.
- [ ] Every method's Javadoc names the exceptions it throws and quotes the specification or
      contract line that requires each rule.
