# Database setup

Two scripts, applied in order to a database you create first, and numbered migrations for
upgrading an older one. No migration tool — plain SQL.

| File | What it does |
|---|---|
| `schema.sql` | Creates `users`, `category`, `pet`, `pet_image` and `favorite`, their constraints and indexes |
| `seed.sql` | Inserts the four categories and the `admin` account |
| `migrate-001-pet-image.sql` | Upgrades an existing database from one photograph per pet to `pet_image` — see [Migrations](#migrations) |
| `migrate-002-favorite.sql` | Adds the `favorite` table to an existing database — see [Migrations](#migrations) |
| `migrate-003-user-profile.sql` | Adds `users.avatar_url` and `users.version` to an existing database — see [Migrations](#migrations) |
| `migrate-004-image-focus.sql` | Adds the photo focal points (`pet_image.focus_x`/`focus_y`, `users.avatar_focus_x`/`avatar_focus_y`) to an existing database — see [Migrations](#migrations) |

`schema.sql` and `seed.sql` are re-runnable: every statement is `IF NOT EXISTS` or `ON CONFLICT DO NOTHING`. The JPA
provider never touches the schema — `persistence.xml` sets
`jakarta.persistence.schema-generation.database.action=none`, so what is in these files is what the
database has.

## Apply

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

On Windows the binaries live under `C:\Program Files\PostgreSQL\17\bin`, which the installer does
not put on the PATH; the version number in the path must match the installed version. Add it for
the current terminal session first:

```powershell
# PowerShell
$env:Path += ";C:\Program Files\PostgreSQL\17\bin"
```

```bat
:: cmd
set PATH=%PATH%;C:\Program Files\PostgreSQL\17\bin
```

Apply them to a **fresh** database. `CREATE TABLE IF NOT EXISTS` makes re-running safe; it does not
make a drifted schema correct. On tables created by something else — JPA auto-DDL from an earlier
draft, say — every statement is skipped, the file reports success, and the old columns and foreign
keys survive. Drop the database and re-apply rather than patching it.

One statement can fail on a populated database instead of being skipped: `ux_users_email_lower`
cannot be created if two rows hold the same address in different cases. That is what the index is
for, so reconcile the rows:

```sql
SELECT LOWER(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
SELECT LOWER(category_name), count(*) FROM category GROUP BY 1 HAVING count(*) > 1;
```

## Migrations

A fresh install needs none: `schema.sql` already has every table. A migration is for a database
created by an earlier version of `schema.sql`, and each one runs once, in number order.

### 001 — `pet_image`

Needed if the `pet` table still has an `image_url` column (`\d pet` shows it). The script creates
`pet_image` and its two indexes, copies each non-blank `pet.image_url` into it as that pet's main
image (dated with the pet's `created_at`), drops `pet.image_url`, and grants `petlee_app` access
to the new table and its sequence. The files in the upload directory are not touched: the URLs
are copied as they are.

1. **Back up first.** The script drops a column; the backup is the only way back.

   ```bash
   pg_dump -U postgres -Fc -f petlee-before-001.dump petlee
   ```

2. Stop the application, or at least undeploy it: the old build writes `pet.image_url`, and the
   new build reads `pet_image`, so neither works against the other's schema.

3. Run the script as `postgres`. It is one transaction, so a failure changes nothing, and
   `ON_ERROR_STOP` makes `psql` stop at the first error rather than carry on outside it.

   ```bash
   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-001-pet-image.sql
   ```

   It fails if the `petlee_app` role does not exist; create it first (below).

4. Check, then deploy the new build:

   ```sql
   \d pet                                   -- no image_url column
   \d pet_image                             -- ux_pet_image_main and idx_pet_image_pet present
   SELECT count(*) FROM pet_image;          -- the number of pets that had a photograph
   ```

To undo it, restore the backup: `pg_restore -U postgres -d petlee --clean petlee-before-001.dump`.

### 002 — `favorite`

Needed if `\dt` does not list `favorite`. The script creates the table (primary key
`(user_id, pet_id)`, both columns cascading on delete), the index `idx_favorite_pet`, and grants
`petlee_app` `SELECT, INSERT, DELETE` on it — no `UPDATE`, since a favourite is only ever added
or removed, and no sequence, since the key is the pair. It only adds, so nothing existing
changes, and the old build keeps working against the migrated database.

1. Back up first, as for 001:

   ```bash
   pg_dump -U postgres -Fc -f petlee-before-002.dump petlee
   ```

2. Run it as `postgres`. One transaction; every statement is `IF NOT EXISTS`, so running it
   twice is harmless. It fails, changing nothing, if the `petlee_app` role does not exist.

   ```bash
   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-002-favorite.sql
   ```

3. Check, then deploy the build that has the favourites feature:

   ```sql
   \d favorite                              -- favorite_pkey (user_id, pet_id) and idx_favorite_pet
   \dp favorite                             -- petlee_app=ard (INSERT, SELECT, DELETE)
   ```

To undo it: `DROP TABLE favorite;` — nothing else refers to it.

### 003 — profile photo and profile editing

Needed if `\d users` lists neither `avatar_url` nor `version`. The script adds `avatar_url
VARCHAR(512) NULL` (the profile photo's URL, `NULL` meaning the initials circle) and `version
BIGINT NOT NULL DEFAULT 0` (optimistic locking for the edit-profile form; the default fills every
existing row with 0). Nothing else changes: existing rows keep their values, including telephone
numbers stored before the Israeli-number rule, which are only normalised when their owner next
saves the profile.

No `GRANT` this time. 001 and 002 created tables, which the original grants could not name;
these are columns of an existing table, and `petlee_app`'s table-level privileges cover every
column the table has or gains.

1. Back up first, as for 001:

   ```bash
   pg_dump -U postgres -Fc -f petlee-before-003.dump petlee
   ```

2. Run it as `postgres`. One transaction; both statements are `ADD COLUMN IF NOT EXISTS`, so
   running it twice is harmless. The old build ignores both columns, so it may keep running.

   ```bash
   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-003-user-profile.sql
   ```

3. Check, then deploy the build that has the profile feature:

   ```sql
   \d users                                 -- avatar_url varchar(512), version bigint not null default 0
   SELECT count(*) FROM users WHERE version IS NULL;   -- 0
   ```

To undo it: `ALTER TABLE users DROP COLUMN avatar_url, DROP COLUMN version;` — the uploaded
profile photos stay in the upload directory and can be deleted by hand.

### 004 — photo focal points

Needed if `\d pet_image` does not list `focus_x`. The script adds four columns, all `SMALLINT
NOT NULL DEFAULT 50`: `pet_image.focus_x` and `focus_y`, and `users.avatar_focus_x` and
`avatar_focus_y`. Each is the point a cropped view of the photo centres on, in percent of its
width or height, and each has a named `CHECK (… BETWEEN 0 AND 100)`. The default fills every
existing row with 50/50, the centre, which is how every photo was shown before. The image files
are not touched.

No `GRANT`, for the same reason as 003: new columns of existing tables are covered by
`petlee_app`'s table-level privileges.

1. Back up first, as for 001:

   ```bash
   pg_dump -U postgres -Fc -f petlee-before-004.dump petlee
   ```

2. Run it as `postgres`. One transaction; the columns are `ADD COLUMN IF NOT EXISTS` and each
   constraint is added only when it is missing, so running it twice is harmless. The old build
   ignores the new columns, so it may keep running.

   ```bash
   psql -U postgres -d petlee -v ON_ERROR_STOP=1 -f src/main/resources/db/migrate-004-image-focus.sql
   ```

3. Check, then deploy the build that has the focal-point editor:

   ```sql
   \d pet_image        -- focus_x, focus_y smallint not null default 50; ck_pet_image_focus_x and _y
   \d users            -- avatar_focus_x, avatar_focus_y; ck_users_avatar_focus_x and _y
   ```

To undo it: `ALTER TABLE pet_image DROP COLUMN focus_x, DROP COLUMN focus_y;` and
`ALTER TABLE users DROP COLUMN avatar_focus_x, DROP COLUMN avatar_focus_y;` (each column's
constraint goes with it).

## The application's database role

The server's connection pool should authenticate as `petlee_app`, not as `postgres`. After
`schema.sql` and `seed.sql`, from the project root, replacing `<chosen>` with a password of your
choice:

```bash
psql -U postgres -d petlee -c "CREATE ROLE petlee_app LOGIN PASSWORD '<chosen>';"
psql -U postgres -d petlee -c "GRANT CONNECT ON DATABASE petlee TO petlee_app; GRANT USAGE ON SCHEMA public TO petlee_app; GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO petlee_app; GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO petlee_app;"
```

The `GRANT`s must run while connected to `petlee` (`-d petlee`), not to the default `postgres`
database: `ON ALL TABLES IN SCHEMA public` means the tables of the database you are connected to,
so run anywhere else they grant nothing on Pet-Lee's tables, and every request then fails with
`permission denied`. For the same reason they must run after `schema.sql`, once the tables exist.
A role belongs to the whole server, so if `petlee_app` already exists the first command fails
with `role "petlee_app" already exists`; keep its existing password and run the second command
anyway. The password chosen here is the one the WildFly `data-source add` command needs.

It deliberately has no DDL rights, so the application cannot alter the schema even by accident.
The password is stored only in the server's JDBC pool, never in this repository.

## Verifying

```sql
\dt                                                    -- users, category, pet, pet_image, favorite
SELECT count(*) FROM category;                         -- 4
SELECT count(*) FROM users WHERE user_name = 'admin';  -- 1
\d users                                               -- ux_users_email_lower present
```

## Administrator account

`seed.sql` inserts a live `admin` / `Admin123!` account with a real PBKDF2 digest. It is a known
demo credential in a seeded reference database, not a secret — change it before deploying anywhere
other people can reach.
