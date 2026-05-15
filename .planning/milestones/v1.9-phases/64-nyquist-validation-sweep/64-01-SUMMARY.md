---
plan_id: 64-01
plan: "01"
phase: 64
phase_name: nyquist-validation-sweep
title: Nyquist Validation Sweep — 6 phases retroactive
type: execute
status: complete
completed: 2026-05-07
mode: retroactive
---

# Phase 64 — Plan 01 Summary: Nyquist Validation Sweep complete

## Outcome

v1.9 closes with **7 phases nyquist_compliant / 0 partial / 0 missing**.

| Phase | State at audit | Outcome |
|-------|----------------|---------|
| 56 | State B (no VALIDATION.md) | NEW file at full Phase-61 depth, 10 REQ-IDs, 1 auto-fill (V3MigrationTest) |
| 57 | State A (draft, false/false) | Upgraded to Phase-61 depth, 4 REQ-IDs, 0 auto-fill (lateral coverage already exists) |
| 58 | State A (true/false) | Mechanical T1 flip — wave_0_complete: true (PhaseTestFixtures + 3 Repo ITs already green) |
| 59 | State B (no VALIDATION.md) | NEW file at full Phase-61 depth, 6 REQ-IDs, 0 auto-fill, 1 fix |
| 60 | State A (draft, false/false) | Upgraded to Phase-61 depth, 7 REQ-IDs, 0 auto-fill, 2 fixes |
| 62 | State A (draft, false/false) | Upgraded to Phase-61 depth, 5 REQ-IDs, 0 auto-fill (lateral coverage already exists) |

## REQ-ID coverage per phase

| Phase | REQ-IDs audited | Source |
|-------|-----------------|--------|
| 56 | MODEL-01..08, MIGR-01, MIGR-07 (10) | matches ROADMAP signal |
| 57 | MIGR-02, MIGR-03, MIGR-04, MIGR-05 (4) | from PLAN frontmatters |
| 58 | 20 task rows from existing Per-Task Map | unchanged from prior planning sign-off |
| 59 | IMPORT-01..04, DATA-01..02 (6) | matches ROADMAP signal |
| 60 | UI-01..07 (7) | from PLAN frontmatters |
| 62 | QUAL-01, QUAL-02, UI-02, UI-05, UI-07 (5) | from PLAN frontmatters |

## Auto-filled gaps

One auto-fill triggered, in Phase 56:

- `src/test/java/db/migration/V3MigrationTest.java` — 8 INFORMATION_SCHEMA assertion methods modeled on the V5MigrationTest / V6MigrationTest pattern from Phase 61. Covers the 3 new V3 tables (season_phases, season_phase_groups, phase_teams), 3 UNIQUE constraints (uk_season_phase_type, uk_phase_team, uk_playoff_phase), nullable phase_id columns on matchdays / playoffs, season_phases column inventory, and V1 original-table preservation. 8/8 green on first iteration. Committed as `test(64): add Nyquist gap-fill V3 migration test`.

No other auto-fill triggered — every other REQ-ID was already COVERED laterally by tests committed during the original phase execution.

## Manual-Only escalations

| Phase | Manual-Only count | Predominant rationale |
|-------|-------------------|------------------------|
| 56 | 3 | MariaDB CI tier; SeasonFormat variants in Phase 58 scope; cosmetic @JoinColumn hint |
| 57 | 1 | MariaDB-specific migration smoke (de-facto closed via mariadb-migration-smoke.yml CI gate + Phase 61 UAT-03) |
| 58 | 2 | UI-level smoke after service refactor; MariaDB Spring Data magic-naming |
| 59 | 2 | Visual warning badge; DevDataSeeder boot smoke |
| 60 | 6 | Visual-Quality-Bar (color, spacing, hover state); Production-Data-Boundary |
| 62 | 6 | Visual-Quality-Bar for public-site visuals |

All Manual-Only entries carry a concrete `Why Manual` rationale per row.

## Scope expansion (user-approved deviation)

Phase 64's plan explicitly forbade `src/main/...` modification ("DO NOT modify files under src/main/ — return NEEDS_CONTEXT"). Two auditor returns surfaced three real defects that escaped that boundary:

