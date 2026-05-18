---
phase: 87
plan: 07
subsystem: planning
tags:
  - validation
  - nyquist
  - retroactive
  - v1.10-archive
  - phase-78
  - state-b
  - plat-ci-01
  - plat-ci-02
  - dockerfile-pin
requires:
  - 87-06-SUMMARY
provides:
  - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-VALIDATION.md (status: approved, nyquist_compliant: true, State B — created new)
  - src/test/java/org/ctc/DockerfilePinGuardTest.java (new Surefire unit, 2 scenarios, in-process structural duplicate of CI dockerfile-noble-pin-guard grep gate)
affects:
  - VAL-02 (1 of 2 missing VALIDATION.md files now created + approved: 78; 71 already created under Plan 87-01)
  - VAL-03 (auditor execution for phase 78 complete — 7 of 8 phases now closed: 71, 72, 73, 74, 75, 76, 78; only 79 remaining)
tech_stack:
  added: []
  patterns:
    - "Retroactive VALIDATION audit — State B path (template-generated, no prior draft)"
    - "In-process structural duplicate of CI grep gate (Files.readString + JUnit AssertJ)"
    - "Wave 0 satisfied retroactively annotation per CONTEXT D-12"
key_files:
  created:
    - src/test/java/org/ctc/DockerfilePinGuardTest.java
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-01-PLAN.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-02-PLAN.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-03-PLAN.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-01-SUMMARY.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-02-SUMMARY.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-03-SUMMARY.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-CONTEXT.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-VERIFICATION.md
    - .planning/milestones/v1.10-phases/78-docker-release-image-fix/78-VALIDATION.md
  modified: []
decisions:
  - "Identified 1 gap via inline orchestrator-driven audit matching 87-RESEARCH.md §Phase 78 HIGH-likelihood prediction (1-2 tests, conservative end delivered) — PLAT-CI-01 had only CI-workflow grep-gate coverage (dockerfile-noble-pin-guard), no in-process Java assertion; the new DockerfilePinGuardTest reads Files.readString(Path.of(\"Dockerfile\")) and asserts (a) every FROM eclipse-temurin: line ends in -noble, (b) the exact pinned forms 25-jdk-noble (build) + 25-jre-noble (runtime) remain"
  - "PLAT-CI-02 classified Manual-Only — a workflow-level structural-guard-test parsing ci.yml was considered and explicitly rejected; tying the Java test suite to YAML structure would be brittle to legitimate workflow refactors without proportional value over the workflow execution itself (the CI dockerfile-noble-pin-guard + docker-build jobs ARE the contractual structural assertion). Post-merge release-workflow observation (Phase 78 SC#3) re-stated as Manual-Only with backlink to 78-VERIFICATION.md human_verification block"
  - "DockerfilePinGuardTest is plain JUnit — no @SpringBootTest, no @Tag (Surefire default group), no Maven profile change. File-IO only via Files.readString; 0.080 s exec, BUILD SUCCESS. Wallclock impact within CONTEXT D-06 5% headroom (24:09 max vs 23:00 baseline → 69 s headroom across milestone; this plan consumes ~0.1 s)"
  - "Zero implementation bugs surfaced. Dockerfile lines 3 + 21 remain correctly pinned to -noble (DockerfilePinGuardTest confirms structurally); .github/workflows/ci.yml dockerfile-noble-pin-guard + docker-build jobs remain present (CI run 26008754136 on b7f20b53 confirms green). No fix(78): commit emitted, no CONTEXT D-08 trivial-fix invocation, no non-trivial impl-bug escalation"
  - "State B (no prior draft) means VALIDATION.md was created fresh from template, NOT transitioned from status=draft. Commit message reflects 'create' rather than 'approve draft'. Frontmatter shape matches 87-RESEARCH.md §Template Frontmatter for State B (Phase 71, 78) with slug=docker-release-image-fix (short form per CONTEXT D-04, NOT truncated). Same template pattern as the parallel State B Phase 71 — produces structurally identical VALIDATION.md shape"
