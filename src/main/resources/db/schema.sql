-- Pet-Lee schema (T-03). The database owns the schema; the JPA provider never creates or
-- alters it. Apply this file to an empty 'petlee' database, then seed.sql. Every statement is
-- idempotent: re-running this file on an already-migrated database must succeed and change
-- nothing.

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id       BIGSERIAL    PRIMARY KEY,
    user_name     VARCHAR(20)  NOT NULL UNIQUE,
    -- Stores a PBKDF2 digest (T-10), never a password.
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(50)  NOT NULL,
    email         VARCHAR(100) NOT NULL,
    phone_number  VARCHAR(20),
    region        VARCHAR(100),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Case-insensitive uniqueness on email; a plain UNIQUE would let A@B.com sit beside a@b.com.
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_lower ON users (LOWER(email));

-- Migration: drop the exact-match UNIQUE that ux_users_email_lower supersedes.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_key;

-- Migration: widen phone_number from its original VARCHAR(10).
ALTER TABLE users ALTER COLUMN phone_number TYPE VARCHAR(20);

-- ---------------------------------------------------------------------------
-- category
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS category (
    category_id   SERIAL      PRIMARY KEY,
    category_name VARCHAR(50) NOT NULL
);

-- Case-insensitive uniqueness on category_name, same reasoning as ux_users_email_lower.
CREATE UNIQUE INDEX IF NOT EXISTS ux_category_name_lower ON category (LOWER(category_name));

-- Migration: drop the exact-match UNIQUE that ux_category_name_lower supersedes.
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
    -- A category holding live listings cannot be deleted out from under them.
    category_id INTEGER      NOT NULL REFERENCES category (category_id) ON DELETE RESTRICT,
    -- Deleting a user removes their listings with them.
    owner_id    BIGINT       NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    created_at  TIMESTAMP    NOT NULL,
    -- Optimistic locking for @Version (T-04).
    version     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_pet_age    CHECK (age >= 0 AND age <= 50),
    CONSTRAINT ck_pet_gender CHECK (gender IN ('MALE', 'FEMALE')),
    CONSTRAINT ck_pet_size   CHECK (size IN ('SMALL', 'MEDIUM', 'LARGE')),
    CONSTRAINT ck_pet_status CHECK (status IN ('AVAILABLE', 'ADOPTED', 'REMOVED'))
);

-- One photo per pet. The pet_image table and its partial unique index are gone:
-- a single nullable column carries what a whole table used to.
ALTER TABLE pet ADD COLUMN IF NOT EXISTS image_url VARCHAR(512);
DROP TABLE IF EXISTS pet_image;

-- ---------------------------------------------------------------------------
-- Indexes supporting the gallery query (T-08)
-- ---------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_pet_created_at ON pet (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_pet_category   ON pet (category_id);
