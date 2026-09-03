# T-28 · PetManagedBean — gallery data and filters

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-21, T-22, T-24 |
| **Blocks** | T-29, T-30 |
| **Estimate** | 4h |

## Goal
Back the home gallery and its filter panel (specification §9.4, §12).

## Scope — files to create / modify
- `src/main/java/com/petlee/web/bean/PetManagedBean.java`

## Requirements
1. `@Named("petBean") @ViewScoped implements Serializable`. `@ViewScoped`, not `@SessionScoped`:
   filter state belongs to the page being viewed, and session scope would make a user's filters
   reappear unexpectedly in another tab.
2. State: `List<PetDTO> pets`, `List<CategoryDTO> categories`, and the filter fields
   `Integer selectedCategoryId`, `String selectedSize`, `String selectedGender` (null = "any").
3. `@PostConstruct init()` loads categories once and performs the initial unfiltered pet load.
4. `void applyFilter()` re-queries through `ApiClient.getPets(...)`. **Filtering happens
   server-side**, via the contract's query parameters. Fetching everything and filtering in Java
   would defeat T-08's indexes and break as the catalogue grows.
5. `void clearFilters()` resets all three fields to null and reloads.
6. `List<SelectItem> getSizeOptions()` and `getGenderOptions()` built from the contract's exact
   enum strings (`SMALL|MEDIUM|LARGE`, `MALE|FEMALE`), each with an "Any" option whose value is
   null. Labels are localised via `#{msg.*}`; **the submitted values are the raw enum names**, since
   T-22 parses them.
7. `String viewDetails(Long petId)` returns
   `"/petDetails.xhtml?faces-redirect=true&includeViewParams=true&id=" + petId`.
8. `boolean isEmpty()` so the page can show a friendly "no pets match" message instead of a blank
   grid.
9. `ApiException` from any call becomes a `FacesMessage`; the bean must not swallow it and leave
   the user with a silently empty page.
10. `String getMainImageUrl(PetDTO)` returns the pet's `mainImageUrl`, or a bundled placeholder
    image path when it is null. Guarantees no broken image icons in the grid.

## Out of scope
- No pet creation or editing (T-31).
- No owner dashboard (T-32).
- No XHTML (T-29, T-30).

## Acceptance criteria
1. `init()` populates categories with 6 entries and pets with all `AVAILABLE` listings.
2. Selecting a category and calling `applyFilter()` narrows the list, and the access log shows
   `GET /api/pets?categoryId=…` — proving server-side filtering.
3. Combining all three filters returns the correct intersection.
4. `clearFilters()` restores the full list.
5. A filter combination matching nothing sets `empty` true and produces no error.
6. Size and gender option values are exactly the contract's uppercase strings.
7. With the API stopped, the page shows an error message rather than a blank grid or an exception.
8. `getMainImageUrl` returns the placeholder for a pet with no images.
9. Two browser tabs can hold different filters simultaneously (`@ViewScoped` verification).

## Definition of Done
- [ ] All nine acceptance criteria demonstrated.
- [ ] No filtering logic in Java over the returned list — verify by reading the code.
- [ ] `grep -rE "import com.petlee.(service|repository)"` on this file returns nothing.
