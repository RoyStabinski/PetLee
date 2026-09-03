# T-22 · PetResource

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-15, T-18, T-19 |
| **Blocks** | T-24, T-28, T-31, T-32 |
| **Estimate** | 5h |

## Goal
Expose the five pet endpoints in `api-contract.md` — the core of the public API.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/PetResource.java`

## Requirements
1. `@Path("/pets")`. Endpoints, with their contract markings:

   | Method | Path | Auth | Success |
   |---|---|---|---|
   | `GET` | `/api/pets` | open | 200, `List<PetDTO>` |
   | `GET` | `/api/pets/{id}` | open\* | 200, `PetDetailDTO` |
   | `POST` | `/api/pets` | auth | 200, created `PetDTO` |
   | `PUT` | `/api/pets/{id}` | auth + owner | 200, updated `PetDTO` |
   | `DELETE` | `/api/pets/{id}` | owner or admin | **204**, no body |

2. `GET /api/pets` accepts optional `@QueryParam`s `categoryId`, `size`, `gender`, assembled into a
   `PetFilter` (T-08). Absent params mean "no restriction". An **invalid** enum value (e.g.
   `size=HUGE`) returns **400** with code `INVALID_FILTER`, not 500 and not a silently empty list —
   quietly ignoring a bad filter hides typos from the caller.
3. `GET /api/pets/{id}` carries **no** `@Secured`. It calls
   `PetService.findDetail(id, CurrentUser.userIdOrNull(request))`. This single line is what
   implements the contract's `open*` footnote and specification §6's privacy rule.
4. `POST` and `PUT` carry `@Secured`; the owner id comes from the session, never the body.
5. `DELETE` carries `@Secured` and passes both `userId` and `isAdmin` from the `SessionUser` into
   `PetService.delete`, which makes the owner-or-admin decision. Returns `204` with an empty body.
6. All five methods `@Produces(MediaType.APPLICATION_JSON)`; `POST` and `PUT` also
   `@Consumes(MediaType.APPLICATION_JSON)`.
7. No `try/catch`. 403, 404 and 409 all arrive via T-19 from the exceptions T-15 throws.
8. `GET /api/pets` on an empty catalogue returns `200` and `[]`.

## Out of scope
- Image upload — T-23, a separate resource class.
- Admin-wide listing including `REMOVED` — T-34.
- Any endpoint not in the table above. The contract is frozen.

## Acceptance criteria
1. `GET /api/pets` returns `200` and a JSON array whose objects match `PetDTO`'s nine contract keys.
2. `GET /api/pets?categoryId=1&size=SMALL&gender=MALE` returns only matching pets; each filter
   works alone as well.
3. `GET /api/pets?size=HUGE` returns `400` code `INVALID_FILTER`.
4. `GET /api/pets/{id}` **without** a session returns `200` with `ownerFullName`, `ownerEmail` and
   `ownerPhone` all `null`; **with** a session all three are populated. Both must be shown.
5. `POST /api/pets` without a session returns `401`; with a session returns `200` and the pet's
   `owner_id` in the database equals the session user.
6. `PUT /api/pets/{id}` as a non-owner returns `403`; as the owner returns `200`.
7. Two concurrent `PUT`s on the same pet: the second returns `409`.
8. `DELETE /api/pets/{id}` as a stranger returns `403`; as the owner returns `204` with an empty
   body; as an admin on another user's pet returns `204`.
9. `GET /api/pets/999999` returns `404`.
10. Gallery results are ordered newest first and contain no `ADOPTED` or `REMOVED` pets.

## Definition of Done
- [ ] All ten acceptance criteria demonstrated with `curl -i` output pasted in the PR.
- [ ] Criteria 4, 6, 7 and 8 additionally covered by automated tests in T-39.
- [ ] Every method's Javadoc quotes its contract line, including the `open*` footnote on the detail
      endpoint.
- [ ] No business rule is evaluated inside this class.
