---
gsd_state_version: 1.0
milestone: v1.11
milestone_name: Tooling Infrastructure & Tech-Debt Sweep
status: active
last_updated: "2026-05-16T00:00:00.000Z"
last_activity: 2026-05-16
progress:
  total_phases: 8
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-16)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.11 Tooling Infrastructure & Tech-Debt Sweep — Phase 80 (OpenRewrite Integration) is next.

## Current Position

Phase: 80 — OpenRewrite Integration (not started)
Plan: —
Status: Roadmap complete, ready to plan Phase 80
Last activity: 2026-05-16 — v1.11 roadmap created (phases 80-87)

```
Progress: [                                        ] 0/8 phases (0%)
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
| tech_debt | REVIEW.md WR-01..WR-08 + IN-01..IN-04 (12 Info/Warning items) | Phase 82 |
| tech_debt | Phase 79 D-06 wallclock-reduction (16.85% vs ≥30% target) | Phase 86 |
| tech_debt | Driver-detail Season-Assignment chip ordering (ORDER BY year) | Phase 83 |
| tech_debt | DevDataSeeder @Profile("dev")-only widening for local,demo | Phase 83 |
| tech_debt | Nyquist *-VALIDATION.md for 6 phases (72-76, 79) + creation for 71 + 78 | Phase 87 |
| tech_debt | Per-group matchday generation UI affordance (SeasonController:251) | Phase 83 |
| tech_debt | StandingsController.java:139 lazy collection style cleanup | Phase 83 |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | Phase 83 |
| backlog | OpenRewrite (Phase 999.1) — promoted to active milestone | Phase 80 |
| backlog | Clean-Code enforcement (Phase 999.2) — promoted (SpotBugs only) | Phase 81 |
| backlog | Renovate (Phase 999.3) — promoted to active milestone | Phase 84 |
| backlog | SAST (Phase 999.4) — promoted (CodeQL, public repo) | Phase 85 |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | QUAL-05 wiki image embed render | resolved on v1.10 PR merge to master |
| post_merge | PLAT-CI-02 release-workflow run on master | resolved by-design post-merge |

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

### Phase Numbering

Last phase shipped: **79** (v1.10 closer). v1.11 runs **phases 80-87**.

### Roadmap Evolution

- 2026-05-16: v1.10 milestone closed. Archives at `milestones/v1.10-ROADMAP.md`, `milestones/v1.10-REQUIREMENTS.md`, `milestones/v1.10-MILESTONE-AUDIT.md`. ROADMAP.md v1.10 block collapsed to `<details>`.
- 2026-05-16: v1.11 roadmap created. 8 phases (80-87), 46 REQ-IDs across 8 categories. Backlog phases 999.1-999.4 promoted into active milestone.

### Blockers/Concerns

None at roadmap creation. Research flags:

- **MEDIUM confidence** on OpenRewrite recipe diff outcome — `rewrite:dryRun` must be inspected before `rewrite:run` at Phase 80 execution
- **MEDIUM confidence** on wallclock reduction achievability — `@DirtiesContext` count and context key variation must be measured at Phase 86 start
- Renovate onboarding requires manual GitHub App installation by repo owner (jegr78) — schedule as Phase 84 first step

### Baselines to Preserve

- JaCoCo line coverage: **87.80%** (v1.10 baseline; gate 82%)
- Test count: **1652 Surefire unit + 231 Failsafe IT + 36 Playwright E2E** (v1.10 final)
- `./mvnw verify -Pe2e` wallclock: **11m 11s** (v1.10 baseline; Phase 86 target ≤7m 50s)
- `BackupSchema.SCHEMA_VERSION`: **1** (must remain 1 throughout Phase 82)
- `EXPORT_ORDER` size: **24 entities** (guard test in Phase 82)

## Session Continuity

Last session: 2026-05-16 — roadmap creation for v1.11

**Next action:** Run `/gsd:plan-phase 80` to create the plan for Phase 80 (OpenRewrite Integration).

**Branch:** `gsd/v1.11-tooling-and-cleanup`
