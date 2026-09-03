# T-08 · PetRepository with dynamic filtering

| Field | Value |
|---|---|
| **Phase** | 1 — Data access |
| **Depends on** | T-05 |
| **Blocks** | T-15 |
| **Estimate** | 6h |

## Goal
Serve the gallery and detail queries the whole product is built around: filtered, ordered listing
and a single-fetch detail load (specification §9.2).

## Scope — files to create / modify
- `src/main/java/com/petlee/repository/PetRepository.java`
- `src/main/java/com/petlee/repository/PetFilter.java` (immutable filter criteria holder)

## Requirements
1. `PetRepository extends AbstractRepository<Pet, Long>`.
2. `PetFilter` carries three optional criteria matching the contract's
   `?categoryId=1&size=SMALL&gender=MALE`: `Integer categoryId`, `Pet.PetSize size`,
   `Pet.PetGender gender`. Any subset may be null. Give it a builder or a static factory; it must
   be immutable so it can be safely passed across layers.
3. `List<Pet> findByFilter(PetFilter filter)` built with the **JPA Criteria API**, not string
   concatenation. Each non-null criterion contributes one `Predicate`; they are combined with
   `AND`. A filter with all three fields null returns the full available catalogue.
4. The gallery must only show adoptable animals: `findByFilter` always adds
   `status = AVAILABLE`. `ADOPTED` and `REMOVED` listings never appear in the public gallery.
   T-34's admin listing is the one place that sees all statuses, via a separate method.
5. Ordering is `created_at DESC` — specification §11 requires newest-to-oldest. This is not
   optional or caller-configurable.
6. `findByFilter` **must not trigger N+1 queries**. The gallery needs each pet's category name and
   main image. Use `LEFT JOIN FETCH` on `category` and `images`, or issue one follow-up batch query
   for main images. Rendering 50 pets must not produce 100+ SQL statements.
7. `Optional<Pet> findDetailById(Long id)` — loads one pet with `category`, `owner` and **all**
   `images` fetched eagerly in a single query, because `PetDetailDTO` needs every one of them and
   the `EntityManager` is already closed by the time the mapper runs. A `LazyInitializationException`
   here is the single most likely bug in this task.
8. `List<Pet> findByOwnerId(Long ownerId)` — powers the T-32 profile dashboard. Returns **all**
   statuses (an owner must see their own adopted and removed listings), ordered `created_at DESC`.
9. `List<Pet> findAllForAdmin(PetFilter filter)` — same as `findByFilter` but with no status
   restriction. Used only by T-34.
10. Named parameters / Criteria parameter expressions only. No concatenated user input.

## Out of scope
- No authorisation checks. The repository answers questions; T-15 decides who may ask.
- No DTO conversion (T-11).
- No pagination. The specification does not ask for it; adding it would change the frozen
  contract's response shape.

## Acceptance criteria
1. `findByFilter(empty)` returns only `AVAILABLE` pets, newest first — verified by inserting three
   pets with distinct `created_at` values plus one `REMOVED` pet and asserting order and exclusion.
2. Filtering by `categoryId` alone, `size` alone, `gender` alone, and all three together each
   return the correct subset (four separate assertions).
3. With SQL logging enabled on the server, loading a 20-pet gallery issues a **bounded** number of
   statements (≤ 3), not one per pet. Paste the statement count in the PR.
4. `findDetailById` returns a `Pet` whose `getCategory().getCategoryName()`,
   `getOwner().getFullName()` and `getImages()` are all readable **after** the `EntityManager` is
   closed, with no `LazyInitializationException`.
5. `findByOwnerId` returns that owner's `REMOVED` pets too.
6. `findDetailById` on an unknown id returns `Optional.empty()`.
7. A filter value that does not match any enum constant is rejected before reaching SQL — no
   database error surfaces to the caller.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All seven acceptance criteria demonstrated, criterion 3 with pasted SQL log output.
- [ ] `PetFilter` has no setters.
- [ ] Javadoc on every method states exactly which statuses it returns — this is the detail most
      likely to be misremembered by later tasks.
