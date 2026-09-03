# T-12 · Domain exception hierarchy

| Field | Value |
|---|---|
| **Phase** | 4 — Business logic |
| **Depends on** | T-01 |
| **Blocks** | T-13, T-14, T-15, T-16, T-19 |
| **Estimate** | 1.5h |

## Goal
Give the service layer a vocabulary for failure that the REST layer can translate into the exact
HTTP status codes `api-contract.md` promises — without services importing anything from JAX-RS.

## Scope — files to create / modify
- `src/main/java/com/petlee/exception/PetLeeException.java` (abstract base)
- `src/main/java/com/petlee/exception/NotFoundException.java`
- `src/main/java/com/petlee/exception/ConflictException.java`
- `src/main/java/com/petlee/exception/ForbiddenException.java`
- `src/main/java/com/petlee/exception/UnauthorizedException.java`
- `src/main/java/com/petlee/exception/ValidationException.java`

## Requirements
1. All extend `RuntimeException` via `PetLeeException`. Checked exceptions would force `throws`
   clauses through every layer for conditions that are never locally recoverable.
2. `PetLeeException` carries a `message` (safe to show a user) and a `code` (short machine token,
   e.g. `USERNAME_TAKEN`, `NOT_OWNER`, `STALE_PET`). T-19 puts both into `ErrorDTO`.
3. The intended mapping, which T-19 implements and which must be stated in each class's Javadoc:

   | Exception | HTTP | Contract reference |
   |---|---|---|
   | `ValidationException` | 400 | malformed request body |
   | `UnauthorizedException` | 401 | *"401 if credentials are wrong"*, *"reject with 401 if not logged in"* |
   | `ForbiddenException` | 403 | *"403 if not the owner"*, *"403 if not owner and not admin"* |
   | `NotFoundException` | 404 | unknown pet / user id |
   | `ConflictException` | 409 | *"409 if username/email already exists"*, *"409 if a concurrent edit happened"* |

4. Convenience constructors: `(String message)`, `(String code, String message)`, and
   `(String code, String message, Throwable cause)`.
5. `ValidationException` additionally carries an optional field name, so T-27/T-31 can attach the
   message to the right JSF input.
6. **No JAX-RS, servlet, or JPA imports** anywhere in this package. These types are thrown by
   services and also consumed by T-24 in the web tier; a dependency on either transport would make
   the hierarchy unusable on one side.

## Out of scope
- No `ExceptionMapper` implementations — that is T-19.
- No logging inside the exception classes; T-19 owns logging policy.

## Acceptance criteria
1. Every class compiles and is `public`.
2. `new ConflictException("USERNAME_TAKEN", "Username already in use").getCode()` returns
   `USERNAME_TAKEN`.
3. The `code` is never null: the single-argument constructor supplies a sensible default derived
   from the class name.
4. `grep -rE "jakarta.(ws|servlet|persistence)" src/main/java/com/petlee/exception/` returns nothing.
5. Each class's Javadoc names the HTTP status it maps to and quotes the contract line that requires it.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All five acceptance criteria demonstrated.
- [ ] The mapping table above is reproduced in `PetLeeException`'s Javadoc, so the next engineer
      finds it without opening this task file.
