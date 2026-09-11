-- ---------------------------------------------------------------------------
-- Pet-Lee demo dataset (T-40)
--
-- A repeatable stage for the end-to-end walkthrough: three accounts, the six
-- seeded categories, and twelve listings spread across categories, sizes,
-- genders and dates, eight of them with a photograph.
--
-- Re-runnable. Every statement is guarded, so running it twice changes nothing
-- the second time - which is what lets a failed walkthrough be repeated from a
-- known state instead of from whatever the last attempt left behind.
--
--   psql -U postgres -d petlee -f docs/testing/demo-data.sql
--
-- It does NOT delete anything. To start completely clean, drop and re-create
-- the database with src/main/resources/db/README.md, then apply schema.sql,
-- seed.sql and this file.
-- ---------------------------------------------------------------------------

BEGIN;

-- ---------------------------------------------------------------------------
-- Categories - the six from specification section 1.
--
-- seed.sql already installs these; repeated here so the demo stage stands up on
-- a database that has only had schema.sql applied.
-- ---------------------------------------------------------------------------
INSERT INTO category (category_name)
SELECT name
FROM (VALUES ('Dogs'), ('Cats'), ('Fish'), ('Rodents'), ('Birds'), ('Reptiles')) AS wanted(name)
WHERE NOT EXISTS (
    SELECT 1 FROM category c WHERE LOWER(c.category_name) = LOWER(wanted.name)
);

-- ---------------------------------------------------------------------------
-- Accounts - two members and one administrator.
--
-- All three share the password Demo123!, and the digest below is a real
-- PBKDF2-HMAC-SHA256 value from T-10's PasswordHasherCli. A placeholder would
-- look like a working credential and would not be one.
--
-- These are demo accounts in a demo database. T-42's runbook does not create
-- them, and no deployment anyone else can reach should have them.
-- ---------------------------------------------------------------------------
INSERT INTO users (user_name, password_hash, full_name, email, phone_number, region, role, created_at)
SELECT * FROM (VALUES
    ('demo_owner',
     'pbkdf2_sha256$210000$5vg6dXawEEN8WC/ikjvIWw==$3iZY/CuMz2VIeDiSZJyOAMpMzU2Gf43tty8dClKyIiY=',
     'Dana Owner', 'dana.owner@petlee.demo', '050-1110001', 'Tel Aviv', 'USER',
     TIMESTAMP '2026-08-01 09:00:00'),
    ('demo_adopter',
     'pbkdf2_sha256$210000$5vg6dXawEEN8WC/ikjvIWw==$3iZY/CuMz2VIeDiSZJyOAMpMzU2Gf43tty8dClKyIiY=',
     'Adam Adopter', 'adam.adopter@petlee.demo', '050-2220002', 'Haifa', 'USER',
     TIMESTAMP '2026-08-01 09:05:00'),
    ('demo_admin',
     'pbkdf2_sha256$210000$5vg6dXawEEN8WC/ikjvIWw==$3iZY/CuMz2VIeDiSZJyOAMpMzU2Gf43tty8dClKyIiY=',
     'Mia Moderator', 'mia.moderator@petlee.demo', '050-3330003', 'Jerusalem', 'ADMIN',
     TIMESTAMP '2026-08-01 09:10:00')
) AS wanted(user_name, password_hash, full_name, email, phone_number, region, role, created_at)
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.user_name = wanted.user_name);

-- ---------------------------------------------------------------------------
-- Listings - twelve, eleven owned by demo_owner and one by demo_adopter, so the
-- "not your listing" paths have something to refuse.
--
-- created_at is explicit and spread over three weeks: the gallery is ordered
-- newest first, and a stage where every row was inserted in the same second
-- cannot demonstrate that.
--
-- Guarded on pet_name, which is unique among these twelve by construction.
-- ---------------------------------------------------------------------------
INSERT INTO pet (pet_name, breed, age, gender, size, short_desc, long_desc, status,
                 category_id, owner_id, created_at, version)
