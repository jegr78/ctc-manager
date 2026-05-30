---
gsd_state_version: 1.0
milestone: v1.15
milestone_name: CI Optimisation & Race/Match Defaults
status: executing
stopped_at: Phase 108 complete (clean verify -Pe2e green)
last_updated: "2026-05-30T13:05:00.000Z"
last_activity: 2026-05-30 -- Phase 108 executed (LINEUP-01..04 closed)
progress:
  total_phases: 6
  completed_phases: 2
  total_plans: 7
  completed_plans: 7
  percent: 33
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-29 after v1.14 milestone close)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Phase 108 complete (LINEUP-01..04 closed; clean verify -Pe2e green). Next feature work is Phase 109 (Walkover Handling) — but per "Code-Review Before New Phase", Phase 108 has no REVIEW.md yet.

## Current Position

Phase: 108 of 111 (Missing-Driver n/a Rendering) — Complete. Next is Phase 109 (Walkover Handling).
Plan: 3/3 complete (108-01, 108-02, 108-03)
Status: Phase delivered; visual checkpoint approved; full build green
Last activity: 2026-05-30 -- Phase 108 executed (LINEUP-01..04 closed)

Progress: [███░░░░░░░] 33%

## Active Milestone

**v1.15 CI Optimisation & Race/Match Defaults** — Branch: `gsd/v1.15-ci-and-race-defaults`

| Phase | Goal | Requirements | Status |
|-------|------|--------------|--------|
| 106 | CI Pipeline Optimisation | CI-01..06 | Complete (CI-03/04/06 ✓; CI-01/02/05 open-verify) |
| ~~107~~ | ~~Race/Match Prefill Defaults~~ | ~~RACE-01..03~~ | **Removed 2026-05-30** (RACE-01..03 dropped) |
| 108 | Missing-Driver n/a Rendering | LINEUP-01..04 | Complete (LINEUP-01..04 ✓; REVIEW.md pending) |
| 109 | Walkover Handling | WO-01..04 | Not started |
| 110 | Lobby Settings Graphic | LOBBY-01..05 | Blocked (external design handoff) |
| 111 | Log-Injection Remediation (CodeQL CWE-117) | SEC-LOG-01..04 | Not started |

## Baselines to Preserve

- JaCoCo line coverage: **≥ ~89.42 %** (v1.14 baseline; gate 82 % in pom.xml)
- Test count: **≥ 2416** (v1.14: 1772 Surefire + 529 Failsafe IT + 115 E2E)
- CI E2E median: **17:39** (v1.14 baseline; Phase 106 targets a reduction)
- `BackupSchema.SCHEMA_VERSION`: **2** (v1.13 Phase 101; no v1.15 schema changes anticipated except WO Flyway V17)
- `EXPORT_ORDER` size: **26 entities** (only walkover flag may need Flyway V17 — not a new entity)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0** on new HIGH/CRITICAL
- CodeQL open alerts: **29 `java/log-injection` (medium)** to be driven to **0** in Phase 111 (below the 7.0 gate, so they never blocked the build — closed via source fix, not suppression)
- Flyway migrations: V1-V16 immutable; next is V17 (walkover flag, Phase 109)

## Accumulated Context

### Decisions

Roadmap-level decisions for v1.15 (2026-05-30):

- **5-phase structure** — 22 requirements across 5 natural delivery categories (CI, RACE, LINEUP, WO, LOBBY); one phase per category gives "standard" granularity (5-8 phases).
- **CI first (D-CI-Independent)** — CI strand has zero shared surface with application strands; running it first cleans the pipeline before any Java work lands.
- **LINEUP before WO (D-Graphic-Sequencing)** — Both phases modify graphic Thymeleaf templates. LINEUP-01..04 establishes the n/a placeholder pattern; WO-03 adds the "w/o" label on top of that stable baseline. Reversing the order would risk clobber on shared template files.
- **LOBBY last + external-handoff gate (D-Lobby-Blocked)** — LOBBY-01..05 are blocked on the Claude-Design Lobby Settings HTML handoff. Phase 110 is explicitly labelled as requiring the handoff before execution begins (same pattern as Phase 105 CARD-01 in v1.14).
- **WO reuses bye semantics (D-WO-Bye-Analogy)** — Per the out-of-scope table in REQUIREMENTS.md, a richer walkover model (dedicated points config, forfeit reasons) is out of scope; the implementation mirrors `Match.bye` auto-win logic.
- **SEC-LOG last + source-fix (D-LogInjection-Last, 2026-05-30)** — Log-injection remediation runs as Phase 111 (after 106-110) so it also captures any log statements added by the feature phases. Strategy is per-callsite sanitization via a central `LogSanitizer` (strips CR/LF + control chars), fixing the CodeQL taint path at source rather than adding `query-filters` suppressions.
- **Phase 107 removed — RACE-01..03 dropped (D-Race-Drop, 2026-05-30)** — The Phase 107 discuss surfaced that the three "prefill" targets do not map to a real re-entry problem in the current data model: scoring scheme is already inherited via `Matchday → SeasonPhase → RaceScoring` (no create-form dropdown, no per-race override field); `legs` is a `SeasonPhase` setting and a Race *is* a single leg (no legs field); Matchday has no scheduled date/time to inherit from (only `pickDeadline` + a `scheduledWeekend` label). User decision: remove Phase 107 entirely and drop RACE-01..03 permanently (not backlog). v1.15 now delivers 5 phases (106, 108, 109, 110, 111). Phase number 107 left as a gap per the integer-phase policy.

