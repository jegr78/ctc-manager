---
phase: 84-renovate-integration
status: in-progress
started: 2026-05-17
last_updated: 2026-05-17
waves_completed: [1, 2]
waves_pending: [3, 4]
requirements_satisfied: [DEPS-01, DEPS-02, DEPS-03, DEPS-04, DEPS-05, DEPS-06, DEPS-07]
requirements_pending: [DEPS-08]
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

## Wave 3 — Synthetic Dockerfile-bump PR (DEPS-08 / SC#6) ⏭ pending

Synthetic throwaway PR against `master` bumping `eclipse-temurin:25-jdk-noble` to a real published Adoptium tag like `25.0.1_8-jdk-noble` (preserving `-noble` suffix per the corrected D-19 regex). Goal: prove `dockerfile-noble-pin-guard` CI job accepts a real Renovate-style Dockerfile bump.

See [84-03-PLAN.md](84-03-PLAN.md) for execution detail.

---

## Wave 4 — Phase-End Closeout ⏭ pending

Final `./mvnw verify -Pe2e` gate; VERIFICATION.md `status: approved`; STATE.md + ROADMAP.md + REQUIREMENTS.md finalised; phase milestone bookkeeping.

See [84-04-PLAN.md](84-04-PLAN.md) for execution detail.

**Wave-4 prerequisite follow-up:** Address the T-2 branch-protection gap on master (see Wave 2 Task 4 above) before the v1.11 release PR merges, to maintain the documented T-2 mitigation.

---

## Threat-Model Closeout Status (cumulative)

| Threat | Mitigation surface | Wave delivering | Status |
|---|---|---|---|
| T-1 third-party SaaS trust | Mend single-repo install scope; explicit Renovate Only product tier (no commercial features granted) | Wave 2 | ✅ closed |
| T-2 silent code entry via automerge | `automergeType: "pr"` (in `renovate.json`); branch protection required status checks (master) | Waves 1+2 | ⚠ partial — `automergeType: "pr"` ✓, branch-protection status-checks pending (Wave 4 follow-up) |
| T-3 Dockerfile `-noble` bypass | Corrected eclipse-temurin regex in `renovate.json` (prevention) + existing `dockerfile-noble-pin-guard` CI job (detection) | Waves 1 + 3 | partial — prevention ✓ (Wave 1), detection-test pending (Wave 3) |
| T-4 Thymeleaf CVE bypass | Primary `enabled: false` packageRule + secondary vulnerability-override rule | Wave 1 | ✅ closed |
| T-5 dual-bot duplicate PRs | `.github/dependabot.yml` removed in same atomic commit as `renovate.json` introduction (D-03) | Wave 1 | ✅ closed |
