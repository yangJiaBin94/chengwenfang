#!/usr/bin/env python3
"""Compare row counts for mapped tables between Postgres and MySQL.

Env:
  PGHOST PGPORT PGDATABASE PGUSER PGPASSWORD
  MYSQL_HOST MYSQL_PORT MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD

Usage:
  python verify_counts.py
  python verify_counts.py --dry-connect
"""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

TABLES = [
    line.strip().split("'")[1]
    for line in Path(__file__).with_name("export_counts.sql").read_text(encoding="utf-8").splitlines()
    if "SELECT '" in line
]


def pg_count(table: str) -> int | None:
    import psycopg2

    conn = psycopg2.connect(
        host=os.environ.get("PGHOST", "127.0.0.1"),
        port=int(os.environ.get("PGPORT", "5432")),
        dbname=os.environ["PGDATABASE"],
        user=os.environ["PGUSER"],
        password=os.environ.get("PGPASSWORD", ""),
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables "
                "WHERE table_schema='public' AND table_name=%s)",
                (table,),
            )
            if not cur.fetchone()[0]:
                return None
            cur.execute(f'SELECT COUNT(*) FROM "{table}"')
            return int(cur.fetchone()[0])
    finally:
        conn.close()


def mysql_count(table: str) -> int | None:
    import pymysql

    conn = pymysql.connect(
        host=os.environ.get("MYSQL_HOST", "127.0.0.1"),
        port=int(os.environ.get("MYSQL_PORT", "3306")),
        database=os.environ["MYSQL_DATABASE"],
        user=os.environ["MYSQL_USER"],
        password=os.environ.get("MYSQL_PASSWORD", ""),
        charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT COUNT(*) FROM information_schema.tables "
                "WHERE table_schema=%s AND table_name=%s",
                (os.environ["MYSQL_DATABASE"], table),
            )
            if int(cur.fetchone()[0]) == 0:
                return None
            cur.execute(f"SELECT COUNT(*) FROM `{table}`")
            return int(cur.fetchone()[0])
    finally:
        conn.close()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--dry-connect",
        action="store_true",
        help="Only verify env + drivers + SELECT 1 on both DBs",
    )
    args = parser.parse_args()

    for key in ("PGDATABASE", "PGUSER", "MYSQL_DATABASE", "MYSQL_USER"):
        if key not in os.environ:
            print(f"missing env {key}", file=sys.stderr)
            return 2

    if args.dry_connect:
        import psycopg2
        import pymysql

        pg = psycopg2.connect(
            host=os.environ.get("PGHOST", "127.0.0.1"),
            port=int(os.environ.get("PGPORT", "5432")),
            dbname=os.environ["PGDATABASE"],
            user=os.environ["PGUSER"],
            password=os.environ.get("PGPASSWORD", ""),
        )
        pg.cursor().execute("SELECT 1")
        pg.close()
        my = pymysql.connect(
            host=os.environ.get("MYSQL_HOST", "127.0.0.1"),
            port=int(os.environ.get("MYSQL_PORT", "3306")),
            database=os.environ["MYSQL_DATABASE"],
            user=os.environ["MYSQL_USER"],
            password=os.environ.get("MYSQL_PASSWORD", ""),
        )
        with my.cursor() as cur:
            cur.execute("SELECT 1")
        my.close()
        print("ok: both databases reachable")
        return 0

    mismatches = 0
    print(f"{'table':40} {'pg':>10} {'mysql':>10} status")
    for table in TABLES:
        try:
            pc = pg_count(table)
        except Exception as ex:  # noqa: BLE001
            print(f"{table:40} {'ERR':>10} {'':>10} pg_error={ex}")
            mismatches += 1
            continue
        try:
            mc = mysql_count(table)
        except Exception as ex:  # noqa: BLE001
            print(f"{table:40} {str(pc):>10} {'ERR':>10} mysql_error={ex}")
            mismatches += 1
            continue
        if pc is None and mc is None:
            status = "skip_both_missing"
        elif pc is None:
            status = "skip_pg_missing"
        elif mc is None:
            status = "WARN_mysql_missing"
            mismatches += 1
        elif pc == mc:
            status = "ok"
        else:
            status = "MISMATCH"
            mismatches += 1
        print(f"{table:40} {str(pc):>10} {str(mc):>10} {status}")

    if mismatches:
        print(f"FAILED: {mismatches} problem(s)", file=sys.stderr)
        return 1
    print("PASSED")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
