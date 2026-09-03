# T-14 · CategoryService

| Field | Value |
|---|---|
| **Phase** | 4 — Business logic |
| **Depends on** | T-07, T-11, T-12 |
| **Blocks** | T-21, T-34 |
| **Estimate** | 1.5h |

## Goal
Expose the category vocabulary to the REST layer and provide the lookup that pet creation depends
on (specification §5).

## Scope — files to create / modify
- `src/main/java/com/petlee/service/CategoryService.java`

## Requirements
1. `List<CategoryDTO> findAll()` — delegates to `findAllOrderedByName()` (T-07) and maps to DTOs.
   Backs `GET /api/categories`.
2. `Category requireById(Integer id)` — returns the **entity**, or throws `NotFoundException`
   (404) with code `CATEGORY_NOT_FOUND`. This is the one method that intentionally returns an
   entity, because T-15 must attach a managed `Category` to a `Pet`; it is package-visible or
   clearly documented as internal, and is never reachable from a REST resource.
3. Specification §5 requires *"Every posted pet must belong to a predefined category"*. This method
   is where that rule is enforced: T-15 calls it for every create and update, so an unknown
   `categoryId` yields 404 rather than a foreign-key 500.
4. `CategoryDTO findById(Integer id)` — the DTO-returning variant for REST use.
5. No transport imports.

## Out of scope
- No create/rename/delete — admin category management is T-34.
- No caching. The list is tiny and read rarely enough that a cache would only add a staleness bug.

## Acceptance criteria
1. `findAll()` on the seeded database returns 6 `CategoryDTO`s ordered alphabetically.
2. Each returned DTO has exactly the keys `id` and `name` when serialised.
3. `requireById(999)` throws `NotFoundException` with code `CATEGORY_NOT_FOUND`.
4. `requireById(<valid id>)` returns a `Category` usable as a `Pet.category` without a further
   database round trip.
5. No REST resource class can reach `requireById` — verified by its visibility modifier.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All five acceptance criteria demonstrated.
- [ ] Javadoc on `requireById` explains why it breaks the "DTOs only" rule.
