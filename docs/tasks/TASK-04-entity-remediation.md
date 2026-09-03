# T-04 · Entity remediation and model finalisation

| Field | Value |
|---|---|
| **Phase** | 0 — Foundation |
| **Depends on** | T-03 |
| **Blocks** | T-05…T-11 (everything that touches the model) |
| **Estimate** | 3h |

## Goal
Fix the four defects in the committed entities and align them with `api-contract.md` and the
schema, before any code is written on top of them.

## Scope — files to create / modify
- `src/main/java/com/petlee/model/User.java` (modify)
- `src/main/java/com/petlee/model/Pet.java` (modify)
- `src/main/java/com/petlee/model/Category.java` (modify)
- `src/main/java/com/petlee/model/PetImage.java` (modify)

## Requirements
1. **`User.region`** — add `@Column(name = "region", length = 100) private String region;` with
   accessors. `POST /api/users/register` in the frozen contract sends `"region"`; today it has
   nowhere to land. See ADR-002 #1. It is **not** exposed in `UserDTO`.
2. **`User.password` → `password_hash`** — keep the Java field name `password` but change the
   mapping to `@Column(name = "password_hash", nullable = false, length = 255)`. The field holds a
   digest; the column name must not claim otherwise. ADR-002 #7.
3. **`Pet.getPetSize()` / `setPetSize()` → `getSize()` / `setSize()`** — JSON-B and JSF EL both
   derive the property name from the accessor, so the current names would serialise the contract's
   `"size"` field as `"petSize"`. ADR-002 #2.
4. **`Pet.getVersion()` returns `Long`, not `long`** — the field is `Long` and is `null` on any
   entity that has not yet been persisted, so unboxing throws `NullPointerException`. ADR-002 #3.
   Provide no public setter: the persistence provider owns this field.
5. **`Pet.createdAt` → `updatable = false`** — specification §11 orders the gallery by `created_at`;
   an edit must not reorder listings. ADR-002 #4.
6. Add an explicit `public` no-arg constructor to `User`, `Pet` and `PetImage`. `Category` already
   has one. JPA requires it, and adding a second constructor later would silently remove the
   implicit default.
7. Implement `equals` and `hashCode` on all four entities **on the identifier only**, null-safe,
   returning `false` when either id is `null`. Collection-based code (T-08 fetch joins, T-16 image
   lists) misbehaves without them.
8. Add `toString()` to each entity printing id and one human-readable field.
   **`User.toString()` must never include `password`.**
9. `Pet.status` gets `nullable = false` and the `@PrePersist` default already present stays.
   `PetImage.isMain` gets a Java-side default of `Boolean.FALSE` so the `NOT NULL` column is never
   violated by a caller that forgot to set it.
10. Add `length` attributes to every `String` column so they match `schema.sql` exactly
    (`Pet.petName` 100, `Pet.breed` 100, `Category.categoryName` 50). A mismatch is caught by
    T-38's persistence tests, which run against the real schema.

## Out of scope
- Do **not** add Bean Validation annotations (`@NotNull`, `@Size`). Input validation belongs to
  the service layer (T-13, T-15) so it can produce contract-shaped error responses.
- Do not add DTO conversion methods to entities — that is T-11's mapper classes.
- No repository or service code.

## Acceptance criteria
1. `mvn clean package` compiles; a project-wide search for `getPetSize` returns zero hits.
2. The WAR deploys against the T-03 schema and a round-trip persist/load of every entity succeeds,
   proving requirements 1, 2, 5 and 10 together.
3. `new Pet().getVersion()` returns `null` instead of throwing.
4. Two `Pet` instances with the same non-null `petId` are `equals`; two with `null` ids are not.
5. `new User(...).toString()` output contains no password or digest material.
6. Reflection over `Pet` shows a readable property literally named `size`
   (`Introspector.getBeanInfo(Pet.class)`).

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All six acceptance criteria demonstrated.
- [ ] Every change in this task is cross-referenced to its ADR-002 row in the commit message.
- [ ] No behaviour added beyond the listed fixes — this is a remediation task, not a feature task.
