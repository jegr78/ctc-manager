---
phase: 85
type: verification
created: 2026-05-17
---

# Phase 85: CodeQL SAST — Verification Evidence

> Evidence-capture log mirroring `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md`.
> Sections are populated incrementally across plans 85-01 (scaffold), 85-02 (baseline triage),
> 85-03 (final-enable + `./mvnw verify -Pe2e`), and 85-04 (SAST-06 throwaway-branch test).

## SAST-01 / SAST-02 / SAST-03 Workflow Structure (Scaffold — plan 85-01)

**Date:** 2026-05-17
**Branch:** `gsd/v1.11-tooling-and-cleanup`
**Commit SHA:** f61fcbc0
**Commit message:** `feat(85): scaffold CodeQL workflow (workflow_dispatch only, gate disabled) (SAST-01..SAST-05, D-22..D-29)`

### Structural-YAML Checks (yq)

| Check | Command | Expected | Actual |
|-------|---------|----------|--------|
| SAST-01 scaffold triggers (workflow_dispatch only) | `yq -e '.on \| keys == ["workflow_dispatch"]' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| SAST-01 language + query suite | `yq -e '.jobs.analyze.steps[] \| select(.uses == "github/codeql-action/init@v4") \| .with.languages == "java-kotlin" and .with.queries == "security-extended"' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| SAST-02 manual build step | `yq -e '.jobs.analyze.steps[] \| select(.name == "Build for CodeQL") \| .run \| contains("./mvnw compile") and contains("-DskipTests") and contains("--no-transfer-progress")' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| SAST-03 job-level permissions | `yq -e '.jobs.analyze.permissions \| (."security-events" == "write" and .contents == "read" and .actions == "read")' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| SAST-03 workflow-level least-privilege | `yq -e '.permissions.contents == "read" and (.permissions \| length == 1)' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| D-04 pre-staged excludes | `yq -e '[.query-filters[].exclude.id] \| (contains(["java/ssrf"]) and contains(["java/zipslip"]) and contains(["java/path-injection"]))' .github/codeql/codeql-config.yml` | exit 0 | _(executor)_ |
| D-02-REVISED schema (no `where:`) | `! grep -qE '^\s*where:' .github/codeql/codeql-config.yml` | exit 0 | _(executor)_ |
| D-22 floating-v4 tags | `grep -E "github/codeql-action/(init\|analyze)@v4" .github/workflows/codeql.yml \| wc -l` | `2` | _(executor)_ |
| D-26 Maven cache | `yq -e '.jobs.analyze.steps[] \| select(.uses == "actions/setup-java@v5") \| .with.cache == "maven"' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| D-27 concurrency | `yq -e '.concurrency."cancel-in-progress" == true' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| D-17 + SAST-05 sections present | `grep -cE '^## (SSRF \(Server-Side Request Forgery\)\|ZIP-Slip \(Archive Path Traversal\)\|BCrypt Password Hashing \(Not Applicable\)\|Other Triaged Findings)' docs/security/sast-acceptance.md` | `4` | _(executor)_ |
| D-24 + D-25 CLAUDE.md edits | `grep -qE "^### CodeQL SAST \(Code Scanning\)" CLAUDE.md && [ "$(grep -cE "(sast-acceptance\.md\|\.github/workflows/codeql\.yml)" CLAUDE.md)" -ge "2" ]` | exit 0 | _(executor)_ |
| D-29 Renovate packageRule | `jq -e '[.packageRules[] \| select((.matchPackageNames // []) \| index("github/codeql-action") // null)] \| length >= 2' renovate.json` | exit 0 | _(executor)_ |

### Maven Sanity-Build

**Command:** `./mvnw test-compile --no-transfer-progress`
**Exit code:** 0
**Result:** PASS — `[PLAT-07 build-guard] OK`

### Wave 1 Scaffold Completion

**Completed:** 2026-05-17
**All 6 files committed atomically:** yes (codeql.yml, codeql-config.yml, sast-acceptance.md, CLAUDE.md, renovate.json, 85-VERIFICATION.md)
**Commit hash:** f61fcbc0

---

## Baseline Scan Triage Table (Plan 85-02)

Execute-time deviations encountered during Wave 2 — both resolved before baseline scan:

1. **Default CodeQL Setup conflict.** `jegr78/ctc-manager` had `state: configured` default CodeQL setup (auto-scanning all 5 languages with default query suite). First scan run #25995164105 failed with `"CodeQL analyses from advanced configurations cannot be processed when the default setup is enabled"`. **Resolution (user-confirmed):** disabled default setup via `gh api -X PATCH repos/jegr78/ctc-manager/code-scanning/default-setup -f state=not-configured`. Phase 85 now owns CodeQL scanning. Trade-off: javascript / typescript / actions auto-scanning is gone (java-kotlin only via Phase 85 advanced workflow). Documented in milestone-summary as TRACKED — future phase can add matrix-strategy for the dropped languages if desired.

2. **workflow_dispatch + default-branch requirement.** Original D-13 specified `on: workflow_dispatch:` only, but GitHub Actions requires the workflow file to exist on the **default branch (master)** for dispatch to function — the scaffold lives only on `gsd/v1.11-tooling-and-cleanup`. **Resolution:** D-13 revised (commit `fd397a61`) to add `push: { branches: [ 'gsd/v1.11-tooling-and-cleanup' ] }`; workflow now auto-triggers on each milestone-branch push. Plan 85-03 final-enable swaps this to `push: { branches: [master] }` + `pull_request` + `schedule`.

### Baseline Scan #1 (failed — default-setup conflict)

**Run URL:** https://github.com/jegr78/ctc-manager/actions/runs/25995164105
**Run ID:** 25995164105
**Event:** push (commit `fd397a61`)
**Status:** failure (annotation: "CodeQL analyses from advanced configurations cannot be processed when the default setup is enabled")
**Resolution commit:** `gh api -X PATCH` disable default-setup (no source commit — repo-settings only)

### Baseline Scan #2 (succeeded — clean)

**Run URL:** https://github.com/jegr78/ctc-manager/actions/runs/25995329890
**Run ID:** 25995329890
**Event:** workflow_dispatch
**Status:** completed (2m7s)
**Total alerts (HIGH+CRITICAL):** **0**
**Total alerts (all severities):** **0**
**Pre-staged-FP triade hits:** 0 — `query-filters[].exclude` entries (`java/ssrf`, `java/zipslip`, `java/path-injection`) suppressed the rules BEFORE alert upload to the Security tab. No alerts were emitted to triage.

### Per-Finding Decision Table (D-15 soft-scope loop, D-10 decision tree)

| # | Alert-ID | Rule | Location | Bucket | Action | Commit SHA | Rationale |
|---|----------|------|----------|--------|--------|------------|-----------|
| 1 | N/A (filtered) | java/ssrf | FileStorageService.storeFromUrl:86 | suppressed | codeql-config-exclude (scaffold) + source-marker (Wave 2) + sast-acceptance-row | Wave 2 commit | startsWith-chain hostname blocklist not recognized as sanitizer; defense-in-depth via Phase 81 SpotBugs SSRF_SPRING |
| 2 | N/A (filtered) | java/zipslip | BackupArchiveService.assertEntrySafe:614 | suppressed | codeql-config-exclude (scaffold) + source-marker (Wave 2) + sast-acceptance-row | Wave 2 commit | PathTraversalGuard.assertWithin delegation not traceable by CodeQL; defense-in-depth via Phase 81 SpotBugs PATH_TRAVERSAL_IN |
| 3 | N/A (filtered) | java/path-injection | BackupImportService.restoreOneTable:673 | suppressed | codeql-config-exclude (scaffold) + source-marker (Wave 2) + sast-acceptance-row | Wave 2 commit | Same ZipFile#getEntry pattern as BackupArchiveService; defense-in-depth via Phase 81 SpotBugs co-suppression |

**D-15 soft-scope outcome:** ZERO additional findings beyond the pre-staged triade. Codebase passes `security-extended` cleanly after rule-id whole-codebase exclusion of the SSRF/ZIP-Slip triade. No D-15 soft-scope expansion required.

**D-19 three-layer invariant verified:**
- Layer 1 (codeql-config.yml `query-filters`): 3 entries for `java/ssrf`, `java/zipslip`, `java/path-injection` — scaffold commit `f61fcbc0`
- Layer 2 (source markers): `// CodeQL FP: <rule>` lines at FileStorageService.java:86, BackupArchiveService.java:611, BackupImportService.java:672 — Wave 2 commit
- Layer 3 (sast-acceptance.md table rows): 3 rows with `N/A (filtered)` Alert-ID — Wave 2 commit
- BCrypt-N/A section present per D-05 — scaffold commit

