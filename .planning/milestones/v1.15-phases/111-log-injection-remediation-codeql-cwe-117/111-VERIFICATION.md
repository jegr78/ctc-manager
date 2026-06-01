---
phase: 111
type: verification
verdict: PASS
verified: 2026-05-31
---

# Phase 111 Verification — Log-Injection Remediation (CodeQL CWE-117)

Goal-backward verification: does the codebase deliver the phase goal — drive all 29
open `java/log-injection` (CWE-117) alerts to 0 via a central `LogSanitizer` source
fix (no suppressions), with the clean build and all SAST gates intact?

## Requirement coverage

| Req | Criterion | Verdict | Evidence |
|-----|-----------|---------|----------|
| SEC-LOG-01 | Central sanitizer + behaviour-pinning untagged unit test; CodeQL-recognised `\R` barrier | ✅ PASS | `org/ctc/util/LogSanitizer.java` (two-pass `replaceAll("\\R","_")`+C0/TAB), `LogSanitizerTest` (no `@Tag`, 10 green executions). Plan 111-01. |
| SEC-LOG-02 | Every flagged user-controlled log arg (+ same-statement siblings) routed through `sanitize()` at the call site; no entry-point mutation | ✅ PASS | 17 files carry `import static …LogSanitizer.sanitize`; alerts 1–29 + D-06 siblings wrapped; `safeWeekend` ad-hoc replace removed; `setScheduledWeekend` entry point untouched. 203 targeted tests green. Plan 111-02. |
| SEC-LOG-03 | CodeQL re-scan reports 0 open `java/log-injection`; no new suppressions | ✅ PASS | PR #132 `pull_request` run `26712254416` (success); analysis `c004ff27` at `refs/pull/132/merge` → **0 open `java/log-injection`**. `codeql-config.yml` diff vs `origin/master` has no log-injection `query-filters` (D-07). Plan 111-03. |
| SEC-LOG-04 | `./mvnw clean verify -Pe2e` green; SpotBugs/find-sec-bugs green; coverage ≥ 82% | ✅ PASS | BUILD SUCCESS; 2468 tests (1815+537+116), 0 fail/err; SpotBugs `BugInstance size is 0`; JaCoCo "All coverage checks have been met". Plan 111-03. |

## Strategy fidelity (CONTEXT decisions)
- **Source fix, not suppression** (D-04/D-07): all fixes are call-site `sanitize()` wraps; zero `query-filters` / `@SuppressFBWarnings` / `// CodeQL FP` markers added.
- **Helper boundary auto-recognised**: the `\R` literal inside `sanitize()` broke the taint across the method boundary — RESEARCH Pitfall 2 did not occur, so the conditional `barrierModel` model-pack (Task 3) was correctly **not** built.
- **No entry-point mutation** (D-05): verified for `MatchdayService.setScheduledWeekend`, `searchTerm`, `row.psnId()`.

## Deviation (recorded, user-approved)
- CsvImportService line ~311 — an identical "Overwriting existing match" clone inside a lambda, unflagged by CodeQL (lambda-flow gap) — was also wrapped (interactive-checkpoint approval) to prevent a latent CWE-117. Documented in 111-02-SUMMARY.

## Pre-merge note
The default-branch (`master`) code-scanning view still lists the original 29 alerts as `open`; this is expected pre-merge state (master alert state updates only when a scan runs on master, i.e. after PR #132 squash-merges). The verification ref is the PR-merge ref, which is clean (0).

## Verdict: PASS — all of SEC-LOG-01..04 satisfied.
