# T-11 · DTOs and mappers matching the frozen contract

| Field | Value |
|---|---|
| **Phase** | 3 — Contract surface |
| **Depends on** | T-04 |
| **Blocks** | T-13, T-14, T-15, T-16, T-20…T-24 |
| **Estimate** | 5h |

## Goal
Create the exact wire types `api-contract.md` specifies, so the REST tier never serialises a JPA
entity and the JSON shape can never drift from the contract.

## Scope — files to create / modify
- `src/main/java/com/petlee/dto/UserDTO.java`
- `src/main/java/com/petlee/dto/CategoryDTO.java`
- `src/main/java/com/petlee/dto/PetDTO.java`
- `src/main/java/com/petlee/dto/PetDetailDTO.java`
- `src/main/java/com/petlee/dto/PetImageDTO.java`
- `src/main/java/com/petlee/dto/RegisterForm.java`
- `src/main/java/com/petlee/dto/LoginForm.java`
- `src/main/java/com/petlee/dto/PetForm.java`
- `src/main/java/com/petlee/dto/ErrorDTO.java`
- `src/main/java/com/petlee/mapper/UserMapper.java`
- `src/main/java/com/petlee/mapper/CategoryMapper.java`
- `src/main/java/com/petlee/mapper/PetMapper.java`

## Requirements
1. **Field names are copied character-for-character from `api-contract.md`.** JSON-B derives
   property names from the accessors, so a rename silently breaks the contract. The exact sets:
   - `UserDTO`: `id`, `username`, `fullName`, `email`, `phone`, `role`.
     Note `phone` (not `phoneNumber`) and `username` (not `userName`) — both differ from the
     entity field names, which is precisely why a DTO exists.
   - `CategoryDTO`: `id`, `name`.
   - `PetDTO`: `id`, `name`, `shortDesc`, `age`, `size`, `gender`, `status`, `categoryName`,
     `mainImageUrl`.
   - `PetDetailDTO`: `id`, `name`, `breed`, `age`, `size`, `gender`, `shortDesc`, `longDesc`,
     `status`, `categoryName`, `images` (`List<PetImageDTO>`), `ownerFullName`, `ownerEmail`,
     `ownerPhone`.
   - `PetImageDTO`: `id`, `imageUrl`, `isMain`.
   - `RegisterForm`: `username`, `password`, `fullName`, `email`, `phone`, `region`.
   - `LoginForm`: `username`, `password`.
   - `PetForm`: `name`, `breed`, `age`, `size`, `gender`, `shortDesc`, `longDesc`, `categoryId`.
2. **`UserDTO` has no password field and no `region` field.** The contract's response body lists
   neither. A password must be structurally impossible to leak, not merely omitted by convention.
3. Enums (`size`, `gender`, `status`, `role`) are serialised as the exact uppercase strings in the
   contract's ENUM VALUES section. Declare the DTO fields as the enum types and let JSON-B emit
   `name()`, or use `String` — either is acceptable, but the emitted text must match exactly.
4. `PetImageDTO.isMain` must serialise as JSON key `isMain`. A `boolean` field with a `getIsMain()`
   accessor yields `isMain`; a getter named `isMain()` yields `main`. **Verify the emitted JSON;
   do not assume.** This is the most common JSON-B naming trap in this codebase.
5. All DTOs are plain JavaBeans: public no-arg constructor, private fields, public
   getters/setters. JSON-B requires the no-arg constructor for deserialisation.
6. `ErrorDTO` carries `message` (safe, user-facing) and `code` (a short machine token such as
   `USERNAME_TAKEN`). It must never carry a stack trace or a SQL fragment; T-19 is the only
   producer.
7. Mappers are stateless classes with static methods:
   - `UserMapper.toDto(User)`.
   - `CategoryMapper.toDto(Category)` and `toDtoList(List<Category>)`.
   - `PetMapper.toDto(Pet)` → `PetDTO`, taking the main image URL from the pet's loaded images.
   - `PetMapper.toDetailDto(Pet pet, boolean includeContact)` → `PetDetailDTO`.
8. **`toDetailDto`'s `includeContact` flag is a business-critical control.** When `false`,
   `ownerFullName`, `ownerEmail` and `ownerPhone` are `null` — `api-contract.md` states *"Owner
   contact fields are filled ONLY if the caller is logged in; otherwise null"*, and specification
   §6 requires that unregistered clients cannot see private contact information. The parameter is
   mandatory and has no default, so no caller can forget to decide.
9. Mapping is one-directional: entity → DTO. Form → entity conversion happens in the services
   (T-13, T-15), which have the repository access needed to resolve `categoryId` into a `Category`.
10. Mappers must not open an `EntityManager` or trigger lazy loading. They operate on
    already-fetched graphs supplied by T-08. If a mapper needs data that was not fetched, the fix
    belongs in the repository query, not here.

## Out of scope
- No validation annotations or logic (T-13, T-15).
- No JAX-RS annotations on DTOs — they are transport-agnostic and are also consumed by T-24.
- No `equals`/`hashCode` needed beyond what tests require.

## Acceptance criteria
1. Serialising a fully populated `PetDetailDTO` with JSON-B produces JSON whose key set is
   **identical** to the example body in `api-contract.md` — compare key-by-key, including `isMain`.
2. `toDetailDto(pet, false)` yields null for all three owner fields; `toDetailDto(pet, true)`
   yields all three populated.
3. Serialising a `UserDTO` produces exactly six keys; neither `password` nor `region` appears.
4. `PetDTO.size` for a `MEDIUM` pet serialises as the string `"MEDIUM"`.
5. Deserialising the contract's exact `PetForm` example JSON populates every field.
6. `PetMapper.toDto` on a pet with no images sets `mainImageUrl` to `null` without throwing.
7. `PetMapper.toDto` on a pet with three images picks the one flagged main.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All seven acceptance criteria demonstrated, criterion 1 with the two JSON bodies side by side.
- [ ] A reviewer has diffed every DTO field list against `api-contract.md` and signed off.
- [ ] No JPA import (`jakarta.persistence.*`) appears in the `dto` package.