### Post-Triage Baseline Re-Run

Not required — baseline scan #2 already produced ZERO HIGH/CRITICAL alerts. The "termination check" condition (Step F of Plan 85-02 Task 2) is satisfied by baseline scan #2 itself, which IS the post-triage state because all 3 pre-staged exclusions were in place from scaffold-time.

A confirmatory scan will run automatically on the Wave-2 source-marker push (auto-trigger via the `push: gsd/v1.11-*` branch in scaffold). Expected: zero HIGH/CRITICAL alerts.

---

## SAST-01 / D-10 Final-Enable Commit Evidence (Plan 85-03)

**Date:** 2026-05-17
**Commit SHA:** _(pending — backfilled after commit lands)_
**Commit message:** `feat(85): activate CodeQL gate on push + pull_request (SAST-01, SAST-06/1)`

### Structural-YAML Checks (Post-Final-Enable)

| Check | Command | Expected | Actual |
|-------|---------|----------|--------|
| SAST-01 final triggers | `grep -E "^  push:\|^  pull_request:\|^  schedule:\|^  workflow_dispatch:" .github/workflows/codeql.yml \| wc -l` | 4 | 4 (PASS) |
| D-20 weekly cron | `grep "cron: '0 2 \* \* 0'" .github/workflows/codeql.yml` | match | PASS — cron: '0 2 * * 0' present |
| D-06 inline-bash gate step | `grep -q "gh api" .github/workflows/codeql.yml && grep -q "code-scanning/alerts" .github/workflows/codeql.yml && grep -q "dismissed_at" .github/workflows/codeql.yml` | exit 0 | PASS |
| D-10 schedule-skip | `grep -q "if: github.event_name != 'schedule'" .github/workflows/codeql.yml` | exit 0 | PASS |
| D-28 comm -23 set-difference | `grep -q "comm -23" .github/workflows/codeql.yml` | exit 0 | PASS |
| GH_TOKEN env var | `grep -qF 'GH_TOKEN: ${{ github.token }}' .github/workflows/codeql.yml` | exit 0 | PASS |
| ::error:: annotation | `grep -q "::error::" .github/workflows/codeql.yml` | exit 0 | PASS |
| exit 1 on new alerts | `grep -q "exit 1" .github/workflows/codeql.yml` | exit 0 | PASS |
| No commented-out stub | `! grep -qE "^\s*# - name: Gate on new HIGH/CRITICAL security alerts" .github/workflows/codeql.yml` | exit 0 | PASS |
| NOT scaffold-only on: | `grep -c "^  push:\|^  pull_request:\|^  schedule:\|^  workflow_dispatch:" count > 1` | >1 trigger | PASS — 4 triggers |

