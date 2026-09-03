# T-07 · CategoryRepository

| Field | Value |
|---|---|
| **Phase** | 1 — Data access |
| **Depends on** | T-05 |
| **Blocks** | T-14, T-34 |
| **Estimate** | 1.5h |

## Goal
Provide persistence operations for the fixed category vocabulary that every pet listing must
reference (specification §5).

## Scope — files to create / modify
- `src/main/java/com/petlee/repository/CategoryRepository.java`

## Requirements
1. `CategoryRepository extends AbstractRepository<Category, Integer>`.
   Note the id type is `Integer`, matching `Category.categoryId`.
2. `List<Category> findAllOrderedByName()` — returns every category sorted by `categoryName`
   ascending. `GET /api/categories` renders a filter dropdown; a stable alphabetical order keeps
   the UI from reshuffling between requests.
3. `Optional<Category> findByName(String name)` — case-insensitive, used by T-34 to reject
   duplicate category creation before hitting the UNIQUE constraint.
4. `boolean existsByName(String name)` — `COUNT` based.
5. `long countPetsInCategory(Integer categoryId)` — used by T-34 to return a clear 409 when an
   admin tries to delete a category that still holds listings, instead of leaking the raw
   foreign-key violation from T-03 requirement 5.
6. Named parameters only; no string concatenation.

## Out of scope
- No create/update/delete beyond what `AbstractRepository` already provides — the admin write
  paths are wired up in T-34.
- No DTO conversion (T-11).
- No seeding; categories come from `seed.sql` (T-03).

## Acceptance criteria
1. `findAllOrderedByName()` on the seeded database returns 6 categories, alphabetically ordered,
   `Birds` first.
2. `findByName("dogs")` finds the `Dogs` row.
3. `existsByName("Unicorns")` returns `false`.
4. `countPetsInCategory` returns the exact number of pets referencing that category, including
   pets whose status is `REMOVED`.
5. Attempting to persist a `Category` with a duplicate name fails on the database UNIQUE
   constraint.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All five acceptance criteria demonstrated.
- [ ] Javadoc on every method.
- [ ] No method returns `null`.
