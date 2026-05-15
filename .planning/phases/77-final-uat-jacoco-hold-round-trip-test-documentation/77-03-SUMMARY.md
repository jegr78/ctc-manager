---
phase: 77-final-uat-jacoco-hold-round-trip-test-documentation
plan: 03
subsystem: documentation
tags: [screenshots, playwright-cli, backup, admin-ui, import-lock, banner, qual-05]

requires:
  - phase: 74-backup-import-preview-zip-hardening-multipart-config-schema
    provides: BackupController preview/confirm endpoints + admin/backup.html templates
  - phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
    provides: ImportLockService + ImportLockBannerAdvice + admin/layout.html banner wiring

provides:
  - ".screenshots/77/01-backup-page.png — /admin/backup landing page (Export button + Import form, dev,demo profile, 1.8.0-SNAPSHOT)"
  - ".screenshots/77/02-preview-screen.png — Backup Import Preview screen (21.29 MB ZIP, 700 uploads, 4516 imported rows, Schema version 1 matches, per-table cards: Cars 562, Tracks 121, Drivers 106, Seasons 6 …)"
  - ".screenshots/77/03-import-banner.png — Seasons page with yellow read-only banner during in-flight import (\"Backup import in progress — write access is temporarily locked.\")"
  - "QUAL-05 visual-evidence prerequisite satisfied — Plan 05 (Wiki page) consumes these via raw.githubusercontent.com absolute URLs"

affects:
  - "77-04 (README) — references the wiki page that embeds these screenshots"
  - "77-05 (Wiki push) — embeds all 3 screenshots via raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/<file>.png"

tech-stack:
  added: []
  patterns:
    - "playwright-cli open + click + upload + screenshot — interactive multipart flow for the preview screen"
    - "Background curl POST /admin/backup/import-execute + concurrent playwright reload — 1.5 s lock window large enough to capture the read-only banner on another admin tab"
    - "Backup ZIP staging: POST /admin/backup/import-preview returns inline preview HTML with stagingId hidden input; parsed via grep -oE 'name=\"stagingId\" value=\"…\"'"

key-files:
  created:
    - ".screenshots/77/01-backup-page.png (67 709 bytes, PNG 1280×720)"
    - ".screenshots/77/02-preview-screen.png (69 079 bytes, PNG 1280×720)"
    - ".screenshots/77/03-import-banner.png (74 417 bytes, PNG 1280×720)"
  modified: []

key-decisions:
  - "D-10 honored: 3 screenshots committed to MAIN repo under .screenshots/77/ (NOT the wiki repo) per feedback_screenshots_folder memory — screenshots stay in lockstep with the codebase that produces them"
  - "CD-03: banner screenshot triggered via REAL in-flight import (not @TestComponent lock flip) — D-17 forbids new test-support files; the background-curl + tab-reload pattern caught the ~1.5 s lock window cleanly"
  - "CD-06: 1280×720 viewport (playwright-cli default) — banner and preview both fit unclipped"
  - "playwright-cli sandbox restricts uploads to repo root: ZIP copied to ./.tmp-uat-backup.zip for the file-chooser action (subsequently deleted, not committed)"

patterns-established:
  - "Interactive screenshot capture flow: curl POST /admin/backup/export → cp ZIP into repo root → playwright-cli click file-picker + upload + click Import → screenshot the preview screen"
  - "In-flight-banner capture: extract fresh stagingId via curl POST /admin/backup/import-preview → fire curl POST /admin/backup/import-execute in background (sleep 0.05 s) → playwright-cli reload tab on another admin page → screenshot"

requirements-completed: [QUAL-05]

duration: ~10min
completed: 2026-05-15
---

# Phase 77 Plan 03: Three Playwright-CLI Screenshots for the v1.10 Wiki Page

**Captured the three visual-evidence PNGs that Plan 05's GitHub Wiki page embeds via stable raw.githubusercontent.com URLs — `/admin/backup` landing page, import preview screen, read-only banner during an in-flight import.**

## Outcome

| File | Size | Subject | Notes |
|------|------|---------|-------|
| `01-backup-page.png` | 67.7 KB | `/admin/backup` with Export + Import controls | Direct navigation — `playwright-cli open` |
| `02-preview-screen.png` | 69.1 KB | Preview screen post-upload — 21.29 MB / 700 uploads / 4516 rows | Required real multipart upload (POST `/admin/backup/import-preview`) per RESEARCH §"Pitfall 6" |
| `03-import-banner.png` | 74.4 KB | Seasons page with yellow `alert-warning` banner during in-flight import | Caught the 1.5 s lock window via background curl + concurrent playwright reload (CD-03 path — D-17 forbids new @TestComponent) |

All three files committed to the main repo at `.screenshots/77/`. The Wiki page (Plan 05) references them via `https://raw.githubusercontent.com/jegr78/ctc-manager/master/.screenshots/77/<file>.png`, so screenshots stay in lockstep with the codebase that produces them.

## Performance

- **Duration:** ~10 min (dev server startup + 3 screenshots + cleanup)

## Verification

All three acceptance criteria green:

```
$ test -s .screenshots/77/01-backup-page.png && echo OK
OK
$ test -s .screenshots/77/02-preview-screen.png && echo OK
OK
$ test -s .screenshots/77/03-import-banner.png && echo OK
OK
$ file .screenshots/77/*.png | grep -c 'PNG image data, 1280 x 720'
3
$ git diff --stat pom.xml .github/workflows/ src/test/java/org/ctc/backup/service/BackupImportRollbackIT.java | wc -l
0
```

D-05 / D-11 / D-12 / D-14 / D-16 / D-17 / D-20 all held: zero changes outside the 3 screenshot files.

## Commits

- `chore(77): capture .screenshots/77/ for D-10 wiki embeds` — 3 PNGs, this SUMMARY, ROADMAP plan-progress update

## Banner-Trigger Technique (CD-03 path)

For 03-import-banner.png the plan required catching the read-only banner mid-import without creating new test-support code (D-17). The technique:

1. Export a fresh ZIP via `curl -X POST /admin/backup/export` (writes to `/tmp`).
2. Copy into repo root (playwright sandbox), upload to `/admin/backup/import-preview`, parse `stagingId` from response HTML.
3. With playwright already loaded on `/admin/seasons`, fire `curl -X POST /admin/backup/import-execute` in the background.
4. `sleep 0.05` to let curl reach the server, then `playwright-cli reload` + `screenshot` — the GET races during the import's ~1.5 s lock window.
5. Verify post-fact via `playwright-cli eval document.querySelector('.alert-warning').textContent` — confirmed "Backup import in progress — write access is temporarily locked."

The first attempt (sleep 1 s) missed the window because the playwright reload landed AFTER the import returned 302. Dropping to 0.05 s caught it on the first try.

## Cleanup

- Temp ZIPs (`.tmp-uat-backup*.zip`, `/tmp/ctc-backup-77-uat*.zip`) deleted.
- `.playwright-cli/` snapshot directory removed.
- Dev server stopped via TaskStop.