metrics:
  duration: "~25 min"
  completed: 2026-05-18
---

# Phase 87 Plan 07: Restore v1.10 Phase 78 + Retroactive VALIDATION Creation Summary (State B)

Restored v1.10 Phase 78 (Docker Release Image Fix — Pin to Noble) from git ref `60f5f915^` into `.planning/milestones/v1.10-phases/`, ran the retroactive audit, filled the 1 HIGH-likelihood gap predicted in 87-RESEARCH.md (PLAT-CI-01 — in-process structural duplicate of the CI `dockerfile-noble-pin-guard` grep gate) with a `DockerfilePinGuardTest` regression guard, and **created a brand-new** `78-VALIDATION.md` at `status: approved` + `nyquist_compliant: true` (State B — no prior draft existed in `60f5f915^`).

## What was delivered

### Restored artefacts (8 files)

3 PLAN + 3 SUMMARY + `78-CONTEXT.md` + `78-VERIFICATION.md` restored verbatim from git history under the **short slug** `78-docker-release-image-fix` (CONTEXT D-04 — confirmed in 87-RESEARCH.md §"Phase 78" as NOT truncated, full canonical form). Per CONTEXT D-02 minimal restore scope: `.gitkeep` and `78-DISCUSSION-LOG.md` from `60f5f915^` deliberately excluded. Per 87-RESEARCH.md §"Phase 78": no `78-RESEARCH.md` exists in `60f5f915^` (reactive surgical phase had none).

### Gap-fill test (1 file / 2 test cases)

**Gap-1 (PLAT-CI-01 in-process structural duplicate, 87-RESEARCH.md §"Phase 78" HIGH-likelihood prediction):** `DockerfilePinGuardTest` — Surefire untagged unit, 2 scenarios:

1. `givenDockerfile_whenInspectingFromLines_thenAllEclipseTemurinTagsArePinnedToNoble` — generic whitelist-on-suffix assertion: collects every `FROM eclipse-temurin:` line via `content.lines().filter(line -> line.startsWith(FROM_PREFIX))` then asserts `tagSpec.endsWith(NOBLE_SUFFIX)` for each. Mirrors the CI workflow's `grep -E '^FROM eclipse-temurin:' | grep -v -F -e '-noble'` idiom in-process.
2. `givenDockerfile_whenInspectingBothStages_thenBuildAndRuntimePinsArePresent` — pin-exact assertion: requires the literal strings `FROM eclipse-temurin:25-jdk-noble AS build` (build stage) and `FROM eclipse-temurin:25-jre-noble` (runtime stage). Catches subtle tag rotations the generic check would miss (e.g. `25-jdk-noble-alpine`).

Plain JUnit — no `@SpringBootTest`, no `@Tag`, no Spring context, file-IO only via `Files.readString(Path.of("Dockerfile"))`. **0.080 s exec time**, BUILD SUCCESS on first run. Located at `src/test/java/org/ctc/DockerfilePinGuardTest.java` (project-level package per 87-RESEARCH.md §"Test Class Placement" Phase 78 row).

### VALIDATION.md creation (State B)

- Frontmatter: `status: approved`, `nyquist_compliant: true`, `wave_0_complete: true`, `created: 2026-05-18`, `approved_on: 2026-05-18`, `audit_method: retroactive`, `slug: docker-release-image-fix`. Matches the parallel State B Phase 71 frontmatter shape verbatim (per 87-RESEARCH.md §"Template Frontmatter for State B").
- Per-Task Verification Map: 7 rows (78-01-01..03 for Plan 01, 78-02-01..02 for Plan 02, 78-03-01..02 for Plan 03), every row `✅ green`, every row cites a real evidence path:
  - Plan 01 rows: Dockerfile lines 3 + 21, plus the new DockerfilePinGuardTest
  - Plan 02 rows: ci.yml `dockerfile-noble-pin-guard` (lines 73-111) + `docker-build` (lines 113-142) jobs, plus the new DockerfilePinGuardTest as in-process duplicate
  - Plan 03 rows: v1.10-REQUIREMENTS.md PLAT-CI-01/02 traceability rows + post-merge release-workflow tracking
