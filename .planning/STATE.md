---
gsd_state_version: 1.0
milestone: v1.13
milestone_name: Discord Integration & Carry-Forwards
status: planning
last_updated: "2026-05-20T21:26:19.368Z"
last_activity: 2026-05-20
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-20)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** Planning next milestone (v1.13) — pre-merge milestone closure complete; awaiting `gh pr merge --squash --subject "feat(v1.12): ..."` then `/gsd-new-milestone`.

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-05-20 — Milestone v1.13 started

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
- v1.12 Driver-Import Gap-Closure & Test Performance Round 2 (4 phases 88-91, 15 plans, 15/15 requirements substantively satisfied, JaCoCo 88.44%, 1696 tests, CI E2E median **17:39** Δ−23.3 %, Nyquist 4/0/0 compliant) — shipped 2026-05-20

## Active Milestone

None — v1.12 closed 2026-05-20. Next milestone (v1.13) starts via `/gsd-new-milestone` after the v1.12 PR squash-merge produces `v1.12.0` on master.

## Deferred Items

Items carried forward into v1.13 (from v1.12 audit + post-merge follow-ups):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| tech_debt | UX-01 scope-gap — `CsvImportController` (race-results sheet-import) not migrated to typed-catch + `errorCategory` flash + badge UX; T-91-02-IL info-leak (`e.getMessage()` echo) re-introduced for this 3rd consumer of typed `GoogleSheetsService` | v1.13 — apply typed-catch + `errorCategory` flash + badge UX to `CsvImportController` for parity (see v1.12 audit Warning 1) |
| tech_debt | JaCoCo Δ−0.44 pp (88.44 % vs 88.88 %, above 82 % gate); root cause javac-mandated defensive `catch (GoogleApiException)` blocks (Java 25 lacks sealed-exhaustiveness on catch) + uncovered service-layer IOException paths | v1.13 — add `RaceControllerCalendarTest` + IT coverage for Google service error paths (see 91-02-SUMMARY § JaCoCo coverage delta) |
| tech_debt | CLEAN-02 grep-predicate drift — Phase 89 PERF-01 introduced AssertJ `Assumptions.assumeThat` in `BackupStagingDirPerForkIT.java:12,37`; different package + intent than the JUnit `Assumptions.assumeFalse` that CLEAN-02 originally targeted, but grep can't distinguish | v1.13 — tighten predicate to `org\.junit\.jupiter\.api\.Assumptions` OR whitelist `BackupStagingDirPerForkIT` (see v1.12 audit Warning 2) |
| docs_debt | Optional audit-trail retrofill — Phases 89/90/91 close on VALIDATION.md + per-plan SUMMARY.md without phase-level VERIFICATION.md (v1.11 had VERIFICATION.md per phase, some retroactively via commit `2e84fd57`) | v1.13 — optional retroactive `89-VERIFICATION.md` / `90-VERIFICATION.md` / `91-VERIFICATION.md` authoring; substantive verification already present via VALIDATION.md + SUMMARY.md (see v1.12 audit Warning 4) |
| bookkeeping_debt | REQUIREMENTS.md checkbox + traceability lag — 7 of 15 v1.12 REQ-IDs require post-merge flip from `Pending`/`[ ]` to `Resolved`/`[x]` (PERF-01, PERF-02, PERF-03, PERF-04, PERF-05, PERF-06, UX-01); Plan 91-03 deliberately deferred per stale-state avoidance pattern | post-merge — flip in a single doc-only commit on master after `v1.12.0` release fires |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | post-deploy operator action (procedure docs/uat/UAT-02-legacy-season-smoke.md) |
| uat | QUAL-02 local-profile MariaDB manual smoke (DevDataSeeder widening) | post-deploy operator action |
| uat | UX-01 visual UAT — 4 error-category badges × Desktop + Mobile (8 Playwright screenshots) | post-deploy operator action (procedure 91-02-SUMMARY.md § Manual UAT) |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | v1.12 release workflow tagging (produces `v1.12.0` annotated tag + GitHub Release + `ghcr.io/jegr78/ctc-manager:1.12.0` + `:latest`) | will resolve when squash-merge subject `feat(v1.12): ...` lands on master (see `docs/operations/release-runbook.md § 6 — Squash-merge subject discipline`) |

## Pending UATs

### UAT-02: Legacy Season Visual Smoke (carry-forward from v1.11 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** v1.12 milestone complete
- **Result:** _(operator fills after execution)_
- **Date:** _(operator fills)_
- **Screenshots:** _(operator links)_

