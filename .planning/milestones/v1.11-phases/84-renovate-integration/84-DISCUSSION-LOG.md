# Phase 84: Renovate Integration - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-17
**Phase:** 84-renovate-integration
**Mode:** `--chain` invocation under "no clarifying questions" operator directive → all gray areas decided autonomously by Claude based on prior phases, research SUMMARY.md Stream 3, PITFALLS.md pitfalls 4-6, and codebase inspection. No AskUserQuestion interactions occurred. Decisions surfaced here for retrospective review and possible redirect.

**Areas decided autonomously:** Bot Selection, Onboarding Order, Dependabot Disposition, Configuration File Format, Schedule + Concurrency, Automerge Scope, Package Grouping Strategy, Manual-Review Gates, Dependency Dashboard, Verification Strategy.

---

## Bot Selection: Mend Hosted vs Self-Hosted Action

| Option | Description | Selected |
|--------|-------------|----------|
| Mend Renovate GitHub App (hosted) | Free, zero runner-minute cost, self-updating, no workflow file required | ✓ |
| Self-hosted via `renovatebot/github-action` | Pinned renovate version, runs on cron via GitHub Actions runner minutes | |
| Both (parallel) | Redundancy at the cost of complexity | |

**Decision:** Mend hosted app (CONTEXT.md D-01).
**Notes:** Research SUMMARY.md Stream 3 locks this. Self-hosted would require ongoing version-pin maintenance with no benefit on a public repo where Mend is free. CTC has a single maintainer → simplest path wins.

---

## Existing Dependabot Disposition

| Option | Description | Selected |
|--------|-------------|----------|
| Remove `.github/dependabot.yml` in same commit as `renovate.json` | Clean migration; never have two bots active simultaneously | ✓ |
| Disable Dependabot manager-by-manager as Renovate proves itself | Gradual migration with rollback safety | |
| Leave Dependabot in place and let Renovate's onboarding propose removal | Defer the decision to Mend's onboarding PR | |

**Decision:** Remove in same commit (CONTEXT.md D-03).
**Notes:** Research FEATURES.md Stream 3 anti-feature table calls out dual-bot duplicate-PR pitfall. Existing `.github/dependabot.yml` covers exactly the same three managers Renovate covers (`maven`, `github-actions`, `docker`) at the same `weekly Monday` cadence with `open-pull-requests-limit: 5` — Renovate is a complete superset, no loss from removal. Same-commit invariant pair (CONTEXT.md `<code_context>` Established Patterns) avoids intermediate broken states.

---

## Onboarding Order: Pre-commit Config vs Mend Onboarding PR

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-commit `renovate.json` before installing app | Curated config active from first scan; safety rules in place from PR #1 | ✓ |
| Install app first, let Mend generate onboarding PR with default config | Standard Mend onboarding flow; relies on operator to edit the onboarding PR before merge | |

**Decision:** Pre-commit before app install (CONTEXT.md D-02).
**Notes:** Mend's default onboarding config does NOT include the CTC-specific `packageRules` (Guava `-jre`, Thymeleaf disabled, Java LTS regex). If we install first, Mend's first scan would briefly run under default rules and could propose Guava `-android` or Thymeleaf 3.x.x bumps. Pre-commit guarantees DEPS-03..05 are active for the FIRST scan. DEPS-02 ("at least one onboarding PR") still satisfied because Mend produces a config-validation onboarding PR even when `renovate.json` exists.

---

## Configuration File Format & Location

| Option | Description | Selected |
|--------|-------------|----------|
| `renovate.json` at project root | Matches DEPS-01 ("root-level"); canonical Renovate convention | ✓ |
| `renovate.json5` at project root | Allows comments inside the config | |
| `.github/renovate.json` | Standard GitHub-adjacent location | |
| `.renovaterc` | Legacy location | |

**Decision:** `renovate.json` at root (CONTEXT.md D-04).
**Notes:** DEPS-01 literally says "root-level `renovate.json`". JSON5 (with comments) is not needed because the `description` field on every packageRule already carries rationale — that's the JSON-native way to document each rule.

---

## Schedule & Concurrency Controls

| Option | Description | Selected |
|--------|-------------|----------|
| `before 6am on Monday` + concurrent caps (5/2/10) | Off-hours weekly cadence matching existing Dependabot; solo-maintainer-friendly | ✓ |
| Daily schedule | Continuous trickle of PRs; high notification volume | |
| `at any time` (Renovate default) | Fastest update propagation; firehose risk | |
| Monthly | Too slow; CVE-driven bumps would be delayed (unless `vulnerabilityAlerts.enabled: true`) | |

**Decision:** Monday before 6am + `prConcurrentLimit: 5` + `prHourlyLimit: 2` + `branchConcurrentLimit: 10` (CONTEXT.md D-06, D-07).
**Notes:** Matches existing Dependabot Monday cadence (no mental-model shift for operator). 6am UTC = pre-business-hours in Europe. `vulnerabilityAlerts.enabled: true` overrides the schedule for CVE-driven PRs (CONTEXT.md D-08).

---

## Automerge Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Patch automerge (PR-typed) + manual review for minor/major | DEPS-07 explicit; matches "Out of Scope" carve-outs | ✓ |
| Fully manual (no automerge anywhere) | Most conservative; matches phase goal "before automerge is enabled" | |
| Patch + minor automerge | More aggressive; would conflict with `Out of Scope` "Renovate auto-merge for major" only by exclusion | |
| Branch-typed automerge (no PR object) | Less audit trail; research PITFALLS calls this an anti-feature | |

