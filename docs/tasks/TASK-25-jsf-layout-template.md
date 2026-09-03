# T-25 · Facelets template, styling, and navigation shell

| Field | Value |
|---|---|
| **Phase** | 7 — Presentation |
| **Depends on** | T-17 |
| **Blocks** | T-27, T-29, T-30, T-31, T-32, T-33, T-35 |
| **Estimate** | 5h |

## Goal
Give every page one consistent frame, so the five screens in specification §12 are written as
content only.

## Scope — files to create / modify
- `src/main/webapp/WEB-INF/templates/main.xhtml`
- `src/main/webapp/resources/css/petlee.css`
- `src/main/webapp/WEB-INF/faces-config.xml`
- `src/main/resources/messages.properties`

## Requirements
1. `main.xhtml` defines `<ui:insert>` regions named `title`, `content` and `footer`. Every later
   page uses `<ui:composition template="/WEB-INF/templates/main.xhtml">` and fills them.
2. The header shows the Pet-Lee brand and a navigation bar that is **state-aware**:
   - Logged out: Home, Login, Register.
   - Logged in: Home, Add Pet, My Listings, Logout, and the user's `fullName`.
   - Admin: the above plus Admin Panel.
   Rendering is driven by `#{userBean.loggedIn}` and `#{userBean.admin}` from T-26. Until T-26
   exists, stub the bean so this task can be completed and reviewed independently.
3. A global `<h:messages globalOnly="true"/>` region so every page inherits error and success
   feedback with no per-page wiring.
4. `petlee.css` is plain, hand-written CSS served from `src/main/webapp/resources` via
   `<h:outputStylesheet library="css" name="petlee.css"/>`. **No CDN link** — the app must run
   with no internet access. Provide: a responsive gallery grid (`grid-template-columns:
   repeat(auto-fill, minmax(240px, 1fr))`), card, form, button, and message styles.
5. Readable at 360 px and at 1440 px. The specification does not demand responsiveness, but a
   fixed-width gallery is unusable on the phone a reviewer will inevitably try.
6. All user-facing text comes from `messages.properties`, registered as `<resource-bundle>` with
   variable `msg` in `faces-config.xml`. No hard-coded English in any XHTML file — this is what
   keeps later Hebrew localisation from being a rewrite.
7. `faces-config.xml` declares the bundle and any navigation rules; prefer implicit navigation and
   keep the file minimal.
8. Every image carries meaningful `alt` text and every form input a `<h:outputLabel for="...">`.

## Out of scope
- No page content (T-27 onward).
- No managed beans (T-26, T-28).
- No CSS framework, no JavaScript build step.

## Acceptance criteria
1. A page consisting only of a `ui:composition` renders the full header, footer and styling.
2. With the user bean reporting logged-out, Login and Register appear and Add Pet does not; logged
   in, the reverse. Admin Panel appears only for an admin.
3. `grep -r "cdn\|https://" src/main/webapp/resources/` returns nothing.
4. The gallery grid reflows from one column at 360 px to multiple columns at 1440 px.
5. `grep -oE '>[A-Za-z ]{4,}<' ` over the XHTML files finds no hard-coded user-facing sentence
   outside `#{msg.*}` expressions.
6. The page validates as well-formed XML (Facelets fails hard on malformed markup).

## Definition of Done
- [ ] All six acceptance criteria demonstrated, criteria 2 and 4 with screenshots.
- [ ] `messages.properties` holds every string used by the shell.
- [ ] No inline `style=` attributes; all styling lives in `petlee.css`.
