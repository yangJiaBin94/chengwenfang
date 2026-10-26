#!/usr/bin/env python3
"""Sample primary keys from Postgres and verify presence + FK integrity on MySQL.

Env: same as verify_counts.py
Usage: python sample_ids.py --limit 20
"""

from __future__ import annotations

import argparse
import os
import sys

import pymysql
import psycopg2

SAMPLES = [
    ("jobs", "id", [("workflow_runs", "job_id")]),
    ("workflow_definitions", "id", [("workflow_drafts", "workflow_id")]),
    ("courses", "id", [("articles", "course_id")]),
    ("source_channels", "id", [("source_items", "channel_id")]),
    ("topic_clusters", "id", [("topic_sources", "topic_id")]),
    ("hot_news_articles", "id", [("hot_news_article_versions", "article_id")]),
]


def pg_conn():
    return psycopg2.connect(
        host=os.environ.get("PGHOST", "127.0.0.1"),
        port=int(os.environ.get("PGPORT", "5432")),
        dbname=os.environ["PGDATABASE"],
        user=os.environ["PGUSER"],
        password=os.environ.get("PGPASSWORD", ""),
    )


def mysql_conn():
    return pymysql.connect(
        host=os.environ.get("MYSQL_HOST", "127.0.0.1"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        database=os.environ["MYSQL_DATABASE"],
        user=os.environ["MYSQL_USER"],
        password=os.environ.get("MYSQL_PASSWORD", ""),
        charset="utf8mb4",
    )


def table_exists_pg(cur, table: str) -> bool:
    cur.execute(
        "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
        "WHERE table_schema='public' AND table_name=%s)",
        (table,),
    )
    return bool(cur.fetchone()[0])


def table_exists_mysql(cur, table: str) -> bool:
    cur.execute(
        "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=%s AND table_name=%s",
        (os.environ["MYSQL_DATABASE"], table),
    )
    return int(cur.fetchone()[0]) > 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=20)
    args = parser.parse_args()

    for key in ("PGDATABASE", "PGUSER", "MYSQL_DATABASE", "MYSQL_USER"):
        if key not in os.environ:
            print(f"missing env {key}", file=sys.stderr)
            return 2

    errors = 0
    with pg_conn() as pg, mysql_conn() as my:
        with pg.cursor() as pcur, my.cursor() as mcur:
            for table, pk, children in SAMPLES:
                if not table_exists_pg(pcur, table):
                    print(f"skip {table}: missing on PG")
                    continue
                if not table_exists_mysql(mcur, table):
                    print(f"FAIL {table}: missing on MySQL")
                    errors += 1
                    continue
                pcur.execute(f'SELECT "{pk}" FROM "{table}" ORDER BY "{pk}" LIMIT %s', (args.limit,))
                ids = [row[0] for row in pcur.fetchall()]
                if not ids:
                    print(f"ok {table}: empty on PG")
                    continue
                missing = []
                for i in ids:
                    mcur.execute(f"SELECT 1 FROM `{table}` WHERE `{pk}`=%s", (i,))
                    if mcur.fetchone() is None:
                        missing.append(i)
                if missing:
                    print(f"FAIL {table}: {len(missing)}/{len(ids)} ids missing on MySQL e.g. {missing[:3]}")
                    errors += 1
                else:
                    print(f"ok {table}: {len(ids)} sampled ids present")

                for child_table, fk in children:
                    if not table_exists_mysql(mcur, child_table):
                        continue
                    orphans = 0
                    for i in ids:
                        mcur.execute(
                            f"SELECT COUNT(*) FROM `{child_table}` c "
                            f"LEFT JOIN `{table}` p ON p.`{pk}`=c.`{fk}` "
                            f"WHERE c.`{fk}`=%s AND p.`{pk}` IS NULL",
                            (i,),
                        )
                        # child rows for this parent should not be orphaned; check inverse:
                        mcur.execute(
                            f"SELECT COUNT(*) FROM `{child_table}` WHERE `{fk}`=%s",
                            (i,),
                        )
                        # just ensure query works; orphan check across all:
                    mcur.execute(
                        f"SELECT COUNT(*) FROM `{child_table}` c "
                        f"LEFT JOIN `{table}` p ON p.`{pk}`=c.`{fk}` "
                        f"WHERE c.`{fk}` IS NOT NULL AND p.`{pk}` IS NULL"
                    )
                    orphans = int(mcur.fetchone()[0])
                    if orphans:
                        print(f"FAIL FK {child_table}.{fk} -> {table}.{pk}: {orphans} orphans")
                        errors += 1
                    else:
                        print(f"ok FK {child_table}.{fk} -> {table}.{pk}")

    if errors:
        print(f"FAILED: {errors}", file=sys.stderr)
        return 1
    print("PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
