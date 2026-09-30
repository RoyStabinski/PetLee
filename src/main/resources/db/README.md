# Database setup

Two scripts, applied in order to a database you create first. No migration tool — plain SQL.

| File | What it does |
|---|---|
| `schema.sql` | Creates `users`, `category`, `pet`, `pet_image` and `favorite`, their constraints and indexes |
| `seed.sql` | Inserts the four categories and the `admin` account |
| `migrate-001-pet-image.sql` | Upgrades an existing database from one photograph per pet to `pet_image` — see [Migrations](#migrations) |
| `migrate-002-favorite.sql` | Adds the `favorite` table to an existing database — see [Migrations](#migrations) |

Both are re-runnable: every statement is `IF NOT EXISTS` or `ON CONFLICT DO NOTHING`. The JPA
provider never touches the schema — `persistence.xml` sets
`jakarta.persistence.schema-generation.database.action=none`, so what is in these files is what the
database has.

## Apply

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

On Windows the binaries live under `C:\Program Files\PostgreSQL\18\bin`.

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

## The application's database role

The server's connection pool should authenticate as `petlee_app`, not as `postgres`:

```sql
CREATE ROLE petlee_app LOGIN PASSWORD '<choose one>';
GRANT CONNECT ON DATABASE petlee TO petlee_app;
GRANT USAGE ON SCHEMA public TO petlee_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO petlee_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO petlee_app;
```

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
