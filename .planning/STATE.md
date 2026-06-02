---
gsd_state_version: 1.0
milestone: v1.17
milestone_name: Guest Drivers
status: shipped
stopped_at: v1.17 milestone close (PR + squash-merge pending CI)
last_updated: "2026-06-02T15:00:00.000Z"
last_activity: 2026-06-02 -- v1.17 Guest Drivers SHIPPED (archived; PR/merge pending)
progress:
  total_phases: 4
  completed_phases: 4
  total_plans: 16
  completed_plans: 16
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-02 after v1.17 milestone close)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.17 Guest Drivers SHIPPED — next milestone TBD via `/gsd-new-milestone`

## Current Position

Milestone: v1.17 Guest Drivers — SHIPPED 2026-06-02 (archived)
Phase: 116 — COMPLETE (all 4 phases 113-116 complete)
Status: milestone archived; PR + squash-merge into `master` pending CI
Last activity: 2026-06-02 -- v1.17 SHIPPED

## Shipped Milestone

**v1.17 Guest Drivers** — SHIPPED 2026-06-02 — Branch: `gsd/v1.17-guest-drivers` (PR + squash-merge pending CI)

| Phase | Goal | Requirements | Status |
|-------|------|--------------|--------|
| 113 | Guest Assignment Foundation | GUEST-01..04 | Complete (Flyway V18 `is_guest` + RaceLineup flag + admin CRUD; REVIEW + VALIDATION + auto-UAT 4/4) |
| 114 | Scoring & Personal Crediting | SCORE-01..03 | Complete (fielding-team aggregation + unified driver-ranking crediting incl. pure guests, idempotent; REVIEW + VALIDATION) |
| 115 | Guest Marking & Visibility | MARK-01..06 | Complete (`--guest` token + `hasGuestAppearance` across 3 graphics + admin + rankings + profile; REVIEW + VALIDATION + visual gate) |
| 116 | German Comment Sweep | CLEAN-01..04 | Complete (German comments → English repo-wide, comments-only; REVIEW PASS + VALIDATION manual-only + UAT 4/4) |

*Prior shipped milestone: v1.15 CI Optimisation & Race/Match Defaults (Phases 106, 108-112) — see `milestones/v1.15-*` + MILESTONES.md.*

## Baselines to Preserve

- JaCoCo line coverage: **≥ ~89 %** (v1.17 baseline **89.88 %**; gate 82 % in pom.xml)
- Test count: **≥ 2530** (v1.17 baseline; was 2472 at v1.15)
- CI E2E median: **17:39** (v1.12 baseline; warm runs settle 18-20 min)
- `BackupSchema.SCHEMA_VERSION`: **2** (v1.13 Phase 101)
- `EXPORT_ORDER` size: **26 entities** (v1.13 Phase 101; the v1.17 guest flag is a column on `RaceLineup`, not a new entity)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0** on new HIGH/CRITICAL
- Flyway migrations: V1-**V18** immutable (V18 = guest `is_guest`, Phase 113); next is **V19**
- Checkstyle: **0** UnusedImports/RedundantImport violations (validate-gate); all source comments English-only (v1.17 CLEAN sweep)

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
- 2026-06-02: Phase 116 added — German Comment Sweep: replace German comments (crept in via two Claude-Design handoffs in v1.14/v1.15) with minimal English across templates, Java, pom.xml, yml, properties.

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

Acknowledged at v1.17 close (2026-06-02) — both verified complete, scanner format quirks only (not real debt):

| Category | Item | Status |
| -------- | ---- | ------ |
| quick_task | 260531-suj — Link existing Discord channel | Complete (PLAN+SUMMARY+CONTEXT+AUTO-UAT 4/4, executed 2026-05-31); audit-open mislabeled "missing" due to quick-task format lacking a top-level status field |
| uat | 113-AUTO-UAT.md | Complete (4/4 passed, 0 failed); audit-open reported "unknown" due to AUTO-UAT format parse quirk |

## Session Continuity

**Last session:** 2026-06-01T17:04:54.792Z

**Stopped at:** Phase 115 UI-SPEC approved

**Next action:** `/gsd-discuss-phase 113` on branch `gsd/v1.17-guest-drivers`.

**Branch:** `gsd/v1.17-guest-drivers`

## Operator Next Steps

- `/gsd-discuss-phase 113` — Guest Assignment Foundation (Flyway V18 + RaceLineup guest flag + admin CRUD)
