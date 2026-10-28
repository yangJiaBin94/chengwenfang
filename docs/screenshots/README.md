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

1. Run the app (Vite + API) with demo data. Private demo: Vite `5174` → API `8002`.
2. Capture the **viewport** at 1440×900 via CDP `Page.captureScreenshot` (not a browser-chrome crop).
3. Save into temp `_raw/`, then `python scripts/oss/normalize-readme-screenshots.py`
   (contain + pad only — **never cover-crop**, or the left sidebar gets clipped).

Optional edge trim: `scripts/oss/trim-screenshot-padding.py` (keeps left chrome).