**Decision:** `patch` automerge with `automergeType: "pr"` (CONTEXT.md D-20).
**Notes:** DEPS-07 explicitly requires this. The phase-goal phrase "before automerge is enabled for any dependency class" is ordering within this phase (safety rules first, then automerge config) — not deferring to a later phase. Both safety rules and automerge config arrive in the same `renovate.json`. `automergeType: "pr"` preserves the PR object for audit/revert.

---

## Package Grouping Strategy (Beyond DEPS-06's Four Mandated Groups)

| Option | Description | Selected |
|--------|-------------|----------|
| Add OpenRewrite + SpotBugs + Playwright groups (Phase 80/81/78 follow-through) | Three new groups carrying manual-review gates for known migration-trigger or compatibility-sensitive coords | ✓ |
| Only DEPS-06's four groups (Spring Boot, Spring Security, Google API, Testcontainers) | Strict requirements-only interpretation | |
| Maximum grouping (one group per Maven groupId prefix) | Cleanest PR list; loses semantic separation (Spring Security CVE bundled with unrelated Boot bumps) | |

**Decision:** DEPS-06's four groups PLUS OpenRewrite (D-16) + SpotBugs+find-sec-bugs (D-17) + Playwright manual-review (D-18) + Dockerfile `eclipse-temurin` `-noble` constraint (D-19).
**Notes:** OpenRewrite recipe-pack bumps are explicit migration triggers per Phase 80 D-08; manual review prevents silent recipe-set changes. SpotBugs new-detector-pack bumps can change the violation surface per Phase 81 D-10. Playwright is coupled to Dockerfile `-noble` per Phase 78. Each group's `description` field documents the cross-reference.

---

## Manual-Review Gates Beyond DEPS-03/04/05

| Option | Description | Selected |
|--------|-------------|----------|
| Major bump manual review for `spring-boot-*`, OpenRewrite, SpotBugs, Playwright | Explicit `automerge: false` + `dependencyDashboardApproval: true` where appropriate | ✓ |
| Trust patch-only-automerge as sufficient guard | Rely on the patch/minor/major boundary to gate dangerous bumps | |

**Decision:** Explicit `automerge: false` on the four sensitive coord groups (CONTEXT.md D-12, D-16, D-17, D-18).
**Notes:** Even with patch-only automerge, a minor bump on `spring-boot-starter-parent` would otherwise sail through CI to the merge queue without explicit operator decision. The `dependencyDashboardApproval: true` flag on OpenRewrite makes recipe-pack bumps click-to-create on the dashboard, matching Phase 80's developer-invoked posture.

---

## Dependency Dashboard

| Option | Description | Selected |
|--------|-------------|----------|
| Default-enabled tracking issue | Free, zero-CI-cost single-pane-of-glass | ✓ |
| Disabled (`dependencyDashboard: false`) | Quieter GitHub Issues list | |

**Decision:** Default-enabled (CONTEXT.md D-23).
**Notes:** DEPS-FUTURE-01 defers the CURATED-review-queue feature (with operator-driven prioritization) — not the basic dashboard issue itself. The basic dashboard is free and useful for the solo maintainer; only the curation work is future scope.

---

## Verification Strategy for DEPS-08 / SC#6 (dockerfile-noble-pin-guard pass)

| Option | Description | Selected |
|--------|-------------|----------|
| Wait for organic Renovate Dockerfile-bump PR within 7 days | Tests real Renovate behavior end-to-end | |
| Synthetic throwaway PR bumping Dockerfile in a Renovate-style way | Same outcome as organic; immediate; mirrors Phase 81 D-13 precedent | ✓ |
| Forced manual scan via dashboard "Click to rebase" | Middle ground; depends on a publishable patch existing | |

**Decision:** Three-path completion structure in VERIFICATION.md; planner picks based on Renovate's first-48-hour behavior, defaults to synthetic if no organic PR by then (CONTEXT.md D-24).
**Notes:** SC#6 phrasing ("dockerfile-noble-pin-guard CI job passes after Renovate's first Dockerfile-bump PR is opened") does NOT require the PR to come organically from Renovate's scheduler — it requires the guard to PASS on a Dockerfile-bump PR that Renovate WOULD open. A synthetic PR opened by the operator on a feature branch (with a Renovate-style Dockerfile diff) tests the same guard behavior in seconds rather than days.

---

## Claude's Discretion

Items where CONTEXT.md explicitly leaves room for planner judgment (not blocking decisions):

- Final Mend Renovate app installation moment (recommendation: AFTER `renovate.json` PR merges)
- Exact `description` text on each `packageRules` entry (match `config/spotbugs-exclude.xml` terse-english tone)
- Whether to topic-group the `packageRules` array (recommendation: yes, with marker-rule headers)
- `timezone: "Europe/Berlin"` to lock schedule semantics (recommendation: yes)
- `rebaseWhen: "behind-base-branch"` explicit vs default (recommendation: explicit per PITFALLS pitfall #5)
- Regex managers for `<playwright.version>` property (recommendation: only if standard `maven` manager fails detection)
- Whether to create `docs/operations/renovate.md` runbook (recommendation: planner judgment)

## Deferred Ideas

- Curated Dependency Dashboard review queue → DEPS-FUTURE-01, deferred until team grows beyond single maintainer
- Renovate automerge for `minor` updates → REQUIREMENTS.md Out of Scope; reopen after 3+ months of patch-automerge confidence
- Renovate automerge for `major` updates → REQUIREMENTS.md Out of Scope (major bumps are explicit OpenRewrite migration triggers)
- Self-hosted Renovate via GitHub Actions → reopen only if Mend hosted service is discontinued
- Regex managers for additional pinned version properties → only if `maven` default manager fails
- `docs/operations/renovate.md` runbook → discretion item for planner
- Dropping the `renovate` secondary label after migration bedded in → not a phase deliverable

