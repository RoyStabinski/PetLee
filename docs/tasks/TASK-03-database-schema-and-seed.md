# T-03 · PostgreSQL schema and seed data

| Field | Value |
|---|---|
| **Phase** | 0 — Foundation |
| **Depends on** | T-01 |
| **Blocks** | T-02 (validation), T-05…T-09, T-36 |
| **Estimate** | 3h |

## Goal
Own the database schema as versioned SQL so every environment is identical and Hibernate can run
in `validate` mode.

## Scope — files to create / modify
- `src/main/resources/db/schema.sql`
- `src/main/resources/db/seed.sql`
- `src/main/resources/db/README.md` (how to apply both, in order)

## Requirements
1. Four tables exactly matching specification §11 and the entity mappings finalised in T-04:
   `users`, `category`, `pet`, `pet_image`. Table names must match the `@Table` annotations —
   note the existing entities use singular `pet`, `category`, `pet_image` and plural `users`.
2. `users`: `user_id BIGSERIAL PK`, `user_name VARCHAR(20) NOT NULL UNIQUE`,
   `password_hash VARCHAR(255) NOT NULL`, `full_name VARCHAR(50) NOT NULL`,
   `email VARCHAR(100) NOT NULL UNIQUE`, `phone_number VARCHAR(10)`,
   `region VARCHAR(100)` (see ADR-002 #1), `role VARCHAR(20) NOT NULL DEFAULT 'USER'`
   with `CHECK (role IN ('USER','ADMIN'))`, `created_at TIMESTAMP NOT NULL`.
3. `category`: `category_id SERIAL PK`, `category_name VARCHAR(50) NOT NULL UNIQUE`.
   The UNIQUE constraint is what makes specification §2's "preventing duplicate listings" real at
   the category level — enforce it in the database, not only in Java.
4. `pet`: `pet_id BIGSERIAL PK`, `pet_name VARCHAR(100) NOT NULL`, `breed VARCHAR(100)`,
   `age INTEGER CHECK (age >= 0 AND age <= 50)`,
   `gender VARCHAR(10) NOT NULL CHECK (gender IN ('MALE','FEMALE'))`,
   `size VARCHAR(10) NOT NULL CHECK (size IN ('SMALL','MEDIUM','LARGE'))`,
   `short_desc VARCHAR(255)`, `long_desc TEXT`,
   `status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE','ADOPTED','REMOVED'))`,
   `category_id INTEGER NOT NULL REFERENCES category(category_id) ON DELETE RESTRICT`,
   `owner_id BIGINT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE`,
   `created_at TIMESTAMP NOT NULL`, `version BIGINT NOT NULL DEFAULT 0`.
5. `ON DELETE RESTRICT` on `category_id` enforces specification §5 *"Every posted pet must belong
   to a predefined category"* — a category with live listings cannot be deleted out from under them.
   T-34's admin category delete must surface this as a 409, not a 500.
6. `pet_image`: `image_id SERIAL PK`,
   `pet_id BIGINT NOT NULL REFERENCES pet(pet_id) ON DELETE CASCADE` (specification §11 requires
   cascade delete on pet removal), `image_url VARCHAR(512) NOT NULL`,
   `is_main BOOLEAN NOT NULL DEFAULT FALSE`, `updated_at TIMESTAMP NOT NULL`.
7. Partial unique index guaranteeing at most one main image per pet:
   `CREATE UNIQUE INDEX ux_pet_image_main ON pet_image (pet_id) WHERE is_main = TRUE;`
   This makes T-16's invariant a database guarantee rather than a hopeful Java check.
8. Performance indexes for the gallery query (T-08): `idx_pet_created_at` on `pet (created_at DESC)`,
   `idx_pet_category` on `pet (category_id)`, `idx_pet_status` on `pet (status)`.
9. `seed.sql` inserts the categories named in specification §1 — `Dogs`, `Cats`, `Fish`, `Rodents`,
   `Birds`, `Reptiles` — and exactly one `ADMIN` user (`admin` / `Admin123!`) whose
   `password_hash` is a **real PBKDF2 digest produced by T-10**, not a placeholder. Until T-10
   lands, leave the admin insert commented out with a note naming T-10.
10. Both scripts are idempotent: `CREATE TABLE IF NOT EXISTS`, and seed inserts use
    `ON CONFLICT DO NOTHING`. Re-running a script must never fail a build.

## Out of scope
- No migration tool (Flyway/Liquibase). Plain SQL applied manually; T-42 documents the procedure.
- Do not change entity classes here — that is T-04.
- No test data beyond categories and the admin user. Demo pets belong to T-40.

## Acceptance criteria
1. `psql -d petlee -f schema.sql` succeeds on an empty database, and succeeds again unchanged when
   re-run.
2. `psql -d petlee -f seed.sql` inserts 6 categories; re-running leaves the count at 6.
3. The WAR deploys against this schema and T-38's persistence tests pass — the definitive proof
   that schema and entities agree. (Provider-specific `validate` is unavailable by design: ADR-003
   keeps `persistence.xml` provider-agnostic, so the check lives in the test suite instead.)
4. `INSERT` of a second `is_main = TRUE` row for the same `pet_id` is rejected by the unique index.
5. `DELETE FROM pet WHERE pet_id = X` removes that pet's `pet_image` rows automatically.
6. `DELETE FROM category` for a category holding pets is rejected by the FK constraint.

## Definition of Done
- [ ] Both scripts run clean on a fresh database, twice in a row.
- [ ] Every column in specification §11 is present, and every column present is in §11 or ADR-002.
- [ ] `db/README.md` states the exact `createdb` and `psql` commands, in order.
- [ ] Acceptance criteria 4, 5 and 6 demonstrated with pasted `psql` output.
