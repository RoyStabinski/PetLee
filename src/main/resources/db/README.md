# Database setup

Two scripts, applied in order, on a database you create first. No migration tool — plain SQL
(ADR-003).

| File | What it does | Re-runnable? |
|---|---|---|
| `schema.sql` | Creates the three tables (`users`, `category`, `pet`), their constraints and indexes | Yes — every statement is `IF NOT EXISTS` or a guarded migration |
| `seed.sql` | Inserts the six categories from specification §1 and the `admin` account | Yes — every insert is `ON CONFLICT DO NOTHING` |

The JPA provider never touches the schema: `persistence.xml` sets
`jakarta.persistence.schema-generation.database.action=none`. What is in these files is what the
database has.

## Apply, in order

```bash
createdb -U postgres petlee
psql -U postgres -d petlee -f src/main/resources/db/schema.sql
psql -U postgres -d petlee -f src/main/resources/db/seed.sql
```

On Windows the binaries live under `C:\Program Files\PostgreSQL\18\bin`:

```bat
"C:\Program Files\PostgreSQL\18\bin\createdb.exe" -U postgres petlee
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d petlee -f src\main\resources\db\schema.sql
"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -d petlee -f src\main\resources\db\seed.sql
```

Both scripts are safe to re-run; that is how you apply a change to an existing database.

## Apply to a *fresh* database — `IF NOT EXISTS` will not fix a drifted one

`CREATE TABLE IF NOT EXISTS` makes re-running safe; it does **not** make the schema correct. On a
database whose tables were created by something else — for example JPA auto-DDL from an earlier
draft — every statement in `schema.sql` is skipped and the file reports success while changing
nothing. The tables keep their old columns, and the FKs keep their old (or missing) `ON DELETE`
actions.

Before trusting an existing database, check for the columns and constraints only this schema has:

```sql
\d users        -- must show password_hash and region, not password, plus the unique
                --   index ux_users_email_lower and NO users_email_key constraint
\d pet          -- category_id FK must be ON DELETE RESTRICT; version NOT NULL DEFAULT 0
```

If either is missing, drop the database and re-apply both scripts. There is no migration path from
an auto-generated schema, and no migration tool in this project by design (ADR-003).

One statement in `schema.sql` can fail on a populated database rather than being skipped:
`ux_users_email_lower` cannot be created if two existing rows hold the same address in different
cases. That is the point of the index, so the fix is to reconcile the rows, not to skip it. Find
them first:

```sql
SELECT LOWER(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;
SELECT LOWER(category_name), count(*) FROM category GROUP BY 1 HAVING count(*) > 1;
```

## The application's database role

The server's connection pool authenticates as `petlee_app`, not as `postgres`
(`docs/deployment/datasource-setup.md`). Create it once:

```sql
CREATE ROLE petlee_app LOGIN PASSWORD '<choose one>';
GRANT CONNECT ON DATABASE petlee TO petlee_app;
GRANT USAGE ON SCHEMA public TO petlee_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO petlee_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO petlee_app;
```

`petlee_app` deliberately has no DDL rights. The application cannot alter the schema even by
accident — only these scripts can, applied by an administrator.

The password is chosen at setup time and stored only in the server's JDBC pool, never in this
repository.

## Verifying

```sql
\dt                                    -- users, category, pet
SELECT count(*) FROM category;         -- 6
SELECT count(*) FROM users WHERE user_name = 'admin';  -- 1
\d users                               -- shows unique index ux_users_email_lower
\d category                            -- shows unique index ux_category_name_lower
```

## Administrator account

`seed.sql` inserts a live `admin` / `Admin123!` account (`ADMIN` role), with a real PBKDF2 digest
already in the file — not a placeholder and not commented out. It is a known demo credential in a
seeded reference database, not a secret; change it before deploying anywhere other people can
reach.
