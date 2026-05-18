# Plan 85-04 — SAST-06 Verification + Phase Close Summary

**Plan:** 85-04
**Wave:** 4
**Status:** complete
**Completed:** 2026-05-17
**Branch:** `gsd/v1.11-tooling-and-cleanup`

## Outcome

SAST-06 verified end-to-end. Deliberate `java/sql-injection` pattern on `throwaway/sast-06-validation` triggered the CodeQL workflow on draft PR #128, the SARIF-diff gate-step exited 1 with `::error::` annotation, the PR check status went red. Throwaway branch + PR + alert cleaned up post-verification. No artifact leaked into the milestone branch.

## Execute-Time Discovery (Gate-Step Bug)

First throwaway workflow run #25996453992 (HEAD `03e5f756`, original gate-step) PASSED unexpectedly — gate exited 0. Investigation revealed GitHub stores PR-context alerts under a separate API parameter:

- `gh api ... -f "ref=refs/heads/throwaway/sast-06-validation"` → 0 alerts
- `gh api ... -f "pr=128"` → alert #35 (SQLi, security_severity_level "high")

**Fix (commit `61ccee5f`):** Split `fetch_alerts()` into `fetch_alerts_by_ref()` and `fetch_alerts_by_pr()`. When `EVENT_NAME == "pull_request"`, HEAD alerts use `pr=${PR_NUMBER}` via `${{ github.event.pull_request.number }}`; BASE alerts continue via `ref=refs/heads/${BASE_REF}`. After the fix, run #25996558384 (HEAD `f763593e`) correctly exited 1.

**This is the canonical SAST-06 value:** structural tests (`actionlint`, `yq`) all passed on the original gate-step. Only the live deliberate-violation run with a real PR-context alert surfaced the semantic bug. Without SAST-06 the broken gate would have shipped to master.

## Workflow Runs (PR #128)

| # | Run ID | Event | HEAD | Conclusion | Gate-step | Notes |
|---|--------|-------|------|------------|-----------|-------|
| 1 | 25996453992 | pull_request | `03e5f756` | success (false negative) | exit 0 — bug | `ref=` query missed PR-context alert #35 |
| 2 | 25996558384 | pull_request | `f763593e` | **failure** | **exit 1 — correct** | `pr=` query returned alert #35; gate fired |

Run #2 log output:
```
##[warning]No prior scan data for base ref=master; treating all HEAD alerts as net-new.
##[error]New HIGH/CRITICAL CodeQL alerts introduced:
java/sql-injection|src/main/java/org/ctc/_sast_validation/SastMarker.java
##[error]Process completed with exit code 1.
```

PR #128 `Analyze (java-kotlin)` check: `fail` (2m9s) — CodeQL blocks merge.

## Cleanup Verification

- ✅ `gh pr close 128` — PR closed without merge
- ✅ `git push origin --delete throwaway/sast-06-validation` — remote branch deleted
- ✅ `git branch -D throwaway/sast-06-validation` — local branch deleted
- ✅ `gh api -X PATCH .../alerts/35 -f state=dismissed -f dismissed_reason="won't fix"` — Security-tab alert dismissed with rationale
- ✅ `git log --all --oneline | grep _sast_validation` — empty (no traces on any live ref)
- ✅ `src/main/java/org/ctc/_sast_validation/` — directory absent in working tree

## Commits

- `b96e3a52` (throwaway only) `throwaway: deliberate java/sql-injection for SAST-06 gate verification` — never landed on milestone
- `61ccee5f` `fix(85): gate-step uses pr= query for pull_request events (SAST-06 surfaced ref=branch returns 0 alerts on PR refs)`
- `9747a478` `refactor(85): strip verbose CI-file comments (memory feedback_no_unnecessary_comments)`
- `99aef343` `refactor(85): simplify codeql.yml on: block — remove milestone-branch hardcode, throwaway PR targets master`
- This plan's close-up commit (next) — STATE/ROADMAP/REQUIREMENTS + 85-04-SUMMARY + 85-VERIFICATION finalize

## D-11 Operator-Hoheit Note

Repository branch-protection rule "Required status checks: Analyze (java-kotlin)" / "CodeQL" is OUT OF SCOPE for Phase 85 per CONTEXT D-11. The CodeQL workflow + gate-step deliver the technical capability; turning the gate into a merge-block requires a post-merge manual step in GitHub Repo Settings (Settings → Branches → master rule → add `Analyze (java-kotlin)` to "Required status checks"). This step is documented in the v1.11 milestone-summary as a post-merge operator action.

## SC#1..SC#5 Status

- ✅ SC#1 standalone workflow with push/PR/cron triggers (SAST-01)
- ✅ SC#2 manual `./mvnw compile` build step (SAST-02)
- ✅ SC#3 Security tab zero unreviewed alerts (`gh api .../alerts?state=open` returns 0 HIGH/CRITICAL after triage; alert #35 dismissed)
- ✅ SC#4 SSRF + ZIP-Slip + BCrypt-N/A classified with linked alert IDs (SAST-05)
- ✅ SC#5 deliberate violation fails PR gate (SAST-06 verified, run #25996558384)

## Sign-Off

- [x] All Phase-85 requirements (SAST-01..SAST-06) complete in REQUIREMENTS.md
- [x] All Wave-2/3/4 commits on `gsd/v1.11-tooling-and-cleanup` per D-23
- [x] SAST-06 gate-step bug discovered and fixed pre-merge (the canonical SAST-06 value)
- [x] Throwaway artifact hygiene clean — no `_sast_validation` traces on any live ref
- [x] Branch-protection-rule setup deferred to operator-hoheit post-merge (D-11)
