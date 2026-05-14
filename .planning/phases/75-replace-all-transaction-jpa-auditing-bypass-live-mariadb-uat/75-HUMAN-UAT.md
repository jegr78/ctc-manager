---
status: partial
phase: 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
source: [75-10-PLAN.md, 75-CONTEXT.md D-16, ROADMAP Success Criterion 5, QUAL-03]
started: 2026-05-14T00:00:00Z
updated: 2026-05-14T00:00:00Z
---

# 75-HUMAN-UAT — Live-MariaDB Round-Trip Screenshot Checklist

## Current Test

[awaiting operator execution]

## Scope (D-16)

Two-layer verification:

- **CI layer (autonomous):** `BackupImportMariaDbSmokeIT` (Failsafe IT, Testcontainers MariaDB:11)
  proves 24-entity row-count parity on the same DB engine as production. Already runs in the main
  CI workflow via Failsafe's default `*IT` pattern.
- **Human layer (this file):** 6 vor-/nach-Import screenshot pairs on local MariaDB with the
  Saison-2023 dev fixture. Operator inspects position order, point totals, group split, and
  driver–team assignment byte-by-byte across the export → wipe → import boundary.

## Pre-Conditions

1. Local MariaDB up via `docker compose up -d db`.
2. App started on `local,demo` profile via
   `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,demo` → http://localhost:9091.
3. Wait for the `Started CtcManagerApplication` log line.
4. (Optional) Run `playwright-cli install chromium` once if no Chromium is cached.

## Screenshot Capture Tooling

- **Preferred:** `gsd-auto-uat` skill — automates the open/screenshot/diff loop and writes
  results back into this file. Invoke: `/gsd-auto-uat 75` (per memory
  `feedback_auto_uat_reminder.md`).
- **Fallback:** `playwright-cli` skill — manual per-route capture
  (memory `feedback_playwright_cli.md`). Example:
  `playwright-cli open http://localhost:9091/seasons/2023 --screenshot .screenshots/75/before/01-standings-r-a.png`.

Filename convention (D-16): `<NN>-<slug>[-desktop|-mobile].png`. Place each PNG in
`.screenshots/75/before/` and the matching post-import PNG with the SAME filename in
`.screenshots/75/after/`.

## Tests

### 1. Saison 2023 Standings — REGULAR / Group A
- route: `/seasons/2023?phase=REGULAR&group=A`
- filename: `01-standings-r-a.png`
- expected: Position order, point totals, and team assignments identical before vs. after.
- result: [ ] PASS / [ ] FAIL
- notes:

### 2. Saison 2023 Standings — REGULAR / Group B
- route: `/seasons/2023?phase=REGULAR&group=B`
- filename: `02-standings-r-b.png`
- expected: Position order, point totals, and team assignments identical before vs. after.
- result: [ ] PASS / [ ] FAIL
- notes:

### 3. Saison 2023 Driver Ranking (default phase)
- route: `/seasons/2023/driver-ranking`
- filename: `03-driver-ranking.png`
- expected: Driver ordering + point totals + team-affiliation columns identical.
- result: [ ] PASS / [ ] FAIL
- notes:

### 4. Saison 2023 Playoff Bracket
- route: `/seasons/2023/playoff`
- filename: `04-playoff.png`
- expected: Bracket structure, seeds, matchup wires, and winner stubs identical.
- result: [ ] PASS / [ ] FAIL
- notes:

### 5. Sub-Team Phase Breakdown (Saison-2023 Group A)
- route: `/teams/<sub-team-slug>` (pick any Saison-2023 Group-A sub-team)
- filename: `05-team-breakdown.png`
- expected: Phase rows + per-phase point totals + driver lineup identical.
- result: [ ] PASS / [ ] FAIL
- notes:

### 6. Driver Phase Breakdown (cross-phase results)
- route: `/drivers/<driver-slug>` (pick any Saison-2023 driver with results across multiple phases)
- filename: `06-driver-breakdown.png`
- expected: Per-phase point rows + team affiliation per phase identical.
- result: [ ] PASS / [ ] FAIL
- notes:

## Operational Checks

After running the export → import cycle from the admin UI (Admin → Backup → Export → download,
then Admin → Backup → Import → upload → Preview → acknowledge → Confirm), verify:

| # | Check | Command | Expected |
|---|-------|---------|----------|
| O-1 | `uploads-old` retention | `ls -la data/.import-backups/` | `<ts>/uploads-old/` subdir exists (24h manual-recovery window, IMPORT-06) |
| O-2 | `data_import_audit` row | `SELECT id, executed_at, success, source_filename FROM data_import_audit ORDER BY executed_at DESC LIMIT 1;` | `success = TRUE`, recent `executed_at`, source filename matches the uploaded ZIP |
| O-3 | `table_counts_restored` JSON | `SELECT table_counts_restored FROM data_import_audit ORDER BY executed_at DESC LIMIT 1;` | Non-blank JSON with 24 keys and non-zero values |
| O-4 | D-15 #1 success flash | Browser inspection on `/admin/backup` after Confirm | Flash matches `Import completed. {N} rows restored across {M} tables.` |

| # | Check | Result |
|---|-------|--------|
| O-1 | uploads-old retained | [ ] PASS / [ ] FAIL |
| O-2 | audit row success=true | [ ] PASS / [ ] FAIL |
| O-3 | table_counts_restored populated | [ ] PASS / [ ] FAIL |
| O-4 | D-15 success flash shown | [ ] PASS / [ ] FAIL |

## Execution Workflow

1. Bring up MariaDB + start app on `local,demo`.
2. Capture all 6 vor-Import screenshots in `.screenshots/75/before/`.
3. Run full Export → Import cycle via admin UI.
4. Wait for D-15 #1 success flash.
5. Capture all 6 nach-Import screenshots in `.screenshots/75/after/` with the SAME filenames.
6. Visual diff each pair (eyeball or image diff tool).
7. Tick PASS/FAIL + free-text notes per entry above.
8. Run the 4 operational checks (O-1..O-4) and tick results.
9. Sign off below, commit `75-HUMAN-UAT.md` + `.screenshots/75/{before,after}/*.png`.
10. Type `approved` to the orchestrator; the phase is marked complete.
    On regression: type `issues: <route-names>` — phase enters gap-closure.

## Summary

total: 10
passed: 0
issues: 0
pending: 10
skipped: 0
blocked: 0

## Operator Sign-Off

- operator: ____________________
- date: ____________________
- overall: [ ] PASS — all 6 visual diffs match + all 4 operational checks green
          [ ] FAIL — see notes above

## Gaps
