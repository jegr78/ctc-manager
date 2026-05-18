---
phase: 85-codeql-sast
plan: "01"
subsystem: ci-workflow
tags: [codeql, sast, security, github-actions, renovate, documentation]

requires:
  - phase: 81-static-analysis-gate-spotbugs-find-sec-bugs
    provides: SpotBugs defense-in-depth layer + rationale texts reused verbatim for CodeQL
  - phase: 84-renovate-integration
    provides: renovate.json packageRules schema + DEPS-04 GitHub Actions policy

provides:
  - .github/workflows/codeql.yml — standalone CodeQL SAST workflow (scaffold-state, workflow_dispatch only)
  - .github/codeql/codeql-config.yml — rule-id-only query-filters for SSRF + ZIP-Slip triade
  - docs/security/sast-acceptance.md — per-pattern SAST triage acceptance log skeleton
  - CLAUDE.md CodeQL SAST convention sub-section + 2 References entries
  - renovate.json github/codeql-action packageRule (patch automerge 3d + minor/major manual-review)

affects:
  - 85-02-baseline-triage (populates sast-acceptance.md TBD-baseline rows + triage decision table)
  - 85-03-final-enable (extends codeql.yml triggers + adds inline-bash SARIF-diff gate step)
  - 85-04-sast06-verification (throwaway-branch deliberate-violation gate test)

tech-stack:
  added:
    - github/codeql-action@v4 (floating tag, Renovate-managed)
    - docs/security/ directory (new security documentation tier)
  patterns:
    - "Standalone CodeQL workflow separate from ci.yml — different permissions (security-events:write at job level), different schedule, different failure semantics"
    - "Workflow-level least-privilege (contents:read only) + job-level security-events:write — SAST-03 mandate"
    - "query-filters rule-id-only schema (D-02-REVISED) — per-file filtering rejected (advanced-security/filter-sarif 3rd-party policy)"
    - "D-19 Update-on-Triage three-layer suppression: codeql-config.yml filter + source marker + sast-acceptance.md table row — all three in same commit"
    - "Defense-in-depth: CodeQL whole-codebase rule-id exclude + Phase 81 SpotBugs per-file XML exclude on same sites"

key-files:
  created:
    - .github/codeql/codeql-config.yml
    - .github/workflows/codeql.yml
    - docs/security/sast-acceptance.md
  modified:
    - CLAUDE.md
    - renovate.json
    - .planning/phases/85-codeql-sast/85-VERIFICATION.md

key-decisions:
  - "D-02-REVISED: codeql-config.yml query-filters use rule-id-only schema (no where: field — RESEARCH C-01 schema correction). Whole-codebase rule exclude acceptable because SpotBugs gate provides defense-in-depth on the same files."
  - "D-05: BCrypt false-positive triage is N/A — SecurityConfig.java uses httpBasic(Customizer.withDefaults()) with no PasswordEncoder bean. Documented as TRACKED DEVIATION in sast-acceptance.md."
  - "D-06: SARIF-diff gate via inline-bash (plan 85-03) — not a marketplace action (Phase-84 3rd-party-action minimization policy)."
  - "D-13: Scaffold-disable = workflow_dispatch: only. No push/pull_request/schedule in scaffold-state to prevent accidental gate-blocked PRs during triage."
  - "D-22: Floating @v4 tags for github/codeql-action — Renovate-managed via D-29 packageRule."
  - "D-29: github/codeql-action patch automerge after 3-day cooldown, minor/major via Dependency Dashboard — consistent with Phase-84 DEPS-04 GitHub Actions strategy."

patterns-established:
  - "Pattern S-2: Three-layer suppression (codeql-config.yml filter + // CodeQL FP: source marker + sast-acceptance.md table row) enforced by D-19 Update-on-Triage discipline"
  - "Pattern: docs/security/ directory as security documentation tier alongside docs/uat/ and docs/superpowers/"

requirements-completed:
  - SAST-01
  - SAST-02
  - SAST-03
  - SAST-04
  - SAST-05

duration: 6min
completed: "2026-05-17"
---

# Phase 85 Plan 01: CodeQL SAST Scaffold Summary

**Standalone CodeQL SAST workflow + codeql-config.yml with SSRF/ZIP-Slip query-filters + sast-acceptance.md skeleton + CLAUDE.md conventions + Renovate packageRule landed atomically in scaffold-state (workflow_dispatch only, gate inactive until plan 85-03)**

## Performance

- **Duration:** 6 min
- **Started:** 2026-05-17T14:32:20Z
- **Completed:** 2026-05-17T14:38:34Z
- **Tasks:** 3 (executed as a single atomic commit per plan objective)
- **Files modified:** 6

## Accomplishments

