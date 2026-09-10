# End-to-end walkthrough (T-40)

The four use-case scenarios of specification §10, executed in a browser against a deployed
Pet-Lee. This is the only check that covers the assembled system rather than its parts: T-39 proves
the API, this proves the screens.

**How to use it.** Work down each table. Every step has one expected result; if what you see differs
in any way, the step fails — write `FAIL` and what you saw in the notes row beneath the table.
Fill in **both** result columns: specification §4 claims the application is portable across Windows
and Linux, and this walkthrough is the only place that claim is actually tested.

| | |
|---|---|
| **Tester** | _(name)_ |
| **Windows run** | _(date)_ · Payara ____ · JDK ____ · browser ____ |
| **Linux run** | _(date)_ · Payara ____ · JDK ____ · browser ____ |

## Before you start

1. Deploy `target/pet-lee.war` and confirm `http://localhost:8080/pet-lee/` renders the gallery.
2. Seed the stage — three accounts, six categories, twelve listings, eight photographs:

   ```bash
   psql -U postgres -d petlee -f docs/testing/demo-data.sql
   ```

   It is re-runnable: run it again after a failed attempt and you are back to a known state. It
   never deletes anything, so a listing you created during a run stays until you remove it.

3. Put the eight photographs where the application serves them from. `demo-data.sql` prints the
   file names it expects — they embed each listing's real id, because `ImageServlet` only serves
   names matching T-16's `<petId>_<uuid>.<ext>` convention and answers 404 to anything else.

   ```bash
   # Linux / macOS — UPLOADS is <server domains dir>/petlee-uploads unless petlee.upload.dir is set
   UPLOADS=~/payara6/glassfish/domains/petlee-uploads
   psql -U postgres -d petlee -t -A -c \
     "SELECT SUBSTRING(i.image_url FROM 9) FROM pet_image i
        JOIN pet p ON p.pet_id = i.pet_id
        JOIN users u ON u.user_id = p.owner_id
       WHERE u.user_name LIKE 'demo\_%'" \
   | while read -r f; do cp src/main/webapp/resources/images/placeholder-pet.png "$UPLOADS/$f"; done
   ```

   On Windows the same loop is `for /f "usebackq tokens=*" %f in (`psql … `) do copy /Y … "%UPLOADS%\%f"`.

4. The demo accounts, all with password **`Demo123!`**:

   | Username | Role | Who they are |
   |---|---|---|
   | `demo_owner` | USER | Dana Owner — posted eleven of the twelve listings |
   | `demo_adopter` | USER | Adam Adopter — posted `Pip`, and is the enquirer in scenario 3 |
   | `demo_admin` | ADMIN | Mia Moderator — the administrator in scenario 4 |

   They are demo credentials in a demo database. T-42's runbook does not create them.

5. Use a **private window** for guest steps, or log out first. A stale session is the single most
   common reason a "guest" step fails.

---

## Scenario 1 — A guest browses the catalogue

Specification §10 scenario 1, and the §6 privacy rule.

| # | Do this | Expect | Win | Linux |
|---|---|---|---|---|
| 1.1 | Open `/pet-lee/` with no session | The gallery renders **12 listings**, newest first — `Rex` first, `Ziggy` last | | |
| 1.2 | Look at the cards | 8 show a photograph; 4 (`Clover`, `Kiwi`, `Sunny`, `Ziggy`) show the bundled placeholder — never a broken image icon | | |
| 1.3 | Check the navigation bar | **Log in** and **Register** only. No *My profile*, no *Add a pet*, no *Admin panel* | | |
| 1.4 | Filter by category **Dogs**, apply | Exactly `Rex`, `Bella`, `Pip` | | |
| 1.5 | Add size **Small**, apply | Exactly `Pip` | | |
| 1.6 | Clear the filters | All 12 return | | |
| 1.7 | Open `Rex` | The details page shows breed, age, size, gender and the long description | | |
| 1.8 | Look where contact details would be | A prompt — *"Log in to see how to contact the owner"* — and a **Log in** button. No email, no telephone | | |
| 1.9 | **View the page source** (Ctrl-U) and search for `@petlee.demo` and for `050-` | **No match.** Nothing hides contact data with CSS; it is not in the response at all | | |