SELECT wanted.pet_name, wanted.breed, wanted.age, wanted.gender, wanted.size,
       wanted.short_desc, wanted.long_desc, wanted.status,
       (SELECT category_id FROM category WHERE LOWER(category_name) = LOWER(wanted.category)),
       (SELECT user_id FROM users WHERE user_name = wanted.owner),
       wanted.created_at, 0
FROM (VALUES
    ('Rex',      'Labrador',        3, 'MALE',   'LARGE',  'Friendly and energetic',
     'Rex walks well on a lead, knows sit and stay, and is happiest with a garden.',
     'AVAILABLE', 'Dogs',     'demo_owner',   TIMESTAMP '2026-08-20 10:00:00'),
    ('Bella',    'Border Collie',   2, 'FEMALE', 'MEDIUM', 'Clever and busy',
     'Bella needs a household that likes long walks. She learns a new trick in an afternoon.',
     'AVAILABLE', 'Dogs',     'demo_owner',   TIMESTAMP '2026-08-19 11:30:00'),
    ('Pip',      'Jack Russell',    5, 'MALE',   'SMALL',  'Small, loud, devoted',
     'Pip has lived with children and with an older cat, and got on with both.',
     'AVAILABLE', 'Dogs',     'demo_adopter', TIMESTAMP '2026-08-18 08:15:00'),
    ('Luna',     'Domestic short',  1, 'FEMALE', 'SMALL',  'Quiet lap cat',
     'Luna was found as a stray and has been indoors since. She hides for a day, then settles.',
     'AVAILABLE', 'Cats',     'demo_owner',   TIMESTAMP '2026-08-17 16:45:00'),
    ('Milo',     'Tabby',           4, 'MALE',   'MEDIUM', 'Sunbathing specialist',
     'Milo wants a windowsill and a quiet house. He is not interested in other cats.',
     'AVAILABLE', 'Cats',     'demo_owner',   TIMESTAMP '2026-08-16 12:00:00'),
    ('Nala',     'Siamese',         6, 'FEMALE', 'SMALL',  'Talkative and affectionate',
     'Nala will tell you about her day at length. Rehomed because of an allergy in the family.',
     'AVAILABLE', 'Cats',     'demo_owner',   TIMESTAMP '2026-08-15 09:20:00'),
    ('Bubbles',  'Goldfish',        1, 'FEMALE', 'SMALL',  'Two goldfish, one tank',
     'A pair of goldfish with their tank, filter and heater. Best kept together.',
     'AVAILABLE', 'Fish',     'demo_owner',   TIMESTAMP '2026-08-14 14:10:00'),
    ('Nibbles',  'Syrian hamster',  1, 'MALE',   'SMALL',  'Nocturnal and tidy',
     'Nibbles comes with his cage and wheel. Awake in the evening, asleep all day.',
     'AVAILABLE', 'Rodents',  'demo_owner',   TIMESTAMP '2026-08-13 18:00:00'),
    ('Clover',   'Dwarf rabbit',    2, 'FEMALE', 'SMALL',  'Litter trained',
     'Clover is litter trained and used to being handled. She needs daily floor time.',
     'AVAILABLE', 'Rodents',  'demo_owner',   TIMESTAMP '2026-08-12 10:30:00'),
    ('Kiwi',     'Budgerigar',      2, 'MALE',   'SMALL',  'Whistles the doorbell',
     'Kiwi has learned the doorbell and repeats it at length. Comes with cage and stand.',
     'AVAILABLE', 'Birds',    'demo_owner',   TIMESTAMP '2026-08-11 15:40:00'),
    ('Sunny',    'Cockatiel',       3, 'FEMALE', 'SMALL',  'Hand tame',
     'Sunny steps up onto a finger and enjoys company. Should not be left alone all day.',
     'AVAILABLE', 'Birds',    'demo_owner',   TIMESTAMP '2026-08-10 09:00:00'),
    ('Ziggy',    'Leopard gecko',   4, 'MALE',   'SMALL',  'Low maintenance',
     'Ziggy comes with his vivarium, heat mat and thermostat. Eats twice a week.',
     'AVAILABLE', 'Reptiles', 'demo_owner',   TIMESTAMP '2026-08-09 13:25:00')
) AS wanted(pet_name, breed, age, gender, size, short_desc, long_desc, status,
            category, owner, created_at)
