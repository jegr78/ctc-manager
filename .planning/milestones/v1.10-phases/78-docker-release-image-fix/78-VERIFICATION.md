---
phase: 78-docker-release-image-fix
verified: 2026-05-11T14:30:00Z
status: passed
score: 5/5 success criteria verified (criterion 3 PENDING POST-MERGE by design)
overrides_applied: 0
re_verification: null
human_verification:
  - test: "After PR merge to master, the next release workflow run reaches and passes the `Build and push Docker image` step"
    expected: "`gh run list --workflow=Release --limit 1` shows green run; the `playwright install chromium` RUN step in stage 2 succeeds; release run is no longer reproducing the failure mode of run 25609204039"
    why_human: "Success criterion 3 is by design a post-merge observation — the release workflow only runs on `push: master`, not on the feature branch. The Plan 02 `docker-build` CI job is the pre-merge structural duplicate; the actual release-pipeline green is the contractual signal."
deferred: []
---

# Phase 78: Docker Release Image Fix — Verification Report

**Phase Goal (from ROADMAP §"Phase 78"):**
Restore the release workflow's "Build and push Docker image" step to green by pinning both Dockerfile stages to a Playwright-compatible, Noble-based Eclipse Temurin tag (release run `25609204039` failed at the runtime stage's `playwright install chromium` step with `Playwright does not support chromium on ubuntu26.04-x64`). Plus structural CI guards (build-guard + docker-build job) to prevent silent drift, plus local pre-PR verification.

**Verified:** 2026-05-11
**Branch:** `gsd/v1.10-platform-and-backup`
**Status:** PASSED (criterion 3 PENDING POST-MERGE — by-design deferral, not a gap)
**Re-verification:** No — initial verification

---

## Goal Achievement — 5 ROADMAP Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | `Dockerfile` stage 1 reads `FROM eclipse-temurin:25-jdk-noble AS build` and stage 2 reads `FROM eclipse-temurin:25-jre-noble`; no unpinned Temurin tags remain | ✓ VERIFIED | `grep -c '^FROM eclipse-temurin:25-jdk-noble AS build$' Dockerfile` = 1; `grep -c '^FROM eclipse-temurin:25-jre-noble$' Dockerfile` = 1; `grep -cE '^FROM eclipse-temurin:(25-jdk\|25-jre)( \|$)' Dockerfile` = 0 (no unpinned tags remain) |
| 2 | `docker build .` completes locally without the `Playwright does not support chromium on ubuntu26.04-x64` error; `playwright install chromium` succeeds end-to-end | ✓ VERIFIED | Plan 03 D-02 captured: image digest `sha256:f61a295c112668ce1e4bc7d1a9d95d5a3bfe8e988fabdbcd647518bf52de25ab` built on Docker 29.4.1; `Chrome for Testing 147.0.7727.15 (playwright chromium v1217) downloaded` confirmed in build log; failure string `Playwright does not support chromium on ubuntu26.04-x64` absent from build log (count=0); container health JSON `{"groups":["liveness","readiness"],"status":"UP"}`; curl returns `HTTP 200`; Spring Boot startup confirmed (`Started CtcManagerApplication in 3.726 seconds`) |
| 3 | After merge to master, the next release workflow run reaches and passes the `Build and push Docker image` step | ⏳ PENDING POST-MERGE | By-design deferral — `release.yml` only runs on `push: master`. The Plan 02 `docker-build` CI job structurally duplicates the failing step on PR and is the pre-merge proxy. Will be confirmed via `gh run list --workflow=Release --limit 1` after squash-merge. **NOT a verification gap.** |
| 4 | The `libasound2t64` apt-get line and the rest of the runtime stage continue to install cleanly on Noble (no package-name regressions from the pin) | ✓ VERIFIED | `grep -c 'libasound2t64' Dockerfile` = 1; Plan 03 D-02 capture shows full Dockerfile build succeeds (exit code 0), including the apt-get install RUN step. Noble package name matches the new pinned `25-jre-noble` base. `groupadd -r ctc` non-root user setup preserved (count=1) |
| 5 | No application code, Spring Boot version, Java version, or Playwright version changes — pin-only diff scoped to `Dockerfile` (plus additive ci.yml + REQUIREMENTS/ROADMAP doc updates per Plans 02/03) | ✓ VERIFIED | `git log ffa5303^..HEAD --name-only` shows only: `Dockerfile`, `.github/workflows/ci.yml`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md`, `.planning/phases/78-docker-release-image-fix/78-0*-SUMMARY.md`. No `src/`, `pom.xml`, or any other application-level files touched. `release.yml` untouched. |

**Score:** 5/5 success criteria verified (criterion 3 PENDING POST-MERGE — by design, not a gap)

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `Dockerfile` | Multi-stage Dockerfile pinned to `eclipse-temurin:25-jdk-noble` + `:25-jre-noble`, two inline Phase-78 rationale comments | ✓ VERIFIED | 50 lines; stage 1 line 3 = `FROM eclipse-temurin:25-jdk-noble AS build`; stage 2 line 21 = `FROM eclipse-temurin:25-jre-noble`; two `Pinned to -noble: Playwright 1.59.0 does not support Ubuntu 26.04 (Plucky). See Phase 78 / ...78-CONTEXT.md.` comments on lines 2 and 20. All other lines preserved (apt-get with `libasound2t64`, non-root `ctc` user, `PLAYWRIGHT_BROWSERS_PATH` env, `playwright install chromium` bootstrap, `ENTRYPOINT`). |
| `.github/workflows/ci.yml` | Three top-level jobs: `build-and-test` (preserved), `dockerfile-noble-pin-guard` (new), `docker-build` (new). YAML valid. | ✓ VERIFIED | All three job keys present. Guard job uses the two-stage `grep -E` + `grep -v -F -e '-noble'` idiom (mirrors PLAT-07 / commit f451ff4). `docker-build` job declares `needs: dockerfile-noble-pin-guard`, runs `docker build -t ...`, no buildx, no login, no `paths:` filter. Failure-message tagging `[Phase 78 build-guard]` present (count ≥ 3). YAML parses cleanly (Ruby + Python yaml.safe_load both succeed). |
| `.planning/REQUIREMENTS.md` | New `### PLAT-CI` section with PLAT-CI-01 + PLAT-CI-02 bullets, two new Traceability rows (mapped to Phase 78), one new Coverage row, Total updated 37 → 39 | ✓ VERIFIED | Section header present (line 67); PLAT-CI-01 / PLAT-CI-02 bullets cite all relevant decisions (D-01, D-04, D-05..D-08) and the f451ff4 prior-art commit; Traceability rows on lines 152-153; Coverage row on line 168; Total updated to 39 (with `PLAT-CI × 2` in the breakdown); old "Total: 37" replaced (count = 0). |
| `.planning/ROADMAP.md` Phase 78 Requirements line | Flipped from `TBD ...` to `PLAT-CI-01, PLAT-CI-02` | ✓ VERIFIED | Line 289 = `**Requirements**: PLAT-CI-01, PLAT-CI-02`; no `**Requirements**: TBD` lines remain in ROADMAP (count = 0). |

