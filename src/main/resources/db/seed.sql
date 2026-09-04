-- Pet-Lee seed data (T-03). Apply after schema.sql. See db/README.md.
--
-- Idempotent: every insert is ON CONFLICT DO NOTHING, so re-running this file leaves the
-- row counts unchanged. This is reference data, not test data — demo pets belong to T-40.

-- The six categories named in specification §1. Pets reference these by FK, and
-- ON DELETE RESTRICT means a category in use cannot be removed.
INSERT INTO category (category_name) VALUES
    ('Dogs'),
    ('Cats'),
    ('Fish'),
    ('Rodents'),
    ('Birds'),
    ('Reptiles')
-- Infer the conflict from ux_category_name_lower, the unique index on LOWER(category_name).
-- Plain "ON CONFLICT (category_name)" fails with "there is no unique or exclusion constraint
-- matching the ON CONFLICT specification" — the column's own UNIQUE was replaced by the
-- functional index, and inference matches the indexed expression, not the column.
ON CONFLICT (LOWER(category_name)) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Administrator account
-- ---------------------------------------------------------------------------
-- The single ADMIN user specification §8's admin screens need: admin / Admin123!
--
-- The digest below is a real PBKDF2-HMAC-SHA256 value emitted by T-10's PasswordHasher
-- (PasswordHasherCli "Admin123!"): 210 000 iterations, its own random salt, 90 characters,
-- inside password_hash VARCHAR(255). It was checked to verify against "Admin123!" before being
-- pasted here, so a failed admin login means a code change, not a bad paste.
--
-- This is a known demo credential in a seeded reference database, not a secret. T-42's runbook
-- changes it before any deployment other people can reach.
INSERT INTO users (user_name, password_hash, full_name, email, phone_number, region, role, created_at)
VALUES ('admin',
        'pbkdf2_sha256$210000$AflWj/mcDXuVECDgN2QhEg==$xyXdFLYbGqYvklSvSccsiRTh2F1Gs+lvmQApdARlwts=',
        'System Administrator',
        'admin@petlee.local',
        NULL,
        NULL,
        'ADMIN',
        CURRENT_TIMESTAMP)
ON CONFLICT (user_name) DO NOTHING;
