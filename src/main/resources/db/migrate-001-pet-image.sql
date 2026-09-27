-- Migration 001: one photograph per pet (pet.image_url) becomes one or more (pet_image).
--
-- For an existing database only; a fresh install gets pet_image from schema.sql. Run as postgres,
-- after a backup, while the application is stopped:
--
--   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-001-pet-image.sql
--
-- One transaction: on any error nothing is changed. Re-running it after success is harmless: the
-- copy and the column drop only happen while pet.image_url still exists.

BEGIN;

CREATE TABLE IF NOT EXISTS pet_image (
    image_id    BIGSERIAL    PRIMARY KEY,
    -- Deleting a pet deletes its image rows; the application deletes the files.
    pet_id      BIGINT       NOT NULL REFERENCES pet (pet_id) ON DELETE CASCADE,
    image_url   VARCHAR(512) NOT NULL,
    is_main     BOOLEAN      NOT NULL DEFAULT FALSE,
    uploaded_at TIMESTAMP    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pet_image_pet ON pet_image (pet_id);

-- At most one main image per pet, enforced by the database rather than trusted to the code.
CREATE UNIQUE INDEX IF NOT EXISTS ux_pet_image_main ON pet_image (pet_id) WHERE is_main;

DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_schema = 'public'
                 AND table_name = 'pet'
                 AND column_name = 'image_url') THEN

        -- Each existing photograph becomes its pet's main image, dated when the pet was listed.
        -- A blank URL was never a real photograph (the pages showed the placeholder), so it is
        -- not copied. The NOT EXISTS keeps a pet that somehow already has images untouched.
        INSERT INTO pet_image (pet_id, image_url, is_main, uploaded_at)
        SELECT p.pet_id, p.image_url, TRUE, p.created_at
        FROM pet p
        WHERE p.image_url IS NOT NULL
          AND btrim(p.image_url) <> ''
          AND NOT EXISTS (SELECT 1 FROM pet_image i WHERE i.pet_id = p.pet_id);

        ALTER TABLE pet DROP COLUMN image_url;
    END IF;
END
$$;

-- The original grants named the tables that existed then, so the new table and its sequence
-- need their own. Fails, and rolls everything back, if the role does not exist yet.
GRANT SELECT, INSERT, UPDATE, DELETE ON pet_image TO petlee_app;
GRANT USAGE, SELECT ON SEQUENCE pet_image_image_id_seq TO petlee_app;

COMMIT;