1. **`SeasonPhaseController.detail()` line 69** — used `phase.getGroups()` on a Hibernate-cached entity. GROUPS-layout phases never rendered group sub-tabs in season detail view. (UI-02 / D-29)
2. **`SeasonPhaseGroupController.edit()` lines 61-64** — same root cause; existing groups returned 404 on the edit form. (UI-04)
3. **`DriverSheetImportServiceIT.givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToLeague2024`** — failing because Phase 60 added `(year=2024, number=3)` to the dev seed post-Phase-59, making `findUnique(2024)` ambiguous. Test precondition contract violated. (IMPORT-01)

User chose scope expansion (option 1 in the checkpoint). Resolution lives in `fix(64): replace stale phase.getGroups with repository queries; harden legacy-tab IT`:

- Both controllers now inject `SeasonPhaseGroupRepository` and load groups via `findByPhaseIdOrderBySortIndex` / `findById` (with explicit ownership validation on the second). The `groupDetail` and group-create auto-increment paths got the same treatment for consistency.
- The legacy-tab IT was renamed to `givenLegacyYearTab_whenPreview_thenSeasonAutoResolvedToUniqueYearSeason` and now inserts a fresh single-season year (2027) inline so the precondition is independent of seed evolution.

Post-fix: all three failing tests green; full `./mvnw verify` green at 1224 tests / 0 failures with JaCoCo line coverage 85.6% (gate 82%).

## Net-new test infrastructure

- `src/test/java/db/migration/V3MigrationTest.java` — only net-new file. All other phases reused infrastructure already inventoried in their original SUMMARY trail.

## Commit cadence

| # | Commit | Files | Purpose |
|---|--------|-------|---------|
| 1 | `test(64): add Nyquist gap-fill V3 migration test` | V3MigrationTest.java | Auto-fill from Phase 56 audit |
| 2 | `fix(64): replace stale phase.getGroups with repository queries; harden legacy-tab IT` | 2 controllers + 1 IT test | User-approved scope expansion |
| 3 | `docs(64): nyquist validation sweep — 6 phases retroactive` | 6 VALIDATION.md | Audit artifacts |
| 4 | `docs(64-01): plan summary — nyquist validation sweep complete` | this file | Plan close |

## Plan-level success criteria — verdict

| SC | Criterion | Verdict |
|----|-----------|---------|
| SC1 | 56-VALIDATION.md exists, both flags true | PASS |
| SC2 | 57-VALIDATION.md updated, both flags true | PASS |
| SC3 | 58-VALIDATION.md updated, wave_0_complete=true | PASS |
| SC4 | 59-VALIDATION.md exists, both flags true | PASS |
| SC5 | 60-VALIDATION.md updated, both flags true | PASS |
| SC6 | 62-VALIDATION.md updated, both flags true | PASS |
| — | All 6 files end with `## Validation Audit 2026-05-07` | PASS |
| — | Phase 56 Per-Task Map covers all 10 REQ-IDs | PASS |
| — | Phase 59 Per-Task Map covers all PLAN-frontmatter REQ-IDs | PASS (6/6) |
| — | Auto-fill tests pass | PASS (V3MigrationTest 8/8) |
| — | `./mvnw verify` exits 0 | PASS (1224 tests / 0 failures) |
| — | JaCoCo line coverage ≥ 82% | PASS (85.6%) |
| — | Active branch `gsd/v1.9-season-phases-groups` | PASS |

## Branch hygiene

- Active branch: `gsd/v1.9-season-phases-groups` — unchanged throughout
- 4 commits added on top of `e495c75 docs(64): create phase plan`
- No subagent attempted a branch switch or destructive git operation

## Notes for v1.9 milestone close

- Phase 64 closes the Nyquist coverage debt cluster identified in v1.9-MILESTONE-AUDIT.md.
- Phase 65 (Graphics Services Bridge Migration) remains the only non-shipped phase in v1.9.
- The plan template's `grep -c '^---' = '2'` automated frontmatter check is over-specified — it would also fail on the gold-standard 61-VALIDATION.md (9 occurrences from body section separators). Treat it as a known plan defect; the semantic intent (frontmatter present and properly delimited) is verified by the `head -1 grep '^---'` plus the trio of frontmatter field grep checks. Recommended: relax to `>= 2` in future plan templates.
