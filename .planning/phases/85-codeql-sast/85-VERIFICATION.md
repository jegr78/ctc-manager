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

**Date:** _(executor fills after commit)_
**Branch:** `gsd/v1.11-tooling-and-cleanup`
**Commit SHA:** _(executor fills)_
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
**Exit code:** _(executor fills after commit)_
**Result:** _(executor fills after commit)_

### Wave 1 Scaffold Completion

**Completed:** 2026-05-17
**All 6 files committed atomically:** yes (codeql.yml, codeql-config.yml, sast-acceptance.md, CLAUDE.md, renovate.json, 85-VERIFICATION.md)
**Commit hash:** _(executor fills after commit)_

---

## Baseline Scan Triage Table (Plan 85-02)

First baseline workflow_dispatch run produced by `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup`.

**Run URL:** _(executor)_
**Run ID:** _(executor)_
**Total alerts (HIGH+CRITICAL):** _(executor)_
**Pre-staged-FP triade hits:** _(executor — expected: SSRF on FileStorageService, ZIP-Slip on BackupArchiveService + BackupImportService)_

### Per-Finding Decision Table (D-15 soft-scope loop, D-10 decision tree)

| # | Alert-ID | Rule | Location | Bucket | Action | Commit SHA | Rationale |
|---|----------|------|----------|--------|--------|------------|-----------|
| _(executor populates one row per HIGH/CRITICAL baseline alert; bucket ∈ {fixed, suppressed, accepted}; Action ∈ {fix-commit, codeql-config-exclude + source-marker + sast-acceptance-row, UI-dismiss + sast-acceptance-row})_ |

### Post-Triage Baseline Re-Run

**Command:** `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` (after all triage commits)
**Run URL:** _(executor)_
**HIGH/CRITICAL alert count after triage:** _(executor — expected: 0)_

---

## SAST-01 / D-10 Final-Enable Commit Evidence (Plan 85-03)

**Date:** _(executor)_
**Commit SHA:** _(executor)_
**Commit message:** `feat(85): activate CodeQL gate on push + pull_request (SAST-01, SAST-06/1)`

### Structural-YAML Checks (Post-Final-Enable)

| Check | Command | Expected | Actual |
|-------|---------|----------|--------|
| SAST-01 final triggers | `yq -e '.on \| has("push") and has("pull_request") and has("schedule")' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| D-20 weekly cron | `yq -e '.on.schedule[0].cron == "0 2 * * 0"' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| D-06 inline-bash gate step | `yq -e '.jobs.analyze.steps[] \| select(.name == "Gate on new HIGH/CRITICAL security alerts") \| .run \| contains("gh api") and contains("code-scanning/alerts") and contains("dismissed_at")' .github/workflows/codeql.yml` | exit 0 | _(executor)_ |
| D-10 schedule-skip | `grep -q "if: github.event_name != 'schedule'" .github/workflows/codeql.yml` | exit 0 | _(executor)_ |

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
**Exit code:** _(executor)_
**Wallclock:** _(executor)_
**JaCoCo line coverage:** _(executor — expected ≥ 82%, baseline 87.80% v1.10)_
**`./mvnw test-compile` post-merge sanity:** _(executor)_

---

## Sign-Off

- [ ] SAST-01..SAST-06 requirements covered (verification map in `85-VALIDATION.md` all green)
- [ ] All `TBD-baseline` rows in `docs/security/sast-acceptance.md` replaced with real Alert-IDs from the post-triage baseline run
- [ ] CONTEXT.md D-05 BCrypt-N/A deviation cross-referenced from sast-acceptance.md BCrypt section
- [ ] Operator-hoheit branch-protection toggle (D-11) documented as post-merge follow-up in milestone-summary (NOT in this file)
- [ ] `STATE.md` + `ROADMAP.md` + `REQUIREMENTS.md` updated to reflect Phase 85 complete (in plan 85-04 close-up commit)
