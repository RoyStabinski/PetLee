# T-23 · Pet image upload and static serving

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-16, T-18, T-19 |
| **Blocks** | T-24, T-31 |
| **Estimate** | 4h |

## Goal
Implement `POST /api/pets/{id}/images` and make the stored files reachable at the URLs the DTOs
advertise — using the Servlet API's own multipart support, with no upload library.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/PetImageResource.java`
- `src/main/java/com/petlee/web/ImageServlet.java`

## Requirements
1. `POST /api/pets/{id}/images` — `@Secured`, `@Consumes(MediaType.MULTIPART_FORM_DATA)`,
   `@Produces(APPLICATION_JSON)`. Fields per the contract: `file` and a boolean `isMain`.
   Returns `200` with `PetImageDTO`.
2. **Multipart is read through the Servlet API, not a Jakarta REST extension.** Inject
   `@Context HttpServletRequest request` and read `request.getPart("file")` and
   `request.getParameter("isMain")`. `Part` gives `getInputStream()`, `getSubmittedFileName()`,
   `getContentType()` and `getSize()` — everything T-16 needs.
   Rationale: `@FormDataParam` is Jersey-specific and would not deploy on WildFly, and adding
   `jersey-media-multipart` is forbidden by ADR-003. Servlet multipart is part of the platform and
   works on every Jakarta EE server.
3. Declare the size cap declaratively with `@MultipartConfig(maxFileSize = 5_242_880,
   maxRequestSize = 6_291_456)` on the resource's servlet configuration, so an oversized upload is
   rejected by the container before the body is buffered into memory. This mirrors T-16's 5 MB
   limit; a `IllegalStateException` from the container is mapped to 400 by T-19.
4. Delegates immediately to `ImageStorageService.store(...)` (T-16). **No validation, no file I/O,
   no authorisation logic in this class** — T-16 owns all three, and duplicating the owner check
   here would create two places to get it wrong.
5. `DELETE /api/pets/{petId}/images/{imageId}` — `@Secured`, returns `204`. Needed by T-32's
   dashboard so an owner can remove a bad photo. This **extends** the frozen contract: record it in
   ADR-002 and add it to `api-contract.md` under T-41.
6. `ImageServlet` is a `@WebServlet("/images/*")` serving files from the T-16 upload root:
   - Resolve the requested name against the root, **canonicalise, and reject anything that escapes
     it** with `404`. The URL path segment is attacker-controlled; this is the mirror image of
     T-16's write-side defence and is equally mandatory.
   - Serve only the four permitted image types; anything else `404`.
   - Set `Content-Type` from the detected type and `Cache-Control: public, max-age=86400` —
     photographs are immutable once written, since T-16 generates a fresh filename per upload.
   - Missing file → `404`, never a directory listing and never a stack trace.
7. Image serving is **open**: a guest browsing the gallery must see thumbnails (specification §10
   scenario 1). Photographs are not the private data — contact details are.

## Out of scope
- No storage, validation, or main-flag logic (T-16).
- No image transformation.
- No upload UI (T-31).
- No multipart library of any kind (ADR-003).

## Acceptance criteria
1. Uploading a JPEG as the owner returns `200` and a `PetImageDTO` with the three contract keys
   `id`, `imageUrl`, `isMain`.
2. `GET` on the returned `imageUrl` with **no session** returns `200` and the exact bytes uploaded.
3. Uploading without a session returns `401`; as a non-owner, `403`.
4. `GET /images/../../../../etc/passwd` and its URL-encoded form `%2e%2e%2f` both return `404` and
   serve nothing. Both encodings must be tested.
5. `GET /images/does-not-exist.jpg` returns `404` with no stack trace.
6. A 6 MB upload is rejected by the container with a `400`, without buffering the whole body.
7. Uploading with `isMain=true` makes the new image the pet's `mainImageUrl` in `GET /api/pets`.
8. `DELETE` of an image as the owner returns `204`; the file is gone and `GET` on its URL returns
   `404`.
9. `grep -n "FormDataParam\|MultiPartFeature" src/main/java/` returns nothing.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated; criterion 4 is a security check and must be an
      automated test.
- [ ] The new `DELETE` endpoint is recorded in ADR-002 and queued for `api-contract.md` in T-41.
- [ ] No path from a URL or filename reaches a `File`/`Path` constructor without canonicalisation
      and a containment check.
