# Dual-repo sync (private ↔ public)

| Repo | Role |
|------|------|
| Private `workbench` | Daily driver, personal `content/learning-content`, full git history |
| Public `local-writer-workbench` | Community tree, Apache-2.0, demo themes only |

## Export private → public

From the **private** repo root:

```powershell
# Fresh sibling directory + initial commit + create/push GitHub public repo
powershell -ExecutionPolicy Bypass -File .\scripts\oss\export-to-public.ps1 -InitGit -CreateRemote

# Or export only (Dest must be empty / nonexistent)
powershell -ExecutionPolicy Bypass -File .\scripts\oss\export-to-public.ps1 -Dest D:\path\to\local-writer-workbench
```

Hard excludes (never copy):

- `content/learning-content`, `public/content`, `public/data/workbench-data.json`
- `docs/refactor-v2`, `docs/cutover*.md`, `scripts/pg-mysql-cutover`
- `scripts/import-*.mjs` (personal knowledge-base importers)
- `.git`, build/runtime dirs (`target`, `node_modules`, …)
- Do **not** exclude bare directory name `hotnews` — that wipes `com/workbench/mode/hotnews` sources

After copy, `default-themes.json` in the public tree is replaced by
`default-themes.oss.json` (Demo Course / Demo Hot News).

Public DB name is **`workbench`** (not private `workbench_v2`): export rewrites
`compose.yml` / `application.yml` / README / `.env.example` accordingly.

## Ongoing sync

1. Land features in private until stable
2. Re-export into a clean Dest **or** manually cherry-pick code-only commits into the public clone (never content / secrets)
3. Community PRs: merge on public, then pull/cherry-pick back into private

**Never** push private course bodies, `.env`, DB dumps, or screenshots with keys to the public remote.
