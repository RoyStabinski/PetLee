# T-16 · ImageStorageService — upload, storage, and the main-image rule

| Field | Value |
|---|---|
| **Phase** | 4 — Business logic |
| **Depends on** | T-09, T-11, T-12, T-15 |
| **Estimate** | 5h |
| **Blocks** | T-23, T-31 |

## Goal
Accept an uploaded photograph, store it safely on disk, and record it against a pet while keeping
the "exactly one main image" invariant (specification §11).

## Scope — files to create / modify
- `src/main/java/com/petlee/service/ImageStorageService.java`
- `src/main/java/com/petlee/config/StorageConfig.java` (resolves the upload directory)

## Requirements
1. `PetImageDTO store(Long petId, InputStream data, String originalFilename, String contentType,
   long sizeBytes, boolean isMain, Long callerUserId)`.
2. Authorisation first, before a single byte is written: the caller must be the pet's owner
   (reuse `PetService.isOwner`), otherwise `ForbiddenException` (403). `api-contract.md` marks
   `POST /api/pets/{id}/images` as *"auth + owner"*.
3. Accept only `image/jpeg`, `image/png`, `image/webp` and `image/gif`. Reject anything else with
   `ValidationException` (400). **Determine the type from the file's magic bytes, not from the
   client-supplied `Content-Type` header** — the header is attacker-controlled and a `.jpg`
   labelled image that is really a script is the classic upload attack.
4. Maximum 5 MB per file, maximum 8 images per pet (`countByPetId`, T-09). Both breaches →
   `ValidationException` (400) with a message stating the limit.
5. The stored filename is **generated**, never derived from `originalFilename`:
   `<petId>_<UUID>.<ext>`, extension chosen from the *detected* type. This defeats path traversal
   (`../../etc/passwd`), null bytes, and unicode filename tricks in one move. The original name is
   not persisted.
6. Files are written under a configurable root: `StorageConfig` reads `PETLEE_UPLOAD_DIR`,
   defaulting to a `petlee-uploads` directory beside the server's domain directory. **It must be outside the exploded WAR**, or
   every redeploy silently deletes the users' photographs.
7. After writing, canonicalise the resulting path and assert it is still inside the configured
   root. Belt and braces alongside requirement 5.
8. The persisted `image_url` is the **public URL path** (`/images/<filename>`), not a filesystem
   path. T-23 serves that path. A filesystem path in the database would leak server layout into
   the JSON contract.
9. When `isMain` is true: call `PetImageRepository.clearMainFlag(petId)` **first**, then save the
   new row as main. The partial unique index from T-03 rejects any other ordering. If the pet has
   no images yet, the first upload is forced to main regardless of the flag — a listing with photos
   but no thumbnail would render a broken gallery card.
10. Writing the file and inserting the row must not leave the two out of step: if the database
    insert fails, delete the just-written file before rethrowing.
11. `void deleteImage(Integer imageId, Long callerUserId, boolean callerIsAdmin)` — owner-or-admin,
    removes both the row
    and the file. Deleting the current main image promotes the oldest remaining image to main so
    the invariant "a pet with images has a main image" survives.
    The `callerIsAdmin` flag is not in the original signature; it is here because the requirement
    says "owner-or-admin" and a service must never work the caller's role out for itself (T-15,
    requirement 8) — it is decided by the REST tier from the session, exactly as `PetService.delete`
    takes it.

## Out of scope
- No image resizing, thumbnail generation, or EXIF stripping. Not required by the specification.
- No cloud storage (the README's mention of Cloudinary is from the wrong project; see ADR-002 #5).
- No multipart parsing — that is T-23's job; this service receives a plain `InputStream`.

## Acceptance criteria
1. Uploading a valid JPEG as the owner returns a `PetImageDTO` and the file exists under the
   configured root.
2. Uploading as a non-owner throws `ForbiddenException` **and writes no file** — check the
   directory afterwards.
3. A `.txt` file renamed to `.jpg` and sent with `Content-Type: image/jpeg` is rejected, proving
   magic-byte detection.
4. A 6 MB file is rejected; a 9th image for one pet is rejected.
5. `originalFilename` of `../../../evil.jpg` produces a file inside the upload root with a
   generated name, and nothing is written outside it.
6. Uploading a second image with `isMain=true` leaves exactly one row with `is_main = true`.
7. The first image uploaded for a pet is main even when `isMain=false` is sent.
8. A forced database failure after the file write leaves no orphan file on disk.
9. Deleting the main image of a three-image pet promotes another image to main.
10. The stored `image_url` starts with `/images/` and contains no filesystem path separator from
    the server root.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All ten acceptance criteria demonstrated; 3 and 5 are security criteria and must be
      automated tests, not manual checks.
- [ ] The upload directory default is documented in `docs/deployment/upload-directory.md` and in
      T-42's runbook. **Not** `application.properties`: the project has no properties-file
      mechanism and ADR-003 keeps the dependency list closed, so nothing would read one, and a
      file documenting a setting no code loads is worse than no file.
- [ ] No user-supplied string ever reaches a `File`/`Path` constructor.