---

## Key Link Verification (Wiring)

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| Dockerfile stage 2 `RUN apt-get install` | `libasound2t64` (Noble package name) | Noble base image (`eclipse-temurin:25-jre-noble`) | ✓ WIRED | `libasound2t64` line present in Dockerfile and would install cleanly on Noble. Plan 03 D-02 build success (image digest `sha256:f61a295c...`) confirms apt-get RUN step works against the pinned base. |
| `.github/workflows/ci.yml` `docker-build` job | Dockerfile stage 1 + stage 2 | `docker build .` exercising both stages including `playwright install chromium` | ✓ WIRED | Job present, `needs: dockerfile-noble-pin-guard` declared (so the cheap structural guard gates the heavy build), `docker build -t ...` shells out to Dockerfile, full multi-stage build will run on every PR + push. |
| `.github/workflows/ci.yml` `dockerfile-noble-pin-guard` step | `Dockerfile` FROM lines | `grep -E '^FROM eclipse-temurin:'` candidates filtered by `grep -v -F -e '-noble'` | ✓ WIRED | Spot-check confirmed (Step 7b): against the currently pinned Dockerfile the filter yields zero violations (guard PASSES); against a simulated unpinned line `FROM eclipse-temurin:25-jre` the filter correctly yields the offending line (guard FAILS). Both code paths exercise correctly. |
| Phase 78 ROADMAP entry (was TBD) | REQUIREMENTS.md PLAT-CI-01 + PLAT-CI-02 | Traceability table | ✓ WIRED | Both Traceability rows present and mapped to Phase 78; Coverage row for Phase 78 present; ROADMAP Requirements line flipped from TBD to PLAT-CI-01, PLAT-CI-02. Bidirectional consistency confirmed. |

---

