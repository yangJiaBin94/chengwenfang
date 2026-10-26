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
- Rotate `CHENGWENFANG_SECRET_KEY` away from the development default before storing
  production credentials
- Do not paste database dumps, `.chengwenfang/` trees, or screenshots that show
  secrets into Issues or PRs

## Reporting a vulnerability

Please open a **private** security advisory on the public GitHub repository, or
email the maintainer via the contact method listed on the GitHub profile
`yangJiaBin94`. Do not file a public Issue for unfixed vulnerabilities.