-- Scoped to the demo owner, not to the name alone: a database that already has
-- somebody's "Rex" in it would otherwise skip this listing and, worse, the
-- photograph block below would attach a demo image to their pet.
WHERE NOT EXISTS (
        SELECT 1 FROM pet p
        JOIN users u ON u.user_id = p.owner_id
        WHERE p.pet_name = wanted.pet_name AND u.user_name = wanted.owner)
  AND EXISTS (SELECT 1 FROM users u WHERE u.user_name = wanted.owner);

-- ---------------------------------------------------------------------------
-- Photographs - one main image for eight of the twelve.
--
-- The file name is NOT free-form. ImageServlet only serves names matching
-- T-16's convention, <petId>_<uuid>.<ext>, and refuses anything else with a 404
-- - which is what stops the public image URL becoming a way to read arbitrary
-- files. So the URL is built from the pet's real id plus a fixed uuid per
-- listing, and the walkthrough copies a placeholder PNG to those names (see
-- "Before you start" in e2e-scenarios.md; the final SELECT prints the list).
--
-- Four listings are left without a photograph on purpose: the gallery must show
-- the bundled placeholder rather than a broken image, and that is only visible
-- if something has none.
-- ---------------------------------------------------------------------------
INSERT INTO pet_image (pet_id, image_url, is_main, updated_at)
SELECT demo.pet_id,
       '/images/' || demo.pet_id || '_' || wanted.file_uuid || '.png',
       TRUE,
       TIMESTAMP '2026-08-20 10:05:00'
FROM (VALUES
    ('Rex',     'aaaaaaaa-0000-4000-8000-00000000d001'),
    ('Bella',   'aaaaaaaa-0000-4000-8000-00000000d002'),
    ('Pip',     'aaaaaaaa-0000-4000-8000-00000000d003'),
    ('Luna',    'aaaaaaaa-0000-4000-8000-00000000d004'),
    ('Milo',    'aaaaaaaa-0000-4000-8000-00000000d005'),
    ('Nala',    'aaaaaaaa-0000-4000-8000-00000000d006'),
    ('Bubbles', 'aaaaaaaa-0000-4000-8000-00000000d007'),
    ('Nibbles', 'aaaaaaaa-0000-4000-8000-00000000d008')
) AS wanted(pet_name, file_uuid)
-- The join is what keeps this on the demo stage: only a listing owned by one of
-- the three demo accounts can receive one of these rows.
JOIN (
    SELECT p.pet_id, p.pet_name
    FROM pet p
    JOIN users u ON u.user_id = p.owner_id
    WHERE u.user_name LIKE 'demo\_%'
) AS demo ON demo.pet_name = wanted.pet_name
WHERE NOT EXISTS (
      SELECT 1 FROM pet_image i WHERE i.pet_id = demo.pet_id
  );

COMMIT;

-- ---------------------------------------------------------------------------
-- What you should see
-- ---------------------------------------------------------------------------
SELECT (SELECT count(*) FROM users WHERE user_name LIKE 'demo\_%')       AS demo_users,      -- 3
       (SELECT count(*) FROM category)                                   AS categories,      -- 6
       (SELECT count(*) FROM pet WHERE owner_id IN
            (SELECT user_id FROM users WHERE user_name LIKE 'demo\_%'))  AS demo_pets,       -- 12
       (SELECT count(*) FROM pet_image i JOIN pet p ON p.pet_id = i.pet_id
         JOIN users u ON u.user_id = p.owner_id
        WHERE u.user_name LIKE 'demo\_%')                                    AS demo_images;-- 8

-- The eight files the walkthrough has to place in the upload directory. Each is
-- the same placeholder PNG under the name ImageServlet will serve it by.
SELECT SUBSTRING(i.image_url FROM 9) AS file_to_create
FROM pet_image i
JOIN pet p ON p.pet_id = i.pet_id
JOIN users u ON u.user_id = p.owner_id
WHERE u.user_name LIKE 'demo\_%'
ORDER BY 1;