## Behavioral Spot-Checks (Step 7b)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Pin guard passes on the currently-pinned Dockerfile | `from_lines=$(grep -E '^FROM eclipse-temurin:' Dockerfile); printf '%s\n' "$from_lines" \| grep -v -F -e '-noble' \|\| true` | Empty (no violations) → guard would log `[Phase 78 build-guard] OK` | ✓ PASS |
| Pin guard correctly FAILS on a simulated unpinned line | `printf 'FROM eclipse-temurin:25-jdk-noble AS build\nFROM eclipse-temurin:25-jre\n' \| grep -v -F -e '-noble'` | `FROM eclipse-temurin:25-jre` (correctly flagged) → guard would log `[Phase 78 build-guard] FAIL` | ✓ PASS |
| ci.yml YAML is structurally valid | `ruby -ryaml -e "YAML.load_file('.github/workflows/ci.yml')"` | exit 0 (`YAML valid`) | ✓ PASS |
| Local docker build of pinned Dockerfile produces a healthy image (Plan 03 D-02) | `docker build -t ctc-manager:phase78-local .` + `docker run -p 18080:8080 ...` + `curl /actuator/health` | Image `sha256:f61a295c...` built; `HTTP 200`; `{"status":"UP"}`; Spring Boot started in 3.726 s; no `Playwright does not support chromium on ubuntu26.04-x64` in build log | ✓ PASS (captured pre-verification in 78-03-SUMMARY) |

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PLAT-CI-01 | 78-01-PLAN (declared), 78-03-PLAN (registered) | Pin both Dockerfile stages to `eclipse-temurin:25-{jdk,jre}-noble` (suffix-only, no SHA256), each FROM preceded by Phase-78 rationale comment | ✓ SATISFIED | Plan 01 commit `ffa5303` shipped the pin; Dockerfile lines 3 + 21 reflect the pin; rationale comments present on lines 2 + 20. PLAT-CI-01 is in REQUIREMENTS.md (Plan 03 commit `e5f7fe0`). |
| PLAT-CI-02 | 78-02-PLAN (declared), 78-03-PLAN (registered) | Structural CI protection: `dockerfile-noble-pin-guard` job + `docker-build` job in ci.yml | ✓ SATISFIED | Plan 02 commits `c446b43` + `72ba72e` shipped both jobs; ci.yml has both job keys, the guard uses the cross-platform two-stage grep idiom, the docker-build job is `needs:`-gated on the guard. PLAT-CI-02 is in REQUIREMENTS.md. |

No orphaned requirements: REQUIREMENTS.md maps Phase 78 to exactly `PLAT-CI-01, PLAT-CI-02`, both of which are claimed by phase 78 plans.

---

## Decision Coverage Gate (D-01..D-08)

All eight locked decisions from `78-CONTEXT.md` are cited in plan must_haves and surface in the shipped artifacts:

| Decision | Citation Count (PLANs + SUMMARYs) | Shipped Evidence |
|----------|----------------------------------|------------------|
| D-01 (suffix-only Noble pin, no SHA256) | 18 | Dockerfile lines 3, 21 are suffix-pinned to `-noble`; no `@sha256:` digest present |
| D-02 (local docker build + /actuator/health verification) | 27 | 78-03-SUMMARY `## D-02 Local Verification` section captures all four artifacts (build-log tail with `Successfully tagged` + chromium download line, health JSON `{"status":"UP"}`, `HTTP 200` curl line, image digest `sha256:f61a295c...`) |
| D-03 (Team-Card-Generation smoke NOT required pre-PR) | 6 | 78-03-SUMMARY explicitly notes Team-Card smoke was deliberately skipped per locked D-03 |
| D-04 (inline rationale comment per FROM line) | 12 | Dockerfile lines 2 and 20 — both reference Phase 78, Playwright 1.59.0, Ubuntu 26.04 (Plucky), and CONTEXT.md |
| D-05 (CI build-guard, whitelist-on-suffix, grep -E / grep -F idiom) | 13 | ci.yml `dockerfile-noble-pin-guard` job uses `grep -E '^FROM eclipse-temurin:'` extract + `grep -v -F -e '-noble'` filter (mirrors PLAT-07 / commit f451ff4); failure message names Phase 78 explicitly |
| D-06 (structural guard, no path filter, no opt-out) | 13 | Both new ci.yml jobs inherit the workflow-level `on: push: master / pull_request: master` trigger; no per-job `paths:`, `if:`, or conditional logic |
| D-07 (full `docker build .` job on every PR + push) | 12 | ci.yml `docker-build` job runs `docker build -t ...` with no skipping and no path filter; +1-3 min CI cost accepted |
| D-08 (single full docker-build job, no separate path-filtered or container-smoke job) | 11 | ci.yml has only one `docker-build` job (no second smoke/path-filter job); confirmed by `grep -c '^  docker-build:$' = 1` |

**Decision coverage gate: PASS (non-blocking).** All 8 decisions are honored in code; no decision is silently dropped.

