---
status: partial
phase: 70-driver-import-parent-only-team-resolution
source: [70-VERIFICATION.md]
started: 2026-05-09T15:00:00Z
updated: 2026-05-09T15:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Live-MariaDB UAT — Driver Import on Saison 2023 (D-22 / ROADMAP SC6)
expected: With the app started on the `local` profile (MariaDB) and the Saison-2023 sheet imported (parent `MRL` + sub-team `MRL 1` in Group 2 + sub-team `MRL 2` in Group 1), the import preview shows **no** Group column and **no** `TEAM_NOT_IN_REGULAR_PHASE` warning box. After clicking Execute, every MRL driver's `SeasonDriver.team_id` resolves to the **parent MRL row** (not to a sub-team), verifiable via SQL: `SELECT sd.driver_id, sd.team_id, t.short_name, t.parent_team_id FROM season_drivers sd JOIN teams t ON sd.team_id = t.id JOIN seasons s ON sd.season_id = s.id WHERE s.season_year = 2023;` — every row's `parent_team_id` should be `NULL` (i.e. the team is itself a parent), and the matching `t.short_name` should be `MRL` for the MRL drivers.
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
