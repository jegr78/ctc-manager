---
phase: 84
slug: renovate-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-17
---

# Phase 84 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | None — Phase 84 has zero Java code or test changes. Verification is JSON-schema validity + CI behavior + Mend app behavior. |
| **Config file** | n/a |
| **Quick run command** | `./mvnw verify -DskipTests --no-transfer-progress` (sanity — confirms `renovate.json` does not break Maven build) |
| **Full suite command** | `./mvnw verify -Pe2e --no-transfer-progress` (final phase-end gate per CLAUDE.md `feedback_e2e_verification`) |
| **Estimated runtime** | Quick: ~30s · Full: ~11 min (v1.10 baseline) |

---

## Sampling Rate

- **After every task commit:** Skip per-commit `./mvnw verify` (zero Java changes). Use one `jq` / `test -f` / `gh` structural check per task acceptance criterion instead.
- **After every plan wave:** N/A — Phase 84 is a single-wave phase.
- **Before `/gsd:verify-work`:** Full suite (`./mvnw verify -Pe2e`) must be green; `renovate.json` must validate against the official Renovate JSON schema; Mend onboarding PR must exist; synthetic Dockerfile-bump PR must show `dockerfile-noble-pin-guard: pass`.
- **Max feedback latency:** Structural checks ~1s · Schema validation ~3s · CI gate observation ~5 min · Mend onboarding PR latency ~5–15 min after app install.

---

## Per-Task Verification Map

> Per-task IDs will be finalized by the planner. The map below is the requirement→verification scaffolding the planner MUST cover. Every DEPS-XX requirement and every CONTEXT.md decision that translates to a structural fact in `renovate.json` MUST appear in PLAN.md `acceptance_criteria` blocks with the matching `automated_command`.

| Req / Decision | Behavior | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------------|----------|------------|-----------------|-----------|-------------------|-------------|--------|
| DEPS-01 | `renovate.json` exists at root with `enabledManagers: ["maven","github-actions","dockerfile"]` | — | n/a (config phase) | structural | `test -f renovate.json && jq -e '.enabledManagers == ["maven","github-actions","dockerfile"]' renovate.json` | ✅ after Wave 1 commit | ⬜ pending |
| DEPS-01 | `renovate.json` validates against the official Renovate JSON schema | — | n/a | schema-validation | `npx --yes ajv-cli@5 validate -s <(curl -fsSL https://docs.renovatebot.com/renovate-schema.json) -d renovate.json` | ✅ | ⬜ pending |
| DEPS-02 | Mend Renovate GitHub App installed against `jegr78/ctc-manager`; onboarding PR exists | T-1 (third-party-app trust) | manual-review-only on install | operator-action + observation | `gh pr list --repo jegr78/ctc-manager --search "in:title configure renovate author:app/renovate"` returns ≥ 1 PR | ❌ until operator installs | ⬜ pending |
| DEPS-03 | `renovate.json` Guava `-jre` allowedVersions rule (regex form) | — | preserves Java 25 VarHandle path | structural | `jq -e '.packageRules[] \| select(.matchPackageNames == ["com.google.guava:guava"]) \| .allowedVersions == "/^[0-9.]+-jre$/"' renovate.json` | ✅ | ⬜ pending |
| DEPS-04 | `renovate.json` Thymeleaf `enabled: false` rule | T-4 (CVE-2026-40478 mitigation) | manual review required for any thymeleaf bump | structural | `jq -e '.packageRules[] \| select(.matchPackageNames == ["org.thymeleaf:thymeleaf"]) \| .enabled == false' renovate.json` | ✅ | ⬜ pending |
| DEPS-05 | `config:recommended` inherits `workarounds:javaLTSVersions`; `<java.version>` proposals are LTS-only by preset | — | LTS-only Java | preset-inheritance | `jq -e '.extends \| contains(["config:recommended"]) and (.ignorePresets // [] \| contains(["workarounds:javaLTSVersions"]) \| not)' renovate.json` | ✅ | ⬜ pending |
| DEPS-06 | Spring Boot, Spring Security, Google API, Testcontainers groups defined | — | reduces PR noise | structural | `jq -e '[.packageRules[] \| .groupName // empty] \| (index("Spring Boot") and index("Spring Security") and index("Google API clients") and index("Testcontainers"))' renovate.json` | ✅ | ⬜ pending |
| DEPS-07 | Patch automerge rule with `automergeType: "pr"` | T-2 (silent code entry to master via automerge) | PR object preserves audit trail; branch protection enforces CI gate | structural | `jq -e '.packageRules[] \| select(.matchUpdateTypes == ["patch"]) \| .automerge == true and .automergeType == "pr"' renovate.json` | ✅ | ⬜ pending |
| DEPS-08 / SC#6 | `dockerfile-noble-pin-guard` passes on a Dockerfile-change PR (synthetic per D-24 path 3) | — | dockerfile suffix invariant preserved | CI-job-observation | `gh pr checks <synthetic-pr-number> --required` shows `dockerfile-noble-pin-guard: pass` | ✅ after synthetic PR run | ⬜ pending |
| D-03 | `.github/dependabot.yml` removed in the same commit that introduces `renovate.json` | T-5 (dual-bot duplicate PRs) | exactly one dependency bot active | structural negative | `test ! -f .github/dependabot.yml` | ✅ | ⬜ pending |
| D-19 | Dockerfile `eclipse-temurin` rule pins `-noble` suffix (corrected regex per RESEARCH.md Pitfall) | T-3 (Renovate bypassing the Dockerfile -noble invariant) | belt-and-braces with CI guard | structural | `jq -e '.packageRules[] \| select(.matchDatasources == ["docker"] and .matchPackageNames == ["eclipse-temurin"]) \| .allowedVersions == "/^25(?:\\\\.[0-9._]+)?-(?:jdk\|jre)-noble$/"' renovate.json` | ✅ | ⬜ pending |
| Build invariant | `renovate.json` does not break Maven build | — | n/a | sanity-build | `./mvnw verify -DskipTests --no-transfer-progress` exits 0 | ✅ | ⬜ pending |
| Phase gate | Full E2E suite green at phase end | — | n/a | full-build | `./mvnw verify -Pe2e --no-transfer-progress` exits 0 | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] **No new test files needed** — Phase 84 has no Java code surface.
- [ ] **No `conftest` / shared fixtures needed**.
- [ ] **No framework install needed**.
- [ ] `jq` is already part of the macOS dev environment and GitHub-Actions Ubuntu image (no project-level install required). `ajv-cli@5` runs via `npx --yes` on demand (verification-time only).
- [ ] `gh` CLI is the project standard per CLAUDE.md `## Git Workflow` (no install required).

