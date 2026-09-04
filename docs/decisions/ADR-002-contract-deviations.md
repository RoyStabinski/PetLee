# ADR-002 — Deviations between the specification, `api-contract.md`, and existing code

**Status:** Accepted · **Date:** 2026-09-03

`api-contract.md` is marked FROZEN. Where the committed entity code and the contract disagree,
**the contract wins and the code changes** — the contract is the shared integration surface, and
altering it breaks callers. Where the contract is silent and the specification speaks, the
specification wins and the contract is *extended* (never rewritten), with the extension logged here.

| # | Conflict | Ruling | Task |
|---|---|---|---|
| 1 | `POST /api/users/register` sends `"region"`. `User.java` has no `region` field; spec §11's user table does not list one. | Add `region` to `User` and the `users` table, nullable, `VARCHAR(100)`. Contract is frozen; the entity yields. `region` is **not** returned in `UserDTO` — the contract's response body does not include it. | T-04 |
| 2 | `Pet.java` exposes `getPetSize()` / `setPetSize()` for the field `size`. | Rename to `getSize()` / `setSize()`. JSON-B derives JSON property names from bean accessors and JSF EL resolves `#{pet.size}` the same way; the current names would emit `petSize` and break the contract's `"size"` key. | T-04 |
| 3 | `Pet.getVersion()` returns primitive `long` from a `Long` field. | Return `Long`. A `Pet` that has not been persisted has `version == null`, so the current signature throws `NullPointerException` on unboxing. | T-04 |
| 4 | `Pet.createdAt` is declared `updatable = true`; spec §11 uses `created_at` to order listings "newest to oldest". | Set `updatable = false`. An update must never silently reorder the gallery. | T-04 |
| 5 | `README.md` documents React, Node.js, Express, MongoDB, Firebase and Cloudinary. The project is JSF + Jersey + Hibernate + PostgreSQL. | Rewrite entirely. The current file misleads every new reader on day one. | T-41 |
| 6 | Spec §3 requires *"administration management capabilities for system administrators"* and §8 describes *"admin screens (listing and category management tables)"*, but `api-contract.md` defines no admin endpoints. | **Extend** the contract with an `ADMIN` section (category create/delete, list-all-pets). Existing endpoints are untouched, so no caller breaks. | T-34 |
| 7 | Spec §11 names the user password column `password_hash`; `User.java` maps `password`. | Rename the column to `password_hash`. The field stores a PBKDF2 digest, never a password — the name should say so. Not contract-visible: passwords never appear in any response. | T-04 |
| 8 | `User.userId` is primitive `long` while every other entity id is a wrapper type. | Change to `Long`. T-04 requirement 7 asks for identity `equals` that returns `false` when either id is `null`; a primitive cannot express "not yet persisted", reporting `0` instead. Same defect class as #3. | T-04 |

## Standing rule
Any further deviation discovered during implementation is added to this table **in the same pull
request that introduces it**. An undocumented deviation is a review blocker.
