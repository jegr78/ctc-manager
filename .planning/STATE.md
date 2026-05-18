---
gsd_state_version: 1.0
milestone: v1.12
milestone_name: Driver-Import Gap-Closure & Test Performance Round 2
status: planning
last_updated: "2026-05-18T16:05:56.719Z"
last_activity: 2026-05-18
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-18)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.12 milestone planning — defining requirements + roadmap

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-05-18 — Milestone v1.12 started

## Completed Milestones

- v1.0 Technical Debt Cleanup (5 phases, 12 plans) — shipped 2026-04-04
- v1.1 Codebase Concerns Cleanup (10 phases, 20 plans) — shipped 2026-04-07
- v1.2 Driver Merge (4 phases, 5 plans) — shipped 2026-04-07
- v1.3 English Test Data (8 phases) — shipped 2026-04-10
- v1.5 Code Review Fixes (9 phases, 14 plans) — shipped 2026-04-15
- v1.6 Static Site Quality (17 phases, 56 requirements) — shipped 2026-04-18
- v1.8 Bulk Driver Import from Google Sheets (2 phases, 4 plans, +52 tests) — shipped 2026-04-25
- v1.9 Season Phases & Groups (15 phases, ~70 plans, 38/38 requirements, +88.4k LOC) — shipped 2026-05-09
- v1.10 Spring Boot 4.0.6 Upgrade & Data Export/Import (9 phases, 50 plans, 39/39 requirements, +77.4k LOC, 87.80% JaCoCo) — shipped 2026-05-16
- v1.11 Tooling Infrastructure & Tech-Debt Sweep (8 phases 80-87, 46 plans, 46/46 requirements, JaCoCo 88.88%, 1675 tests, CI E2E median 23:00) — shipped 2026-05-18

## Deferred Items

Items carried forward into v1.12 (to be cleared by roadmap phases 88+):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| tech_debt | Driver-import shortname-resolver picks parent without PhaseTeam (data-correctness) | DRIV-01 — season-aware resolver |
| tech_debt | Driver-import group-warnings fire for non-GROUPS seasons (UI noise) | DRIV-02 — layout gate + per-tab usesGroups flag |
| tech_debt | PERF-FUTURE-01 — CI test wallclock 23:00 vs target ≤7m 50s (Phase 86 OR-branch) | PERF-01..PERF-05 — 3-lever forward path + module-split decision |
| tech_debt | BackupSchemaExclusionIT.java:40 Java-25 AssertJ generic-inference compile error | CLEAN-01 — fix or `@SuppressWarnings("unchecked")` |
| ux_debt | Google Sheets/Calendar generic error messages on auth/network/sheet-id failures | UX-01 (stretch) |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | post-deploy operator action (procedure docs/uat/UAT-02-legacy-season-smoke.md) |
| uat | QUAL-02 local-profile MariaDB manual smoke (DevDataSeeder widening) | post-deploy operator action |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | v1.11 PR #122 release workflow tagging | resolved by-design post-merge |

## Pending UATs

### UAT-02: Legacy Season Visual Smoke (carry-forward from v1.11 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** post-deploy operator action (procedure + result slot in place)
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

### QUAL-02: Local-Profile MariaDB Smoke (carry-forward from v1.11 QUAL-02)

- **Procedure:** start app with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` and verify `DevDataSeeder` widened to `@Profile({"dev","local"})` seeds correctly against real MariaDB
- **Status:** post-deploy operator action
- **Result:** _(operator fills after execution)_

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md "Key Decisions" table and per-milestone in `milestones/v*.x-ROADMAP.md`. v1.12 decisions will be captured during phase planning.

### Phase Numbering

Last phase shipped: **87** (v1.11 closer). v1.12 starts at **phase 88**, numbering continues (no `--reset-phase-numbers`).

### Roadmap Evolution

- 2026-05-18: v1.11 milestone closed; archived at `milestones/v1.11-{ROADMAP,REQUIREMENTS,MILESTONE-AUDIT}.md` + `milestones/v1.11-phases/`.
- 2026-05-18: v1.12 milestone started. Branch `gsd/v1.12-driver-import-and-test-perf`. Scope: driver-import data-correctness (DRIV-01..02) + test-wallclock Round 2 (PERF-01..05) + cleanup (CLEAN-01) + optional UX (UX-01).

### Blockers/Concerns

At roadmap creation:

- **Pre-existing `BackupSchemaExclusionIT.java:40` compile error** (Java-25 AssertJ generic inference) blocks any `./mvnw verify` exit 0 + JaCoCo CSV generation — must be fixed early in v1.12 (CLEAN-01) or all PERF measurements remain blocked. Tracked in v1.11 `80-deferred-items.md`.
- **Test-Module-Split decision risk** — Lever 4 may not pay off without restructuring `src/test/java` into independent Maven modules; D-decision-point planning required before commitment.
- **Driver-import resolver semantics** — DRIV-01 inverts the Phase 70 D-05 default ("parent wins"); must coordinate with `SeasonDriver.team_id` invariant (parent reference still holds for non-collision rows).
- **CI wallclock measurement methodology** — every PERF lever must re-harvest via D-17 trigger-equivalence (PR-branch `workflow_dispatch` ≡ post-merge master CI) to avoid orphan post-merge measurement commits.

### Baselines to Preserve

- JaCoCo line coverage: **88.88%** (v1.11 baseline; gate 82%, must not regress)
- Test count: **1675 tests** (Surefire + Failsafe + Playwright E2E combined; v1.11 final)
- `./mvnw verify -Pe2e` CI median (E2E step): **23:00** (v1.11 baseline; v1.12 Round-2 target TBD post-PERF-01..03)
- `BackupSchema.SCHEMA_VERSION`: **1** (must remain 1; schema change → bump to 2)
- `EXPORT_ORDER` size: **24 entities** (guard test active)
- SpotBugs `BugInstance` count: **0** (blocking gate)

## Session Continuity

**Next action:** Define v1.12 REQUIREMENTS.md + ROADMAP.md (in progress via `/gsd-new-milestone`).

**Branch:** `gsd/v1.12-driver-import-and-test-perf`

## Operator Next Steps

- Approve v1.12 REQUIREMENTS.md
- Approve v1.12 ROADMAP.md from roadmapper
- Then `/gsd:discuss-phase 88` to start the first phase
