# T-30 · Pet details page with gated contact information

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-25, T-26, T-28 |
| **Blocks** | T-40 |
| **Estimate** | 4h |

## Goal
Build specification §12's Pet Details Page and enforce its central rule: contact details are
*"accessible to registered users only"*.

## Scope — files to create / modify
- `src/main/webapp/petDetails.xhtml`
- `src/main/java/com/petlee/web/bean/PetDetailBean.java`

## Requirements
1. `@Named("petDetailBean") @ViewScoped`. The pet id arrives as a view parameter
   (`<f:viewParam name="id" value="#{petDetailBean.petId}"/>`) and is loaded in a
   `<f:viewAction action="#{petDetailBean.load}"/>`. Loading in a getter would re-fetch on every EL
   evaluation — several API calls per render.
2. `load()` calls `ApiClient.getPet(id)`. A 404 `ApiException` navigates to the not-found page
   (T-33) rather than rendering an empty shell.
3. The page displays name, breed, age, size, gender, status, category and the **long** description
   — the fuller view that distinguishes this page from the gallery card.
4. Image gallery over `#{petDetailBean.pet.images}`: main image large, the rest as thumbnails that
   swap the large image. Plain JSF/CSS; no JavaScript library.
5. **Contact block**: rendered inside
   `<h:panelGroup rendered="#{userBean.loggedIn}">` showing `ownerFullName`, `ownerEmail` and
   `ownerPhone`. When logged out, render a localised prompt with a link to the login page instead.
6. The server has already nulled those three fields for anonymous callers (T-15, T-22). The
   `rendered` check is the **second** layer, not the only one. Both must be present: the server
   guarantees the data never arrives, the UI guarantees nothing half-rendered appears. State this
   in a comment so a later refactor does not remove one believing the other suffices.
7. Email renders as a `mailto:` link and phone as a `tel:` link, supporting specification §10
   scenario 3 (contact happens outside the system).
8. A back link returns to the gallery.
9. If the pet's status is `ADOPTED` or `REMOVED`, show a clear badge. Reaching such a pet by direct
   URL is possible even though it is absent from the gallery.
10. All text via `#{msg.*}`; all images carry `alt`.

## Out of scope
- No in-system messaging or chat (explicitly a future item in the README roadmap; specification
  §10 scenario 3 says contact happens outside the system).
- No edit/delete controls (T-32).
- No adoption-request workflow.

## Acceptance criteria
1. A guest opening a pet detail URL sees full pet information and **no** contact block, with a
   login prompt in its place.
2. **The raw HTML source for that guest request contains no email address and no phone number.**
   This is the definitive test of specification §6 — check the source, not the rendered page.
3. A logged-in user sees `ownerFullName`, `ownerEmail` and `ownerPhone`, with working `mailto:`
   and `tel:` links.
4. All images render; clicking a thumbnail changes the main image.
5. A pet with a single image renders without an empty thumbnail strip.
6. `/petDetails.xhtml?id=999999` shows the not-found page, not an exception.
7. `/petDetails.xhtml` with no `id` parameter shows the not-found page.
8. An `ADOPTED` pet reached by direct URL shows the status badge.
9. Opening the page issues exactly one `GET /api/pets/{id}` — check the access log, confirming the
   `viewAction` pattern rather than getter-driven loading.
10. Usable at 360 px.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated; criterion 2 with the raw HTML pasted into the PR.
- [ ] Both layers of the contact gate are present and commented.
- [ ] Specification §10 scenario 3 completes end to end.