- Created `.github/workflows/codeql.yml` scaffold (workflow_dispatch only, java-kotlin, security-extended, manual `./mvnw compile` build, job-level security-events:write, workflow-level contents:read only, Maven cache, concurrency cancel-in-progress)
- Created `.github/codeql/codeql-config.yml` with rule-id-only query-filters for `java/ssrf`, `java/zipslip`, `java/path-injection` (D-02-REVISED schema — no `where:` field)
- Created `docs/security/sast-acceptance.md` with all 4 required sections (SSRF / ZIP-Slip / BCrypt-N/A / Other), three seeded TBD-baseline rows, and D-05 BCrypt tracked deviation documented
- Extended `CLAUDE.md` with `### CodeQL SAST (Code Scanning)` convention sub-section (after SpotBugs block) + 2 new References entries
- Added `github/codeql-action` Renovate packageRule to `renovate.json` (patch 3-day automerge + minor/major dashboard approval) before catch-all rule
- Updated `85-VERIFICATION.md` skeleton with Wave 1 scaffold completion evidence

## Task Commits

All 3 tasks executed atomically in a single commit (per plan `<objective>` — 5 goal-backward truths must be simultaneously true):

1. **Task 1+2+3: Scaffold CodeQL + docs + config** — `f61fcbc0` (feat)

## Files Created/Modified

- `.github/workflows/codeql.yml` — Standalone CodeQL SAST workflow, scaffold-state (workflow_dispatch only)
- `.github/codeql/codeql-config.yml` — query-filters with rule-id-only excludes for SSRF + ZIP-Slip triade
- `docs/security/sast-acceptance.md` — SAST triage acceptance log skeleton
- `CLAUDE.md` — CodeQL SAST convention sub-section + 2 References
- `renovate.json` — github/codeql-action packageRule (2 objects before catch-all)
- `.planning/phases/85-codeql-sast/85-VERIFICATION.md` — Wave 1 scaffold evidence added

## Decisions Made

- Followed CONTEXT.md D-02-REVISED exactly: `query-filters` use rule-id-only `exclude` (no `where:` field). RESEARCH C-01 confirmed this is the correct GitHub schema.
- BCrypt-N/A deviation documented in sast-acceptance.md per D-05/D-18: no `PasswordEncoder` bean in `SecurityConfig.java` — CodeQL will emit no BCrypt findings.
- Used `3 days` (not `"3 days"`) as `minimumReleaseAge` value in renovate.json — consistent with Renovate schema for this property.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed "autobuild" word from codeql.yml comment**
- **Found during:** Task 1 verification
- **Issue:** Acceptance criteria `! grep -q "autobuild" .github/workflows/codeql.yml` would fail because "autobuild" appeared in comment explaining why we are NOT using it ("NOT autobuild")
- **Fix:** Rephrased comment to "manual build step (./mvnw compile)" without mentioning "autobuild"
- **Files modified:** `.github/workflows/codeql.yml`
- **Verification:** `! grep -q "autobuild" .github/workflows/codeql.yml` exits 0
- **Committed in:** f61fcbc0 (part of task commit)

**2. [Rule 3 - Blocking] `yq` not available locally — used python3 + grep for structural validation**
- **Found during:** Task 1 verification
- **Issue:** `yq` not installed on the local machine; acceptance criteria `automated_command` blocks use `yq -e`
- **Fix:** Used `python3 -c "import yaml; ..."` + grep-based checks as equivalent validation. All structural facts confirmed by python3 YAML parsing and grep.
- **Impact:** Validation equivalent, just different tooling. `yq` checks will run correctly in CI where it is available.
- **Note:** Python3 YAML 1.1 parses `on:` key as boolean `True` — structural checks for triggers done via grep instead.

---

**Total deviations:** 2 auto-fixed (1 Rule 1 bug fix in comment wording, 1 Rule 3 blocking tool fallback)
**Impact on plan:** Both minimal. Comment fix required for acceptance criteria compliance. Tool fallback has no impact on output quality.

## Known Stubs

The following stubs in `docs/security/sast-acceptance.md` are intentional and expected for scaffold-state:

| File | Stub | Reason |
|------|------|--------|
| `docs/security/sast-acceptance.md` | `Alert-ID: TBD-baseline` (3 rows) | Real Alert IDs are populated from the first `gh workflow run` baseline scan in plan 85-02 — they don't exist until the workflow runs on GitHub |

These stubs prevent the plan's goal (scaffold a triage log for future population) from being complete only in the sense that they will be filled in plan 85-02 as designed.

## Issues Encountered

None beyond the documented deviations.

## User Setup Required

None — no external service configuration required for this plan. The scaffold workflow must be manually triggered via `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` in plan 85-02 to produce the baseline scan.

## Next Phase Readiness

- Plan 85-02 (baseline triage) can now run `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` to trigger the first SARIF upload
- The pre-staged query-filters will suppress the known SSRF + ZIP-Slip FPs; any remaining HIGH/CRITICAL alerts need triage per D-10 decision tree
- `85-VERIFICATION.md` Baseline Scan Triage Table is ready for population

---
*Phase: 85-codeql-sast*
*Completed: 2026-05-17*
