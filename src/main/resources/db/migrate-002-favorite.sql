-- Migration 002: members can save pets as favourites (table favorite).
--
-- For an existing database only; a fresh install gets favorite from schema.sql. Run as postgres,
-- after a backup; the application may keep running, since nothing it uses today changes:
--
--   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-002-favorite.sql
--
-- One transaction: on any error nothing is changed. Re-running it after success is harmless:
-- every statement is IF NOT EXISTS, and repeating a grant is a no-op.

BEGIN;

CREATE TABLE IF NOT EXISTS favorite (
    -- Deleting the member or the pet deletes the saved row with it.
    user_id    BIGINT    NOT NULL REFERENCES users (user_id) ON DELETE CASCADE,
    pet_id     BIGINT    NOT NULL REFERENCES pet (pet_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    -- Also what makes saving idempotent: a second insert of the same pair is refused.
    PRIMARY KEY (user_id, pet_id)
);

-- The primary key serves lookups by member; this one serves the cascade when a pet is deleted.
CREATE INDEX IF NOT EXISTS idx_favorite_pet ON favorite (pet_id);

-- The original grants named the tables that existed then, so the new table needs its own. No
-- UPDATE: a favourite is only ever inserted or deleted. No sequence: the key is the pair.
-- Fails, and rolls everything back, if the role does not exist yet.
GRANT SELECT, INSERT, DELETE ON favorite TO petlee_app;

COMMIT;
