# PG → MySQL cutover preflight

Check every item before opening the stop window.

## Environment

- [ ] `PGHOST` / `PGDATABASE` / `PGUSER` / `PGPASSWORD` set and `psql -c 'select 1'` works
- [ ] `MYSQL_HOST` / `MYSQL_DATABASE` / `MYSQL_USER` / `MYSQL_PASSWORD` set and `mysql -e 'select 1'` works
- [ ] Target MySQL Flyway is at **latest** (`flyway_schema_history` shows newest version)
- [ ] Target data tables are empty (or intentionally wiped); schema-only preferred
- [ ] Disk free space ≥ 2× estimated `pg_dump` size
- [ ] `.workbench/generated-images` (or configured runtime image dir) is readable on source host
- [ ] Java build packages; Redis reachable for post-cutover smoke
- [ ] Rollback path known: `backup/main-20260907-before-java-rewrite` + PG dump location

## Process

- [ ] Stakeholders agree stop-window start/end UTC
- [ ] Python API + workers can be stopped without concurrent writers
- [ ] Runbook [`docs/cutover-pg-to-mysql.md`](../../docs/cutover-pg-to-mysql.md) reviewed for new unmapped PG tables
- [ ] `python verify_counts.py --dry-connect` succeeds (see script `--help`)
- [ ] Cutover log file path reserved (e.g. `/tmp/workbench-cutover/cutover.log`)

## Do not proceed if

- Flyway behind latest
- Unknown business tables exist only on PG
- Image directory missing while `generated_images` has rows
