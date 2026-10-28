# Screenshots

Assets for the root `README.md`. Captured from a local demo UI at a fixed
**1440×900** viewport (product chrome only — no OS / browser window frame).
Do not commit shots that show real API keys or private manuscript bodies.

## What ships in the README

| Priority | File | Why |
|----------|------|-----|
| Hero | `hotnews-walkthrough.gif` | Shows the hot-news loop in motion |
| 2nd hero | `06-course-route.png` | Real curriculum path (7 series) — more representative than an empty theme card |
| Feature | `05-hotnews-radar.png` | Populated radar (scores, sources, counts) |
| Details | `01` / `02` / `03` / `04` | Theme overviews, workflows, AI settings |

## Regenerating

1. Run the app (Vite + API) with demo data.
2. Capture at 1440×900 into a temp `_raw/` folder.
3. `python scripts/oss/normalize-readme-screenshots.py`

Optional trim helper for letterboxed stills: `scripts/oss/trim-screenshot-padding.py`.
