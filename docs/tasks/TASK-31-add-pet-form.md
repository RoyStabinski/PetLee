# T-31 · Add-pet form with image upload

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-23, T-24, T-26 |
| **Blocks** | T-32, T-40 |
| **Estimate** | 6h |

## Goal
Build specification §12's "Add Pet Listing Form" — the flow in specification §10 scenario 2.

## Scope — files to create / modify
- `src/main/java/com/petlee/web/bean/PetFormBean.java`
- `src/main/webapp/addPet.xhtml`
- `src/main/webapp/editPet.xhtml`

## Requirements
1. `@Named("petFormBean") @ViewScoped implements Serializable`, holding a `PetForm` plus an
   uploaded `Part`.
2. `addPet.xhtml` fields: name, breed, age, size, gender, short description, long description,
   category (menu from `ApiClient.getCategories()`), and a file input. The field set matches
   `PetForm` in the frozen contract exactly — no extra fields, since the API would ignore them.
3. File upload uses `<h:inputFile value="#{petFormBean.uploadedFile}"/>` inside a form with
   `enctype="multipart/form-data"`. Omitting the enctype silently yields a null file — a
   frequent and hard-to-spot mistake.
4. `String save()` performs two calls in order: `ApiClient.createPet(form)`, then, if a file was
   chosen, `ApiClient.uploadImage(newPetId, ..., isMain=true)`. The first photo is the thumbnail
   (T-16 requirement 9).
5. **If the pet is created but the image upload fails, the pet must not be silently left without a
   photo and the user must not be told the whole operation failed.** Show a warning naming what
   succeeded and what did not, and land the user on their dashboard where they can retry the
   upload. Two sequential HTTP calls cannot be atomic; the UI's job is to be honest about it.
6. Client-side validation mirrors T-15's rules (name required, age 0–50, short description ≤ 255,
   category required) to save a round trip. Server messages still always surface.
7. `editPet.xhtml` reuses the same bean, pre-loaded from `ApiClient.getPet(id)`, and calls
   `updatePet` on save. A **409** `ApiException` produces a specific message: "This listing was
   changed by someone else. Reload and try again." A generic error here would leave the user with
   no idea what to do — this is the user-visible face of specification §4's concurrency control.
8. Only the owner may open `editPet.xhtml`; a non-owner is sent to the 403 page (T-33). The server
   enforces this too (T-22).
9. After a successful save, redirect to the dashboard (T-32) with a success message. Redirect, not
   forward, so a refresh does not re-submit and create a duplicate listing.
10. Show the file-size and type limits from T-16 next to the file input, before submission.
11. All labels via `#{msg.*}`.

## Out of scope
- No multi-image upload in one submission — one photo at creation; more are added from the
  dashboard (T-32).
- No image cropping or preview.
- No draft saving.

## Acceptance criteria
1. A logged-in user completes the form and the listing appears in the gallery, newest first.
2. The uploaded photo appears as the card thumbnail.
3. Submitting with no file creates the pet successfully and the card shows the placeholder.
4. Category is required; submitting without one shows a field message and issues no HTTP call.
5. Age 51 is rejected.
6. An 8 MB file produces a clear size-limit message, not a stack trace.
7. Simulating an image-upload failure after successful creation shows the partial-success warning
   and the pet still exists.
8. A guest opening `/addPet.xhtml` is redirected to login (T-33).
9. Editing an own listing saves and shows updated values.
10. Editing a listing changed concurrently in another session shows the specific 409 message.
11. Opening `editPet.xhtml` for another user's pet shows the 403 page.
12. Refreshing after a successful save does not create a duplicate listing.

## Definition of Done
- [ ] All twelve acceptance criteria demonstrated; 7, 10 and 12 are the ones most likely to be
      skipped and must be shown explicitly.
- [ ] Specification §10 scenario 2 completes end to end.
- [ ] No validation rule exists here that does not also exist server-side.
