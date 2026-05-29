---
gsd_state_version: 1.0
milestone: v1.15
milestone_name: CI Optimisation & Race/Match Defaults
status: planning
last_updated: "2026-05-30T00:00:00.000Z"
last_activity: 2026-05-30
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-29 after v1.14 milestone close)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.15 roadmap complete — ready for `/gsd-discuss-phase 106` or `/gsd-plan-phase 106`.

## Current Position

Phase: 106 of 110 (CI Pipeline Optimisation) — Not started
Plan: —
Status: Roadmap created; ready to plan
Last activity: 2026-05-30 — v1.15 ROADMAP.md created (5 phases, 22/22 requirements mapped)

Progress: [░░░░░░░░░░] 0%

## Active Milestone

**v1.15 CI Optimisation & Race/Match Defaults** — Branch: `gsd/v1.15-ci-and-race-defaults`

| Phase | Goal | Requirements | Status |
|-------|------|--------------|--------|
| 106 | CI Pipeline Optimisation | CI-01..06 | Not started |
| 107 | Race/Match Prefill Defaults | RACE-01..03 | Not started |
| 108 | Missing-Driver n/a Rendering | LINEUP-01..04 | Not started |
| 109 | Walkover Handling | WO-01..04 | Not started |
| 110 | Lobby Settings Graphic | LOBBY-01..05 | Blocked (external design handoff) |

## Baselines to Preserve

- JaCoCo line coverage: **≥ ~89.42 %** (v1.14 baseline; gate 82 % in pom.xml)
- Test count: **≥ 2416** (v1.14: 1772 Surefire + 529 Failsafe IT + 115 E2E)
- CI E2E median: **17:39** (v1.14 baseline; Phase 106 targets a reduction)
- `BackupSchema.SCHEMA_VERSION`: **2** (v1.13 Phase 101; no v1.15 schema changes anticipated except WO Flyway V17)
- `EXPORT_ORDER` size: **26 entities** (only walkover flag may need Flyway V17 — not a new entity)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0** on new HIGH/CRITICAL
- Flyway migrations: V1-V16 immutable; next is V17 (walkover flag, Phase 109)

## Accumulated Context

### Decisions

Roadmap-level decisions for v1.15 (2026-05-30):

- **5-phase structure** — 22 requirements across 5 natural delivery categories (CI, RACE, LINEUP, WO, LOBBY); one phase per category gives "standard" granularity (5-8 phases).
- **CI first (D-CI-Independent)** — CI strand has zero shared surface with application strands; running it first cleans the pipeline before any Java work lands.
- **LINEUP before WO (D-Graphic-Sequencing)** — Both phases modify graphic Thymeleaf templates. LINEUP-01..04 establishes the n/a placeholder pattern; WO-03 adds the "w/o" label on top of that stable baseline. Reversing the order would risk clobber on shared template files.
- **LOBBY last + external-handoff gate (D-Lobby-Blocked)** — LOBBY-01..05 are blocked on the Claude-Design Lobby Settings HTML handoff. Phase 110 is explicitly labelled as requiring the handoff before execution begins (same pattern as Phase 105 CARD-01 in v1.14).
- **WO reuses bye semantics (D-WO-Bye-Analogy)** — Per the out-of-scope table in REQUIREMENTS.md, a richer walkover model (dedicated points config, forfeit reasons) is out of scope; the implementation mirrors `Match.bye` auto-win logic.

### Blockers/Concerns

- Phase 110 (Lobby Settings) is blocked on external Claude-Design Lobby Settings HTML/CSS handoff. Phases 106-109 are unblocked and can proceed in order.

### Phase Numbering

Last phase shipped: **105** (v1.14 Carbon HUD Graphics Redesign). v1.15 spans phases **106-110** (integer phases, no insertions, no reset).

### Roadmap Evolution

- 2026-05-29: v1.14 shipped (PR #131 squash-merged to master; release CI tagged v1.14.0).
- 2026-05-30: v1.15 milestone started; REQUIREMENTS.md defined (22 requirements across CI/RACE/LINEUP/WO/LOBBY); ROADMAP.md created (5 phases 106-110, 22/22 coverage).

## Deferred Items

Carried forward from v1.13/v1.14 close (unchanged):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| future_req | DISC-FUTURE-01..05 — Inbound Discord, auto-trigger, settings-form, multi-guild, public-site webhook | Later milestone; requires deployment model change |
| tech_debt | String `.isEmpty()` audit (~10 callsites) | Case-by-case; per Phase 103 CONTEXT D-06 |
| uat (carry) | UAT-02 legacy season visual smoke | Post-deploy operator action; cross-milestone per CLAUDE.md |
| uat (carry) | QUAL-02 `local`-profile MariaDB manual smoke | Post-deploy operator action |
| uat (carry) | UX-01 driver-import error-category badge screenshots | Post-deploy operator action |
| tech_debt | Existing match-channels under old Phase-94 naming scheme | Potential future admin bulk-rename action; two-scheme coexistence accepted |

## Session Continuity

**Last session:** 2026-05-30 — v1.15 roadmap creation

**Stopped at:** ROADMAP.md + STATE.md written; REQUIREMENTS.md traceability populated. Roadmap ready for review.

**Next action:** `/gsd-discuss-phase 106` or `/gsd-plan-phase 106` to begin CI Pipeline Optimisation.

**Branch:** `gsd/v1.15-ci-and-race-defaults`
