# T-35 · Admin panel page

| Field | Value |
|---|---|
| **Phase** | 8 — Administration |
| **Depends on** | T-24, T-26, T-33, T-34 |
| **Blocks** | T-40 |
| **Estimate** | 5h |

## Goal
Build the admin screens specification §8 describes — *"admin screens (listing and category
management tables)"* — and the flow in §10 scenario 4.

## Scope — files to create / modify
- `src/main/java/com/petlee/web/bean/AdminBean.java`
- `src/main/webapp/admin.xhtml`
- `src/main/java/com/petlee/web/client/ApiClient.java` (modify — add the T-34 calls)

## Requirements
1. `@Named("adminBean") @ViewScoped implements Serializable`. Extend `ApiClient` with
   `getAllPets(filter)`, `setPetStatus(id, status)`, `createCategory(name)` and
   `deleteCategory(id)`.
2. `admin.xhtml` has two sections on one page: **Listing management** and **Category management**.
3. Listing management: a table of every pet from `/api/admin/pets` with columns thumbnail, name,
   owner, category, status, created date, actions. Actions are **Hide** (set `REMOVED`), **Restore**
   (set `AVAILABLE`) and **Delete** (permanent).
4. Hide and Delete are meaningfully different in the UI: Hide is presented as the reversible,
   ordinary action, Delete as destructive and requiring confirmation naming the pet. Specification
   §2 makes the admin a supervisor, and reversible moderation should be the path of least
   resistance.
5. The listing table reuses the same filter controls as the gallery (category, size, gender) plus a
   status filter, since an admin's first question is usually "show me everything that is hidden".
6. Category management: a table of categories with a pet count and a Delete action, plus a form to
   add one. A category holding listings shows its count and a disabled Delete with an explanatory
   tooltip, so the 409 from T-34 is prevented rather than merely handled — though the error must
   still be handled, because the count can change between render and click.
7. All errors surface as `FacesMessage`s carrying the server's message. A 409 `CATEGORY_IN_USE`
   must read as a plain explanation, not a raw code.
8. The page is reachable only by an `ADMIN`: T-33's filter blocks the page, T-34 blocks the
   endpoints, and T-25 hides the menu entry. Three layers, one boundary — the REST layer.
9. All text via `#{msg.*}`.

## Out of scope
- No user management screens (no endpoints exist; T-34 requirement 8).
- No analytics or dashboards.
- No bulk operations.

## Acceptance criteria
1. An admin sees every listing including `REMOVED` ones; a `USER` cannot reach the page (403 via
   T-33) and the menu entry is hidden.
2. Hiding a listing removes it from the public gallery immediately; Restore brings it back.
3. Deleting a listing asks for confirmation naming the pet, then removes it and its images.
4. Adding a category makes it appear in the gallery filter and the add-pet form.
5. Adding a duplicate category name shows a readable message, not a code or a stack trace.
6. Deleting an unused category succeeds.
7. Deleting a category holding pets is prevented in the UI, and if forced (count changed after
   render) shows the readable `CATEGORY_IN_USE` message.
8. The listing filters narrow the admin table correctly, including by status.
9. Specification §10 scenario 4 — admin spots a non-compliant listing and removes it — completes
   end to end.
10. Usable at 1440 px; the tables scroll horizontally rather than breaking the layout on narrow
    screens.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated, screenshots for 1, 3 and 7.
- [ ] Specification §10 scenario 4 recorded as a passing walkthrough.
- [ ] No admin action bypasses the REST layer.