*Existing test infrastructure is sufficient. The structural checks (`jq`, `test -f`, schema validation, `gh pr checks`) live in VERIFICATION.md as shell snippets, not in committed test code. This is the same shape as Phase 80's `help:active-profiles` verification (Phase 80 D-10) — verification-time-only CLI assertions.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Mend Renovate GitHub App installation against `jegr78/ctc-manager` | DEPS-02 | External SaaS install requires GitHub admin OAuth consent; cannot be scripted in CI | Visit https://github.com/apps/renovate/installations/new → select `jegr78/ctc-manager` (single repo, do NOT grant org-wide) → submit. Confirm onboarding PR appears within 15 min: `gh pr list --search "author:app/renovate"`. |
| Branch protection `Require status checks: build-and-test + dockerfile-noble-pin-guard` is active on `master` | T-2 mitigation (automerge safety net) | GitHub branch-protection settings cannot be safely modified in-phase by an automation script | Verify via `gh api repos/jegr78/ctc-manager/branches/master/protection \| jq '.required_status_checks.contexts'` — output MUST include `build-and-test` and `dockerfile-noble-pin-guard`. If missing, document in VERIFICATION.md and propose enabling before merging Phase 84. |
| Synthetic Dockerfile-bump PR triggers `dockerfile-noble-pin-guard` job (Path 3 per CONTEXT.md D-24) | DEPS-08 / SC#6 | Renovate-organic PR may take days; synthetic PR proves the gate behaviour deterministically within the same session | On a throwaway branch off `origin/master`: edit `Dockerfile` to replace `25-jdk-noble` with `25.0.1_8-jdk-noble` (a real published Adoptium tag); `gh pr create --draft`; observe `dockerfile-noble-pin-guard` passes; close PR; delete branch. Capture the workflow-run URL in `84-VERIFICATION.md`. |

---

## Validation Sign-Off

- [ ] All requirement IDs (DEPS-01..DEPS-08) have a corresponding row in Per-Task Verification Map with an `automated_command` OR an entry in Manual-Only Verifications with explicit test instructions
- [ ] CONTEXT.md decisions that translate to structural facts (D-03, D-19, D-24) have rows in the map
- [ ] Sampling continuity: every requirement has either an automated `jq`/`test -f`/`gh` command or an explicit manual procedure — no requirement is unverified
- [ ] No watch-mode flags
- [ ] Feedback latency < 5 min for all structural checks
- [ ] `nyquist_compliant: true` will be set in frontmatter after planner confirms every task's acceptance criteria reference one row in the Per-Task Verification Map

**Approval:** pending
