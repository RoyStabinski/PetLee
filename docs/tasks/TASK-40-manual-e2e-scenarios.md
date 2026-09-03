# T-40 · Scripted end-to-end walkthrough of the specification's use cases

| Field | Value |
|---|---|
| **Phase** | 9 — Verification |
| **Depends on** | T-27, T-29, T-30, T-31, T-32, T-33, T-35, T-39 |
| **Blocks** | T-41, T-42 |
| **Estimate** | 5h |

## Goal
Prove the four use-case scenarios in specification §10 work in a browser, end to end — the only
check that covers the assembled system rather than its parts.

## Scope — files to create / modify
- `docs/testing/e2e-scenarios.md` (the script, with a result column)
- `docs/testing/demo-data.sql` (a repeatable demo dataset)
- `docs/testing/screenshots/` (evidence)

## Requirements
1. `demo-data.sql` seeds a repeatable stage: 3 users (2 `USER`, 1 `ADMIN`), 6 categories, 12 pets
   across categories/sizes/genders with varied `created_at`, and images on at least 8 of them. It
   is idempotent and re-runnable, so a failed run can be repeated from a known state.
2. Each scenario is written as numbered steps with an explicit expected result per step, so that
   someone who did not build the system can execute it. Vague steps make a passing run meaningless.
3. **Scenario 1 — Guest browses** (specification §10): open the home page with no session; see the
   gallery; filter by category, then by size, then by both; open a pet's details; confirm the
   contact block is replaced by a login prompt; **view the page source and confirm no email or
   phone number is present**.
4. **Scenario 2 — Registered user posts a pet**: register; log in; open the add-pet form; fill every
   field; choose a category; upload a photo; save; confirm the listing appears at the top of the
   gallery with its thumbnail; confirm it appears on the dashboard.
5. **Scenario 3 — Registered user inquires**: as a *different* logged-in user, open that pet's
   detail page; confirm full contact details are shown; confirm the `mailto:` and `tel:` links are
   correct.
6. **Scenario 4 — Admin removes a listing**: log in as admin; open the admin panel; find the
   listing; Hide it; confirm it vanishes from the public gallery but remains in the admin table;
   Restore it; then Delete it permanently and confirm both the listing and its image files are gone.
7. Cross-cutting checks, each a numbered step:
   - Guest requesting `/profile.xhtml` is redirected to login and returned there after logging in.
   - A `USER` requesting `/admin.xhtml` gets the 403 page.
   - Two browsers editing the same listing: the second save shows the concurrency message.
   - Session timeout produces the expired page, not a stack trace.
   - Every page renders acceptably at 360 px and 1440 px.
8. Every scenario is executed on **both Windows and Linux**, satisfying specification §4's
   portability requirement — a claim the project otherwise never actually tests.
9. A screenshot per scenario, stored under `docs/testing/screenshots/` and referenced from the script.
10. The result column is filled in with pass/fail, date, and the tester's name. A blank column means
    the task is not done.

## Out of scope
- No automated browser tooling (Selenium). Manual execution is proportionate here, and the API is
  already covered automatically by T-39.
- No performance testing.

## Acceptance criteria
1. All four specification §10 scenarios execute with every step passing.
2. All seven cross-cutting checks pass.
3. Scenario 1's page-source check confirms no contact data reaches a guest.
4. Both operating systems are recorded as executed, with dates.
5. Screenshots exist for all four scenarios.
6. `demo-data.sql` runs twice in a row without error.
7. Any failure found is filed as an issue and either fixed or explicitly accepted before sign-off.

## Definition of Done
- [ ] `e2e-scenarios.md` has a fully populated result column, no blanks.
- [ ] All four scenarios pass on Windows and on Linux.
- [ ] Screenshots committed.
- [ ] Every defect found is closed or documented.