### Blockers/Concerns

- Phase 110 (Lobby Settings) is blocked on external Claude-Design Lobby Settings HTML/CSS handoff. Phases 108-109 are unblocked and can proceed in order.

### Phase Numbering

Last phase shipped: **105** (v1.14 Carbon HUD Graphics Redesign). v1.15 spans phases **106-111** (integer phases, no insertions, no reset). **107 removed 2026-05-30** — number left as a gap (no renumbering); active phases are 106, 108, 109, 110, 111.

### Roadmap Evolution

- 2026-05-29: v1.14 shipped (PR #131 squash-merged to master; release CI tagged v1.14.0).
- 2026-05-30: v1.15 milestone started; REQUIREMENTS.md defined (22 requirements across CI/RACE/LINEUP/WO/LOBBY); ROADMAP.md created (5 phases 106-110, 22/22 coverage).
- 2026-05-30: Phase 111 (Log-Injection Remediation, CodeQL CWE-117) added at end of v1.15 — 29 open `java/log-injection` alerts discovered in GitHub code scanning; SEC-LOG-01..04 added (now 6 phases, 26/26 coverage). Strategy: per-callsite sanitization via central `LogSanitizer` (user decision 2026-05-30).
- 2026-05-30: Phase 107 (Race/Match Prefill Defaults) **removed** during discuss — RACE-01..03 dropped permanently (data-model mismatch: scoring/legs already inherited via `SeasonPhase`, no Matchday date to inherit). v1.15 now 5 phases (106, 108-111), 23/23 coverage on the remaining requirements.

## Deferred Items

Carried forward from v1.13/v1.14 close (unchanged):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| future_req | DISC-FUTURE-01..05 — Inbound Discord, auto-trigger, settings-form, multi-guild, public-site webhook | Later milestone; requires deployment model change |
| future_req | Per-season configurable team size — `driverSlots` field on `SeasonPhase`/`Season` (default 6) + Flyway migration + admin UI; graphics/scoring read the season value | Own phase (v1.15 backlog or later). Surfaced in Phase 108 discuss; Phase 108 uses a central constant so the later swap stays local |
| tech_debt | String `.isEmpty()` audit (~10 callsites) | Case-by-case; per Phase 103 CONTEXT D-06 |
| uat (carry) | UAT-02 legacy season visual smoke | Post-deploy operator action; cross-milestone per CLAUDE.md |
| uat (carry) | QUAL-02 `local`-profile MariaDB manual smoke | Post-deploy operator action |
| uat (carry) | UX-01 driver-import error-category badge screenshots | Post-deploy operator action |
| tech_debt | Existing match-channels under old Phase-94 naming scheme | Potential future admin bulk-rename action; two-scheme coexistence accepted |
| open-verify (v1.15, in-milestone) | **CI-05** warm-cache empirical proof — Docker `CACHED` layers + Maven/Playwright cache hits on a 2nd code run, no cross-eviction | Observe on the next code run on the milestone branch; close before v1.15 milestone close (logic sound via isolated `scope=ctc-docker`) |
| open-verify (v1.15, in-milestone) | **CI-01/CI-02** docs-only empirical proof — required checks `success` (not skipped) + CodeQL/MariaDB absent | Exercise on any docs-only commit/PR on the branch; close before v1.15 milestone close (logic sound: ungated checkout/uploads + paths-ignore) |

## Session Continuity

**Last session:** 2026-05-30T13:05:00.000Z

**Stopped at:** Phase 108 complete (108-01..03 executed; visual checkpoint approved; clean verify -Pe2e green — 1773 unit + 529 IT + 115 E2E, coverage gate met, SpotBugs 0)

**Next action:** `/gsd-code-review 108` (Phase 108 has no REVIEW.md — review before starting Phase 109 per "Code-Review Before New Phase"). Then `/gsd-discuss-phase 109` (Walkover Handling). Phase 106 already has a REVIEW.md.

**Branch:** `gsd/v1.15-ci-and-race-defaults`