---

## Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `.planning/ROADMAP.md` | 299, 304 | `**Plans:** 2/3 plans executed` and `- [ ] 78-03-PLAN.md` checkbox unchecked, but 78-03 SUMMARY exists and `e5f7fe0` / `4e2161c` were committed | ℹ️ Info | Clerical bookkeeping inconsistency. ROADMAP progress counter and checkbox were not flipped after Plan 03 completed. Does NOT affect any of the 5 phase success criteria (none of which mention ROADMAP checkbox state). Typically updated by the phase-evolve step after verification. Recommend: flip `- [ ]` → `- [x]` on line 304 and `2/3` → `3/3` on line 299 as part of phase wrap-up. |
| `.planning/phases/78-docker-release-image-fix/78-03-SUMMARY.md` | 46, 60, 120 | Uses placeholder `Playwright/Plucky regression string` instead of the literal failure string `Playwright does not support chromium on ubuntu26.04-x64` in the narrative + grep example | ℹ️ Info | Cosmetic — readability hint for maintainers reading the SUMMARY. Does NOT violate any Plan 03 acceptance criterion (the criterion was: literal failure string MUST be absent from SUMMARY = 0, which is satisfied). The grep example on line 120 uses the placeholder, not the literal string, so future audit `grep "ubuntu26.04-x64" 78-03-SUMMARY.md = 0` continues to hold. |

No blocker or warning anti-patterns. No TODO/FIXME/placeholder code. No empty implementations or hardcoded stubs. No security-relevant patterns (no secrets, no credentials, no PII).

---

## Human Verification Required

### 1. Release workflow run on master goes green (Criterion 3)

**Test:** After this branch is squash-merged to `master`, observe the next release workflow run.
**Expected:**
1. `gh run list --workflow=Release --limit 1 --branch master` shows status `completed` with conclusion `success`.
2. Within that run, the `Build and push Docker image` step (the step that failed in run `25609204039` with `Playwright does not support chromium on ubuntu26.04-x64`) reaches and passes — no Playwright/Plucky error in the step log.
3. The pushed image at `ghcr.io/jegr/ctc-manager:<version>` (or the configured registry path) is `eclipse-temurin:25-jre-noble`-based.

**Why human:** Success criterion 3 is by design a post-merge observation. `release.yml` triggers only on `push: master`, not on feature-branch PRs. The Plan 02 `docker-build` CI job is the pre-merge structural duplicate (same `docker build .` shape, same Playwright RUN step) and is the strongest pre-merge proxy available; the actual release run is the final contractual signal.

**Not a verification gap.** The phase plan explicitly carved out criterion 3 as the only post-merge verification surface.

---

## Gaps Summary

**No gaps found.**

All four pre-merge success criteria are verified by direct codebase inspection plus the Plan 03 D-02 local-build artifacts (image digest, health JSON, HTTP 200, build log tail). The phase scope was tightly controlled — pin-only diff in Dockerfile, additive jobs in ci.yml, REQUIREMENTS/ROADMAP doc updates — and the verification confirms no scope creep (no `src/`, no `pom.xml`, no Java/Spring/Playwright version bump).

Criterion 3 is the single remaining open item and is by-design post-merge — flagged for human verification once the PR squash-merges to master.

Two cosmetic Info-level findings (ROADMAP Plans counter still says 2/3, SUMMARY uses `Playwright/Plucky regression string` placeholder phrasing). Neither blocks goal achievement.

---

## Phase Verdict

## PHASE VERIFICATION PASSED

- **Pre-merge criteria 1, 2, 4, 5:** ✓ VERIFIED in codebase
- **Post-merge criterion 3:** ⏳ PENDING POST-MERGE (by-design deferral, awaiting `gh run list --workflow=Release` after squash-merge)
- **Decision coverage (D-01..D-08):** ✓ all 8 decisions cited and honored
- **Requirements coverage (PLAT-CI-01, PLAT-CI-02):** ✓ both registered and satisfied
- **Scope creep:** ✓ none — only Dockerfile, ci.yml, and planning docs touched
- **Anti-patterns:** 2 Info-level cosmetic items (ROADMAP bookkeeping + SUMMARY placeholder phrasing), 0 blockers, 0 warnings

Ready for PR. The PR-time CI on this branch will exercise both new ci.yml jobs (`dockerfile-noble-pin-guard` + `docker-build`) on themselves — the first end-to-end CI signal that the guard regex is correct and `docker build .` on a `-noble` base succeeds on `ubuntu-latest`.

---

*Verified: 2026-05-11*
*Verifier: Claude (gsd-verifier)*
