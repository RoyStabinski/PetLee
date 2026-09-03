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
ON CONFLICT (category_name) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Administrator account — BLOCKED ON T-10
-- ---------------------------------------------------------------------------
-- The single ADMIN user (admin / Admin123!) that specification §8's admin screens need.
--
-- password_hash must be a real PBKDF2 digest produced by the T-10 PasswordHasher, in the
-- exact encoding it emits. It is deliberately NOT written here: a placeholder string would
-- either fail verification silently or, worse, be mistaken for a working credential.
--
-- To enable, run T-10's hasher over "Admin123!", paste the result below, and uncomment.
--
-- INSERT INTO users (user_name, password_hash, full_name, email, phone_number, region, role, created_at)
-- VALUES ('admin',
--         '<PBKDF2 digest from T-10 PasswordHasher for Admin123!>',
--         'System Administrator',
--         'admin@petlee.local',
--         NULL,
--         NULL,
--         'ADMIN',
--         CURRENT_TIMESTAMP)
-- ON CONFLICT (user_name) DO NOTHING;
