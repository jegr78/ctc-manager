---
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
executed: 2026-05-12T13:00:00Z
server_profile: dev,demo
total: 3
passed: 2
failed: 0
skipped: 1
---

# Auto-UAT Report: Phase 73

## Results

### 1. Visual sanity check of /admin/backup page rendering on dev profile

- **Status:** passed
- **Screenshots:**
  - [test-1-result.png](../../../.screenshots/auto-uat/test-1-result.png)
  - [test-1-backup-page.yaml](../../../.screenshots/auto-uat/test-1-backup-page.yaml) — accessibility snapshot
- **Evidence:**
  - Page title: `CTC Admin - Backup`
  - H1 `[level=1]`: "Backup"
  - Description paragraph mentioning "24 entities": "Exports the full league database (all 24 entities) plus every uploaded file into a single ZIP archive. The download starts immediately and may take a few seconds for large leagues."
  - Single `button "Export Backup"` rendered (`.btn-primary` style)
  - Sidebar "Data" group present with "Backup" link `[/url: /admin/backup]`
  - Active-state confirmed via DOM eval: `aside a[href="/admin/backup"].className === "active"`

### 2. Manual full export click-through with downloaded ZIP inspection

- **Status:** passed
- **Screenshots:**
  - [test-2-downloaded-backup.zip](../../../.screenshots/auto-uat/test-2-downloaded-backup.zip) — actual downloaded archive (22 MB)
- **Evidence:**
  - HTTP 200 on `POST /admin/backup/export`
  - `Content-Disposition: attachment; filename="ctc-backup-20260512T105956Z.zip"` — ISO-instant filename ✓
  - `Content-Type: application/octet-stream`
  - `Transfer-Encoding: chunked` — confirms streaming (no full-buffer)
  - ZIP size: 22,326,392 bytes (725 entries total)
  - **Manifest-first invariant:** `manifest.json` is entry 1 of the archive
  - Manifest content parses cleanly:
    - `schema_version: 1`
    - `app_version: "1.8.0-SNAPSHOT"`
    - `export_date: "2026-05-12T10:59:56.950959Z"` (ISO instant)
    - `table_counts`: 24 entities with real GT7 demo data (562 cars, 121 tracks, 1452 race_results, 1534 race_lineups, etc.)
  - Exactly 24 `data/*.json` entries (matches BackupSchema.getExportOrder)
  - `uploads/` directory mirror present with track images (700+ files)

### 3. Live MariaDB UAT — export → wipe → manual restore inspection

- **Status:** skipped
- **Reason:** Scheduled as Phase 75 QUAL-03 deliverable — full round-trip on real MariaDB profile is downstream scope, not a Phase 73 blocker. Export side now ready for that UAT.

## Summary

Executed via `gsd-auto-uat 73` on dev,demo profile.
- 2/2 automatable items passed
- 1 item correctly deferred to Phase 75
- Phase 73 goal achievement: 6/6 requirements verified + 2 human-UAT items confirmed
