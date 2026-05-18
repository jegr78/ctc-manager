---
phase: 81-static-analysis-gate-spotbugs-find-sec-bugs
plan: 03
subsystem: infra
tags: [spotbugs, find-sec-bugs, maven, static-analysis, gate-activation, documentation]

# Dependency graph
requires:
  - phase: 81-02
    provides: "Zero Medium+HIGH BugInstances on clean baseline (prerequisite for gate-flip)"
provides:
  - "pom.xml spotbugs:check blocking gate — `./mvnw verify` fails on any new Medium+HIGH SpotBugs finding"
  - "STAT-06 deliberate-violation throwaway-branch proof that NP_ALWAYS_NULL (HIGH) triggers the gate"
  - "CLAUDE.md ## Conventions / ### Static Analysis sub-section documenting the gate, suppression-file workflow, and lombok.config invariant"
  - "81-VERIFICATION.md populated with STAT-05/2 gate-flip evidence + STAT-06 failure log"
affects:
  - future phases that modify src/main/java (gate will catch any new Medium+HIGH SpotBugs patterns)
  - phase-85-codeql-sast (three SSRF/PATH_TRAVERSAL/BCrypt suppressions in spotbugs-exclude.xml form the precedent)
  - phase-84-renovate (spotbugs-maven-plugin and findsecbugs-plugin are Renovate-manageable patch/minor pins)

# Tech tracking
tech-stack:
  added: []  # no new libraries — gate-flip is a 1-word change in an already-wired plugin block
  patterns:
    - "spotbugs:check (blocking) bound to verify phase — fails build on Medium+HIGH BugInstances"
    - "throwaway-branch deliberate-violation proof pattern (D-13): create on throwaway, verify fails, delete branch, verify passes"
    - "CLAUDE.md ## Conventions ### sub-section for per-tool gate documentation (D-14 pattern)"

key-files:
  created:
    - ".planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-03-SUMMARY.md"
  modified:
    - "pom.xml — <goal>spotbugs</goal> → <goal>check</goal> (1-word flip activates blocking gate)"
    - "CLAUDE.md — new ### Static Analysis (SpotBugs + find-sec-bugs) sub-section appended after ### CSS Guidelines"
    - ".planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md — STAT-05/2 + STAT-06 sections populated"

key-decisions:
  - "Gate-flip + CLAUDE.md documentation committed atomically (feat(81): activate SpotBugs blocking gate) per D-12 step 4 + D-14 inseparability rule"
  - "STAT-06 throwaway-branch test used src/main/java/ per RESEARCH.md Pitfall F (SpotBugs only scans main classes; <includeTests> not set)"
  - "No README modification per D-15 discretion — CLAUDE.md is the authoritative location for the SpotBugs gate documentation"
  - "DriverProfilePageGeneratorTest screenshot failure (Playwright Page.captureScreenshot timeout) identified as a pre-existing intermittent flaky test; not caused by gate-flip change; confirmed flaky by retry passing"

patterns-established:
  - "D-13 throwaway-branch validation protocol: branch → add violation → verify fails → switch back → delete branch → verify passes"
  - "D-14 gate documentation: new ### sub-section under ## Conventions with three bullets (Gate / Suppressions / lombok.config invariant)"

requirements-completed: [STAT-05, STAT-06, STAT-07]

# Metrics
duration: ~90min (including two full ./mvnw verify runs + ./mvnw verify -Pe2e)
completed: 2026-05-16
---

# Phase 81 Plan 03: Gate Flip + STAT-06 Evidence + CLAUDE.md Docs Summary

**SpotBugs blocking gate activated (goal=check) on clean 0-finding baseline, NP_ALWAYS_NULL gate-failure proven on throwaway branch, and SpotBugs conventions documented in CLAUDE.md; JaCoCo 88.47%, 1648 tests passing**

## Performance

- **Duration:** ~90 min (including two full `./mvnw verify -Pe2e` runs)
- **Started:** 2026-05-16T14:10Z
- **Completed:** 2026-05-16T16:40Z
- **Tasks:** 2
- **Files modified:** 3 (`pom.xml`, `CLAUDE.md`, `81-VERIFICATION.md`)

## Accomplishments

- Flipped the single `<goal>spotbugs</goal>` to `<goal>check</goal>` in pom.xml — `./mvnw verify` now blocks the build on any new Medium+HIGH SpotBugs finding
- Proved the gate works via STAT-06 throwaway-branch test: deliberate `NP_ALWAYS_NULL` violation triggered `BUILD FAILURE` with exit code 1; throwaway branch deleted with no artifact in origin
- Appended `### Static Analysis (SpotBugs + find-sec-bugs)` sub-section to CLAUDE.md `## Conventions` per D-14/STAT-07 — three bullets covering gate activation, suppression-file workflow, and `lombok.config` invariant
- Final `./mvnw verify -Pe2e` exits 0: 1381 unit + 231 integration + 36 E2E tests all green; JaCoCo 88.47%; SpotBugs 0 BugInstances on clean tree

## Task Commits

1. **Task 1: Gate flip + CLAUDE.md Static Analysis sub-section** — `64fdb7ba` (feat)
2. **Task 2: STAT-06 evidence + VERIFICATION.md populated** — `b12ac7f3` (docs)

## Files Created/Modified

