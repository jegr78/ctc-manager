# Phase 85: CodeQL SAST - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in 85-CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-17
**Phase:** 85-codeql-sast
**Areas discussed:** Suppression mechanism, PR gate enforcement, Gate rollout choreography, Acceptance doc structure & scope, Schedule + path-ignore, codeql-action versioning + Renovate interaction, CLAUDE.md Conventions, Performance + Caching, SARIF-Diff edge cases, Renovate packageRule, CLAUDE.md References

---

## Suppression Mechanism

### Suppression strategy for SSRF + ZIP-Slip triade

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: codeql-config.yml + Code-Marker + Acceptance-Doc | query-filters + non-directive source markers + sast-acceptance.md rationale | ✓ |
| Pure codeql-config.yml + Acceptance-Doc | No source marker — devs must consult acceptance doc | |
| Pure UI-Dismissal + Acceptance-Doc | Findings stay in Security tab, dismissed individually | |

**User's choice:** Hybrid (recommended). Mirrors Phase 81 D-09/D-11.

### BCrypt-FP triage handling (SC#4 anticipates site that doesn't exist)

| Option | Description | Selected |
|--------|-------------|----------|
| Drop BCrypt from Phase-85 scope | Document N/A in sast-acceptance.md as TRACKED DEVIATION | ✓ |
| Triage if CodeQL fires, else N/A | Reserve plan slot for possible findings | |
| Pre-emptively filter in codeql-config | Defensive even without real site | |

**User's choice:** Drop BCrypt from scope; document as N/A section in sast-acceptance.md + TRACKED DEVIATION in CONTEXT.md.

### Pre-stage scope before baseline scan

| Option | Description | Selected |
|--------|-------------|----------|
| Pre-stage ONLY SSRF + ZIP-Slip triade | Three known sites only, rest live-triage | ✓ |
| Empty codeql-config.yml | All findings live-triaged from scratch | |
| Pre-stage triade + test/generated excludes | Add defensive paths-ignore | |

**User's choice:** SSRF + ZIP-Slip triade only.

### codeql-config mechanism (path vs query-filter)

| Option | Description | Selected |
|--------|-------------|----------|
| query-filter (rule-precise) | Excludes single rule on single file | ✓ |
| paths-ignore (file-level) | Drops entire file from analysis | |
| Custom query suite with excludes | Maximum control, extra file | |

**User's choice:** query-filter for surgical suppression.

### Source-Marker text format

| Option | Description | Selected |
|--------|-------------|----------|
| Short: `// CodeQL FP: <rule-id> — see docs/security/sast-acceptance.md` | One-liner above protected method/block | ✓ |
| Block comment with full rationale | Multi-line inline rationale | |
| No source marker | Only acceptance-doc mapping | |

**User's choice:** Short one-liner mirroring Phase 81 D-09 code-cross-reference pattern.

---

## PR Gate Enforcement

### Gate enforcement technology

| Option | Description | Selected |
|--------|-------------|----------|
| Native Code-Scanning branch-protection rule | Repo Settings toggle, no custom code | |
| Custom SARIF-parser step in workflow | Inline bash with `gh api` + jq + diff | ✓ |
| codeql-action `severity` input | Job-level fail-on-severity directive | |

**User's choice:** Custom SARIF-parser step. Custom logic preferred over native rule for full control over diff semantics.

### Definition of "new" finding

| Option | Description | Selected |
|--------|-------------|----------|
| Alerts open on PR-branch but not on base | Set-difference via `gh api` | ✓ |
| All open HIGH/CRITICAL alerts on PR-branch | Strict, blocks inherited debt | |
| Only alerts touching PR-changed files | Touch-only enforcement | |

**User's choice:** Diff-against-base (set-difference).

### Parser host location

| Option | Description | Selected |
|--------|-------------|----------|
| Inline-Bash step in codeql.yml | ~20 lines in workflow YAML | ✓ |
| Dedicated `.github/scripts/codeql-gate.sh` | Locally testable | |
| Marketplace action (filter-sarif etc.) | 3rd-party dependency | |

**User's choice:** Inline-bash to minimize 3rd-party action exposure (Phase 84 Renovate-policy consistency).

### Branch-protection-rule responsibility

| Option | Description | Selected |
|--------|-------------|----------|
| Yes: manual Repo-Settings step in plan | Operator action analog to Phase 84 Mend install | |
| No: only workflow configuration, branch-protection out-of-scope | Phase 85 ships workflow + parser only | ✓ |
| Branch-protection in 85-VERIFICATION.md as checklist | Mid-ground documentation | |

**User's choice:** Out-of-scope for Phase 85; branch-protection stays operator-hoheit. SAST-06 reduced to "workflow-fails-on-deliberate-violation" coverage.

### First-run / chicken-and-egg behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Push-to-master skips gate, only PR-events fail | Bootstrap-safe | |
| Gate runs on push but only fails on net-new vs previous master-scan | Persisted SARIF comparison | |
| Gate always active, first run must produce zero HIGH/CRITICAL | Strictest path | ✓ |

**User's choice:** Always active. Choreography MUST clean baseline before final-enable commit.