- Wave 0 Requirements: marked `[x]` retroactively — 4 items (Dockerfile pin, both CI jobs, new DockerfilePinGuardTest). Annotated **"satisfied retroactively"** per CONTEXT D-12 + 87-RESEARCH.md §"Phase 78 special framing" (reactive surgical phase).
- Manual-Only Verifications: 4 entries — `docker build .` local, both CI workflow jobs, and the post-merge release-workflow observation (Phase 78 SC#3, re-stated from `78-VERIFICATION.md` `human_verification` block per 87-RESEARCH.md Q2).
- Sign-Off: all 6 boxes `[x]` (light template-language adaptation: "post-hoc evidence" instead of "Wave 0 dependencies" — see Claude's-Discretion #2 + 87-RESEARCH.md §"Sign-Off Mechanics").
- `## Validation Audit 2026-05-18` block with gap count (1 found / 1 resolved / 0 escalated), pre/post audit classification table, predicted-vs-actual gap-profile validation, CI run-id citation (`26008754136`), wallclock + JaCoCo impact statements, and `@DirtiesContext` invariant preservation note.

## Gap audit findings

| Requirement | Pre-audit | Gap Tests Added | Post-audit |
|-------------|-----------|-----------------|------------|
| PLAT-CI-01 (Dockerfile -noble pin both stages) | PARTIAL — covered only by CI `dockerfile-noble-pin-guard` workflow grep gate; no in-process Java assertion | 1 file (2 scenarios — `DockerfilePinGuardTest`) | COVERED |
| PLAT-CI-02 (Structural CI protection: pin-guard + docker-build jobs) | COVERED via CI (`dockerfile-noble-pin-guard` + `docker-build` jobs on every PR + push); post-merge release-workflow observation by-design deferred | 0 (Manual-Only retained — YAML-coupling cost > value) | COVERED |

**Total:** 1 test file / 2 test cases. Matches the predicted 1-2 gaps in 87-RESEARCH.md §"Phase 78" (HIGH likelihood — actual delivery at the conservative end of the range). No `@DirtiesContext` added on the new test (file-IO unit — no Spring context).

## Implementation bugs

**None.** The predicted Dockerfile pin regression candidate (someone bumping `25-jdk` without `-noble` since v1.10 close) did NOT manifest — `Dockerfile` lines 3 + 21 remain correctly pinned to `25-jdk-noble` + `25-jre-noble` per `DockerfilePinGuardTest` structural assertion. The CI `dockerfile-noble-pin-guard` + `docker-build` jobs in `.github/workflows/ci.yml` remain present (lines 73-142) per CI run `26008754136` on `b7f20b53`. No `fix(78):` commits emitted, no CONTEXT D-08 trivial-fix invocation, no non-trivial impl-bug escalation, no `## CHECKPOINT:USER_DECISION_NEEDED`.

## Commits on `gsd/v1.11-tooling-and-cleanup`

| SHA | Message |
|-----|---------|
| `1111b9d6` | `docs(87-07): restore v1.10 phase 78 for validation closure` |
| `3c74fccb` | `test(87-07): fill 1 validation gap for phase 78 (DockerfilePinGuardTest)` |
| `743ac447` | `docs(87-07): create 78-VALIDATION.md (status: approved, nyquist_compliant: true)` |

State B atomic-per-phase commit group with 3 commits (restore + test + create-and-approve). Matches the State B 3-commit shape predicted in 87-RESEARCH.md §"Sample Per-Plan Commit Sequence" + per CONTEXT D-08 + D-13. **NOT** the 2-commit shape (which would have applied only with zero gaps), **NOT** the 4-commit shape (which would have included a `fix(78):` commit if a trivial impl bug had surfaced).

## State B vs State A differences observed

This plan is the second State B execution in Phase 87 (Plan 87-01 / Phase 71 was the first). Differences from State A (the 87-02..87-06 path):

- **Commit message:** `docs(87-07): create 78-VALIDATION.md ...` (NOT `... approve 78-VALIDATION.md`)
- **Frontmatter shape:** `created: 2026-05-18` line present alongside `approved_on: 2026-05-18` (in State A, `created` would be the original draft date)
- **No draft-to-approved transition narrative:** the VALIDATION.md is generated whole-cloth from the template; no `status: draft → approved` diff exists
- **Sign-Off language:** light template adaptation per Claude's-Discretion #2 (use "post-hoc evidence" instead of "Wave 0 dependencies") to reflect retroactive audit on already-shipped phase

## Deviations from Plan

None. Plan 87-07 executed exactly as written:

- Task 1 (restore): 8 files restored from `60f5f915^`, committed. `.gitkeep` + `78-DISCUSSION-LOG.md` correctly excluded per CONTEXT D-02. No `78-RESEARCH.md` to restore (Phase 78 was reactive surgical per 87-RESEARCH.md §"Phase 78" — never had one).
- Task 2 (audit): Orchestrator-driven inline audit (matching the pattern used in 87-01..87-06), no subagent dispatch needed. Branch + scope discipline preserved. PLAT-CI-01 → PARTIAL → DockerfilePinGuardTest. PLAT-CI-02 → Manual-Only retained.
- Task 3 (gap-fill): 1 test file / 2 scenarios added (conservative end of the predicted 1-2 range). No impl bugs surfaced.
- Task 3b (checkpoint): Skipped per `<skip-condition>` — no non-trivial impl bug surfaced.
- Task 4 (VALIDATION creation): Fresh `78-VALIDATION.md` generated from template with all acceptance criteria met (`status: approved`, `nyquist_compliant: true`, `audit_method: retroactive`, 6+ Sign-Off boxes `[x]`, no `❌ W0` placeholders).

## Self-Check: PASSED

- ✅ `.planning/milestones/v1.10-phases/78-docker-release-image-fix/78-VALIDATION.md` exists with `status: approved`, `nyquist_compliant: true`, `wave_0_complete: true`, `approved_on: 2026-05-18`, `audit_method: retroactive`
- ✅ 9 files present in the restored archive (3 PLAN + 3 SUMMARY + CONTEXT + VERIFICATION + new VALIDATION); no `78-RESEARCH.md` (correctly absent per State B + Phase 78 reactive-surgical framing)
- ✅ `src/test/java/org/ctc/DockerfilePinGuardTest.java` exists (2 tests, BUILD SUCCESS in 0.080 s — `./mvnw test -Dtest='DockerfilePinGuardTest'`)
- ✅ 3 commits on `gsd/v1.11-tooling-and-cleanup` (`1111b9d6`, `3c74fccb`, `743ac447`)
- ✅ Zero `@DirtiesContext` annotations on the new test file
- ✅ No `❌ W0` markers remaining in VALIDATION.md (Per-Task Verification Map fully filled — `grep -c '❌ W0' = 0`)
- ✅ 10 Sign-Off + Wave-0 boxes `[x]` (≥ 6 required)
- ✅ Branch unchanged (`gsd/v1.11-tooling-and-cleanup`)
- ✅ Zero edits to production files: `git diff --name-only 99a0f153..HEAD -- Dockerfile .github/workflows/ci.yml` returns empty (no production-file violation)
- ✅ Phase-87 progress: 7 of 8 v1.10 phases now `status: approved` (71, 72, 73, 74, 75, 76, 78); only Phase 79 remaining for Plan 87-08
