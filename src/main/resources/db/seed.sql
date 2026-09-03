-- Pet-Lee reference data. Apply after schema.sql. Idempotent: every insert is
-- ON CONFLICT DO NOTHING, so re-running leaves the row counts unchanged.

INSERT INTO category (category_name) VALUES
    ('Dogs'),
    ('Cats'),
    ('Birds'),
    ('Bunnies')
-- Inferred from ux_category_name_lower: plain "ON CONFLICT (category_name)" fails, because
-- inference matches the indexed expression, not the column.
ON CONFLICT (LOWER(category_name)) DO NOTHING;

-- The administrator account the admin panel needs: admin / Admin123!
-- A real PBKDF2-HMAC-SHA256 digest from PasswordHasher. A known demo credential in a seeded
-- database, not a secret - change it before any deployment other people can reach.
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
