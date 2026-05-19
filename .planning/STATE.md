---
gsd_state_version: 1.0
milestone: v1.12
milestone_name: Driver-Import Gap-Closure & Test Performance Round 2
status: executing
last_updated: "2026-05-18T18:05:57.860Z"
last_activity: 2026-05-18 -- Phase 88 execution started
progress:
  total_phases: 4
  completed_phases: 0
  total_plans: 13
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-18)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.12 Phase 88 complete (6/6 plans, all 8 REQs Resolved); next up Phase 89 — PERF Instrumentation & Lever 1

## Current Position

Phase: 88 (build-release-unblockers-yagni-sweep-doc-conventions-driver-) — COMPLETE
Plans: 6 of 6 (all SUMMARY.md committed; REVIEW.md status resolved; VERIFICATION.md status passed with 2 documented overrides)
Status: Phase 88 complete; ready for Phase 89 discuss-phase
Last activity: 2026-05-19 -- Phase 88 verified passed; REQUIREMENTS traceability flipped for CLEAN-01/-02/-03, REL-01/-02, DOCS-01, DRIV-01/-02

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

## Active Milestone — v1.12 (Phases 88-91)

| Phase | Name | Requirements | Status | Plans |
| ----- | ---- | ------------ | ------ | ----- |
| 88 | Build/Release Unblockers, YAGNI Sweep, Doc-Conventions & Driver-Import Gap-Closure | CLEAN-01, CLEAN-02, CLEAN-03, REL-01, REL-02, DOCS-01, DRIV-01, DRIV-02 | Context gathered (6 sequential plans designed) | 0/6 |
| 89 | PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir) | PERF-01, PERF-02 | Not started | 0/2 (est.) |
| 90 | PERF Consolidation & Module-Split Decision | PERF-03, PERF-04, PERF-05 | Not started | 0/3 (est.) |
| 91 | PERF Re-Harvest, Stretch UX Polish & Milestone Closer | PERF-06, UX-01 (stretch) | Not started | 0/2 (est.) |

Total: 15 requirements mapped across 4 phases (14 must-have + 1 stretch). Plan counts are roadmapper estimates; refined during `/gsd-discuss-phase`.

## Deferred Items

Items carried forward into v1.12 (to be cleared by roadmap phases 88+):

| Category | Item | Resolution Plan |
| -------- | ---- | --------------- |
| tech_debt | Driver-import shortname-resolver picks parent without PhaseTeam (data-correctness) | DRIV-01 — season-aware resolver (Phase 88) |
| tech_debt | Driver-import group-warnings fire for non-GROUPS seasons (UI noise) | DRIV-02 — layout gate + per-tab usesGroups flag (Phase 88) |
| tech_debt | PERF-FUTURE-01 — CI test wallclock 23:00 vs target ≤7m 50s (Phase 86 OR-branch) | PERF-01..PERF-05 — 3-lever forward path + module-split decision (Phases 89-90); PERF-06 re-harvest (Phase 91) |
| tech_debt | BackupSchemaExclusionIT.java:40 Java-25 AssertJ generic-inference compile error | CLEAN-01 — fix or `@SuppressWarnings("unchecked")` (Phase 88, FIRST commit) |
| tech_debt | Release workflow regression — 4 consecutive milestone releases (v1.8 final, v1.9, v1.10, v1.11) failed with `fatal: tag already exists`; pom.xml manually bumped to 1.11.0-SNAPSHOT (commit 87daec68) | REL-01 — workflow hardening (SemVer-strict tag sort + fetch-tags + parser + idempotency guard) (Phase 88) |
| release_debt | Missed releases: v1.10.0 + v1.11.0 not tagged, no GitHub Release, no Docker images pushed | REL-02 — retroactive publish + legacy short-form tag cleanup + release-runbook.md (Phase 88) |
| docs_debt | 16 deprecated colon-form (`/gsd-` predecessor) skill-invocation refs across 6 active top-level planning files caused operator copy-paste friction; inline-swept this session, regression risk going forward | DOCS-01 — CLAUDE.md "Conventions" Skill Invocation Naming paragraph (Phase 88) |
| tech_debt | YAGNI violations in disabled tests: speculative regression-fence for unreachable code path (DriverSheetImportServiceIT), stale GROUPS+SWISS placeholder (StandingsPageGeneratorTest), single Windows-conditional skip in otherwise OS-agnostic codebase (AutoBackupBeforeImportFailureIT) | CLEAN-02 — YAGNI sweep (Phase 88) |
| tech_debt | `SiteGeneratorBaselineCaptureTest` uses `@Test @Disabled` anti-pattern for what is structurally a manual maintenance task; forces "remove @Disabled, run, re-add" cycle | CLEAN-03 — refactor to `CommandLineRunner`/`main()` utility (Phase 88) |
| ux_debt | Google Sheets/Calendar generic error messages on auth/network/sheet-id failures | UX-01 stretch (Phase 91, descope to v1.13 if PERF over budget) |
| uat | UAT-02 legacy season visual smoke (real pre-V4 production data) | post-deploy operator action (procedure docs/uat/UAT-02-legacy-season-smoke.md) |
| uat | QUAL-02 local-profile MariaDB manual smoke (DevDataSeeder widening) | post-deploy operator action |

Post-merge self-resolving items (not tracked further):

| Category | Item | Status |
| -------- | ---- | ------ |
| post_merge | v1.11 PR #122 release workflow tagging | resolved by-design post-merge |

## Pending UATs

### UAT-02: Legacy Season Visual Smoke (carry-forward from v1.11 QUAL-05)

- **Procedure:** docs/uat/UAT-02-legacy-season-smoke.md
- **Status:** Executing Phase 88
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
- `./mvnw verify -Pe2e` CI median (E2E step): **23:00** (v1.11 baseline; v1.12 Round-2 target TBD post-PERF-01..03, re-harvested in Phase 91 PERF-06)
- `BackupSchema.SCHEMA_VERSION`: **1** (must remain 1; schema change → bump to 2)
- `EXPORT_ORDER` size: **24 entities** (guard test active)
- SpotBugs `BugInstance` count: **0** (blocking gate)
- CodeQL gate-step: **exit 0 on new HIGH/CRITICAL** (3-layer FP suppression invariant maintained)
- Flyway migrations: V1-V7 immutable; any new schema is V8+

## Session Continuity

**Next action:** `/gsd-plan-phase 88` to create the 6 themen-gebundelte sequential plans defined in 88-CONTEXT.md.

**Resume file:** `.planning/milestones/v1.12-phases/88-build-release-unblockers-yagni-sweep-doc-conventions-driver-/88-CONTEXT.md`

**Branch:** `gsd/v1.12-driver-import-and-test-perf`

## Operator Next Steps

- `/gsd-plan-phase 88` — generate the 6 plans: (01) CLEAN-01 verify-only, (02) CLEAN-02+03 `@Disabled` sweep, (03) REL-01 release.yml hardening + dry-run, (04) DOCS-01 CLAUDE.md convention, (05) DRIV-01+02 resolver+layout-gate, (06) REL-02 retroactive v1.10.0/v1.11.0 + legacy-tag cleanup
- After Phase 88 closure: `/gsd-discuss-phase 89` for PERF-01 + PERF-02
- After Phase 90 closure: re-harvest CI median via 5 `workflow_dispatch` runs (Phase 91 / PERF-06)
- Stretch decision: if PERF wallclock budget allows, execute UX-01 in Phase 91; otherwise descope to v1.13 with explicit note in 91-CONTEXT.md
