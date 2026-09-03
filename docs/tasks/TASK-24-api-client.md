# T-24 · ApiClient — the server communication layer

| Field | Value |
|---|---|
| **Phase** | 6 — Server communication |
| **Depends on** | T-11, T-20, T-21, T-22, T-23 |
| **Blocks** | T-26, T-28, T-31, T-32, T-35 |
| **Estimate** | 7h |

## Goal
Build the single class through which the JSF tier reaches the business tier, over HTTP, as
specification §8 requires. **This is the seam the whole architecture rests on** (see ADR-001).

## Scope — files to create / modify
- `src/main/java/com/petlee/web/client/ApiClient.java`
- `src/main/java/com/petlee/web/client/ApiException.java`

## Requirements
1. `@ApplicationScoped ApiClient` wrapping one shared `jakarta.ws.rs.client.Client` — the Jakarta
   REST Client API, specification §7's own technology and part of the platform. Creating a `Client`
   per call leaks connections and is slow; build it once and close it in a `@PreDestroy`.
2. Base URI is **derived at runtime**, never hard-coded: from the current
   `HttpServletRequest` (scheme, server name, port, context path) plus `/api`. A hard-coded
   `localhost:8080` breaks the moment the app is deployed anywhere else.
3. **Cookie forwarding is the core mechanism.** Every outbound call copies the inbound
   `JSESSIONID` cookie from the browser's request onto the API request. Because both servlets are
   in one WAR (ADR-001), the REST tier then resolves the same `HttpSession` and T-18 sees the
   logged-in user. Without this, every authenticated call returns 401 — the single most likely
   failure in this task.
4. Typed methods mirroring the contract, all returning DTOs from T-11:
   - `UserDTO register(RegisterForm)` · `UserDTO login(LoginForm)` · `void logout()`
   - `List<CategoryDTO> getCategories()`
   - `List<PetDTO> getPets(Integer categoryId, String size, String gender)`
   - `PetDetailDTO getPet(Long id)`
   - `PetDTO createPet(PetForm)` · `PetDTO updatePet(Long id, PetForm)` · `void deletePet(Long id)`
   - `List<PetDTO> getMyPets()` · `PetImageDTO uploadImage(Long petId, InputStream, String filename, boolean isMain)`
5. Response translation: any non-2xx status is read as an `ErrorDTO` and rethrown as an
   `ApiException` carrying the status, code and message. Managed beans then render the message as a
   `FacesMessage` — they must never inspect raw HTTP status codes themselves.
6. `login` captures the `Set-Cookie` from the API response and applies it to the browser response,
   so session rotation from T-18 reaches the user's browser. Get this wrong and users appear
   logged out immediately after logging in.
7. Connect and read timeouts of 5 s and 10 s. An unbounded wait would hang a rendering thread.
8. `uploadImage` builds the `multipart/form-data` body by hand — boundary, part headers, payload —
   and POSTs it as a byte array with the matching `Content-Type`. About thirty lines, and it avoids
   the Jersey-specific multipart module ADR-003 forbids, so the code deploys on any Jakarta EE
   server.
9. `ApiClient` is the **only** class in `com.petlee.web` permitted to perform I/O. Per ADR-001, any
   `import com.petlee.service.*` or `com.petlee.repository.*` in the web package is a review blocker.
10. Log the method and path of every call at `FINE`, with status and elapsed time. Never log
    request bodies — they carry passwords.

## Out of scope
- No UI, no `FacesMessage`, no navigation (T-26 onward).
- No caching of API responses.
- No retry logic. A failed call surfaces to the user; silent retries on a POST would risk
  duplicate listings.

## Acceptance criteria
1. `getCategories()` from a managed bean returns 6 DTOs, and the server access log shows a real
   `GET /pet-lee/api/categories` request — proving the HTTP hop genuinely happens.
2. After `login`, a subsequent `createPet` succeeds, proving `JSESSIONID` forwarding works.
3. Without a session, `createPet` throws `ApiException` with status 401 and code
   `NOT_AUTHENTICATED`.
4. `deletePet` on another user's pet throws `ApiException` with status 403.
5. `updatePet` on a stale pet throws `ApiException` with status 409 and code `STALE_PET`.
6. Deploying under a different context path or port still works, with no code change — demonstrate
   by changing the port.
7. `logout()` followed by `getMyPets()` throws a 401 `ApiException`.
8. `grep -rE "import com.petlee.(service|repository)" src/main/java/com/petlee/web/` returns nothing.
9. Application logs contain no password after a login through the UI.

## Definition of Done
- [ ] All nine acceptance criteria demonstrated; criterion 8 is an architectural gate and must be
      checked in review.
- [ ] The base URI is derived, with no host or port literal anywhere in the class.
- [ ] `ApiClient`'s class Javadoc reproduces ADR-001's rule so the next engineer understands why
      the HTTP hop exists and does not "optimise" it away.