### Severity axis

| Option | Description | Selected |
|--------|-------------|----------|
| security-severity HIGH or CRITICAL (>=7.0) | CVSS-aligned, matches SC#5 wording | ✓ |
| level=error AND security-severity HIGH+ | Conjunction; engest | |
| Any level=error | Broadest, includes quality queries | |

**User's choice:** security-severity HIGH/CRITICAL.

### Trigger matrix

| Option | Description | Selected |
|--------|-------------|----------|
| Gate active on PR + push-to-master; skip on schedule/cron | Three triggers, two gate | ✓ |
| Gate active on all triggers | Including weekly cron | |
| Gate only on PR; push only uploads | Loose-coupling | |

**User's choice:** Active on PR + push, skip on cron.

---

## Gate Rollout Choreography

### Commit choreography

| Option | Description | Selected |
|--------|-------------|----------|
| Three-phase: scaffold (workflow_dispatch-only) → baseline-triage → enable | Phase-81-style staged rollout | ✓ |
| Two-phase: workflow active + gate-step disabled → enable | Shorter but toggle-drift risk | |
| One-commit: workflow + gate + triage all upfront | Local CodeQL prerequisite | |

**User's choice:** Three-phase rollout.

### Scaffold-disable mechanism

| Option | Description | Selected |
|--------|-------------|----------|
| `on: workflow_dispatch:` only | Manual trigger via `gh workflow run` | ✓ |
| `on: push: branches: [throwaway-codeql-bootstrap]` | Trigger on non-existent branch | |
| Job-level `if: false` gate | Run-on-skip waste | |

**User's choice:** workflow_dispatch only.

### Deliberate-violation test (SAST-06) placement

| Option | Description | Selected |
|--------|-------------|----------|
| Throwaway-branch after final commit, pre-merge | Branch + draft-PR + cleanup | ✓ |
| Inline on Phase-PR with revert commit | Compact but PR goes through red state | |
| Post-merge as follow-up | Phase 85 ships logic only | |

**User's choice:** Throwaway-branch pre-merge of Phase-PR (Phase 81 D-13 mirror).

### Unexpected baseline findings handling

| Option | Description | Selected |
|--------|-------------|----------|
| D-10 decision tree, plan-update at >=3 real findings | Disciplined scope expansion | |
| Hard-cap 5 triage commits, then split phase | Strict scope ceiling | |
| Soft-approach: all findings in Phase-85 scope | Codebase is clean, surprises unlikely | ✓ |

**User's choice:** Soft-approach. No hard cap; Phase-85 absorbs all baseline findings.

---

## Acceptance Doc Structure & Scope

### Format

| Option | Description | Selected |
|--------|-------------|----------|
| Per-finding table + per-pattern section headers | SSRF/ZIP-Slip/BCrypt/Other sections with tables | ✓ |
| Pure narrative Markdown | Free-form sections | |
| Pure table without pattern sections | Flat global table | |

**User's choice:** Per-pattern sections + per-finding tables.

### Location

| Option | Description | Selected |
|--------|-------------|----------|
| New `docs/security/` top-level dir | Sibling to docs/uat/, docs/superpowers/ | ✓ |
| Under `.planning/security/` | Planning artifact, not shipped doc | |
| Under `docs/superpowers/specs/` | Sibling to existing specs | |

**User's choice:** New `docs/security/` top-level dir (Phase-83 QUAL-05 pattern mirror).

### BCrypt slot handling

| Option | Description | Selected |
|--------|-------------|----------|
| Own section "BCrypt (N/A)" with rationale | Document the deviation explicitly | ✓ |
| Inline note in 85-VERIFICATION.md only | Acceptance doc stays clean | |
| No mention, only in CONTEXT.md | Minimal docs surface | |

**User's choice:** Own N/A section in acceptance doc.

### Maintenance discipline

| Option | Description | Selected |
|--------|-------------|----------|
| Update-on-Triage — each suppression-commit adds table row | CLAUDE.md-enforced atomic update | ✓ |
| Standalone-doc, updated only in dedicated phases | Lower PR-friction, higher doc-rot risk | |
| Auto-generated from SARIF + UI state | Phase 86+ tooling | |

**User's choice:** Update-on-Triage discipline.

---

## Schedule + Path-Ignore

### Cron schedule timing

| Option | Description | Selected |
|--------|-------------|----------|
| Monday 04:30 UTC — before Renovate 06:00 | Tight Monday morning cluster | |
| Sunday 02:00 UTC — separate weekday | Avoids Renovate-day collisions | ✓ |
| Daily | Faster drift detection | |

**User's choice:** Sunday 02:00 UTC.

### Path-ignore strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Default — nothing explicit | Trust CodeQL defaults + `-DskipTests` | ✓ |
| Explicit paths-ignore for target/, build/generated-sources/ | Defensive redundancy | |
| paths-ignore + positive paths list | Maximum control | |

**User's choice:** Default behavior, no explicit excludes.

---

## codeql-action Versioning + Renovate Interaction