### QUAL-02: Local-Profile MariaDB Smoke (carry-forward from v1.11 QUAL-02)

- **Procedure:** start app with `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` and verify `DevDataSeeder` widened to `@Profile({"dev","local"})` seeds correctly against real MariaDB
- **Status:** post-deploy operator action
- **Result:** _(operator fills after execution)_

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md "Key Decisions" table and per-milestone in `milestones/v*.x-ROADMAP.md`. v1.12 decisions will be captured per phase during `/gsd-discuss-phase`.

Roadmap-level decisions (captured during ROADMAP.md drafting):

- **CLEAN-01 sequenced first in Phase 88** — `BackupSchemaExclusionIT.java:40` AssertJ compile error blocks `./mvnw verify` exit 0 + JaCoCo CSV; without fix all PERF measurements run against a broken baseline. Lands as FIRST commit of Phase 88.
- **CLEAN-02 + CLEAN-03 YAGNI-driven** — user-raised challenge during milestone planning: are the 3 "intentional" disabled tests actually justified? Re-evaluation found (a) `DriverSheetImportServiceIT` regression-fence covers a structurally unreachable path (covered by Test #7 already; "future-change" speculation), (b) Windows-conditional skip is single outlier in OS-agnostic codebase (CI ubuntu, dev darwin), (c) `SiteGeneratorBaselineCaptureTest` `@Test @Disabled` semantically misleading. CLEAN-02 deletes/simplifies the YAGNI violators; CLEAN-03 refactors the baseline utility. Combined acceptance: `grep -rn "@Disabled" src/test/java` returns 0.
- **REL-01 + REL-02 co-located in Phase 88** — release-workflow regression has broken 4 consecutive milestone releases. Without REL-01 the v1.12 milestone PR squash-merge will fail identically, leaving v1.12.0 untagged. REL-02 catches up the 2 missed releases (v1.10.0 + v1.11.0) so the release history is contiguous. Both must merge before any PERF phase opens its own milestone-tracking PR.
- **PERF-02 (instrumentation) MUST precede PERF-03 (consolidation)** — cache-key fingerprint data identifies the cluster to consolidate. Blind consolidation risks re-introducing Plan-02 fragmentation pattern (Phase 86 Lesson).
- **PERF-06 sequenced last** — measures the cumulative effect of PERF-01..05 via D-17 trigger-equivalence (PR-branch `workflow_dispatch` ≡ post-merge master CI).
- **DRIV-01 + DRIV-02 co-located in Phase 88** — test rewrite has cross-dependency (DRIV-02 inverts `DriverSheetImportServiceTest#16` and `#17`; once DRIV-01 lands, the `TEAM_NOT_IN_REGULAR_PHASE` warning becomes legitimate again for genuinely missing teams in GROUPS seasons, so the test rewrite handles both contracts without churn).
- **UX-01 stretch in Phase 91** — separate optional plan, descopable to v1.13 if PERF budget exhausted.

### Phase Numbering

Last phase shipped: **87** (v1.11 closer). v1.12 spans phases **88-91** (integer phases, no insertions). Next milestone (v1.13) starts at **phase 92**.

### Roadmap Evolution

- 2026-05-18: v1.11 milestone closed; archived at `milestones/v1.11-{ROADMAP,REQUIREMENTS,MILESTONE-AUDIT}.md` + `milestones/v1.11-phases/`.
- 2026-05-18: v1.12 milestone started. Branch `gsd/v1.12-driver-import-and-test-perf`. Scope: driver-import data-correctness (DRIV-01..02) + test-wallclock Round 2 (PERF-01..05) + cleanup (CLEAN-01) + optional UX (UX-01).
- 2026-05-18: v1.12 ROADMAP.md created via `/gsd-new-milestone` — 4 phases (88-91), 10 requirements mapped (100 % coverage). Scope expanded same-session to 15 requirements: (a) 4-milestone release-workflow regression surfaced → REL-01 + REL-02 added to Phase 88; (b) colon-form deprecated-prefix copy-paste friction surfaced → DOCS-01 added to Phase 88 + inline-swept 16 refs in active files; (c) YAGNI audit of disabled tests surfaced 3 speculative/stale patterns → CLEAN-02 (sweep) + CLEAN-03 (BaselineCapture refactor) added to Phase 88. Awaiting user approval before `/gsd-discuss-phase 88`.
- 2026-05-20: Phase 90 closed via `/gsd-verify-work 90` (UAT 7/7 passed) + `/gsd-secure-phase 90` (3 plan-time threats verified CLOSED — short-circuit, no auditor spawn needed). PERF-03/04/05 REQUIREMENTS rows flipped to Resolved. v1.12 progress now 12/13 plans (92 %). Next phase 91 — PERF-06 CI re-harvest + UX-01 stretch + milestone closer.

### Blockers/Concerns

At roadmap creation:

- **Pre-existing `BackupSchemaExclusionIT.java:40` compile error** (Java-25 AssertJ generic inference) blocks any `./mvnw verify` exit 0 + JaCoCo CSV generation — fixed FIRST in Phase 88 (CLEAN-01). Tracked in v1.11 `80-deferred-items.md`.
- **Release-workflow regression (4 milestones)** — `git describe --tags --abbrev=0` non-deterministic on duplicate-tag pattern (`v1.X` short-form coexists with `v1.X.0`); naive parser leaves PATCH empty on short-form input; minor-bump produces an existing tag (`v1.9.0`), `git tag` fails exit 128. Failed runs: 26044380205 (v1.11), 25955094759 (v1.10), 25609204039 (v1.9), 24925033178 (v1.8 final). Fixed in Phase 88 (REL-01 + REL-02).
- **Test-Module-Split decision risk** — Lever 4 (PERF-05) may not pay off without restructuring `src/test/java` into independent Maven modules; Phase 90 plans BOTH branches (proceed + extraction, or defer + rationale).
- **Driver-import resolver semantics** — DRIV-01 refines (not inverts) the Phase 70 D-05 default ("parent wins"); season-aware variant prefers candidate with PhaseTeam in target REGULAR phase, falls back to parent-precedence when no candidate has a PhaseTeam (legacy seasons preserved).
- **CI wallclock measurement methodology** — every PERF lever must re-harvest via D-17 trigger-equivalence (PR-branch `workflow_dispatch` ≡ post-merge master CI) to avoid orphan post-merge measurement commits.
- **Cache-key fragmentation risk on PERF-03** — Phase 86 Lesson: per-class `@DynamicPropertySource` fragmented 1 shared cache key into 7 keys for sitegen cluster. PERF-02 instrumentation MUST land first so PERF-03 has the data to make a targeted consolidation decision.

### Baselines to Preserve

- JaCoCo line coverage: **88.88%** (v1.11 baseline; gate 82%, must not regress)
- Test count: **1675 tests** (Surefire + Failsafe + Playwright E2E combined; v1.11 final)
- `./mvnw verify -Pe2e` CI median (E2E step): **17:39** (v1.12 baseline — Phase 91 PERF-06 5-run median, drop min+max; was 23:00 in v1.11; Δ−23.3%)
- `BackupSchema.SCHEMA_VERSION`: **1** (must remain 1; schema change → bump to 2)
- `EXPORT_ORDER` size: **24 entities** (guard test active)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0 on new HIGH/CRITICAL** (3-layer FP suppression invariant maintained)
- Flyway migrations: V1-V7 immutable; any new schema is V8+

## Session Continuity

**Last session:** 2026-05-20 — milestone v1.12 closed via `/gsd-complete-milestone v1.12`

**Stopped at:** Milestone v1.12 archived; awaiting operator squash-merge of PR #129

**Next action:** `gh pr merge 129 --squash --subject "feat(v1.12): driver-import gap-closure & test performance round 2"` (subject prefix `feat(v1.12):` is required for the workflow's MINOR bump to `v1.12.0` per `docs/operations/release-runbook.md § 6`).

**Branch:** `gsd/v1.12-driver-import-and-test-perf` (squash target: master)

## Operator Next Steps

1. Squash-merge PR #129 with the `feat(v1.12): ...` subject (critical for `v1.12.0` minor bump).
2. Observe the release workflow on master — should produce `v1.12.0` tag + GitHub Release page + `ghcr.io/jegr78/ctc-manager:1.12.0` + `:latest` within ~20 min.
3. Post-merge bookkeeping (single doc-only commit on master): flip 7 REQUIREMENTS.md checkboxes (`PERF-01..06` + `UX-01`) and 4 traceability rows (`PERF-01`, `PERF-02`, `PERF-06`, `UX-01`).
4. Start v1.13 via `/gsd-new-milestone` — see `## Deferred Items` for audit-surfaced carry-forwards (CsvImportController UX-01 scope-extension, JaCoCo cleanup, CLEAN-02 predicate tightening, optional 89/90/91-VERIFICATION.md retrofill).
