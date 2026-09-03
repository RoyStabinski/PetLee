# T-09 · PetImageRepository

| Field | Value |
|---|---|
| **Phase** | 1 — Data access |
| **Depends on** | T-05 |
| **Blocks** | T-16 |
| **Estimate** | 2h |

## Goal
Persist and retrieve pet photographs, and maintain the "exactly one main image" rule at the data
layer (specification §11).

## Scope — files to create / modify
- `src/main/java/com/petlee/repository/PetImageRepository.java`

## Requirements
1. `PetImageRepository extends AbstractRepository<PetImage, Integer>`.
2. `List<PetImage> findByPetId(Long petId)` — all images for a pet, main image first, then by
   `updatedAt` ascending. Deterministic ordering keeps the T-30 detail gallery stable across
   reloads.
3. `Optional<PetImage> findMainByPetId(Long petId)` — the single `is_main = true` row, or empty.
   T-11's gallery mapper calls this to fill `mainImageUrl`.
4. `void clearMainFlag(Long petId)` — bulk `UPDATE PetImage SET isMain = false WHERE pet.petId = :petId`.
   T-16 calls this immediately before marking a new main image, because the partial unique index
   from T-03 rejects a second `true` row. Order matters: clear, then set.
5. `long countByPetId(Long petId)` — lets T-16 enforce an upload cap.
6. Deleting a `Pet` must remove its images. That is already guaranteed twice — by
   `cascade = ALL, orphanRemoval = true` on `Pet.images` and by `ON DELETE CASCADE` in T-03 — so
   this repository must **not** implement its own pet-deletion cleanup.
7. Named parameters only.

## Out of scope
- No file I/O. Writing bytes to disk is T-16's `ImageStorageService`; this class only stores the
  path string.
- No image validation (type, size, dimensions) — T-16.
- No URL construction — T-16 decides the URL scheme.

## Acceptance criteria
1. `findByPetId` returns the main image at index 0 when one exists.
2. `findMainByPetId` returns empty for a pet with only non-main images.
3. `clearMainFlag` followed by setting a new main image succeeds; doing it in the reverse order
   fails on the unique index — demonstrate both, since this ordering is the whole point of the method.
4. `countByPetId` matches the row count in `pet_image` for that pet.
5. Deleting the parent `Pet` leaves zero rows in `pet_image` for that `pet_id`.
6. `findByPetId` on a pet with no images returns an empty list, never `null`.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All six acceptance criteria demonstrated, criterion 3 showing both orderings.
- [ ] Javadoc on `clearMainFlag` explicitly warns that it must be called before setting a new main image.
- [ ] No method returns `null`.
