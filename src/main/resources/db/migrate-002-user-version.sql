-- Migration 002: users.version, the optimistic lock behind the edit-profile form.
--
-- For an existing database only; a fresh install gets the column from schema.sql. Run as postgres:
--
--   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-002-user-version.sql
--
-- Only adds a column, with a default, so existing rows need no rewrite and no new grant is needed.
-- Re-running it is harmless.

ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
