# T-29 · Home page — gallery grid and filter panel

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-25, T-28 |
| **Blocks** | T-40 |
| **Estimate** | 4h |

## Goal
Build specification §12's Home Page: *"a gallery grid of recently posted pets featuring thumbnail
photos, names, and short descriptions"* with *"a side filter panel"*.

## Scope — files to create / modify
- `src/main/webapp/index.xhtml`
- `src/main/webapp/resources/images/placeholder-pet.png`

## Requirements
1. Two-column layout on wide screens: filter panel on the side, gallery grid beside it. On narrow
   screens the panel stacks above the grid (T-25's CSS).
2. Filter panel: a category `<h:selectOneMenu>` populated from `#{petBean.categories}`, plus size
   and gender menus from `#{petBean.sizeOptions}` / `#{petBean.genderOptions}`, an Apply button
   bound to `#{petBean.applyFilter}` and a Clear button to `#{petBean.clearFilters}`.
3. Filtering uses AJAX (`<f:ajax render="galleryGrid"/>`) so only the grid re-renders. A full page
   reload would scroll the user back to the top on every filter change.
4. Gallery grid over `#{petBean.pets}` using `<ui:repeat>`. **Each card shows exactly what
   specification §12 lists**: thumbnail (`#{petBean.getMainImageUrl(pet)}`), `#{pet.name}` and
   `#{pet.shortDesc}`. Age, size and gender may appear as small metadata; the full description
   must not — that is the detail page's job.
5. The whole card links to the detail page via `#{petBean.viewDetails(pet.id)}`.
6. Ordering is exactly as returned by the API — newest first (T-08). The page must not re-sort.
7. When `#{petBean.empty}`, show a localised "no pets match your filters" message with a Clear
   Filters action, instead of an empty grid.
8. `placeholder-pet.png` is a small neutral bundled image for listings with no photograph.
9. Contact details are **absent from this page entirely** — the gallery view (`PetDTO`) does not
   carry them, which is the structural guarantee behind specification §6.
10. Every thumbnail has `alt="#{pet.name}"`; every filter control has a label.

## Out of scope
- No detail rendering (T-30).
- No edit/delete controls, even for the owner — those live on the dashboard (T-32).
- No infinite scroll or pagination.

## Acceptance criteria
1. A guest with no session sees the gallery, populated, with no login prompt.
2. Cards show thumbnail, name and short description; the long description appears nowhere in the
   page source.
3. Changing the category filter re-renders only the grid, with the scroll position preserved.
4. Combining category + size + gender narrows results correctly.
5. Clear restores the full list.
6. Newest listing appears first.
7. A pet with no image shows the placeholder, not a broken image.
8. A filter matching nothing shows the empty-state message.
9. The page source contains no `ownerEmail`, `ownerPhone` or `ownerFullName` — inspect the raw
   HTML, since this is the privacy rule in specification §6.
10. Usable at 360 px and 1440 px.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated, screenshots for 3, 8 and 10.
- [ ] Criterion 9 checked by viewing raw page source, not just the rendered page.
- [ ] Specification §10 scenario 1 ("Guest Browses Pets for Adoption") completes end to end.
