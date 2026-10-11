-- Migration 003: members can edit their profile and add a profile photo (users.avatar_url,
-- users.version).
--
-- For an existing database only; a fresh install gets both columns from schema.sql. Run as
-- postgres, after a backup; the application may keep running, since the old build neither reads
-- nor writes either column:
--
--   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-003-user-profile.sql
--
-- One transaction: on any error nothing is changed. Re-running it after success is harmless:
-- both statements are IF NOT EXISTS.

BEGIN;

-- The profile photo's URL under /images/, or NULL for the initials circle.
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(512);

-- Optimistic locking, for @Version on the User entity. The DEFAULT fills every existing row
-- with 0, so NOT NULL holds at once.
ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

-- No GRANT, unlike 001 and 002. Those created new tables, which the original grants could not
-- name. These are new columns of an existing table, and petlee_app's SELECT, INSERT, UPDATE and
-- DELETE were granted on the table as a whole, which covers every column it has or gains. No
-- sequence is involved either.

COMMIT;
