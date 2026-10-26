# PG → MySQL stop-window migration

Greenfield MySQL does **not** need this runbook. Use it only when migrating live data from the pre-Java Postgres instance.

Rollback branch: `backup/main-20260907-before-java-rewrite`.  
Target schema: Flyway `V1`–latest under `workbench-app/src/main/resources/db/migration` (and mirrored infra copies).  
Companion scripts: [`scripts/pg-mysql-cutover/`](../scripts/pg-mysql-cutover/).

## Status (2026-09-11)

Live stop-window import **completed** with DataX on this machine:

- Source: Docker volume `workbench_workbench-postgres` → container `workbench-postgres-1` (`postgres:16`)
- Creds (dev): user/password `workbench` / `workbench_dev` (same as backup-branch compose)
- Tooling: `D:\work\personal\designer\product\datax` + jobs under `...\datax-jobs\workbench-pg-to-mysql\`
- Verify: `scripts/pg-mysql-cutover/verify_counts.py` + `sample_ids.py` → **PASSED**
- Dump kept at `.workbench/cutover/pg_workbench_*.dump`

## 0. Preconditions (preflight)

Complete [`scripts/pg-mysql-cutover/preflight.md`](../scripts/pg-mysql-cutover/preflight.md) before the stop window:

- Old Python API/worker can be stopped; no writers remain on PG.
- Target MySQL `workbench` (or dedicated cutover DB) has Flyway applied to **latest**; prefer empty data tables (schema only).
- Disk space ≥ 2× PG dump size; `.workbench/generated-images` path reachable for copy.
- Connection strings ready (placeholders below).
- Verification scripts dry-runnable against both DBs.

### Connection placeholders

```bash
# Postgres (source) — start from backup compose volume if needed:
# docker run -d --name workbench-postgres-1 -e POSTGRES_DB=workbench \
#   -e POSTGRES_USER=workbench -e POSTGRES_PASSWORD=workbench_dev \
#   -p 5432:5432 -v workbench_workbench-postgres:/var/lib/postgresql/data postgres:16
export PGHOST=127.0.0.1
export PGPORT=5432
export PGDATABASE=workbench
export PGUSER=workbench
export PGPASSWORD=workbench_dev

# MySQL (target)
export MYSQL_HOST=127.0.0.1
export MYSQL_PORT=3306
export MYSQL_DATABASE=workbench
export MYSQL_USER=workbench
export MYSQL_PASSWORD=workbench_dev
```

### DataX runner

```powershell
# secret.env next to jobs (see secret.env.example)
cd D:\work\personal\designer\product\datax-jobs\workbench-pg-to-mysql
powershell -File .\run_all.ps1
# then from workbench repo:
cd scripts\pg-mysql-cutover
python verify_counts.py
python sample_ids.py --limit 20
```

Pointer doc: [`scripts/pg-mysql-cutover/README-datax.md`](../scripts/pg-mysql-cutover/README-datax.md).

## 1. Stop-window freeze

1. Stop Python API and any Python job workers.
2. Confirm no new rows in `jobs` / `workflow_runs` for 60s.
3. Optional: revoke app DB roles to read-only for the freeze duration.
4. Record freeze start UTC timestamp in the cutover log.

## 2. Dump Postgres (data only)

Recommended (inserts, easier review; large DBs may prefer CSV):

```bash
mkdir -p /tmp/workbench-cutover
pg_dump \
  --data-only \
  --inserts \
  --no-owner \
  --no-privileges \
  -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" \
  -f /tmp/workbench-cutover/pg_data.sql
