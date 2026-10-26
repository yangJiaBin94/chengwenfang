# DataX PG → MySQL cutover

Jobs live outside this repo (avoid shipping DataX binaries):

- DataX: `D:\work\personal\designer\product\datax`
- Jobs: `D:\work\personal\designer\product\datax-jobs\workbench-pg-to-mysql`

## Setup

1. Copy `secret.env.example` → `secret.env` (dev defaults: `workbench` / `workbench_dev`).
2. Ensure Postgres listens on `127.0.0.1:5432` (Docker volume `workbench_workbench-postgres`) and MySQL compose is up.
3. Flyway on MySQL must be at latest (V7+): start `workbench-app` once if needed.
4. Stop-window: stop Java/Python writers, `pg_dump -Fc`, then:
   `powershell -File run_all.ps1`
5. Verify from this directory’s sibling scripts:
   ```
   python verify_counts.py
   python sample_ids.py --limit 20
   ```

## Notes

- `run_all.ps1` builds per-table `querySql` with JSON `::text`, varchar truncation, boolean→tinyint, and a few PG→MySQL column renames.
- Unmapped PG-only legacy tables are not imported (by design).