> Step 1.9 is the one that matters. A UI that merely hides the block would pass 1.8 and fail here.

**Notes / failures:**

---

## Scenario 2 — A registered user posts a pet

Specification §10 scenario 2.

| # | Do this | Expect | Win | Linux |
|---|---|---|---|---|
| 2.1 | **Register** a new account (any username 3–20 chars, password ≥ 8) | Registration succeeds and you are logged in | | |
| 2.2 | Check the navigation bar | *Add a pet* and *My profile* have appeared | | |
| 2.3 | Open **Add a pet** | The form shows name, breed, age, size, gender, category, short and long description, and a file field | | |
| 2.4 | Submit it empty | Field-level messages, no listing created, nothing lost from the form | | |
| 2.5 | Fill every field, choose a category, choose a photograph, save | The listing is created and you land on its details page | | |
| 2.6 | Open the home page | Your listing is **first** in the gallery, with its photograph as the thumbnail | | |
| 2.7 | Open **My profile** | Your listing is in the table, status *Available*, with Edit and Delete | | |
| 2.8 | Edit it, change the name, save | The new name shows on the dashboard and in the gallery | | |

**Notes / failures:**

---

## Scenario 3 — A registered user enquires about adoption

Specification §10 scenario 3, and the other half of §6.

| # | Do this | Expect | Win | Linux |
|---|---|---|---|---|
| 3.1 | Log out, then log in as **`demo_adopter`** | You are signed in as Adam Adopter | | |
| 3.2 | Open `Rex` — a listing you do **not** own | The details page shows the owner block: **Dana Owner**, `dana.owner@petlee.demo`, `050-1110001` | | |
| 3.3 | Hover the email | It is a `mailto:dana.owner@petlee.demo` link | | |
| 3.4 | Hover the telephone | It is a `tel:050-1110001` link | | |
| 3.5 | Look for Edit and Delete on this listing | Neither is offered — it is not yours | | |
| 3.6 | Open `Pip`, which this account owns | Edit and Delete are offered here | | |

**Notes / failures:**

---

## Scenario 4 — An administrator removes a listing

Specification §10 scenario 4 — the phase-8 gate.

| # | Do this | Expect | Win | Linux |
|---|---|---|---|---|
| 4.1 | Log in as **`demo_admin`** | The navigation bar now shows **Admin panel** | | |
| 4.2 | Open the admin panel | Two sections: listing management (12 rows, with owner and created date) and category management (6 rows with counts) | | |
| 4.3 | Find `Milo` and click **Hide** | A message confirms it; the row's status becomes *Withdrawn* and the action becomes **Restore** | | |
| 4.4 | Open the public gallery (a second tab, or log out) | `Milo` is **gone** — 11 listings | | |
| 4.5 | Return to the admin panel | `Milo` is still listed, as *Withdrawn* — hiding is not deleting | | |
| 4.6 | Set the status filter to **Withdrawn**, apply | Only `Milo` | | |
| 4.7 | Click **Restore** | `Milo` is *Available* again and back in the public gallery (12) | | |
| 4.8 | Click **Delete** on `Milo` | A confirmation names the pet: *"Delete Milo permanently?"* | | |
| 4.9 | Cancel it | Nothing happens; `Milo` is still there | | |
| 4.10 | Click **Delete** again and confirm | `Milo` is gone from both tables and from the gallery (11) | | |
| 4.11 | Ask for its photograph directly: open `/pet-lee/images/<the file name it had>` | **404.** Deleting a listing deletes its photographs from disk, not merely its rows | | |
| 4.12 | In category management, add a category named `Horses` | It appears in the table with 0 listings — and in the gallery filter and the add-pet form | | |
| 4.13 | Add `horses` again | A readable message — *"A category named horses already exists"* — not a code, not a stack trace | | |
| 4.14 | Delete `Horses` | It goes | | |
| 4.15 | Try to delete `Dogs` | Its Delete is **disabled**, and the tooltip says a category holding listings cannot be deleted | | |

