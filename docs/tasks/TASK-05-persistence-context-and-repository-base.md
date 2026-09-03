# T-05 · Persistence context injection and repository base

| Field | Value |
|---|---|
| **Phase** | 1 — Data access |
| **Depends on** | T-02, T-04 |
| **Blocks** | T-06, T-07, T-08, T-09 |
| **Estimate** | 2h |

## Goal
Establish how repositories obtain an `EntityManager` and where transaction boundaries sit, using
the container's own facilities rather than hand-written lifecycle code.

## Scope — files to create / modify
- `src/main/java/com/petlee/repository/AbstractRepository.java`

## Requirements
1. `AbstractRepository<T, ID>` is an `@ApplicationScoped` CDI bean holding
   `@PersistenceContext private EntityManager em;`. The container injects, manages and closes it.
2. **No `EntityManagerFactory`, no `createEntityManager()`, no `em.close()`, and no
   `getTransaction().begin()` anywhere in the project.** The container owns all four. Any
   occurrence is a review blocker — the earlier draft's `TxRunner`/`JpaUtil` are deleted by ADR-003
   and must not reappear.
3. Provide `findById`, `findAll`, `save` (persist when the id is null, merge otherwise), `delete`
   and `count`. Subclasses supply the entity class and their own queries and nothing else.
4. **Transaction policy, stated once here and followed everywhere:** transactions are demarcated in
   the *service* layer with `@Transactional` (Jakarta Transactions), never in repositories.
   Repositories are transaction-agnostic so a service can compose several repository calls into one
   atomic unit — which T-16 needs when it writes an image row and clears a main flag together.
5. Read-only service methods carry `@Transactional(Transactional.TxType.SUPPORTS)`; mutating ones
   the default `REQUIRED`.
6. `jakarta.persistence.OptimisticLockException` must reach the service layer with its type intact,
   because T-15 distinguishes it to return 409 rather than 500. Do not catch it here.
7. Logging via `java.util.logging` — part of the JDK, no dependency. No `printStackTrace()`.

## Out of scope
- No entity-specific queries (T-06…T-09).
- No transaction helper class, no manual `EntityManager` lifecycle (requirement 2).
- No caching layer.

## Acceptance criteria
1. A repository injected into a service performs a `save` inside a `@Transactional` method and the
   row is committed.
2. A service method that throws after a `save` leaves **no** row — proving the container rolled the
   transaction back with no rollback code written by hand.
3. `grep -rE "createEntityManager|EntityManagerFactory|getTransaction\(\)" src/main/java/` returns
   nothing.
4. Two repository calls inside one `@Transactional` service method share a transaction — verify by
   making the second fail and asserting the first is rolled back too.
5. An `OptimisticLockException` raised by the provider reaches the service layer unwrapped.
6. `AbstractRepository` compiles with no reference to any provider-specific type.

## Definition of Done
- [ ] `mvn clean package` succeeds.
- [ ] All six acceptance criteria demonstrated; criterion 3 is an architectural gate.
- [ ] The transaction policy in requirement 4 is reproduced in the class Javadoc, so later tasks
      find it without opening this file.
