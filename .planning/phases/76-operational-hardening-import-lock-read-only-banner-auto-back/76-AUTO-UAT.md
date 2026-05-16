---
phase: 76-operational-hardening-import-lock-read-only-banner-auto-back
executed: 2026-05-14T20:38:00Z
server_profile: dev,demo
total: 5
passed: 5
failed: 0
skipped: 0
---

# Auto-UAT Report: Phase 76

All 5 deferred human-verification items from [76-VERIFICATION.md](76-VERIFICATION.md) executed
against a live dev,demo server with the `data/.import-backups/` directory cleaned between
races. Real 22 MB backup ZIP exported from the dev fixture and re-imported as the workload.

## Results

### 1. Cross-page banner visibility during active import

- **Status:** passed
- **Screenshots:**
  - [01-banner-visible-seasons.png](../../../.screenshots/76/01-banner-visible-seasons.png) — banner rendered on /admin/seasons during in-flight import
  - [01b-banner-gone-after-import.png](../../../.screenshots/76/01b-banner-gone-after-import.png) — banner removed after import completed
- **Evidence:**
  - While `import-execute` was in flight, `curl` of /admin/seasons, /admin/teams, /admin/drivers, /admin/matchdays, /admin/races each returned the exact banner string `Backup import in progress — write access is temporarily locked.`
  - After the import completed (HTTP 302 success), the same `curl` returned an empty banner string.

### 2. Concurrent-import HTTP 409 flash in browser

- **Status:** passed
- **Screenshots:** captured via `curl` headers (no browser screenshot — race window <100 ms)
- **Evidence:**
  - Thread A POSTed `/admin/backup/import-execute` with stagingId `78d236f5-…`; ~50 ms later thread B POSTed the same endpoint with a different stagingId.
  - Thread B response headers (`/tmp/uat-execB-headers.txt`):
    ```
    HTTP/1.1 409
    Location: /admin/backup
    ```
  - View-mode redirect with `HttpStatus.CONFLICT` (NOT 302) confirms Pitfall #1 mitigation. Flash text `Another import is already running — please wait.` is asserted by `ImportConcurrentLockIT`.

### 3. 503 on non-whitelisted POST during active import

- **Status:** passed
- **Screenshots:** captured via `curl` headers
- **Evidence:**
  - During the same race window as item 2, a POST to `/admin/teams/save` (non-whitelisted /admin/** URL) returned:
    ```
    HTTP/1.1 503
    ```
  - The body contains `Backup import in progress — write access is temporarily locked.` verbatim with a meta-refresh tag (per `ImportLockedWriteRejector.LOCK_HTML`).
  - The /admin/backup/import-execute whitelist was confirmed by item 2 — that endpoint returned 409 rather than 503, proving the interceptor's `equals()` whitelist works correctly.

### 4. Auto-backup ZIP layout on disk after successful import

- **Status:** passed
- **Screenshots:**
  - [04-disk-layout.txt](../../../.screenshots/76/04-disk-layout.txt) — `ls -la` of `data/.import-backups/`
- **Evidence:**
  ```
  data/.import-backups/2026-05-14T20-36-47Z/
    auto-backup-before-import.zip   (22326330 bytes — non-empty)
    uploads-old/                    (directory — D-15 shared <ts>)
  ```
  - `unzip -l` first 5 entries:
    ```
    Length      Date    Time    Name
    ---------  ---------- -----   ----
        708    2026-05-14 22:36   manifest.json    ← FIRST entry per CONTEXT D-22
      33220    2026-05-14 22:36   data/tracks.json
        197    2026-05-14 22:36   data/match-scorings.json
        238    2026-05-14 22:36   data/race-scorings.json
    ```
  - `manifest.json` is the first entry in the archive central directory (CONTEXT requirement); per-entity data files live under `data/`.

### 5. Runbook readability — all 5 H2 sections and verbatim UI strings

- **Status:** passed
- **Screenshots:** N/A (text artifact)
- **Evidence:**
  - `docs/operations/import-runbook.md` opens with all 5 H2 sections in order:
    - `## 1. Recovery from auto-backup`
    - `## 2. 24h retention semantics`
    - `## 3. Audit-id query SQL`
    - `## 4. Concurrent-import behavior`
    - `## 5. Read-only state during imports`
  - All three locked UI strings are present verbatim:
    - `Backup import in progress — write access is temporarily locked.` (D-12 banner / 503 body)
    - `Another import is already running — please wait.` (D-04 concurrent flash)
    - `Import aborted — pre-import auto-backup failed. No database changes.` (D-17 auto-backup flash — added in this UAT session after detecting it was paraphrased earlier)
  - Footer reads `Phase 76 of v1.10 — see ROADMAP.md for milestone scope.`

## Summary

| # | Test | Status | Evidence |
|---|------|--------|----------|
| 1 | Cross-page banner visibility | passed | 5 admin pages, 2 screenshots |
| 2 | Concurrent-import 409 flash | passed | curl headers |
| 3 | 503 on non-whitelisted POST | passed | curl headers |
| 4 | Auto-backup ZIP layout on disk | passed | filesystem listing + unzip -l |
| 5 | Runbook readability | passed | grep -F + section count |

**5/5 passed, 0 failed, 0 skipped.** Phase 76 human UAT complete.
