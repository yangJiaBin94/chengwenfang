# Security Policy

## Intended deployment

This project is a **single-instance multi-user** Chengwenfang deployment (**not**
multi-tenant SaaS). Authentication is **always on**: Sa-Token sessions, RBAC,
and per-user `owner_user_id` isolation on sensitive tables. See
[`docs/用户鉴权体系设计.md`](docs/用户鉴权体系设计.md).

Seed admin (Flyway `V19__identity_users.sql`):

| Username | Password |
|----------|----------|
| `admin`  | `admin123` |

**Change the password** after first login (top-right avatar menu) before any
network exposure. Self-registration defaults to off
(`chengwenfang.auth.register-enabled` / `CHENGWENFANG_AUTH_REGISTER_ENABLED`).
Admins can create users under **Settings → 用户管理**.

**Do not** bind the server to a public interface or expose ports through a
reverse proxy / tunnel without reviewing access control for the app and
sidecar services (Qdrant, Neo4j, etc.).

Defaults that matter:

- Spring Boot listens on `127.0.0.1:8001` (override with `SERVER_ADDRESS` /
  `SERVER_PORT`; the compose `app` profile binds the container to `0.0.0.0` but
  publishes only `127.0.0.1:8001`)
- Vite dev server uses `127.0.0.1:5173`
- Compose also publishes **Qdrant** on `6333/6334` (knowledge vectors) and,
  with `--profile neo4j` / `all`, **Neo4j** on `7474/7687`. Treat these like
  the app: do not expose them to the public internet without access control
- The optional `app` container connects to the Compose service name `qdrant`.
  A failed Qdrant connection does not count as ready and does not silently
  switch to an in-memory vector store
- System readiness (`/api/system/builtin-ready`) stays failed until an
  embedding key is configured. Missing keys are not seeded
- `chengwenfang.crawler-allow-loopback` is `true` only for local SearXNG; `prod`
  forces `false`

## Secrets

- Put real keys only in the settings UI or local `.env` (never commit them).
- Encrypt sensitive columns with `chengwenfang.secret-key`; production **must**
  set `CHENGWENFANG_SECRET_KEY` (no weak default).
- Rotate API keys and channel tokens if they may have leaked.

## Reporting

Report security issues privately to the maintainers. Do not open public issues
for unfixed vulnerabilities that would enable remote abuse.
