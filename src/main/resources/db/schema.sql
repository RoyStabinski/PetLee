-- Pet-Lee schema (T-03).
--
-- The database owns the schema; the JPA provider never creates or alters it
-- (jakarta.persistence.schema-generation.database.action=none in persistence.xml).
-- Apply this file to an empty 'petlee' database, then seed.sql. See db/README.md.
--
-- Table names are singular except 'users', matching the @Table annotations on the
-- entities: users is plural because USER is a reserved word in SQL.
--
-- Every statement is idempotent: re-running this file on an already-migrated database
-- must succeed and change nothing.

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id       BIGSERIAL    PRIMARY KEY,
    user_name     VARCHAR(20)  NOT NULL UNIQUE,
    -- Stores a PBKDF2 digest (T-10), never a password. The column name says so.
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(50)  NOT NULL,
    -- No column-level UNIQUE: uniqueness is case-insensitive here and is enforced by
    -- ux_users_email_lower below, which implies the exact-match constraint anyway.
    email         VARCHAR(100) NOT NULL,
    -- 20, not 10: api-contract.md's own registration example sends "050-1234567", which is 11
    -- characters. At VARCHAR(10) the contract's example body failed the INSERT outright. 20 also
    -- leaves room for an international format.
    phone_number  VARCHAR(20),
    -- ADR-002 #1: POST /api/users/register sends "region" and the contract is frozen,
    -- so the schema carries it. Nullable, and never returned in UserDTO.
    region        VARCHAR(100),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- One mailbox, one account. UserRepository.existsByEmail compares
-- LOWER(u.email) = LOWER(:email), so the database must agree: a plain UNIQUE on the column
-- compares exactly and would accept A@B.com beside a stored a@b.com, letting one mailbox
-- register twice whenever two registrations race past the Java check. A functional unique
-- index makes it the database's decision, and gives that query an index it can actually use —
-- LOWER(email) cannot use a plain index on email.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (LOWER(email));

-- Migration for databases created before ux_users_email_lower existed: their email column
-- still carries the exact-match UNIQUE that the index above supersedes. Dropping it is a
-- no-op on a fresh database and on every re-run, so the file stays re-runnable.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

-- Migration for databases created while phone_number was VARCHAR(10). Widening a varchar needs
-- no table rewrite, and re-running it on an already-widened column changes nothing, so the file
-- stays re-runnable.
ALTER TABLE users ALTER COLUMN phone_number TYPE VARCHAR(20);

-- ---------------------------------------------------------------------------
-- category
-- ---------------------------------------------------------------------------
-- The UNIQUE constraint on category_name is what makes specification §2's
-- "preventing duplicate listings" real at the category level. Enforcing it here rather
-- than only in Java means two concurrent admin requests cannot both win the check.
CREATE TABLE IF NOT EXISTS category (
    category_id   SERIAL      PRIMARY KEY,
    -- No column-level UNIQUE: uniqueness is case-insensitive here and is enforced by
    -- ux_category_name_lower below, which implies the exact-match constraint anyway.
    category_name VARCHAR(50) NOT NULL
);

-- One label, one category. CategoryRepository.existsByName compares
-- LOWER(c.categoryName) = LOWER(:name), so the database must agree: a plain UNIQUE on the
-- column compares exactly and would accept 'dogs' beside a stored 'Dogs', giving the filter
-- dropdown two entries that read the same. Same reasoning, and same fix, as
-- ux_users_email_lower above.
CREATE UNIQUE INDEX IF NOT EXISTS ux_category_name_lower ON category (LOWER(category_name));

-- Migration for databases created before ux_category_name_lower existed. A no-op on a fresh
-- database and on every re-run, so the file stays re-runnable.
ALTER TABLE category DROP CONSTRAINT IF EXISTS category_category_name_key;

-- ---------------------------------------------------------------------------
-- pet
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pet (
    pet_id      BIGSERIAL    PRIMARY KEY,
    pet_name    VARCHAR(100) NOT NULL,
    breed       VARCHAR(100),
    age         INTEGER,
    gender      VARCHAR(10)  NOT NULL,
    size        VARCHAR(10)  NOT NULL,
    short_desc  VARCHAR(255),
    long_desc   TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    -- Specification §5: "every posted pet must belong to a predefined category".
    -- ON DELETE RESTRICT is that rule as a database guarantee — a category holding live
    -- listings cannot be deleted out from under them. T-34's admin category delete must
    -- catch the resulting constraint violation and answer 409, not 500.
    category_id INTEGER      NOT NULL REFERENCES category (category_id) ON DELETE RESTRICT,
    -- Specification §5: each listing has exactly one owner. Deleting a user removes their
    -- listings with them.
    owner_id    BIGINT       NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    -- Ordering key for the gallery, "newest to oldest" (specification §11). The entity maps
    -- it updatable = false (ADR-002 #4) so an edit cannot silently reorder the gallery.
    created_at  TIMESTAMP    NOT NULL,
    -- Optimistic locking for @Version (T-04). Concurrent edits to one listing fail the
    -- second writer rather than losing their write (specification §4, concurrency control).
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pet_age    CHECK (age >= 0 AND age <= 50),
    CONSTRAINT ck_pet_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_pet_size   CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT ck_pet_status CHECK (status IN ('AVAILABLE', 'ADOPTED', 'REMOVED'))
);

-- ---------------------------------------------------------------------------
-- pet_image
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS pet_image (
    image_id   SERIAL       PRIMARY KEY,
    -- Specification §11 requires images to disappear with their pet.
    pet_id     BIGINT       NOT NULL REFERENCES pet (pet_id) ON DELETE CASCADE,
    image_url  VARCHAR(512) NOT NULL,
    is_main    BOOLEAN      NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP    NOT NULL
);

-- At most one main image per pet. A partial unique index makes T-16's invariant a database
-- guarantee: two concurrent "set as main" requests cannot both succeed, which a Java-side
-- check-then-write cannot prevent. Rows with is_main = FALSE are unconstrained.
CREATE UNIQUE INDEX IF NOT EXISTS ux_pet_image_main
    ON pet_image (pet_id)
    WHERE is_main = TRUE;

-- ---------------------------------------------------------------------------
-- Indexes supporting the gallery query (T-08)
-- ---------------------------------------------------------------------------
-- The gallery lists newest first, optionally filtered by category and restricted to
-- AVAILABLE pets. These three cover the sort key and both filter predicates.
CREATE INDEX IF NOT EXISTS idx_pet_created_at ON pet (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pet_category   ON pet (category_id);
CREATE INDEX IF NOT EXISTS idx_pet_status     ON pet (status);
