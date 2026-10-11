-- Migration 004: an adjustable focal point for pet photographs and profile photos
-- (pet_image.focus_x and focus_y, users.avatar_focus_x and avatar_focus_y).
--
-- For an existing database only; a fresh install gets all four columns from schema.sql. Run as
-- postgres, after a backup; the application may keep running, since the old build neither reads
-- nor writes these columns:
--
--   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-004-image-focus.sql
--
-- One transaction: on any error nothing is changed. Re-running it after success is harmless:
-- the columns are ADD COLUMN IF NOT EXISTS, and each CHECK constraint is added only when no
-- constraint of that name exists on the table yet (PostgreSQL has no ADD CONSTRAINT IF NOT EXISTS).

BEGIN;

-- Percent of the width and the height; 50/50, the centre, is what every existing photo had
-- until now. The DEFAULT fills every existing row, so NOT NULL holds at once.
ALTER TABLE pet_image ADD COLUMN IF NOT EXISTS focus_x SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE pet_image ADD COLUMN IF NOT EXISTS focus_y SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_focus_x SMALLINT NOT NULL DEFAULT 50;
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_focus_y SMALLINT NOT NULL DEFAULT 50;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_pet_image_focus_x' AND conrelid = 'pet_image'::regclass) THEN
        ALTER TABLE pet_image ADD CONSTRAINT ck_pet_image_focus_x CHECK (focus_x BETWEEN 0 AND 100);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_pet_image_focus_y' AND conrelid = 'pet_image'::regclass) THEN
        ALTER TABLE pet_image ADD CONSTRAINT ck_pet_image_focus_y CHECK (focus_y BETWEEN 0 AND 100);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_users_avatar_focus_x' AND conrelid = 'users'::regclass) THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_avatar_focus_x CHECK (avatar_focus_x BETWEEN 0 AND 100);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'ck_users_avatar_focus_y' AND conrelid = 'users'::regclass) THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_avatar_focus_y CHECK (avatar_focus_y BETWEEN 0 AND 100);
    END IF;
END
$$;

-- No GRANT, as in 003: these are new columns of existing tables, and petlee_app's SELECT,
-- INSERT, UPDATE and DELETE were granted on each table as a whole, which covers every column it
-- has or gains. No new table or sequence is involved.

COMMIT;