> Re-seed with `demo-data.sql` after this scenario: step 4.10 permanently removes `Milo`.

**Notes / failures:**

---

## Cross-cutting checks

Seven checks that belong to no single scenario. Specification §4 and T-33.

| # | Do this | Expect | Win | Linux |
|---|---|---|---|---|
| X.1 | As a guest, open `/pet-lee/profile.xhtml` | Redirected to the login page (the URL carries `returnUrl=%2Fprofile.xhtml`) | | |
| X.2 | Log in from that page | You land on **the profile page**, not the home page | | |
| X.3 | As `demo_adopter`, open `/pet-lee/admin.xhtml` | The 403 page — a Pet-Lee page explaining it, not a server error page and not a login prompt | | |
| X.4 | In two browsers, open the same listing's edit form as `demo_owner`. Save the first, then save the second | The second save shows the concurrency message — *"changed by someone else; reload it and try again"* — and does not overwrite the first | | |
| X.5 | Log in, wait for the session to expire (or delete the `JSESSIONID` cookie), then submit any form | The session-expired page, not a stack trace and not a blank page | | |
| X.6 | Resize to **360 px** (phone) on the gallery, details, add-pet, profile and admin pages | Everything readable, nothing clipped; the admin tables scroll sideways inside their own box rather than stretching the page | | |
| X.7 | Same five pages at **1440 px** | Laid out and usable; no full-width stretched text | | |

**Notes / failures:**

---

## What was pre-verified, and what only a browser can tell you

Every step below was checked against a deployed Pet-Lee on Windows over HTTP before this script was
written, so a failure in one of them is a real regression rather than a mistake in the script. They
still need executing in a browser — an HTTP check cannot see a layout.

| Verified over HTTP | Evidence |
|---|---|
| 1.1, 1.2 | 12 cards, newest `Rex` → oldest `Ziggy`; 8 photographs, 4 placeholders |
| 1.4, 1.5 | Category `Dogs` → `Rex`, `Bella`, `Pip`; plus size `SMALL` → `Pip` |
| 1.8, 1.9 | Guest page carries *"Log in to see how to contact the owner"* and no `@petlee.demo` or `050-` anywhere in the response |
| 3.2–3.4 | Member page carries `Dana Owner`, `mailto:dana.owner@petlee.demo`, `tel:050-1110001` |
| 4.3–4.7 | Hide → gallery 11 / admin 12; restore → gallery 12 |
| 4.13, 4.15 | 409 `CATEGORY_EXISTS` shown as a sentence; in-use Delete rendered disabled with the tooltip |
| X.1, X.2 | 302 to `login.xhtml?returnUrl=%2Fprofile.xhtml`, and the login redirects on to `/profile.xhtml` |
| X.3 | A member requesting `/admin.xhtml` receives 403 |

**Only a browser can judge:** every layout step (X.6, X.7), the confirmation dialogs (4.8, 4.9),
the file chooser in 2.5, the two-browser race in X.4, and session expiry in X.5.

## Defects found

One row per failure, filed before sign-off. A walkthrough with an unexplained `FAIL` is not done.

| # | Step | What happened | Issue | Status |
|---|---|---|---|---|
| | | | | |

## Sign-off

| | Windows | Linux |
|---|---|---|
| All four scenarios pass | | |
| All seven cross-cutting checks pass | | |
| Screenshots committed under `docs/testing/screenshots/` | | |
| Every defect closed or explicitly accepted | | |
| Date / tester | | |

Screenshots to capture, one per scenario plus the two the task calls out specifically:
`scenario-1-guest-gallery.png`, `scenario-2-listing-posted.png`, `scenario-3-contact-details.png`,
`scenario-4-admin-panel.png`, `scenario-4-delete-confirmation.png`, `category-in-use-refused.png`.
