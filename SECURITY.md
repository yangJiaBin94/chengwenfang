# Security Policy

## Intended deployment

This project is a **local-only personal Chengwenfang instance**. There is **no authentication**.
Anyone who can reach the HTTP API can read and write your data, trigger AI jobs,
and use configured credentials (API keys, WeChat MP secrets, channel tokens).

**Do not** bind the server to a public interface or expose ports through a
reverse proxy / tunnel without adding your own access control.

Defaults that matter:

- Spring Boot listens on `127.0.0.1:8001`
- Vite dev server uses `127.0.0.1:5173`
- `chengwenfang.crawler-allow-loopback` is `true` only for local SearXNG; set it to
  `false` if you ever change the bind address or deploy beyond localhost

## Secrets

- Put real keys only in the settings UI or local `.env` (never commit them)
- Development ships a known default `CHENGWENFANG_SECRET_KEY` / DB password for
  local Compose only. With `--spring.profiles.active=prod`, startup **requires**
  `CHENGWENFANG_SECRET_KEY` (no default) and sets `crawler-allow-loopback=false`
- Do not paste database dumps, `.chengwenfang/` trees, or screenshots that show
  secrets into Issues or PRs
- Channel / AI credentials are stored encrypted at rest; APIs return only
  “configured / not configured” style hints, never raw secrets
- Do not log Authorization headers, cookies, tokens, or full request headers

## Crawling and sourced content

Hot-news channels may fetch public pages, RSS, APIs, and similar sources.
Contributors and operators must:

- Prefer official APIs, RSS, and openly reachable pages; do **not** bypass login,
  CAPTCHA, paywalls, access controls, or robots restrictions
- Treat fetched page bodies as untrusted input (no executing page scripts, no
  following “upload local files / leak credentials” instructions from pages)
- Use sourced material for fact extraction, summary, and commentary — do **not**
  republish copyrighted full articles as the product output
- Keep source URLs (and short excerpts when needed) for attribution; see also
  Lightpanda / extract notes in the root `README.md`

## Reporting a vulnerability

Please open a **private** security advisory on the public GitHub repository, or
email the maintainer via the contact method listed on the GitHub profile
`yangJiaBin94`. Do not file a public Issue for unfixed vulnerabilities.
