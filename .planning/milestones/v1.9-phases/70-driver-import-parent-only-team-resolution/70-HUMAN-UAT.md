---
status: diagnosed
phase: 70-driver-import-parent-only-team-resolution
source: [70-VERIFICATION.md]
started: 2026-05-09T15:00:00Z
updated: 2026-05-09T17:05:00Z
---

## Current Test

[failed — see Gaps]

## Tests

### 1. Live-MariaDB UAT — Driver Import on Saison 2023 (D-22 / ROADMAP SC6)
expected: With the app started on the `local` profile (MariaDB) and the Saison-2023 sheet imported (parent `MRL` + sub-team `MRL 1` in Group 2 + sub-team `MRL 2` in Group 1), the import preview shows **no** Group column and **no** `TEAM_NOT_IN_REGULAR_PHASE` warning box. After clicking Execute, every MRL driver's `SeasonDriver.team_id` resolves to the **parent MRL row** (not to a sub-team), verifiable via SQL: `SELECT sd.driver_id, sd.team_id, t.short_name, t.parent_team_id FROM season_drivers sd JOIN teams t ON sd.team_id = t.id JOIN seasons s ON sd.season_id = s.id WHERE s.season_year = 2023;` — every row's `parent_team_id` should be `NULL` (i.e. the team is itself a parent), and the matching `t.short_name` should be `MRL` for the MRL drivers.
result: partial — preview rendering and parent-team resolution both verified OK by user; **execute step fails** with `DataIntegrityViolationException: Duplicate entry 'danfn22016' for key 'psn_id'` (MariaDB 1062-23000) — entire import transaction rolls back, no `season_drivers` rows written. Stack trace captured in `data/local/logs/app.log` at 2026-05-09 16:53:55.

## Summary

total: 1
passed: 0
issues: 1
pending: 0
skipped: 0
blocked: 0

## Gaps

### GAP-70-01 — Cross-tab duplicate Driver insert on Execute (production blocker)

**Symptom (live UAT, Saison 2023, MariaDB local):**
```
Duplicate entry 'danfn22016' for key 'psn_id'
[insert into drivers (active,created_at,nickname,psn_id,updated_at,id) values (?,?,?,?,?,?)]
```
Caught by `DriverSheetImportController.execute()` at line 75; root cause is `JpaTransactionManager.doCommit` flushing a duplicate INSERT into `drivers`. Whole import transaction rolls back; SeasonDriver rows are NOT written.

**Why earlier code-review fix was insufficient:**
WR-01 (`fix(70)` commit `8256a71`) hardened only the FUZZY-no-accept branch (`DriverSheetImportService.java:185-189`) by adding `driverRepository.findByPsnId(...)` inside `crossTabCreatedDrivers.computeIfAbsent(...)`. The NEW_DRIVER branch (`DriverSheetImportService.java:121-127`) was NOT hardened — it relies solely on the in-memory `crossTabCreatedDrivers` cache keyed on `row.psnId()`. The live failure indicates the cache is being bypassed in real-world data.

**Hypotheses to validate during gap-closure planning:**
1. Same PSN appears as NEW_DRIVER row in multiple tabs of the same execute call, but the cache key is escaping due to:
   a. Whitespace / case differences in `row.psnId()` between tabs (cell-level normalization happens at `DriverSheetImportService.java:364` via `trim()`, but case is preserved). If sheet has `DanFn22016` in tab 1 and `danfn22016` in tab 2, cache misses but DB unique constraint catches it.
   b. Some flush-ordering issue where computeIfAbsent puts the not-yet-persisted entity into the cache, but Hibernate flushes a duplicate insert at commit because the cache hit returned the entity reference but the insert was already queued.
2. The driver pre-exists in DB (legacy data from a partial import), but preview classifies as NEW_DRIVER (because matching is fuzzy/exact-only and missed); execute then attempts insert → unique constraint violation. Symmetric to WR-01 but for the NEW_DRIVER branch.
3. A parallel admin tab / second execute click ran during the same Hibernate session.

**Acceptance criteria for fix:**
1. NEW_DRIVER branch defends against all 3 hypotheses by checking `driverRepository.findByPsnId(psnId)` inside the `computeIfAbsent` lambda (mirror of WR-01 hardening at line 185-189).
2. Add an integration regression test that reproduces the live failure shape: NEW_DRIVER row in tab 1 + NEW_DRIVER row in tab 2 with **same PSN string**, executed in one call, no pre-existing Driver row, no fuzzy match — must succeed without unique-constraint violation and create exactly one Driver row.
3. Add a defensive integration regression test for the pre-existing-Driver hypothesis: a Driver with `psnId='danfn22016'` already exists in DB; preview classifies as NEW_DRIVER because matching missed; execute must reuse the pre-existing Driver, not insert.
4. Re-run live UAT on Saison 2023 with the user's actual sheet — expect Execute to succeed and SQL check (parent_team_id IS NULL for MRL drivers) to pass.

**Out of scope for this gap:**
- The PSN normalization question (case-sensitive vs case-insensitive matching). If hypothesis 1a is confirmed during diagnosis, normalization is a deferred decision for a separate phase.
- The "DriverMatchingService missed the existing driver" question (hypothesis 2). If confirmed, that's a separate matching-quality gap, not a Phase-70 concern.

**Live evidence:** `data/local/logs/app.log` — full stack trace at 2026-05-09 16:53:55.
