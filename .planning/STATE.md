---
gsd_state_version: 1.0
milestone: v1.11
milestone_name: Tooling Infrastructure & Tech-Debt Sweep
status: executing
last_updated: "2026-05-18T09:40:00.000Z"
last_activity: 2026-05-18 -- Phase 87 Plan 87-04 complete (Phase 74 retroactive VALIDATION approved, 1 gap resolved)
progress:
  total_phases: 8
  completed_phases: 7
  total_plans: 46
  completed_plans: 44
  percent: 96
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-16)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 87 — Nyquist VALIDATION Closure (last phase of v1.11)

## Current Position

Phase: 87 (nyquist-validation-closure) — **IN PROGRESS** (4/8 plans complete: 87-01 + 87-02 + 87-03 + 87-04)
Next: Plan 87-05 (Restore + audit + approve v1.10 Phase 75, State A — IMPORT-05..07 + QUAL-03)
Last activity: 2026-05-18 -- Plan 87-04 complete (Phase 74 retroactive VALIDATION approved, 1 gap resolved via BackupUploadExceptionHandlerScopeIT, commits db41a1d6 + fb68a87e + 7c58e121)

```
Progress: [█████████▊] 98%
```

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

## Deferred Items

Items carried forward into v1.11 (all cleared by roadmap phases 80-87):

| Category | Item | Resolution Phase |
| -------- | ---- | ---------------- |
| tech_debt | REVIEW.md WR-01..WR-08 + IN-01..IN-04 (12 Info/Warning items) | Phase 82 — resolved |
| tech_debt | Phase 79 D-06 wallclock-reduction (16.85% vs ≥30% target) | Phase 86 |
| tech_debt | Driver-detail Season-Assignment chip ordering (ORDER BY year) | Phase 83 — resolved (QUAL-01) |
| tech_debt | DevDataSeeder @Profile("dev")-only widening for local,demo | Phase 83 — resolved (QUAL-02; local smoke pending operator at milestone-close) |
| tech_debt | Nyquist *-VALIDATION.md for 6 phases (72-76, 79) + creation for 71 + 78 | Phase 87 |
| tech_debt | Per-group matchday generation UI affordance (SeasonController:251) | Phase 83 — resolved (QUAL-03) |
| tech_debt | StandingsController.java:139 lazy collection style cleanup | Phase 83 — resolved (QUAL-04) |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | Phase 83 — procedure + slot ready (QUAL-05); live execution post-deploy |
| backlog | OpenRewrite (Phase 999.1) — promoted to active milestone | Phase 80 |
| backlog | Clean-Code enforcement (Phase 999.2) — promoted (SpotBugs only) | Phase 81 |
| backlog | Renovate (Phase 999.3) — promoted to active milestone | Phase 84 |
| backlog | SAST (Phase 999.4) — promoted (CodeQL, public repo) | Phase 85 |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | QUAL-05 wiki image embed render | resolved on v1.10 PR merge to master |
| post_merge | PLAT-CI-02 release-workflow run on master | resolved by-design post-merge |

## Pending UATs

### UAT-02: Legacy Season Visual Smoke (Phase 83 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** Ready to execute
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md "Key Decisions" table and per-milestone in `milestones/v*.x-ROADMAP.md`.

**v1.11 key decisions (from research synthesis 2026-05-16):**

| Decision | Rationale |
| -------- | --------- |
| CodeQL instead of Semgrep CE for SAST | Repo is public on GitHub — CodeQL is free with full cross-function taint tracking; Semgrep CE post-Dec 2024 limited to single-function only |
| SpotBugs only (no Checkstyle, no PMD) | Lombok false positives in Checkstyle + PMD have no proportionate value given CLAUDE.md conventions + code review discipline already in place |
| OpenRewrite in `-Prewrite` Maven profile only | Plugin with no `<executions>` binding never runs during default `verify` lifecycle — no CI auto-execution risk |
| Exclude `UpgradeSpringBoot_4_0` recipe | Codebase is already on Boot 4.0.6; the migration composite recipe would attempt re-migration and produce confusing diffs |
| Phase ordering: 80→81→82→83→84→85→86→87 | OpenRewrite cleanup reduces initial SpotBugs count; gate active before Renovate PRs; CodeQL additive after gate is clean; wallclock last to avoid gate ambiguity |
| Renovate via Mend GitHub App (not self-hosted) | Zero runner-minute cost; auto-updating; no workflow file required |

- [Phase ?]: Phase 80-03: REWR-01 + REWR-03 structurally verified; dryrun_outcome patch-non-empty (sha256 63072f65…, 25 domain/model entity-file hunks). Plan 04 will execute Branch B cleanup.
- [Phase ?]: Phase 80-04: OpenRewrite CommonStaticAnalysis applied; D-08 fallback 1 file revert; JaCoCo 88.13% no regression

### Phase Numbering

Last phase shipped: **79** (v1.10 closer). v1.11 runs **phases 80-87**.

### Roadmap Evolution

- 2026-05-16: v1.10 milestone closed. Archives at `milestones/v1.10-ROADMAP.md`, `milestones/v1.10-REQUIREMENTS.md`, `milestones/v1.10-MILESTONE-AUDIT.md`. ROADMAP.md v1.10 block collapsed to `<details>`.
- 2026-05-16: v1.11 roadmap created. 8 phases (80-87), 46 REQ-IDs across 8 categories. Backlog phases 999.1-999.4 promoted into active milestone.

### Blockers/Concerns

at roadmap creation. Research flags:

- **MEDIUM confidence** on OpenRewrite recipe diff outcome — `rewrite:dryRun` must be inspected before `rewrite:run` at Phase 80 execution
- **MEDIUM confidence** on wallclock reduction achievability — `@DirtiesContext` count and context key variation must be measured at Phase 86 start
- Renovate onboarding requires manual GitHub App installation by repo owner (jegr78) — schedule as Phase 84 first step
- Pre-existing Phase 72 IT compile error in BackupSchemaExclusionIT.java:40 (Java 25 / AssertJ generic inference) blocks ./mvnw verify exit 0 + JaCoCo CSV generation. Out-of-scope per Plan 80-03 plan_scope; logged in deferred-items.md. Must be resolved before Plan 80-04.

### Baselines to Preserve

- JaCoCo line coverage: **87.80%** (v1.10 baseline; gate 82%)
- Test count: **1652 Surefire unit + 231 Failsafe IT + 36 Playwright E2E** (v1.10 final)
- `./mvnw verify -Pe2e` wallclock: **11m 11s** (v1.10 baseline; Phase 86 target ≤7m 50s)
- `BackupSchema.SCHEMA_VERSION`: **1** (must remain 1 throughout Phase 82)
- `EXPORT_ORDER` size: **24 entities** (guard test in Phase 82)

## Session Continuity

Last session: 2026-05-18T09:40:00.000Z

**Next action:** Spawn /gsd:execute-plan 87-05 (Restore + audit + approve v1.10 Phase 75 — Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT, State A).

**Branch:** `gsd/v1.11-tooling-and-cleanup`
