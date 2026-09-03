# Database setup

Two scripts, applied in order to a database you create first. No migration tool — plain SQL.

| File | What it does |
|---|---|
| `schema.sql` | Creates `users`, `category` and `pet`, their constraints and indexes |
| `seed.sql` | Inserts the four categories and the `admin` account |

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
\dt                                                    -- users, category, pet
SELECT count(*) FROM category;                         -- 4
SELECT count(*) FROM users WHERE user_name = 'admin';  -- 1
\d users                                               -- ux_users_email_lower present
```

## Administrator account

`seed.sql` inserts a live `admin` / `Admin123!` account with a real PBKDF2 digest. It is a known
demo credential in a seeded reference database, not a secret — change it before deploying anywhere
other people can reach.
