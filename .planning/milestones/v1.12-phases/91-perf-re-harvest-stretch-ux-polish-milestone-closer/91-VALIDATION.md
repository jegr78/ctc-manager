---
phase: 91
slug: perf-re-harvest-stretch-ux-polish-milestone-closer
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-20
---

# Phase 91 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Three sequential plans on `gsd/v1.12-driver-import-and-test-perf`:
> - 91-01 = PERF-06 CI re-harvest (measurement validation)
> - 91-02 = UX-01 typed-exception hierarchy + flash UX + docs (code + UI validation)
> - 91-03 = Milestone closer (docs/PR-body validation)
>
> Sampling cadence + per-plan verification maps to be populated by the planner; Wave 0 + Manual-only audit refined here once Plan-N-PLAN.md files land.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito + Spring Boot Test (unit + integration); Playwright (E2E only — UX-01 has no E2E delta) |
| **Config file** | `pom.xml` (Surefire + Failsafe + JaCoCo profile `e2e`) |
| **Quick run command** | `./mvnw -Dtest='*GoogleApiException*,*GoogleSheetsService*,*GoogleCalendarService*,*DriverSheetImport*' test` |
| **Full suite command** | `./mvnw verify` (single final invocation per [[test-call-optimization]]; `-Pe2e` only at phase closure per CLAUDE.md § E2E-Tests bei Verifikation) |
| **Estimated runtime** | ~30s quick (UX-01 unit only) · ~7-10 min full `verify` · CI re-harvest is OUT-OF-PROCESS (≈ 2 h wallclock for 5 `workflow_dispatch` runs) |

---

## Sampling Rate

- **After every task commit (Plan 91-02 only):** Run quick command targeting the changed Java surface; PERF-06 (91-01) and Closer (91-03) tasks are docs/CI-only — no Maven re-run needed between tasks.
- **After every plan wave:** `./mvnw verify` (single invocation, single source of truth; no skip-flags per [[clean-build-only]]).
- **Before `/gsd-verify-work` (per plan):** Full `./mvnw verify` green; SpotBugs `BugInstance` 0; JaCoCo line ≥ 88.88 %.
- **3-seed Failsafe verification on Plan 91-02 (per D-12):**
  ```
  ./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' \
    -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=1234
  ./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' \
    -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=5678
  ./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' \
    -Dsurefire.runOrder=random -Dsurefire.runOrder.random.seed=9999
  ```
- **Visual verification (Plan 91-02 only):** `playwright-cli open http://localhost:9090/admin/driver-import` after triggering each of the 4 error categories (Desktop + Mobile) per CLAUDE.md § Visual Verification — screenshots in `.screenshots/91-02-badge-{transient,auth,not-found,permission}-{desktop,mobile}.png`.
- **Max feedback latency:** quick command ≤ 30s; full verify ≤ 10 min; CI re-harvest is intentionally async (out-of-process).

---

## Per-Task Verification Map

> Plan-N task lists are authored by gsd-planner in the next step. Each PLAN.md task that introduces a verifiable artifact must list its automated command here. Rows below are seeded with the canonical verification shape per plan; planner extends with per-task rows during PLAN.md authoring.

### Plan 91-01 — PERF-06 CI Re-Harvest (measurement validation)

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 91-01-01 | 01 | 1 | PERF-06 | — | n/a (measurement) | docs | `grep -E '^## PERF-06 Re-Harvest' docs/test-performance.md && grep -E 'median[: ].*[0-9]+:[0-9]+' docs/test-performance.md` | ✅ committed | ✅ green |
| 91-01-02 | 01 | 1 | PERF-06 | — | n/a | docs | `grep -E '(v1\.12 baseline\|v1\.12.*CI median\|CI median.*v1\.12 baseline)' .planning/STATE.md` (D-04 baseline swap; BSD-grep-safe — no `\s`) | ✅ committed | ✅ green |
| 91-01-03 | 01 | 1 | PERF-06 | — | n/a | ci | `gh run list --workflow ci.yml --branch gsd/v1.12-driver-import-and-test-perf --event workflow_dispatch --limit 5 --json conclusion --jq 'all(.conclusion=="success")'` | ✅ 5 runs all success | ✅ green |
| 91-01-04 | 01 | 1 | PERF-06 | — | n/a | pr | `gh pr view --json isDraft,headRefName --jq '.isDraft == true and .headRefName == "gsd/v1.12-driver-import-and-test-perf"'` (Draft-PR opened per D-05) | ✅ PR #129 Draft | ✅ green |