---

## SAST-06 Throwaway-Branch Deliberate-Violation Evidence (Plan 85-04)

Per CONTEXT.md D-14 procedure. Throwaway branch `throwaway/sast-06-validation` MUST NOT land on `gsd/v1.11-tooling-and-cleanup` or `master`.

**Throwaway branch:** `throwaway/sast-06-validation`
**Draft PR:** _(executor — URL)_
**PR number:** _(executor)_
**Deliberate violation:** _(executor — e.g. `java/sql-injection` in `src/main/java/org/ctc/_sast_validation/SastMarker.java`)_
**Workflow run ID:** _(executor)_
**Gate-step exit code:** `1` (expected — exit 0 would be a SAST-06 failure)

### `gh run view` Output (first 30 lines after gate-step exit)

```
_(executor pastes first 30 lines of `gh run view <run-id> --log` for the gate-step)_
```

### Security-Tab Verification

**URL:** https://github.com/jegr78/ctc-manager/security/code-scanning?query=pr%3A<pr-number>
**Alert observed:** _(executor — confirm `java/sql-injection` or `java/path-injection` HIGH alert appears on the head ref of the draft PR)_

### Cleanup Verification

- [ ] `gh pr close <num>` executed
- [ ] `git push origin --delete throwaway/sast-06-validation` executed
- [ ] `git log --all --oneline | grep _sast_validation` on `gsd/v1.11-tooling-and-cleanup` returns nothing

---

## Final `./mvnw verify -Pe2e` Evidence (Plan 85-03)

**Command:** `./mvnw verify -Pe2e --no-transfer-progress` (no skip-flags per `feedback_clean_build_only`)
**Exit code:** 0
**Wallclock:** ~8m 55s (18:04:11 → 18:13:06 CEST 2026-05-17)
**JaCoCo line coverage:** 88.88% (7525/8466 lines covered — gate ≥ 82% PASS; v1.10 baseline 87.80%)
**Total tests:** 1668 (149 surefire report files + 59 failsafe report files; 0 failures, 0 errors)
**`./mvnw test-compile` post-merge sanity:** Not applicable — full `./mvnw verify -Pe2e` already ran (superset of test-compile)

---

## Sign-Off

- [ ] SAST-01..SAST-06 requirements covered (verification map in `85-VALIDATION.md` all green)
- [ ] All `TBD-baseline` rows in `docs/security/sast-acceptance.md` replaced with real Alert-IDs from the post-triage baseline run
- [ ] CONTEXT.md D-05 BCrypt-N/A deviation cross-referenced from sast-acceptance.md BCrypt section
- [ ] Operator-hoheit branch-protection toggle (D-11) documented as post-merge follow-up in milestone-summary (NOT in this file)
- [ ] `STATE.md` + `ROADMAP.md` + `REQUIREMENTS.md` updated to reflect Phase 85 complete (in plan 85-04 close-up commit)
