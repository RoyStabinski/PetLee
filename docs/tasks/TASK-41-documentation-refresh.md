# T-41 · Documentation refresh

| Field | Value |
|---|---|
| **Phase** | 10 — Handover |
| **Depends on** | T-40 |
| **Blocks** | none |
| **Estimate** | 3h |

## Goal
Make the repository's documentation describe the system that actually exists.

## Scope — files to create / modify
- `README.md` (rewrite)
- `api-contract.md` (modify — add the admin section and the image-delete endpoint)
- `docs/decisions/ADR-002-contract-deviations.md` (modify — final state)

## Requirements
1. **`README.md` is currently wrong in every technical particular.** It documents React, Node.js,
   Express, MongoDB, Firebase Auth and Cloudinary, and instructs the reader to run `npm install`.
   The project is JSF + JPA + Jakarta REST on a Jakarta EE 10 server with PostgreSQL, built with
   Maven (ADR-002 #5). Rewrite it completely; do not patch it.
2. The new README covers: what Pet-Lee is; the real technology stack (the three technologies in
   specification §7 plus PostgreSQL, and the closed dependency list from ADR-003); the three-tier
   architecture with a short diagram; prerequisites (JDK 17, Maven, PostgreSQL 15+, Payara 6);
   database setup pointing at `db/schema.sql` and `db/seed.sql`; build and deploy commands; how to
   run the tests (both tiers); the project layout; and links to `docs/tasks/` and `docs/decisions/`.
   State plainly that the build adds no runtime libraries — every framework comes from the server.
3. Keep the existing author credits. The technical content is wrong; the attribution is not.
4. Preserve the roadmap section, but mark those items clearly as **not implemented** — real-time
   chat, AI matching and shelter integration are none of them in the specification, and a reader
   must not mistake aspiration for feature.
5. `api-contract.md` gains an **ADMIN** section documenting T-34's four endpoints and T-23's
   `DELETE /api/pets/{petId}/images/{imageId}`, written in the existing style — request, response,
   error codes.
6. The "FROZEN" notice at the top of `api-contract.md` is updated to state that the original
   endpoints remain frozen and that the admin section was **added** (not altered) under T-34, with
   a date. A reader must be able to tell at a glance that nothing they depended on changed.
7. ADR-002's table is completed with any deviations discovered during Phases 1–9, so it is a full
   record rather than only the ones known at planning time.
8. Every command in the README is executed by the author before commit. A README with a command
   that does not run is worse than none.
9. No secrets, no local absolute paths, no personal hostnames anywhere.

## Out of scope
- No Javadoc generation or hosting.
- No user manual — the UI is self-explanatory and the specification is the functional reference.
- No changes to existing contract endpoint definitions.

## Acceptance criteria
1. `grep -iE "react|node|mongo|npm|firebase|cloudinary|tomcat|hibernate|jersey" README.md`
   returns nothing — the stack description names only specification §7's technologies.
2. A person with only JDK 17, Maven, PostgreSQL and Payara installed can follow the README from
   clone to a working deployment without asking a question. **Have someone who did not write it
   actually do this** — it is the only real test of a README.
3. Every command in the README has been run and produces the documented result.
4. `api-contract.md` documents all five added endpoints, and its frozen notice explains what
   changed and when.
5. ADR-002 lists every deviation, including any found during implementation.
6. The roadmap items are unambiguously marked as not implemented.
7. No secrets or machine-specific paths anywhere in the documentation.

## Definition of Done
- [ ] All seven acceptance criteria demonstrated; criterion 2 signed off by a second person.
- [ ] README reviewed against the deployed application, section by section.
- [ ] `api-contract.md` diffed to confirm no existing endpoint definition was altered.
