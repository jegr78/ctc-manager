---
status: partial
phase: 73-backup-export-jackson-mixins-streaming-zip-endpoint
source: [73-VERIFICATION.md, 73-AUTO-UAT.md]
started: 2026-05-12T13:00:00Z
updated: 2026-05-12T13:05:00Z
---

## Current Test

[2/3 auto-verified — 1 deferred to Phase 75]

## Tests

### 1. Visual sanity check of /admin/backup page rendering on dev profile
expected: Page shows H1 "Backup", description paragraph mentioning "24 entities", single blue `.btn-primary` Export Backup button; sidebar "Data" group with "Backup" entry highlighted active when on this page
result: passed (auto-verified 2026-05-12T13:00Z via gsd-auto-uat — see 73-AUTO-UAT.md §1)

### 2. Manual full export click-through with downloaded ZIP inspection
expected: Admin clicks Backup → /admin/backup → Export Backup button → browser downloads `ctc-backup-YYYYMMDDTHHMMSSZ.zip`; opening the ZIP shows manifest.json (first entry, with schema_version=1, app_version, export_date, table_counts), data/*.json files for all 24 entities, and uploads/ directory mirror of referenced files
result: passed (auto-verified 2026-05-12T13:00Z — 22 MB ZIP, manifest first, 24/24 data files, 725 entries, ISO-instant filename — see 73-AUTO-UAT.md §2)

### 3. Live MariaDB UAT — export → wipe → manual restore inspection
expected: Backup exported from local MariaDB instance is restorable manually and contains structurally complete data (covered by Phase 75 QUAL-03 deliverable)
result: skipped (deferred to Phase 75 QUAL-03 — round-trip on real MariaDB profile is downstream scope)

## Summary

total: 3
passed: 2
issues: 0
pending: 0
skipped: 1
blocked: 0

## Gaps
