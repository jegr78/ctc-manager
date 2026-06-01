---
gsd_state_version: 1.0
milestone: v1.17
milestone_name: Guest Drivers
status: executing
stopped_at: Phase 115 UI-SPEC approved
last_updated: "2026-06-01T18:13:51.072Z"
last_activity: 2026-06-01 -- Phase 115 execution started
progress:
  total_phases: 3
  completed_phases: 2
  total_plans: 13
  completed_plans: 7
  percent: 54
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-31 after v1.15 milestone close)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 115 — Guest Marking & Visibility

## Current Position

Phase: 115 (Guest Marking & Visibility) — EXECUTING
Plan: 1 of 6
Status: Executing Phase 115
Last activity: 2026-06-01 -- Phase 115 execution started

## Shipped Milestone

**v1.15 CI Optimisation & Race/Match Defaults** — SHIPPED 2026-05-31 — Branch: `gsd/v1.15-ci-and-race-defaults` (PR #132)

| Phase | Goal | Requirements | Status |
|-------|------|--------------|--------|
| 106 | CI Pipeline Optimisation | CI-01..06 | Complete (CI-03/04/06 ✓; CI-05 ✓ verified; CI-01/02 ✓ config-sound) |
| ~~107~~ | ~~Race/Match Prefill Defaults~~ | ~~RACE-01..03~~ | **Removed 2026-05-30** (RACE-01..03 dropped) |
| 108 | Missing-Driver n/a Rendering | LINEUP-01..04 | Complete (LINEUP-01..04 ✓; 108-REVIEW.md resolved — WR-01 fixed) |
| 109 | Walkover Handling | WO-01..04 | Complete (109-01..05 ✓; WO-01..04 ✓; 2 review passes resolved; verify -Pe2e green) |
| 110 | Lobby Settings Graphic | LOBBY-01..05 | Complete (110-01..05 ✓; 110-REVIEW.md resolved; verify -Pe2e green) |
| 111 | Log-Injection Remediation (CodeQL CWE-117) | SEC-LOG-01..04 | Complete (111-01..03 ✓; SEC-LOG-01..04 ✓; 29→0 java/log-injection on PR ref; 111-REVIEW.md resolved — 5 findings fixed; verify -Pe2e green) |
| 112 | Unused Import Cleanup & Regression Guard | IMP-01..02 | Complete (112-01 ✓; IMP-01/02 ✓; Checkstyle UnusedImports gate bound to validate; verify -Pe2e green) |

## Baselines to Preserve

- JaCoCo line coverage: **≥ ~89 %** (v1.15 baseline; gate 82 % in pom.xml)
- Test count: **≥ 2472** (v1.15 baseline)
- CI E2E median: **17:39** (v1.12 baseline; v1.15 target was reduction, actual 18-20 min warm)
- `BackupSchema.SCHEMA_VERSION`: **2** (v1.13 Phase 101)
- `EXPORT_ORDER` size: **26 entities** (v1.13 Phase 101; guest flag is a column on `RaceLineup`/`RaceResult`, not a new entity)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0** on new HIGH/CRITICAL
- Flyway migrations: V1-V17 immutable; next is V18 (guest flag, Phase 113)

## Accumulated Context

### Decisions

Roadmap-level decisions for v1.17 (2026-06-01):

- **3-phase structure (D-GuestPhase-Count)** — 13 requirements across 3 natural delivery boundaries: guest assignment data model (GUEST-01..04), scoring/crediting logic (SCORE-01..03), and visual marking across all surfaces (MARK-01..06). Standard granularity is 5-8 phases, but this milestone is tightly scoped — 3 phases reflects the natural dependency chain without artificial splitting. Matches the spirit of "let the work determine the phases."
- **GUEST before SCORE before MARK (D-GuestDependencyChain)** — Hard sequential dependency: scoring logic requires a guest-identifiable data model; marking requires scoring to be correct so guests appear in rankings before marks are added to those rankings.
- **All MARK requirements in one phase (D-MarkGrouped)** — MARK-01..06 span Thymeleaf graphic templates (Scorecard, Provisional Scores, matchday-results) and admin/site templates. Per the v1.15 D-Graphic-Sequencing pattern, template-touching work is grouped to avoid clobber across shared files. Splitting MARK into graphics vs. admin/site would create shared-template conflict risk with no benefit.
- **Phase 115 is the UI phase (D-UIPhase)** — The concrete visual treatment of guest marking (asterisk, badge, origin-team label) is intentionally not pre-committed in REQUIREMENTS.md. Phase 115 executes the treatment decision against a rendered reference via `playwright-cli`, consistent with CLAUDE.md "Visual Verification." The `ui_phase: true` config flag applies.
- **Flyway V18 in Phase 113 (D-FlywayContinuation)** — V1-V17 are immutable; the guest-assignment flag (whether a `RaceLineup`/`RaceResult` entry is a guest) needs a new additive migration V18. No existing migration is modified.
- **No separate guest points model (D-NoSeparateGuestPoints)** — Guest scoring flows through `ScoringService.aggregateMatchScores(race)` unchanged — the fielding team receives points as with any driver. Only the driver-ranking crediting is additive (Phase 114). REQUIREMENTS.md Out-of-Scope table is authoritative.

### Phase Numbering

Last phase shipped: **112** (v1.15 Unused Import Cleanup). v1.17 spans phases **113-115** (continuing from 112; integer phases, no reset; 107 remains a gap from v1.15).

### Roadmap Evolution

- 2026-06-01: v1.17 milestone started; REQUIREMENTS.md defined (13 requirements across GUEST/SCORE/MARK); ROADMAP.md created (3 phases 113-115, 13/13 coverage).

## Deferred Items

Carried forward from v1.13/v1.14/v1.15 close (unchanged):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| future_req | DISC-FUTURE-01..05 — Inbound Discord, auto-trigger, settings-form, multi-guild, public-site webhook | Later milestone; requires deployment model change |
| future_req | Per-season configurable team size — `driverSlots` field on `SeasonPhase`/`Season` (default 6) + Flyway migration + admin UI | Own phase (v1.16 backlog or later). Surfaced in Phase 108 discuss |
| tech_debt | String `.isEmpty()` audit (~10 callsites) | Case-by-case; per Phase 103 CONTEXT D-06 |
| uat (carry) | UAT-02 legacy season visual smoke | Post-deploy operator action; cross-milestone per CLAUDE.md |
| uat (carry) | QUAL-02 `local`-profile MariaDB manual smoke | Post-deploy operator action |
| uat (carry) | UX-01 driver-import error-category badge screenshots | Post-deploy operator action |
| tech_debt | Existing match-channels under old Phase-94 naming scheme | Potential future admin bulk-rename action; two-scheme coexistence accepted |

## Session Continuity

**Last session:** 2026-06-01T17:04:54.792Z

**Stopped at:** Phase 115 UI-SPEC approved

**Next action:** `/gsd-discuss-phase 113` on branch `gsd/v1.17-guest-drivers`.

**Branch:** `gsd/v1.17-guest-drivers`

## Operator Next Steps

- `/gsd-discuss-phase 113` — Guest Assignment Foundation (Flyway V18 + RaceLineup guest flag + admin CRUD)