### Pin strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Floating `@v4` tag, Renovate-managed | Matches existing ci.yml floating-major pattern | ✓ |
| SHA-pinned for supply-chain hardening | Breaks Phase-84 policy | |
| Major-pinned `@v4.35` | Non-standard tag granularity | |

**User's choice:** Floating `@v4` with Renovate management.

---

## CLAUDE.md Conventions

### Conventions extension verbosity

| Option | Description | Selected |
|--------|-------------|----------|
| New sub-section "CodeQL SAST (Code Scanning)" with 3 bullets | Phase-81-mirror format | ✓ |
| Single-line in References section only | Minimal friction, low discoverability | |
| Full sub-section under `## Constraints` | Lengthy mirror of SpotBugs subsection | |

**User's choice:** New `## Conventions` sub-section with 3 bullets.

### Branch name

| Option | Description | Selected |
|--------|-------------|----------|
| `feature/codeql-sast` | Phase-81/84 mirror | |
| `feature/85-codeql-sast` | Number-prefixed | |
| Planner picks | Discretion | |

**User's choice:** REJECTED — user pointed out Memory `feedback_milestone_branch.md` invariant. All Phase-85 commits land on `gsd/v1.11-tooling-and-cleanup`. Branch is HARD-LOCKED in D-23. Memory verschärft to add stronger structural-enforcement signals.

---

## Performance + Caching

### Caching strategy

| Option | Description | Selected |
|--------|-------------|----------|
| `actions/setup-java@v5` with `cache: 'maven'` | Mirror ci.yml | ✓ |
| Additional `actions/cache` for CodeQL bundle | Marginal extra speedup | |
| No caching, from-scratch every run | Public-repo: free runner-minutes | |

**User's choice:** Maven cache via setup-java.

### Concurrency block

| Option | Description | Selected |
|--------|-------------|----------|
| Concurrency analog to ci.yml (`cancel-in-progress: true`) | Mirror ci.yml | ✓ |
| No concurrency block | Every push triggers new run | |
| Concurrency with `cancel-in-progress: false` | Queue runs sequentially | |

**User's choice:** Concurrency mirror with `cancel-in-progress: true`.

---

## SARIF-Diff Edge Cases (Round 3)

| Option | Description | Selected |
|--------|-------------|----------|
| Strict: `state=open` AND `dismissed_at=null` AND severity filter | Keyed on (rule.id, location.path) | ✓ |
| Loose: only severity-filter, ignore dismissed | Re-fails UI-dismissed alerts | |
| Skip base-comparison, only head-state | Blocks all PRs with inherited debt | |

**User's choice:** Strict filter, commit-sha-agnostic keying for rebase resilience.

---

## Renovate packageRule for github/codeql-action (Round 3)

| Option | Description | Selected |
|--------|-------------|----------|
| Yes: patch automerge after 3 days, minor manual-review | Phase-84 consistency | ✓ |
| No: rely on generic github-actions manager rule | Phase 84 catches it | |
| Pin via `pinDigests: true` | SHA-pinned via Renovate | |

**User's choice:** Explicit packageRule.

---

## CLAUDE.md References Section (Round 3)

| Option | Description | Selected |
|--------|-------------|----------|
| Two entries — SAST doc + workflow file | Mirror existing references-pattern | ✓ |
| Only SAST doc — workflow is self-evident | Minimal addition | |

**User's choice:** Two entries (sast-acceptance.md + codeql.yml).

---

## Claude's Discretion

- Exact baseline `query-filters` rule IDs (planner inspects first baseline scan)
- Exact inline-bash text of SARIF-diff gate step
- Exact CLAUDE.md sub-section wording
- Exact `_sast_validation` package + class name for SAST-06 throwaway test
- Whether to include `gh workflow run` example in 85-VERIFICATION.md
- Plan-commit count and granularity (1 plan vs split per choreography phase)

## Deferred Ideas

- Repository branch-protection rules configuration → operator-hoheit, post-merge manual step
- `paths-ignore` defensive excludes → rejected (CodeQL defaults sufficient)
- SHA-pinned codeql-action → rejected (breaks Phase-84 policy)
- `actions/cache` for CodeQL bundle → rejected (fragile hit-rate)
- Daily cron → rejected (weekly suffices)
- Custom CodeQL queries → out of v1.11 scope
- Marketplace SARIF-filter action → rejected (Phase-84 policy)
- CodeQL on `src/test/java` → out of scope
- Multi-language CodeQL scan → not applicable
- Standalone CodeQL operator runbook → optional follow-up
- Spring Security PasswordEncoder bean addition → architectural change, not SAST-gate concern

## Process Correction (2026-05-17)

User caught an orchestrator-discipline violation: Area-7 Branch-Name question offered `feature/...` options despite `feedback_milestone_branch.md` Memory explicitly forbidding them ("In jeder discuss-phase-AskUserQuestion zum Thema 'Commit/Branch' gilt: Optionen, die einen neuen feature/...-Branch vorschlagen, dürfen NICHT mehr angeboten werden."). Branch decision was hard-locked to `gsd/v1.11-tooling-and-cleanup` (D-23) and Memory was strengthened with explicit structural-enforcement signals to prevent recurrence.
