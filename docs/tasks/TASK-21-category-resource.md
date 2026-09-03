# T-21 · CategoryResource

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-14, T-19 |
| **Blocks** | T-24, T-28 |
| **Estimate** | 1h |

## Goal
Expose `GET /api/categories`, the vocabulary the gallery filter and the add-pet form both read.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/CategoryResource.java`

## Requirements
1. `@Path("/categories")`, `@GET`, `@Produces(MediaType.APPLICATION_JSON)`, **open** — no
   `@Secured`. A guest filtering the gallery needs this list (specification §10 scenario 1).
2. Returns `List<CategoryDTO>` serialised as a JSON array. The contract's example shows objects
   with exactly `id` and `name`.
3. An empty table returns `200` with `[]`, never `204` and never `null`.
4. Delegates to `CategoryService.findAll()`. No logic here.

## Out of scope
- No POST/PUT/DELETE — admin category management is T-34, which adds them to this same class.
- No filtering or pagination.

## Acceptance criteria
1. `curl /api/categories` with no session returns `200` and a JSON array of 6 objects.
2. Each element has exactly the keys `id` and `name`.
3. The array is ordered alphabetically by `name`.
4. With the `category` table emptied, the response is `200` and `[]`.

## Definition of Done
- [ ] All four acceptance criteria demonstrated.
- [ ] The class is under 30 lines.
- [ ] Response shape diffed against `api-contract.md` by a reviewer.