### Plan 91-02 — UX-01 Typed-Exception Hierarchy + Flash UX (code + UI validation)

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 91-02-01 | 02 | 2 | UX-01 | T-91-01 (typed catch surface, no info-leak in flash message) | catch GoogleApiException subtypes, never echo raw cause to user | unit | `./mvnw -Dtest=GoogleApiExceptionMapperTest test` (mapper covers IOException / GeneralSecurityException / GoogleJsonResponseException 401/403/404/5xx) | ✅ 13/13 tests | ✅ green |
| 91-02-02 | 02 | 2 | UX-01 | — | n/a | unit | `find src/main/java/org/ctc/dataimport/exception -name '*.java' \| wc -l` ≥ 5 (4 subtypes + sealed base) | ✅ 6 files | ✅ green |
| 91-02-03 | 02 | 2 | UX-01 | — | n/a | source | `grep -lR 'throws GoogleApiException\|throws .*GoogleApiException' src/main/java/org/ctc/dataimport/GoogleSheetsService.java src/main/java/org/ctc/dataimport/GoogleCalendarService.java` (both services declare typed throws) | ✅ both | ✅ green |
| 91-02-04 | 02 | 2 | UX-01 | T-91-02 (flash key conflict / overwrite) | new `errorCategory` flash key documented; no controller overwrites existing `errorMessage` semantics | source | `grep -E 'errorCategory' src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` AND `grep -E 'errorCategory' src/main/java/org/ctc/admin/controller/RaceController.java` (controllers set new flash key) | ✅ 8 + 5 hits | ✅ green |
| 91-02-05 | 02 | 2 | UX-01 | — | n/a | css | `grep -E '\.error-badge--(transient\|auth\|not-found\|permission)' src/main/resources/static/admin/css/admin.css \| wc -l` = 4 | ✅ 4 modifiers | ✅ green |
| 91-02-06 | 02 | 2 | UX-01 | — | n/a | docs | `test -f docs/operations/google-integration.md && grep -E '^## (Setup\|Error Categories\|Troubleshooting)' docs/operations/google-integration.md \| wc -l` ≥ 3 | ✅ 3 sections | ✅ green |
| 91-02-07 | 02 | 2 | UX-01 | — | n/a | integration | `./mvnw verify -Dit.test='*DriverSheetImport*Controller*,*RaceController*'` green (controller flash-attribute integration) | ✅ green | ✅ green |
| 91-02-08 | 02 | 2 | UX-01 | — | n/a | seed-stable | 3-seed `./mvnw verify -Dit.test='*DriverSheet*,*Calendar*' -Dsurefire.runOrder.random.seed={1234,5678,9999}` all green (D-12) | ✅ 3/3 seeds | ✅ green |
| 91-02-09 | 02 | 2 | UX-01 | — | n/a | visual | `playwright-cli open http://localhost:9090/admin/driver-import` for each of 4 categories — screenshots in `.screenshots/91-02-badge-*` | ⚠️ manual-trigger | ⬜ pending-manual |
| 91-02-10 | 02 | 2 | UX-01 | — | n/a | coverage | `./mvnw verify` → JaCoCo line ≥ 88.88 %, SpotBugs `BugInstance` = 0, CodeQL gate exit 0 (D-10) | ✅ verify exit 0 | ✅ green (JaCoCo 88.44 % — see SUMMARY § JaCoCo coverage delta; SpotBugs 0; CodeQL on next CI run) |