```

CSV alternative (per mapped table):

```bash
psql -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" -c "\copy jobs TO '/tmp/workbench-cutover/jobs.csv' CSV HEADER"
```

Keep the dump immutable until verify succeeds.

## 3. Table mapping (PG → MySQL)

Same-name tables map 1:1 unless noted. Transform JSONB → MySQL `JSON`; boolean → `TINYINT(1)`; timestamptz → `DATETIME(3)` UTC.

### Core / jobs / workflow

| Postgres | MySQL | Notes |
|----------|-------|-------|
| `jobs` | `jobs` | Import early (FK parent for runs) |
| `job_events` | `job_events` | After `jobs` |
| `workflow_runs` | `workflow_runs` | After `jobs` |
| `workflow_node_runs` | `workflow_node_runs` | After `workflow_runs` |
| `workflow_artifacts` | `workflow_artifacts` | After V7+; if missing on target, skip or pack into `output_summary` |
| `model_calls` | `model_calls` | After runs/node_runs |
| `workflow_definitions` | `workflow_definitions` | |
| `workflow_versions` | `workflow_versions` | After definitions |
| `workflow_drafts` | `workflow_drafts` | After definitions |
| `workflow_version_skill_bindings` | `workflow_version_skill_bindings` | |
| `workflow_templates` | `workflow_templates` | After V7 |
| `workflow_alerts` | `workflow_alerts` | After V7 |
| `ai_provider_configs` | `ai_provider_configs` | Mask secrets in logs; import as-is |

### Skills / content

| Postgres | MySQL | Notes |
|----------|-------|-------|
| `skills` | `skills` | |
| `skill_versions` | `skill_versions` | |
| `skill_node_bindings` | `skill_node_bindings` | |
| `workspaces` | `workspaces` | |
| `courses` | `courses` | |
| `articles` | `articles` | |
| `article_versions` | `article_versions` | |
| `article_layouts` | `article_layouts` | V5+ |
| `course_generation_preferences` | `course_generation_preferences` | V5+ |
| `generated_images` | `generated_images` | Paths relative to runtime dir |
| `repair_runs` | `repair_runs` | V4+ |

### Hot news / sources

| Postgres | MySQL | Notes |
|----------|-------|-------|
| `source_channels` | `source_channels` | |
| `source_fetch_runs` | `source_fetch_runs` | |
| `source_items` | `source_items` | |
| `topic_clusters` | `topic_clusters` | V2+ |
| `topic_sources` | `topic_sources` | |
| `topic_briefs` | `topic_briefs` | V3+ |
| `topic_evidence` | `topic_evidence` | |
| `topic_research_runs` | `topic_research_runs` | |
| `topic_research_items` | `topic_research_items` | |
| `topic_research_conflicts` | `topic_research_conflicts` | |
| `hot_news_articles` | `hot_news_articles` | |
| `hot_news_article_versions` | `hot_news_article_versions` | |
| `hot_article_review_runs` | `hot_article_review_runs` | V5+ |
| `hot_article_review_items` | `hot_article_review_items` | |
| `hot_article_repair_candidates` | `hot_article_repair_candidates` | |

### Publishing / notifications

| Postgres | MySQL | Notes |
|----------|-------|-------|
| `notifications` | `notifications` | |
| `human_reviews` | `human_reviews` | |
| `publishing_themes` | `publishing_themes` | |
| `publishing_theme_versions` | `publishing_theme_versions` | |
| `publishing_snapshots` | `publishing_snapshots` | |

### Skip / do not import

| Postgres (typical) | Reason |
|--------------------|--------|
| Alembic / Flyway meta (`alembic_version`) | Target uses `flyway_schema_history` |
| Session / cache / Redis-backed keys | Not durable PG tables |
| Pytest / tmp tables | Non-prod |
| Unmapped legacy tables not in Flyway | Document in cutover log; do not invent MySQL tables mid-window |

If a PG table has no MySQL counterpart and is business-critical, **stop** and extend Flyway before continuing.

## 4. Transform notes

- `JSONB` / `JSON` → MySQL `JSON` (valid JSON strings; escape accordingly).
- Postgres arrays → either JSON arrays or child tables already present in MySQL; do not leave `{a,b}` literal text.
- UUID / CHAR(36): keep string form; strip `{}` if any driver added them.
- Soft-deleted rows: import unless product policy says purge.
- `workflow_versions.version`: MySQL is `VARCHAR(24)` (V4); truncate or reject longer labels.

## 5. Import order (respect FKs)

1. Ensure Flyway latest on empty/clean MySQL.
2. Disable FK checks only if using bulk load **and** restoring order below; re-enable before verify:

```sql
SET FOREIGN_KEY_CHECKS = 0;
-- load tables...
SET FOREIGN_KEY_CHECKS = 1;
```

3. Preferred order:
   1. `ai_provider_configs`, `workspaces`, `publishing_themes`, `publishing_theme_versions`
   2. `skills`, `skill_versions`, `skill_node_bindings`
   3. `workflow_definitions`, `workflow_versions`, `workflow_drafts`, `workflow_version_skill_bindings`, `workflow_templates`
   4. `jobs`, `job_events`
   5. `workflow_runs`, `workflow_node_runs`, `workflow_artifacts`, `model_calls`, `workflow_alerts`
   6. `courses`, `articles`, `article_versions`, `article_layouts`, `course_generation_preferences`, `generated_images`, `repair_runs`
   7. Source / topic / hot-news tables (channels → fetch_runs → items → topics → briefs → articles → reviews)
   8. `notifications`, `human_reviews`, `publishing_snapshots`

4. Copy `.workbench/generated-images` (or configured runtime dir) **unchanged** to the Java host path (`workbench.runtime-dir`).

## 6. Verify

```bash
cd scripts/pg-mysql-cutover
python verify_counts.py
python sample_ids.py --limit 20
```

Expect: matching counts on mapped tables; sample PK lookups succeed; orphan FK count = 0 for jobs↔runs.

## 7. Cut over traffic

1. Start Java API (`:8001`) against MySQL + Redis.
2. Point Vite proxy / FE at Java.
3. Smoke: `GET /api/health`, one job history, one course content read, one hot-news topic list.
4. Keep PG dump + freeze log until soak period ends.

## 8. Rollback

1. Stop Java API/workers.
2. Restore FE/proxy to old Python API (backup branch runtime).
3. Re-enable PG writers; **do not** write back half-imported MySQL as source of truth.
4. Retain `/tmp/workbench-cutover/pg_data.sql` for retry after fixing mapping gaps.

## 9. Status vs greenfield

| Situation | Action |
|-----------|--------|
| Empty greenfield MySQL | Skip this runbook; Flyway alone is enough |
| Live PG data | Follow §1–§8 in a stop window |
| Runbook + scripts only (no window yet) | Ready; mark cutover gap as “awaiting stop window” |
