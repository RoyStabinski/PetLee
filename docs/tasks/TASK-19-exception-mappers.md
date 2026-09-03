# T-19 · Exception mappers

| Field | Value |
|---|---|
| **Phase** | 5 — REST tier |
| **Depends on** | T-12, T-17 |
| **Blocks** | T-20, T-21, T-22, T-23 |
| **Estimate** | 3h |

## Goal
Translate domain exceptions into the exact HTTP status codes `api-contract.md` promises, so no
resource method ever writes a `try/catch` for error shaping.

## Scope — files to create / modify
- `src/main/java/com/petlee/rest/mapper/PetLeeExceptionMapper.java`
- `src/main/java/com/petlee/rest/mapper/GenericExceptionMapper.java`
- `src/main/java/com/petlee/rest/mapper/JsonbParseExceptionMapper.java`

## Requirements
1. `PetLeeExceptionMapper` is a `@Provider ExceptionMapper<PetLeeException>` implementing exactly
   the table in T-12: `ValidationException`→400, `UnauthorizedException`→401,
   `ForbiddenException`→403, `NotFoundException`→404, `ConflictException`→409.
2. Every response body is an `ErrorDTO` with `Content-Type: application/json`. A JAX-RS default
   HTML error page would break every client, including T-24.
3. `GenericExceptionMapper` catches `Throwable` and returns **500** with a generic message
   (`"An unexpected error occurred."`) and code `INTERNAL_ERROR`. It logs the full stack trace
   server-side at `SEVERE` **and returns none of it to the client** — a stack trace tells an
   attacker your framework versions, class layout and SQL structure.
4. `JsonbParseExceptionMapper` turns malformed request JSON into **400** with code `MALFORMED_JSON`,
   rather than the 500 Jersey produces by default.
5. Include a short correlation id (a random 8-character token) in both the log line and the
   `ErrorDTO` message for 500s, so a user-reported failure can be found in the log.
6. Log levels: 4xx at `FINE` (these are ordinary client mistakes and must not fill the log), 5xx at
   `SEVERE`.
7. `WebApplicationException` thrown deliberately by a resource (including T-18's 401/403 aborts)
   must keep its status — do not let `GenericExceptionMapper` rewrite it to 500. Verify the
   mapper-selection order.
8. `OptimisticLockException` escaping from the persistence layer without being converted by T-15 is
   still mapped to **409**, not 500, as a safety net.

## Out of scope
- No JSF error pages (T-33).
- Mappers do not perform authorisation or validation; they only translate.

## Acceptance criteria
1. Registering a duplicate username returns `409` with `{"code":"USERNAME_TAKEN", ...}` and
   `Content-Type: application/json`.
2. Deleting another user's pet returns `403` with a JSON `ErrorDTO`.
3. `GET /api/pets/999999` returns `404` with a JSON `ErrorDTO`.
4. `POST /api/pets` with body `{ not json` returns `400` code `MALFORMED_JSON`.
5. An endpoint made to throw `new RuntimeException("boom")` returns `500` whose body contains
   neither `boom`, nor a class name, nor a stack frame — while the server log contains all three
   plus a correlation id matching the response.
6. A 401 from T-18's filter arrives as 401, not 500.
7. Every error response in criteria 1–6 parses as JSON.

## Definition of Done
- [ ] All seven acceptance criteria demonstrated with `curl -i` output pasted in the PR.
- [ ] No `printStackTrace()` anywhere in the project.
- [ ] No resource class contains a `catch` block for error-response shaping.