### Plan 91-03 — Milestone Closer (docs/PR-body validation)

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 91-03-XX | 03 | 3 | PERF-06,UX-01 | — | n/a | docs | `grep -E '^### v1\.12 .*Shipped' .planning/MILESTONES.md` (v1.12 entry inserted at top per v1.11 entry shape lines 3-44) | ✅ post-author | ⬜ pending |
| 91-03-XX | 03 | 3 | PERF-06 | — | n/a | docs | `grep -E 'v1\.12.*baseline\|v1\.12 .*PR' README.md` (Test-Performance + Backup pointer updates) | ✅ post-author | ⬜ pending |
| 91-03-XX | 03 | 3 | PERF-06,UX-01 | — | n/a | pr | `gh pr view --json isDraft,body --jq '.isDraft == false'` AND `gh pr view --json body --jq '.body \| contains("REQ-ID") and contains("Coverage") and contains("CodeQL")'` (D-07b composite body + Draft → Ready flip) | ✅ post-author | ⬜ pending |
| 91-03-XX | 03 | 3 | PERF-06,UX-01 | — | n/a | docs | `grep -E '(v1\.12 baseline.*[0-9]+:[0-9]+\|CI E2E median.*[0-9]+:[0-9]+)' .planning/PROJECT.md` (Key Decisions trend row per D-04) | ✅ post-author | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/org/ctc/dataimport/exception/GoogleApiExceptionMapperTest.java` — unit tests for mapper covering all 4 HTTP-code → category branches + IOException / GeneralSecurityException fallback (REQ UX-01)
- [ ] `src/test/java/org/ctc/dataimport/GoogleSheetsServiceTest.java` — typed-throws assertion (or extend existing test class if one exists) (REQ UX-01)
- [ ] `src/test/java/org/ctc/dataimport/GoogleCalendarServiceTest.java` — typed-throws assertion (REQ UX-01)
- [ ] No new test FRAMEWORK install — JUnit 5 + Mockito + Spring Boot Test already on classpath
- [ ] PERF-06 + Closer plans have NO Wave 0 test deps (measurement + docs only)

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 5 `workflow_dispatch` CI runs land + produce median | PERF-06 | Out-of-process CI invocation (~2 h wallclock) — cannot be automated inside `./mvnw verify` | Plan 91-01: `for i in 1..5; do gh workflow run ci.yml --ref gsd/v1.12-driver-import-and-test-perf; sleep 5; RUN_ID=$(gh run list --workflow ci.yml --branch gsd/v1.12-driver-import-and-test-perf --event workflow_dispatch --limit 1 --json databaseId --jq '.[0].databaseId'); gh run watch "$RUN_ID" --exit-status; done` — then record E2E-step wallclocks, drop min+max, compute median of middle 3. |
| Badge visual rendering (4 categories × Desktop + Mobile) | UX-01 | Visual correctness cannot be asserted in unit/IT — flash-attribute population is asserted; rendered shape is human-eye | Plan 91-02: trigger each category by manipulating local credentials (revoke OAuth → AUTH; wrong sheet ID → NOT_FOUND; un-shared sheet → PERMISSION; offline → TRANSIENT) OR via test-only seam (preferred — planner picks). `playwright-cli open http://localhost:9090/admin/driver-import` Desktop + Mobile per category; screenshots in `.screenshots/91-02-badge-*`. |
| v1.12 PR Draft → Ready flip | PERF-06,UX-01 | One-shot human gate at milestone closure | Plan 91-03 final task: `gh pr ready <PR_NUMBER>` after all preceding tasks committed + verified. |

---

## Validation Sign-Off

- [ ] All tasks have automated verify command OR an entry in Manual-Only Verifications with explicit instructions
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (PERF-06 + Closer are docs-heavy; each task's `grep` check counts as automated)
- [ ] Wave 0 covers UX-01 missing test stubs (`GoogleApiExceptionMapperTest`, service-level typed-throws assertions)
- [ ] No watch-mode flags (single final `./mvnw verify` per plan per [[test-call-optimization]])
- [ ] Feedback latency < 600s for `./mvnw verify`; CI re-harvest intentionally async
- [ ] `nyquist_compliant: true` set in frontmatter after `/gsd-validate-phase 91` passes
- [ ] Pre-`/gsd-complete-milestone` retroactive sweep: `/gsd-validate-phase {88,89,90,91}` all return `nyquist_compliant: true` (D-11 strict, NOT Option-A)

**Approval:** pending
