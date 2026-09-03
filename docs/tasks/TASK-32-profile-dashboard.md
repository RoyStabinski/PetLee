# T-32 · User profile / personal dashboard

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-24, T-26, T-31 |
| **Blocks** | T-40 |
| **Estimate** | 4h |

## Goal
Build specification §12's "User Profile / Personal Dashboard": *"the list of pet listings created
by the logged-in user, offering options to edit or remove listings."*

## Scope — files to create / modify
- `src/main/java/com/petlee/web/bean/MyListingsBean.java`
- `src/main/webapp/profile.xhtml`

## Requirements
1. `@Named("myListingsBean") @ViewScoped implements Serializable`.
2. `@PostConstruct` loads `ApiClient.getMyPets()`. This returns **all** statuses (T-08, T-15) — an
   owner must see their own `ADOPTED` and `REMOVED` listings, unlike the public gallery.
3. A table (not the gallery grid) with columns: thumbnail, name, category, status, created date,
   and an actions column. A table suits management; the grid suits browsing.
4. Actions per row: **Edit** (navigates to `editPet.xhtml?id=…`, T-31) and **Delete**
   (`ApiClient.deletePet(id)`).
5. Delete requires a confirmation step before the call. Deletion is permanent and cascades to the
   photographs (T-03); a mis-click must not be able to destroy a listing. A plain
   `onclick="return confirm(...)"` is acceptable.
6. After a delete, refresh the list and show a success message. On `ApiException` show the server's
   message; a 403 here means the session expired mid-session, so send the user to the login page.
7. The page header shows the user's profile details from the session `UserDTO` — full name,
   username, email, phone, role. These are the user's own details, so no privacy gate applies.
8. Empty state: a localised "you have not posted any listings yet" message with a prominent link to
   `addPet.xhtml`, rather than an empty table.
9. Status renders as a coloured badge (`AVAILABLE` / `ADOPTED` / `REMOVED`) so the list is
   scannable.
10. The page requires a login; a guest is redirected by T-33.
11. Listings are ordered newest first, consistent with everywhere else in the product.

## Out of scope
- No profile editing (no such endpoint in the frozen contract, and specification §3 does not ask
  for one).
- No account deletion.
- No adoption-request inbox.

## Acceptance criteria
1. A logged-in user with three listings sees all three, newest first.
2. A user's own `REMOVED` listing appears here but not in the public gallery — check both pages.
3. Edit navigates to the pre-filled edit form for the right pet.
4. Delete asks for confirmation; cancelling makes no HTTP call (verify in the access log).
5. Confirming the delete removes the row, and the listing disappears from the gallery.
6. Deleting a listing removes its images from disk (T-16/T-23) — check the upload directory.
7. A user with no listings sees the empty state and the add link.
8. A guest opening `/profile.xhtml` is redirected to login.
9. Profile details shown match the logged-in user.
10. A session expiring between page load and delete sends the user to login rather than showing a
    raw 403.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated, screenshots for 1, 7 and 9.
- [ ] No listing belonging to another user is ever reachable from this page.
- [ ] Specification §12's dashboard requirement is fully satisfied.
