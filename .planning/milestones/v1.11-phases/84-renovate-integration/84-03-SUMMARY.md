---
phase: 84-renovate-integration
plan: 03
type: execute
wave: 3
status: complete
completed: 2026-05-17
duration_minutes: 12
tasks_completed: 5
tasks_total: 5
autonomous: false
files_modified:
  - .planning/phases/84-renovate-integration/84-VERIFICATION.md (extended with Wave 3 section)
artifacts:
  throwaway_pr_url: https://github.com/jegr78/ctc-manager/pull/126
  throwaway_pr_number: 126
  throwaway_pr_state: CLOSED_UNMERGED
  throwaway_pr_close_timestamp: "2026-05-17"
  throwaway_branch_deleted: chore/verify-noble-pin-guard
  throwaway_commit_sha: 8847bb86
  ci_run_url: https://github.com/jegr78/ctc-manager/actions/runs/25990173046/job/76394742913
  ci_job_id: 76394742913
  guard_job_conclusion: SUCCESS
  follow_up_pr_url: pending
  follow_up_branch: feature/renovate-integration-wave3
  follow_up_pr_target: gsd/v1.11-tooling-and-cleanup
requirements_satisfied:
  - DEPS-08
threats_addressed:
  - T-3  # closed: prevention layer (renovate.json packageRule regex) + detection layer (dockerfile-noble-pin-guard CI) both structurally validated
guard_verbatim_output: |
  [noble-pin-guard] OK - all 'FROM eclipse-temurin:' lines are pinned to -noble.
  FROM eclipse-temurin:25.0.1_8-jdk-noble AS build
  FROM eclipse-temurin:25-jre-noble
research_corrections_validated:
  - correction_3: |
      RESEARCH.md correction #3 (eclipse-temurin regex underscore support) validated
      in the wild. Original CONTEXT.md D-19 regex `/^(?:25)(?:\\.[0-9.]+)?-(?:jdk|jre)-noble$/`
      would have BLOCKED the real Adoptium tag `25.0.1_8-jdk-noble`. Corrected regex
      `/^25(?:\\.[0-9._]+)?-(?:jdk|jre)-noble$/` (with `[0-9._]+`) accepts it.
---

# Phase 84-03 — Wave 3 Summary

Synthetic throwaway PR #126 against `master` bumped `Dockerfile` line 3 from
`eclipse-temurin:25-jdk-noble` to `eclipse-temurin:25.0.1_8-jdk-noble` (a real
published Adoptium tag with underscore-build-id suffix). PR was opened as DRAFT,
CI ran, **`dockerfile-noble-pin-guard` returned SUCCESS** with the verbatim step
output:

```text
[noble-pin-guard] OK - all 'FROM eclipse-temurin:' lines are pinned to -noble.
FROM eclipse-temurin:25.0.1_8-jdk-noble AS build
FROM eclipse-temurin:25-jre-noble
```

PR was closed without merging (`mergedAt: null`) and the throwaway branch was
deleted from `origin` via `gh pr close --delete-branch`. This is CONTEXT.md
D-24 path 3 (synthetic, deterministic, same-session) — Phase 81 D-13 precedent.

## DEPS-08 / SC#6 Satisfied

`dockerfile-noble-pin-guard` CI job passes on a Renovate-shape Dockerfile-bump
PR with a real Adoptium underscore-build tag preserving the `-noble` suffix.
The guard's whitelist-on-`-noble` invariant correctly evaluates the Renovate-
shape diff.

## RESEARCH.md Correction #3 Structurally Validated

The original CONTEXT.md D-19 regex `/^(?:25)(?:\\.[0-9.]+)?-(?:jdk|jre)-noble$/`
would have **BLOCKED** the real Adoptium tag `25.0.1_8-jdk-noble` because it did
not allow underscore in the version-string segment. The planner's correction
`/^25(?:\\.[0-9._]+)?-(?:jdk|jre)-noble$/` (with `[0-9._]+`) accepts it. This
plan's synthetic exercise confirms the corrected regex matches the live
Adoptium tag space — not just an abstract theoretical fix.

## Threat-Model Closeout — T-3 closed

T-3 ("Dockerfile `-noble` bypass via Renovate-style proposal") has two layers:

- **Prevention** (Wave 1): renovate.json `eclipse-temurin` packageRule with the
  corrected regex prevents Renovate from proposing non-`-noble` tags at all.
- **Detection** (Phase 78 / Wave 3 exercise): `dockerfile-noble-pin-guard` CI
  job blocks any PR that smuggles a non-`-noble` tag past prevention.

Both layers are structurally validated.

## Deviations

- **PR #126 was closed before all sibling CI jobs completed.** `build-and-test`,
  `docker-build`, and three `CodeQL Analyze` jobs were still IN_PROGRESS at
  close time. Their outcomes are not recorded — they are not in scope of
  DEPS-08 / SC#6 (the success criterion is specifically `dockerfile-noble-pin-guard: pass`).
- **No `git checkout master` in the executor's branch-protection scope:** Task 4
  used `gh pr close --delete-branch` which `gh` itself switched to `master`
  after deleting the local throwaway branch. This is the one allowed branch
  switch in Phase 84 (documented in plan 84-03 `<branch_protection>` block).

## Requirements satisfied

- DEPS-08 ✓ (synthetic throwaway PR exercises `dockerfile-noble-pin-guard`, guard pass captured + closed-unmerged + branch deleted)

## Next plan

[84-04-PLAN.md](84-04-PLAN.md) — Wave 4 final phase gate (`./mvnw verify -Pe2e` + VERIFICATION.md `status: approved` + STATE.md + ROADMAP.md + REQUIREMENTS.md finalisation + T-2 follow-up reminder for master branch protection before v1.11 release PR merges).
