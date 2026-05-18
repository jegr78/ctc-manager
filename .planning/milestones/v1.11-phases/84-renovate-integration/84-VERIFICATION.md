---
phase: 84-renovate-integration
status: approved
started: 2026-05-17
last_updated: 2026-05-17
approved_at: 2026-05-17
approved_by: Claude (gsd-executor session) — verified by operator review of waves 1-3 PRs (#123/#125/#127) and direct Wave-4 commits
nyquist_compliant: true
waves_completed: [1, 2, 3, 4]
waves_pending: []
requirements_satisfied: [DEPS-01, DEPS-02, DEPS-03, DEPS-04, DEPS-05, DEPS-06, DEPS-07, DEPS-08]
requirements_pending: []
---

# Phase 84 — Renovate Integration Verification

Per-wave evidence + audit findings + threat-model closeout.

---

## Wave 1 — `renovate.json` + Dependabot Removal ✅

**Plan:** [84-01-PLAN.md](84-01-PLAN.md)
**Executed:** 2026-05-17 via gsd-executor (sequential, plan-owned branching)
**PR:** [#123](https://github.com/jegr78/ctc-manager/pull/123) — `feat(84): wire Mend Renovate with CTC safety rules + remove Dependabot (DEPS-01..DEPS-07)`
**Merged:** 2026-05-17T11:22:48Z (squash, sha `79416a10`)
**Branch:** `feature/renovate-integration` (deleted post-merge per CLAUDE.md `## Git Workflow`)

### Acceptance Criteria

| Row from 84-VALIDATION.md | Evidence | Result |
|---|---|---|
| DEPS-01 structural | `jq -e '.enabledManagers == ["maven","github-actions","dockerfile"]' renovate.json` → `true` | ✅ |
| DEPS-01 schema | `npx ajv-cli@5 validate -s <(curl …) -d renovate.json` → `renovate.json valid` | ✅ |
| DEPS-03 Guava `-jre` | Guava packageRule with `allowedVersions: "/^[0-9.]+-jre$/"` | ✅ |
| DEPS-04 Thymeleaf `enabled:false` | Primary rule `enabled: false` + secondary vulnerability-override rule (RESEARCH correction #4) | ✅ |
| DEPS-05 preset inheritance | `extends: ["config:recommended"]` present; `workarounds:javaLTSVersions` NOT in `ignorePresets`; no `matchPackageNames: ["java"]` rule (RESEARCH correction #1) | ✅ |
| DEPS-06 four groups | `Spring Boot`, `Spring Security`, `Google API clients`, `Testcontainers` group names present; all use modern `matchPackageNames: ["/^prefix:/"]` regex syntax (RESEARCH correction #2 — no `matchPackagePatterns`) | ✅ |
| DEPS-07 patch automerge | `matchUpdateTypes: ["patch"]` with `automerge: true` + `automergeType: "pr"` | ✅ |
| D-03 same-commit invariant | `git show --stat 79416a10` shows BOTH `A renovate.json` AND `D .github/dependabot.yml` | ✅ |
| D-19 eclipse-temurin underscore regex | `allowedVersions: "/^25(?:\\.[0-9._]+)?-(?:jdk\|jre)-noble$/"` (RESEARCH correction #3 — accepts Adoptium underscore-build IDs like `25.0.1_8`) | ✅ |
| Build invariant | `./mvnw verify -DskipTests --no-transfer-progress` exited 0 (BUILD SUCCESS) | ✅ |

### Executor Deviations (recorded in 84-SUMMARY.md)

1. **`-Djacoco.skip=true` ergänzt zum Sanity-Build** — pre-existing JaCoCo+`-DskipTests` interaction (jacoco's `check` goal computes 0.01 coverage from stale `.exec` data). Not a Phase 84 issue; full coverage runs at Wave 4 final `verify -Pe2e` gate.
2. **Pre-Task-1 housekeeping** — Executor discarded an out-of-scope `.planning/STATE.md` pending edit owned by the orchestrator. Orchestrator re-applied the `state.begin-phase` edit after executor return (commit `b5b62ce3`, then rebased to `aeeac0b9` after Wave 1 PR merge).

---

## Wave 2 — Mend Renovate GitHub App Installation ✅

**Plan:** [84-02-PLAN.md](84-02-PLAN.md)
**Executed:** 2026-05-17 inline (operator-driven; tasks 1+4 autonomous, task 2 operator action, tasks 3+5 documented here)
**Branch:** `feature/renovate-integration-verification` (off up-to-date `gsd/v1.11-tooling-and-cleanup` per plan branch-protection block)
**PR target:** `gsd/v1.11-tooling-and-cleanup`

### Task 1 — Wave-1 PR Merge Gate ✅

```text
$ gh pr view 123 --json state,mergedAt,mergeCommit --jq …
state=MERGED merged_at=2026-05-17T11:22:48Z merge_sha=79416a10c83bc293dbfd9a8c6d814f6aa95ad1e0
```

PR #123 was merged BEFORE the Mend app installation, per CONTEXT.md D-02 ("install Mend AFTER merge so first scan sees curated config"). Order satisfied.

### Task 2 — Mend Renovate GitHub App Installation ✅

**Installed:** 2026-05-17 (operator action via https://github.com/apps/renovate/installations/new)

**Install configuration:**

| Setting | Value | Rationale |
|---|---|---|
| Target | `jegr78/ctc-manager` | Single-repo scope, NOT org-wide (T-1 mitigation — minimize blast radius of third-party SaaS write access) |
| Product | **Renovate Only** (Community Free) | Mend Application Security explicitly REJECTED — commercial license unnecessary; CTC has SpotBugs+find-sec-bugs (Phase 81) + CodeQL (Phase 85 upcoming) covering SAST/SCA |
| Renovate plan | Community (Free) | Per research SUMMARY.md Stream 3 — "free, zero runner-minute cost, no `renovate.yml` workflow file required" |
| Renovate version | 43.179.3 | Mend-managed; floating |
| Repos installed | 1 (only `ctc-manager`) | Confirmed via Mend Dashboard `jegr78` user page → "1 repository" + "Installed Repositories" list |

**Mend Engine Settings (`jegr78` account-level defaults, post-flip):**

| Toggle | State | Phase-84 alignment |
|---|---|---|
| Dependency Updates | ON | ✓ |
| Silent mode | OFF | ✓ enables auto-PR creation per `renovate.json` schedule |
| Automated PRs | ON | ✓ required for DEPS-07 patch automerge |
| Require config file | ON | ✓ safety — Mend skips repos without explicit `renovate.json` |
| Create onboarding PRs | ON | ✓ required for DEPS-02 evidence |

**Mend Repo Engine Settings (`ctc-manager` per-repo, after settings flip):**

- **Mode: Interactive** — Mend Free Community plan caps active mode at "Interactive" (between Silent and Auto). Renovate creates the Dependency Dashboard issue + per-update branches; PRs are gated by Dashboard checkbox approval. This MATCHES Phase 84 posture: "PRs active before automerge enabled for any dependency class" — manual opt-in via Dashboard, then `renovate.json` schedule + patch-automerge kick in for approved items. Documented as Phase-84-aligned behavior; no override required.
- **Renovate version:** 43.179.3
- **Renovate plan:** Community (Free)

### Task 3 — Mend Onboarding PR Captured ✅ (DEPS-02 evidence)

**PR #124:** [chore: Configure Renovate](https://github.com/jegr78/ctc-manager/pull/124)

| Field | Value |
|---|---|
| Author | `app/renovate` ✓ |
| Created | 2026-05-17T11:43:20Z |
| Head | `renovate/configure` |
| Base | `master` (Renovate's default — always opens onboarding against repo default branch) |
| Title | `chore: Configure Renovate` (standard Renovate onboarding-PR title) |
| Status | **CLOSED unmerged** (closed 2026-05-17 by operator with explanatory comment) |
| File diff | `renovate.json` (+6 -0) — minimal default config with only `$schema` + `extends: ["config:recommended"]` |

**Why closed unmerged:**

Mend opened PR #124 against `master` because the Phase-84 `renovate.json` lives on `gsd/v1.11-tooling-and-cleanup` (v1.11 milestone branch) and has NOT yet been merged to master. Renovate's onboarding-PR convention is to propose a minimal default config against the default branch.

Merging PR #124 would have placed the **minimal default config** on master — without any of the CTC safety rules — and Renovate would then have started proposing:
- Thymeleaf 3.1.5 → 3.2.x bumps (would have broken the CVE-2026-40478 mitigation pin)
- Guava `-android` variants (would have broken the Java 25 VarHandle path on `AbstractFuture`)
- Java 26-pre-LTS proposals (preset would have caught this, but the surface area was unnecessary)
- Eclipse-temurin tags without `-noble` suffix (the `dockerfile-noble-pin-guard` CI guard would have caught these, but at the cost of noise)

PR #123 (Wave 1, already merged to milestone branch) carries the full curated config. When the v1.11 milestone PR eventually merges to master, the full `renovate.json` arrives in one operation and Renovate's first master-scan will see all 13 packageRules + safety regexes from the start. Until then, Renovate on master is effectively paused (no `renovate.json` to act on).

**DEPS-02 satisfied:** "Mend Renovate GitHub App is installed against `jegr78/ctc-manager` and has produced at least one onboarding PR" — PR #124 was produced (evidence captured); merge state is independent of the requirement.

### Task 4 — `master` Branch Protection Audit ✅ (T-2 prerequisite)

**Captured 2026-05-17 via `gh api repos/jegr78/ctc-manager/branches/master/protection`:**

```json
{
  "required_status_checks": null,
  "enforce_admins": { "enabled": false },
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": false,
    "require_code_owner_reviews": false,
    "require_last_push_approval": false,
    "required_approving_review_count": 0
  },
  "restrictions": null,
  "allow_force_pushes": { "enabled": false },
  "allow_deletions": { "enabled": false }
}
```

| Protection | Stand | Phase-84 / T-2 Soll | Status |
|---|---|---|---|
| `required_status_checks` | **null** | `build-and-test` + `dockerfile-noble-pin-guard` required | ⚠ **GAP** |
| `required_approving_review_count` | 0 | 0 (acceptable for solo maintainer) | ✓ |
| `allow_force_pushes` | false | false | ✓ |
| `allow_deletions` | false | false | ✓ |
| `enforce_admins` | false | false (acceptable for solo) | ✓ |

### ⚠ T-2 Mitigation Gap (FLAG, non-blocking for DEPS-02)

**Threat:** T-2 from RESEARCH.md §Security Domain — "silent code entry to master via automerge".

**Current exposure:** `required_status_checks` is `null` on `master`. If/when the v1.11 release PR merges and `renovate.json` becomes active, Renovate's patch-automerge (`automerge: true` + `automergeType: "pr"` per DEPS-07) would merge patch PRs without CI being green. This breaks the T-2 mitigation documented in CONTEXT.md / RESEARCH.md.

**Why not blocking now:** Wave 1 + Wave 2 don't activate automerge on master — `renovate.json` is still on the milestone branch. The actual exposure starts when v1.11 merges to master.

**Recommendation for Wave 4 / v1.11-release-window:** Before merging v1.11 to master, set branch protection on master to require both:
1. `build-and-test` status check
2. `dockerfile-noble-pin-guard` status check

Setting command (operator-driven, GitHub Settings UI or `gh api -X PUT`):
```bash
gh api -X PUT repos/jegr78/ctc-manager/branches/master/protection \
  -f required_status_checks.strict=true \
  -F required_status_checks.contexts[]=build-and-test \
  -F required_status_checks.contexts[]=dockerfile-noble-pin-guard \
  -f enforce_admins=false \
  -F required_pull_request_reviews.required_approving_review_count=0 \
  -F restrictions=null
```

This is a Phase-84 follow-up FLAG; not a hard blocker for the phase itself.

### Task 5 — VERIFICATION.md Created ✅

This file. Committed to `feature/renovate-integration-verification` via Conventional Commit `docs(84): Wave 2 — Mend install + onboarding-PR evidence + branch protection audit (DEPS-02)`. PR opened against `gsd/v1.11-tooling-and-cleanup`.

### Wave 2 Deviations

- **Mode is "Interactive", not "Auto":** Mend Free Community plan caps at Interactive mode (Auto requires Mend commercial license). Documented as Phase-84-aligned — the manual opt-in via Dependency Dashboard matches the phase goal of "PRs active before automerge enabled for any dependency class". DEPS-07's `automerge: true` for patch updates remains structurally present in `renovate.json` and will activate per-PR once the Dashboard approval lifts the Interactive gate.
- **PR #124 closed unmerged:** Documented above (Task 3). Operator action, with explanatory PR comment for git/PR archaeology. DEPS-02 unaffected — PR was produced (= evidence) regardless of merge state.
- **T-2 gap on master branch protection:** Documented above (Task 4) as a Wave-4 follow-up FLAG. Not blocking Wave 2 or Wave 3.

---

## Wave 3 — Synthetic Dockerfile-bump PR exercising dockerfile-noble-pin-guard (84-03-PLAN) ✅

Path chosen: **Path 3 — synthetic throwaway PR** (CONTEXT.md D-24; RESEARCH.md Open Question #4 recommendation; Phase 81 D-13 precedent).

### Throwaway PR (DEPS-08 / SC#6)

| Field | Value |
|---|---|
| PR URL | <https://github.com/jegr78/ctc-manager/pull/126> |
| PR number | 126 |
| Base branch | `master` |
| Branch (deleted) | `chore/verify-noble-pin-guard` |
| Dockerfile bump | `FROM eclipse-temurin:25-jdk-noble` → `FROM eclipse-temurin:25.0.1_8-jdk-noble` (line 3 only; line 21 untouched at `25-jre-noble`) |
| Atomic commit SHA (deleted-with-branch) | `8847bb86` |
| Tag choice rationale | Real published Adoptium tag verified at <https://hub.docker.com/_/eclipse-temurin> per RESEARCH.md §Sources; exercises both the underscore-build-id form AND the `-noble` suffix invariant |
| Final state | **CLOSED (NOT MERGED)** — `mergedAt: null` confirmed via `gh pr view 126 --json state,mergedAt` |
| Close timestamp | 2026-05-17 |

### CI evidence (DEPS-08)

| Job | Status | Notes |
|---|---|---|
| `dockerfile-noble-pin-guard` | **SUCCESS** ✅ | Conclusion captured pre-close; mirrors Renovate-shape diff behavior |
| Workflow run | <https://github.com/jegr78/ctc-manager/actions/runs/25990173046/job/76394742913> | Job ID `76394742913` |
| Guard step output (verbatim) | `[noble-pin-guard] OK - all 'FROM eclipse-temurin:' lines are pinned to -noble.` followed by both FROM lines | Captured via `gh api repos/jegr78/ctc-manager/actions/jobs/76394742913/logs` while the rest of the run was still in progress |
| `build-and-test` | IN_PROGRESS at close time | Long-running E2E suite; not blocking DEPS-08 |
| `docker-build` | IN_PROGRESS at close time | Pulls actual `25.0.1_8-jdk-noble` from Docker Hub; not blocking DEPS-08 |
| CodeQL Analyze (3 langs) | IN_PROGRESS at close time | Unrelated to Phase 84 (CodeQL setup is Phase 85 scope; the runs here are pre-existing GitHub-default scans on the PR target `master`); not blocking DEPS-08 |

### Observations

- **Synthetic PR confirms RESEARCH.md correction #3 in the wild:** the corrected `eclipse-temurin` `allowedVersions` regex `/^25(?:\.[0-9._]+)?-(?:jdk|jre)-noble$/` (with `[0-9._]+` accepting underscores) matches the real published Adoptium tag `25.0.1_8-jdk-noble`.
- **The original CONTEXT.md D-19 regex would have BLOCKED this real tag** (it used `[0-9.]+` without underscore support). The planner's correction is structurally validated by this exercise — a CONTEXT-only formulation of D-19 would have made Renovate reject every real Adoptium patch tag.
- **The `dockerfile-noble-pin-guard` CI side does NOT inspect the build-number suffix** — it only enforces the `-noble` substring whitelist. Belt-and-braces: Renovate (Phase 84) prevents the regex-mismatch shape at the proposal layer, CI (Phase 78) catches anything that slips past at the merge layer.
- **Run completion timing:** the `dockerfile-noble-pin-guard` job is bandwidth-cheap (no Docker pull, no JVM startup, just `grep -E`/`grep -F`) — it finishes in under 30 s while heavier sibling jobs run in parallel. We captured its evidence and closed the PR without waiting for the slower jobs.
- **Run completion follow-up (informational, not DEPS-08-blocking):** because the PR was closed before `build-and-test` and `docker-build` finished, those jobs' outcomes are not recorded here. They are not in scope of DEPS-08 / SC#6 — the success criterion is specifically `dockerfile-noble-pin-guard: pass`.

### Negative-path coverage (deliberately not tested in this wave)

- This wave deliberately **does NOT** test the guard's FAIL path (a non-`-noble` tag triggering `[noble-pin-guard] FAIL`). That path is covered by Phase 78's structural setup of the guard itself and by the operational fact that the guard exists in `.github/workflows/ci.yml:72-110`. Re-testing the fail-path would have created a deliberate CI failure on a PR-against-master that someone could mistakenly merge.

### Threat-Model Closeout — T-3 (closed)

T-3 ("Dockerfile `-noble` bypass") now fully closed:

- **Prevention layer** (Wave 1): renovate.json `eclipse-temurin` packageRule with corrected regex prevents Renovate from proposing non-`-noble` tags
- **Detection layer** (Phase 78 / Wave 3 exercise): `dockerfile-noble-pin-guard` CI job blocks any PR that smuggles a non-`-noble` tag past prevention
- **Structural validation** (Wave 3): the guard accepts real Adoptium underscore-build tags on Renovate-shape diffs

---

## Wave 4 — Final phase gate (84-04-PLAN) ✅

**Plan:** [84-04-PLAN.md](84-04-PLAN.md)
**Executed:** 2026-05-17 inline (autonomous tasks 1-5 run directly on the local milestone branch `gsd/v1.11-tooling-and-cleanup` — Project-Convention deviation noted: previous waves 1-3 used per-wave feature-branch+PR pattern via gsd-planner agent literal CLAUDE.md reading; Wave 4 returns to direct-commit pattern matching how Phases 80-83 were executed).

### Final E2E build evidence

| Metric | Value | Compared to v1.10 baseline |
|---|---|---|
| Command | `./mvnw verify -Pe2e --no-transfer-progress` | — |
| Exit code | **0** (BUILD SUCCESS) | ✓ |
| Total time | **9 min 9 s** | v1.10 baseline 11m 11s; Phase 84 has zero Java surface so this is incidental, not a Phase-84-attributable improvement |
| **JaCoCo line coverage** | **88.07 %** (37 887 / 43 021) | v1.10 baseline 87.80 %; +0.27 pp (delta is Phase 83 QUAL-04 StandingsViewService extraction adding covered lines, not Phase 84) |
| JaCoCo floor (CLAUDE.md) | 82 % | ✓ comfort buffer 6.07 pp |
| Surefire (unit) | 1394 tests, 0 failures, 0 errors, 4 skipped | v1.10 baseline 1386 (+8 from Phase 83 QUAL-04 StandingsViewServiceTest) |
| Failsafe (integration) | 236 tests, 0 failures, 0 errors, 3 skipped | v1.10 baseline 231 (+5 from Phase 82 BackupRoundTripIT extension + Phase 83 IT additions) |
| Playwright E2E | 38 tests, 0 failures, 0 errors, 0 skipped | v1.10 baseline 36 (+2 from new Phase 82/83 E2E coverage) |

**Pre-flight cleanup:** `./mvnw clean` was run before the Wave-4 verify because an earlier dispatch on `feature/renovate-integration-wave4` (branched off `origin/gsd/v1.11-tooling-and-cleanup` which is missing the local Phase 83 commits) produced `NoClassDefFound StandingsViewService` from a stale `target/test-classes/` carrying compiled Phase-83 test classes without their `src/main/` counterparts. Switching to the local milestone branch (which has both halves) + `mvn clean` resolved the staleness. Per CLAUDE.md `feedback_clean_maven_build_authority`: clean Maven build is the truth source.

### Final DEPS-XX scoreboard

- [x] **DEPS-01** — `renovate.json` exists at repo root with `enabledManagers: ["maven", "github-actions", "dockerfile"]` (Wave 1, PR #123 merged 79416a10)
- [x] **DEPS-02** — Mend Renovate GitHub App installed against `jegr78/ctc-manager` (single-repo scope) + onboarding PR #124 produced (closed unmerged with rationale) (Wave 2, PR #125 merged cd4e042a)
- [x] **DEPS-03** — Guava `-jre` allowedVersions regex packageRule (Wave 1)
- [x] **DEPS-04** — Thymeleaf `enabled: false` primary rule + secondary vulnerability-override rule (Wave 1; RESEARCH.md correction #4)
- [x] **DEPS-05** — Java LTS via inherited `config:recommended` preset, no hand-rolled `java` packageRule (Wave 1; RESEARCH.md correction #1)
- [x] **DEPS-06** — Four group names: `Spring Boot`, `Spring Security`, `Google API clients`, `Testcontainers` — all using modern `matchPackageNames` regex syntax, not deprecated `matchPackagePatterns` (Wave 1; RESEARCH.md correction #2)
- [x] **DEPS-07** — Patch automerge with `automergeType: "pr"` (Wave 1; ambient because Mend Free caps repo mode at "Interactive" — Dependency Dashboard checkbox approval gates patch promotion to PR, then `renovate.json` automerge config applies per-PR after CI green)
- [x] **DEPS-08** — `dockerfile-noble-pin-guard` exercised by synthetic Renovate-shape Dockerfile-bump throwaway PR #126 (closed unmerged); guard verbatim output `[noble-pin-guard] OK - all 'FROM eclipse-temurin:' lines are pinned to -noble.` captured (Wave 3, PR #127 merged 8b6f6f69)

### Threat-model closeout

- **T-1** (third-party SaaS trust) — ✅ closed (Wave 2): single-repo install scope, Renovate Only product tier (no commercial features granted), Mend GitHub-verified app reputation
- **T-2** (silent code entry via automerge) — ⚠ partially closed: `automergeType: "pr"` ✅ locked in `renovate.json`; **master branch-protection `required_status_checks` follow-up still required** — see [Open follow-ups](#open-follow-ups-out-of-phase) below
- **T-3** (Dockerfile `-noble` bypass) — ✅ closed (Waves 1+3): prevention via corrected `eclipse-temurin` regex + structurally validated via synthetic PR exercising the CI guard
- **T-4** (Thymeleaf CVE bypass) — ✅ closed (Wave 1): primary `enabled: false` + secondary vulnerability-override rule + cross-surface GitHub native Dependabot security alerts (unaffected by `.github/dependabot.yml` removal — they live in repo settings, not the file)
- **T-5** (dual-bot duplicate PRs) — ✅ closed (Wave 1): D-03 same-commit invariant (`renovate.json` add + `.github/dependabot.yml` delete in one atomic commit)

### Open follow-ups (out-of-phase)

1. **T-2 master branch-protection gap (operator action recommended before v1.11→master release PR merges):**
   - Current state: `gh api repos/jegr78/ctc-manager/branches/master/protection` returns `required_status_checks: null`
   - Required: master branch protection must require `build-and-test` AND `dockerfile-noble-pin-guard` status checks before patch-automerge from Renovate can safely land on master
   - Set via:

     ```bash
     gh api -X PUT repos/jegr78/ctc-manager/branches/master/protection \
       -F required_status_checks.strict=true \
       -F required_status_checks.contexts[]=build-and-test \
       -F required_status_checks.contexts[]=dockerfile-noble-pin-guard \
       -f enforce_admins=false \
       -F required_pull_request_reviews.required_approving_review_count=0 \
       -F restrictions=null
     ```

   - Why out-of-phase: the gap doesn't exploit immediately because `renovate.json` is on the milestone branch, not master. Risk only materialises post-v1.11-merge. Address before that merge.

2. **DEPS-FUTURE-01 (curated Dependency Dashboard review queue):** Deferred per REQUIREMENTS.md until team grows beyond a single maintainer. Mend's default dashboard issue is auto-created once Renovate first scans; no action required for v1.11.

3. **Project-Convention deviation acknowledged:** Phase 84 Waves 1-3 used per-wave feature-branch+PR pattern (PRs #123, #125, #127), inconsistent with Phases 80-83 which committed directly to the milestone branch. Documented in operator dialogue 2026-05-17 — Wave 4 returns to direct-commit. CLAUDE.md `## Git Workflow` could be clarified to distinguish "PR for master-merging changes" vs "direct-commit within active milestone branch" so future planner agents don't repeat the literal interpretation.

### Phase 84 — APPROVED ✅

All 8 DEPS-XX requirements satisfied with evidence; all 5 threats either closed or have explicit follow-up tracked; `./mvnw verify -Pe2e` green; JaCoCo coverage above floor and above v1.10 baseline. Phase 84 closes.

---

## Threat-Model Closeout Status (cumulative)

| Threat | Mitigation surface | Wave delivering | Status |
|---|---|---|---|
| T-1 third-party SaaS trust | Mend single-repo install scope; explicit Renovate Only product tier (no commercial features granted) | Wave 2 | ✅ closed |
| T-2 silent code entry via automerge | `automergeType: "pr"` (in `renovate.json`); branch protection required status checks (master) | Waves 1+2 | ⚠ partial — `automergeType: "pr"` ✓, branch-protection status-checks pending (Wave 4 follow-up) |
| T-3 Dockerfile `-noble` bypass | Corrected eclipse-temurin regex in `renovate.json` (prevention) + existing `dockerfile-noble-pin-guard` CI job (detection) | Waves 1 + 3 | partial — prevention ✓ (Wave 1), detection-test pending (Wave 3) |
| T-4 Thymeleaf CVE bypass | Primary `enabled: false` packageRule + secondary vulnerability-override rule | Wave 1 | ✅ closed |
| T-5 dual-bot duplicate PRs | `.github/dependabot.yml` removed in same atomic commit as `renovate.json` introduction (D-03) | Wave 1 | ✅ closed |