- `pom.xml` — `<goal>spotbugs</goal>` → `<goal>check</goal>` (+ comment update from STAT-05/1 to STAT-05/2); 4 lines changed
- `CLAUDE.md` — new `### Static Analysis (SpotBugs + find-sec-bugs)` sub-section with three bullets after `### CSS Guidelines`; 6 lines added
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md` — STAT-05/2 gate-flip evidence section + STAT-06 deliberate-violation evidence section populated

## Phase 81 Completeness — All 7 STAT-NN Requirements

| Req ID | Description | Plan | Status |
|--------|-------------|------|--------|
| STAT-01 | `lombok.config` with `addSuppressFBWarnings=true` | 01 | DONE |
| STAT-02 | `spotbugs-maven-plugin` 4.9.8.3 in main build, effort=Max, threshold=Default | 01 | DONE |
| STAT-03 | `findsecbugs-plugin` 1.14.0 plugin-dep, 144 patterns | 01 | DONE |
| STAT-04 | `config/spotbugs-exclude.xml` with rationale comments on every entry | 01+02 | DONE |
| STAT-05 | Two atomic commits: report-only first → blocking gate flip | 01+03 | DONE |
| STAT-06 | Gate blocks on deliberate HIGH violation (throwaway-branch proof) | 03 | DONE |
| STAT-07 | CLAUDE.md Conventions updated with gate + suppression + lombok.config | 03 | DONE |

## Final Verification Numbers

| Metric | Value | Note |
|--------|-------|------|
| JaCoCo line coverage | **88.47%** | +0.67pp vs v1.10 baseline 87.80%; ≥ 82% gate |
| SpotBugs BugInstances (clean tree) | **0** | Medium+HIGH; gate does not fire |
| `@{argLine}` count in pom.xml | **3** | Lines 258, 289, 405 — Pitfall #7 invariant |
| `<argLine>` in SpotBugs block | **0** | Pitfall #7 safe |
| Surefire (unit) tests | **1381** passed, 4 skipped | |
| Failsafe IT tests | **231** passed, 3 skipped | |
| Failsafe E2E tests | **36** passed | Playwright all green |
| `./mvnw verify -Pe2e` runtime | **11:10 min** | v1.11 wallclock baseline |
| STAT-06 gate exit code | **1** | NP_ALWAYS_NULL HIGH on throwaway branch |
| STAT-06 SpotBugs pattern | **NP_ALWAYS_NULL** | "Null pointer dereference of o" |
| Throwaway branch in origin | **NO** | git push not invoked; `git branch -D` deleted locally |
| `@SuppressWarnings("all")` in codebase | **0** | Confirmed by grep |

## Decisions Made

- Gate-flip + CLAUDE.md documentation committed atomically per D-12 step 4 + D-14 inseparability rule (gate activation and its documentation are logically one change)
- STAT-06 throwaway-branch file placed in `src/main/java/` per RESEARCH.md Pitfall F — SpotBugs only scans `${project.build.outputDirectory}` because `<includeTests>` is not set in pom.xml
- No README modification (D-15 discretion) — CLAUDE.md is the authoritative location for SpotBugs gate documentation per STAT-07

## Deviations from Plan

None — plan executed exactly as written. The `DriverProfilePageGeneratorTest` intermittent screenshot failure observed on the first `./mvnw verify` run was confirmed as a pre-existing flaky Playwright test (Team Card generation screenshot race): not caused by the gate-flip, passed on retry.

## Issues Encountered

- **Flaky Playwright test on first `./mvnw verify`:** `DriverProfilePageGeneratorTest` threw `Unable to capture screenshot` during `TestDataService.seed()`. This is a pre-existing intermittent issue with the Playwright Chromium screenshot path (not related to SpotBugs or the goal flip). The test passed on immediate retry; confirmed flaky. The final `./mvnw verify -Pe2e` run also passed. No code change needed.
- **Python replacement for pom.xml edit:** The em-dash character in the STAT-05/1 comment prevented the standard Edit tool from matching `old_string`. Used Python's `str.replace()` to perform the substitution — functionally equivalent, same end result.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- All 7 STAT-NN requirements satisfied across Plans 01-02-03.
- Phase 81 feature branch (`worktree-agent-a0dc1ee49574f227d`) is ready for worktree merge by orchestrator.
- PR checklist (per CLAUDE.md `## Git Workflow` "Before PR"):
  - [x] `./mvnw verify -Pe2e` green (88.47% coverage, 0 SpotBugs findings, 1648 tests passing)
  - [ ] Self code review (`superpowers:code-reviewer` on the squash-merge diff) — user action
  - [x] No local `git tag` operations performed (CI Release Workflow tags after merge)
  - [x] Branch is off `origin/master` via worktree mechanism
- Phase 85 (CodeQL SAST): the SSRF/PATH_TRAVERSAL/BCrypt suppressions in `config/spotbugs-exclude.xml` form the precedent pattern.
- Phase 84 (Renovate): `spotbugs-maven-plugin` 4.9.8.3 and `findsecbugs-plugin` 1.14.0 are candidates for Renovate patch-allow/minor-manual-review policy.

---

## Self-Check

**Checking created files exist:**
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-03-SUMMARY.md` — this file

**Checking commits exist:**
- `64fdb7ba` — `feat(81): activate SpotBugs blocking gate (STAT-05/2, STAT-06, STAT-07)`
- `b12ac7f3` — `docs(81): record STAT-05/2 gate-flip + STAT-06 deliberate-violation evidence`

**Key invariants:**
- `pom.xml` SpotBugs block: `<goal>check</goal>` present, `<goal>spotbugs</goal>` absent, `<argLine>` absent
- `@{argLine}` count in pom.xml: 3 (lines 258, 289, 405)
- `CLAUDE.md` contains `### Static Analysis (SpotBugs + find-sec-bugs)` with all 6 required tokens
- Throwaway branch deleted: `git branch --list 'throwaway/*'` returns empty
- `DeliberateNullDereference.java` absent from working tree
- `./mvnw verify -Pe2e` exit 0 on clean tree

## Self-Check: PASSED

---
*Phase: 81-static-analysis-gate-spotbugs-find-sec-bugs*
*Completed: 2026-05-16*
