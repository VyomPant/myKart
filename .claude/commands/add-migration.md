# /add-migration

Create the next Flyway migration file for a service with the correct naming convention.

## Usage

```
/add-migration <service-name> <description>
```

Example: `/add-migration order-service add_saga_state_table`

## What this command does

1. Scans `<service-name>/src/main/resources/db/migration/` for the highest existing `V{n}__*.sql`

2. Creates `V{n+1}__{description}.sql` where:
   - `{n+1}` is the next version number
   - `{description}` is the provided description in `lower_snake_case`

3. Adds a header comment with the creation timestamp and purpose

4. If the migration modifies an indexed column (detects `ALTER COLUMN` on a column that appears in a `CREATE INDEX` statement), prints a warning:
   > "Warning: modifying indexed column — consider index rebuild time on large tables"

## Flyway naming convention

```
V{version}__{description}.sql
  ^           ^
  Version     Two underscores, then lowercase description with underscores
```

Valid examples:
- `V1__create_users_table.sql`
- `V2__create_refresh_tokens_table.sql`
- `V3__add_index_on_email.sql`

Invalid (will warn):
- `V1_create_users.sql` (single underscore)
- `V1__CreateUsers.sql` (camelCase)
- `create_users.sql` (missing version prefix)

## Notes

- Flyway migrations are append-only — never modify an existing migration
- If the table already has data, prefer `ALTER TABLE ... ADD COLUMN ... DEFAULT ...` over recreating
- For adding NOT NULL columns to populated tables, add with DEFAULT first, then remove the DEFAULT
