# Phase 85: CodeQL SAST - Research

**Researched:** 2026-05-17
**Domain:** GitHub Code Scanning via `github/codeql-action@v4` on a Spring Boot 4.0.6 / Java 25 / Lombok-heavy Maven codebase; standalone workflow + custom inline-bash SARIF-diff gate; pre-staged false-positive triage for SSRF + ZIP-Slip
**Confidence:** HIGH for action version + Java 25 support + canonical rule IDs + REST API alert schema + Lombok behaviour (all verified against official GitHub docs / GitHub Changelog / codeql.github.com query-help pages); **HIGH for one decisive correction:** `query-filters` does NOT support per-path filtering — the `where:` field in CONTEXT.md D-02 is fabricated and will cause the scaffold-commit YAML to be a no-op (see Research Correction C-01 below); MEDIUM for the SARIF-diff first-run handling pattern (no canonical GitHub example exists; this is project-specific glue code).

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Suppression Mechanism (Area 1)**
- D-01: Hybrid suppression — `codeql-config.yml` `query-filters` + non-directive source markers (`// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md`) + `sast-acceptance.md`. Mirrors Phase 81 D-09.
- D-02: `query-filters` (rule@where), NOT `paths-ignore`. Surgical per-rule-per-file suppression. **⚠ See Research Correction C-01 below — the `where:` syntax does not exist in CodeQL config schema.**
- D-03: Source marker format fixed: `// CodeQL FP: <rule-id> — <one-line reason>; see docs/security/sast-acceptance.md`. Code-review signal only, NOT a CodeQL directive.
- D-04: Pre-stage ONLY SSRF + ZIP-Slip triade (`FileStorageService`, `BackupArchiveService`, `BackupImportService`); rest live-triage via Phase-81-D-10 decision tree.
- D-05 (TRACKED DEVIATION from SC#4 / SAST-05): BCrypt FP triage is N/A — no `PasswordEncoder` bean exists in `src/main/java/`. Documented as N/A section in `sast-acceptance.md`. Verified live in `SecurityConfig.java` (only `SecurityFilterChain` with `httpBasic(Customizer.withDefaults())`, no PasswordEncoder bean).

**PR Gate Enforcement (Area 2)**
- D-06: Custom SARIF-diff gate via inline-bash step in `codeql.yml` (NOT marketplace 3rd-party action, NOT separate script file). Minimum-3rd-party-action policy from Phase 84.
- D-07: "New" = alerts open on PR-branch but not on base-branch; set-difference via `gh api`. Key on `(rule.id, most_recent_instance.location.path)` — commit-sha-agnostic.
- D-08: Severity = `security-severity` HIGH (>= 7.0) OR CRITICAL (>= 9.0). NOT `level=error` (which catches non-security quality queries).
- D-09: Gate always active. First push to master MUST be clean (combined with D-12 choreography).
- D-10: Gate active on `push` + `pull_request`, SKIP on `schedule` cron (`if: github.event_name != 'schedule'`).
- D-11: Branch-protection rule = OPERATOR HOHEIT (out of scope). SAST-06 reduced to "workflow fails on violation".

**Gate Rollout Choreography (Area 3)**
- D-12: 3-phase choreography on milestone branch: scaffold (`workflow_dispatch:` only, gate disabled) → baseline-triage → final-enable (push/PR/schedule triggers + gate-step active).
- D-13: Scaffold-disable = `on:\n  workflow_dispatch:` only.
- D-14: SAST-06 throwaway-branch deliberate-violation test BEFORE final merge of Phase-85 PR (mirrors Phase 81 D-13).
- D-15: Soft scope — all baseline-emitted findings stay within Phase 85, no Phase-85b spawn.

**Acceptance Doc Structure (Area 4)**
- D-16: Format = per-pattern Markdown sections (SSRF | ZIP-Slip | BCrypt-N/A | Others) + per-finding tables (Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker).
- D-17: New top-level `docs/security/` directory (sibling to `docs/uat/`, `docs/superpowers/`).
- D-18: BCrypt section explicitly documents the SC-vs-reality deviation.
- D-19: Update-on-Triage discipline — every suppression edit also edits `sast-acceptance.md` atomically.

**Schedule + Path-Ignore (Area 5)**
- D-20: Cron Sunday 02:00 UTC (`0 2 * * 0`).
- D-21: NO explicit `paths-ignore` (trust CodeQL defaults + `-DskipTests`).

**codeql-action Versioning (Area 6)**
- D-22: Floating `@v4` tag, Renovate-managed. Both `init` and `analyze` steps.

**Branch & Documentation (Area 7)**
- **D-23 (HARD-LOCKED):** All Phase-85 commits on `gsd/v1.11-tooling-and-cleanup`. NO new feature branch. Subagent prompts forbid `git stash`, `git checkout`, `git reset`, `git switch`, `git checkout -b`.
- D-24: CLAUDE.md `## Conventions > CodeQL SAST (Code Scanning)` new sub-section (3 bullets).
- D-25: 2 new entries in CLAUDE.md `## References` (sast-acceptance.md + codeql.yml).

**Performance + Caching (Area 8)**
- D-26: Maven cache via `actions/setup-java@v5 cache: 'maven'` (identical to `ci.yml`).
- D-27: Concurrency block: `group: '${{ github.workflow }}-${{ github.ref }}', cancel-in-progress: true`.

**Round-3 Detail Clarifications (Area 9)**
- D-28: SARIF-diff strict filter: `state=open AND dismissed_at=null AND security-severity >= 7.0`. Keyed on `(rule.id, location.path)`.
- D-29: Renovate packageRule for `github/codeql-action`: patch automerge (`minimumReleaseAge: "3 days"`), minor/major via Dependency Dashboard approval.

### Claude's Discretion
- Exact baseline `query-filters` entries — planner inspects actual rule IDs from baseline workflow_dispatch run.
- Exact inline-bash text of SARIF-diff gate step — planner refines edge-case handling.
- Exact CLAUDE.md sub-section wording.
- Exact `_sast_validation` package + class name for SAST-06 throwaway test (SQLi vs path-traversal).
- Whether to add `gh workflow run` invocation example to `85-VERIFICATION.md`.
- Plan-commit count and granularity (one plan or split per choreography phase).

### Deferred Ideas (OUT OF SCOPE)
- Repository branch-protection rules ("Required status checks: CodeQL Analysis") — D-11 operator-hoheit.
- Defensive `paths-ignore` for `target/`, `src/test/java/`, `build/generated-sources/` — D-21 rejected (redundant).
- SHA-pinned `github/codeql-action` — D-22 rejected.
- `actions/cache` for the CodeQL bundle — D-26 rejected (fragile vs floating `@v4`).
- Daily cron schedule — D-20 rejected (weekly suffices).
- Custom CodeQL queries / project-specific query suite — out of v1.11 scope.
- Marketplace 3rd-party SARIF-filter action — D-06 rejected.
- CodeQL on `src/test/java` — D-21 + default-extractor behaviour.
- Multi-language scan beyond `java-kotlin` — out of scope.
- Operator runbook for triaging new alerts — partially covered by CLAUDE.md + sast-acceptance.md.
- Spring Security PasswordEncoder bean — completely out of scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SAST-01 | `.github/workflows/codeql.yml` runs CodeQL v4 against `language: java-kotlin` on push to master, pull request, weekly cron | Verified action `@v4.35.5` (May 15 2026) bundles CodeQL 2.25.4 [CITED: github.com/github/codeql-action/releases]; `java-kotlin` is the canonical post-deprecation identifier [CITED: research/STACK.md §"Stream 4 — SAST"]; cron schedule `0 2 * * 0` valid GitHub Actions cron — UTC, fires Sunday 02:00 UTC [CITED: docs.github.com workflow-syntax events] |
| SAST-02 | Manual `./mvnw compile -DskipTests --no-transfer-progress` build step (not autobuild) | Stream-4 research locks manual build to protect Lombok annotation processing + Playwright compile-scope dep [CITED: research/STACK.md §"Stream 4 — SAST" lines 430-432]; Lombok now natively supported by CodeQL since 2.14.4 (Sept 2023) — no `delombok` needed [CITED: github.blog/changelog/2023-09-01] |
| SAST-03 | Job-level `permissions: { security-events: write, contents: read, actions: read }`; workflow-level restricted (e.g., `contents: read`) | Verified pattern — Stream 4 mandate. `security-events: write` is REQUIRED to upload SARIF to Security tab [CITED: research/STACK.md lines 403-407]; workflow-level + job-level layered (workflow-level downgrade is a defence-in-depth choice, not a CodeQL requirement) |
| SAST-04 | Findings triaged into 3 buckets (fixed / suppressed / accepted) with rationale | Bucket model = Phase 81 D-10 decision tree templated. **⚠ CONTEXT.md D-01 + REQUIREMENTS-SAST-04 mention `// codeql[<rule>]` source directives — these do NOT exist as a CodeQL suppression mechanism for the security-extended pack (see Research Correction C-02)**; D-01 hybrid mechanism (config + non-directive source marker + acceptance doc) is the correct substitute |
| SAST-05 | SSRF / ZIP-Slip / BCrypt classified with linked Alert IDs | Canonical rule IDs verified: `java/ssrf` (sev 9.1), `java/zipslip` (sev 7.5), `java/path-injection` (sev 7.5) [CITED: codeql.github.com/codeql-query-help/java/{java-ssrf,java-zipslip,java-path-injection}/]; BCrypt N/A per D-05 (live `SecurityConfig.java` inspection — no `PasswordEncoder` bean) |
| SAST-06 | CodeQL findings appear in Security tab; workflow gates PR merges on new HIGH/CRITICAL (throwaway-branch deliberate-violation test) | Gate-step inline-bash plus `gh api code-scanning/alerts` set-difference. `ref` + `pr` query parameters confirmed [CITED: docs.github.com REST code-scanning]. Branch-protection enforcement out of scope per D-11 (operator-hoheit) |
</phase_requirements>

<project_constraints>
## Project Constraints (from CLAUDE.md)

The following CLAUDE.md directives apply to Phase 85 and MUST NOT be violated by any plan:

- **Language:** German communication; ALL documentation, code, comments, UI text in English. New files (`codeql.yml`, `codeql-config.yml`, `sast-acceptance.md`) must be English-only.
- **Test Coverage:** ≥ 82% line coverage maintained. Phase 85 adds no Java source, so this is a no-op gate, but the final `./mvnw verify -Pe2e` run must remain green.
- **Flyway:** No changes to existing V1 migrations. Phase 85 touches no migration files.
- **Profiles:** Auth only for `prod`/`docker`; `dev`/`local` no-auth. Phase 85 does not alter this.
- **Branch policy:** Normally branch from `origin/master`. **OVERRIDE by D-23:** all v1.11 phases stay on `gsd/v1.11-tooling-and-cleanup`. Subagent prompts MUST echo this branch and forbid `git switch`/`git stash`/`git checkout`/`git reset`.
- **Commit message style:** Conventional Commits with phase scope, e.g., `feat(85): ...`, `chore(85): ...`, `docs(85): ...`.
- **PR workflow:** v1.11 ships as a single milestone-PR to master. Phase 85 does NOT open its own PR. CI must be green before milestone merge.
- **Final verification:** `./mvnw verify -Pe2e` once at end (no skip-flags) per `feedback_clean_build_only`. Captured in `85-VERIFICATION.md`.
- **SpotBugs gate active:** Phase 81 invariant. Phase 85 must not regress SpotBugs findings.
- **`lombok.config`:** Phase 81 invariant. Phase 85 does NOT modify it.
- **No local git tags:** CI release workflow handles tags after merge to master.
- **Subagent rules:** model `opus` or `sonnet` for code, branch protection mandatory, post-dispatch validation. No Haiku for code changes.
- **No emojis** in code, docs, or commit messages.

These directives have the same authority as CONTEXT.md locked decisions.
</project_constraints>

---

## Summary

Phase 85 is a CI / DevOps + docs phase. The full surface is four net-new files (`.github/workflows/codeql.yml`, `.github/codeql/codeql-config.yml`, `docs/security/sast-acceptance.md`, plus optional `src/main/java/org/ctc/_sast_validation/SastMarker.java` on a throwaway branch) and three edited files (`renovate.json`, `CLAUDE.md`, and any per-suppression Java source files). No `pom.xml`, no `lombok.config`, no `ci.yml` touched. The 6 SAST-XX requirements are all satisfiable as-specified in CONTEXT.md — **with two material corrections to the technical mechanism**:

**Critical correction C-01 (`codeql-config.yml` schema):** CONTEXT.md D-02 specifies `query-filters: - exclude: { id: <rule>, where: <file-path> }`. The `where:` field does NOT exist in the CodeQL config schema. CodeQL's `query-filters` only supports filtering on query metadata (`id`, `kind`, `precision`, `tags`, `problem.severity`, `security-severity`, `name`). Per-path filtering for compiled languages (Java) is officially endorsed only through the 3rd-party `advanced-security/filter-sarif` action [CITED: github.com/github/codeql/discussions/11220 maintainer guidance]. The CONTEXT.md skeleton on lines 326-338 will be a NO-OP — the entries will silently fail to suppress anything because the field is unrecognized. **Three viable paths forward**, each with tradeoffs:

  - **(C-01-A) RECOMMENDED — Drop `where:`, use rule-id-only `exclude`.** Suppresses the rule across the entire codebase. Acceptable risk because `java/ssrf` only fires on `HttpURLConnection`/`URI.toURL().openStream()` etc. and the only site is `FileStorageService.storeFromUrl`. Similarly `java/zipslip` and `java/path-injection` have very narrow fire sites. Lose the ability to catch a future SSRF/zipslip elsewhere via CodeQL — but SpotBugs+find-sec-bugs (Phase 81 gate) catches the same patterns at the bytecode tier. Accept the dual-tier overlap.
  - **(C-01-B) Use UI dismissals + sast-acceptance.md only.** Skip the `codeql-config.yml` filter entirely. Each baseline alert dismissed in the Security tab with rationale `Won't fix` + comment pointing at sast-acceptance.md. Pure-runtime suppression; nothing in version control. Compatible with D-19 Update-on-Triage discipline. Weakness: dismissals are not visible in code review.
  - **(C-01-C) Adopt `advanced-security/filter-sarif`.** Violates CONTEXT.md D-06 (minimize 3rd-party actions). Restore the surgical per-rule-per-file precision D-02 asked for. Requires re-opening the D-06 discussion in /gsd-discuss-phase before implementation.

  **Recommendation: C-01-A.** It preserves CONTEXT.md D-01/D-02 intent (suppress in config), preserves D-06 (no 3rd-party action), and accepts a known precision tradeoff (whole-file vs whole-codebase) that is mitigated by SpotBugs+find-sec-bugs already catching SSRF/PATH_TRAVERSAL on the same files at the bytecode tier. This needs a discuss-phase confirmation before the scaffold-commit lands.

**Critical correction C-02 (REQUIREMENTS-SAST-04 wording):** The requirement says findings are suppressed "with explanatory comment + `// codeql[...]` directive". The `// codeql[...]` inline source directive is NOT a supported CodeQL Code-Scanning suppression mechanism. CONTEXT.md D-03 already addresses this: source markers are non-directive code-review signals (`// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md`). REQUIREMENTS.md SAST-04 should be read as satisfied by the D-01/D-03 hybrid mechanism — no need to mutate REQUIREMENTS.md, but the plan's verification step must confirm D-01/D-03 rather than literal `// codeql[...]` directive presence.

**Other non-blocking findings:**
- `gh api .../code-scanning/alerts` DOES accept `ref` and `pr` query parameters (full table verified — initial WebFetch was incomplete). The CONTEXT.md gate-step sketch is correct in principle.
- `rule.security_severity_level` is the canonical low/medium/high/critical string field on the alert response. Numeric thresholds (>= 7.0) come from a CVSS-aligned policy decision; the API itself exposes only the categorical label and the raw numeric is at `rule.security_severity_level` + a parallel numeric field. For the D-28 strict filter, use the string `IN("high","critical")` form — simpler and stable.
- Lombok-annotated Java is natively supported by CodeQL since 2.14.4 (Sept 2023). No `delombok` step needed for this codebase.
- Java 25 is explicitly supported by CodeQL 2.23.1+ (Sept 2025); current `@v4.35.5` bundles 2.25.4. No version-pinning risk.

**Primary recommendation:** Take Correction C-01-A back through `/gsd-discuss-phase` for a single clarifying decision before planning starts, then implement everything else per CONTEXT.md verbatim. The remaining 27 of 29 locked decisions are all sound and verified.

---

## Research Corrections

### C-01: `codeql-config.yml` `query-filters` does NOT support `where:` for path-based filtering

**Finding (HIGH confidence, multiple sources):**

CodeQL's `query-filters` is a query-metadata filter only. The supported filter keys are: `id`, `kind`, `precision`, `tags`, `problem.severity`, `security-severity`, `name` [CITED: docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/customizing-your-advanced-setup-for-code-scanning]. `where:` is not a recognized key — the schema would silently accept it (YAML permissive) but the filter would not apply to any alert.

GitHub maintainer guidance (`aibaars` on github/codeql discussion #11220): *"The `advanced-security/filter-sarif` Action allows filtering based on combinations of file path and query id."* That is, the official path forward for per-path-per-rule filtering on compiled languages is the post-analyze SARIF filter action.

**Sources:**
- [VERIFIED: docs.github.com workflow configuration options for code scanning](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/customizing-your-advanced-setup-for-code-scanning) — `query-filters` examples only show metadata filters (id, kind, problem.severity)
- [VERIFIED: github.com/github/codeql/discussions/11220](https://github.com/github/codeql/discussions/11220) — maintainer endorsement of `advanced-security/filter-sarif` as the path-specific suppression mechanism
- [VERIFIED: josh-ops.com/posts/github-codeql-ignore-files/](https://josh-ops.com/posts/github-codeql-ignore-files/) — confirms `paths-ignore` does NOT work for compiled languages (Java/C#/etc.); only path-aware filter is post-analyze SARIF filter
- [VERIFIED: live read of github/codeql .github/codeql/codeql-config.yml](https://github.com/github/codeql/blob/main/.github/codeql/codeql-config.yml) — uses `paths-ignore` only, no `query-filters` `where:` syntax

**Impact on Phase 85:**

The CONTEXT.md scaffold-commit YAML (lines 326-338) literally specifies:
```yaml
- exclude:
    id: java/ssrf
    where: src/main/java/org/ctc/domain/service/FileStorageService.java
```
This will be a no-op. The scaffold-commit baseline scan will surface the SSRF + ZIP-Slip alerts unfiltered. The D-12 "triage commits" phase will then need to either dismiss them via UI (per C-01-B) or revise the config to drop `where:` (per C-01-A).

**Three viable paths (planner picks one before scaffold-commit lands):**

| Path | Mechanism | Pros | Cons |
|------|-----------|------|------|
| **C-01-A — Rule-id-only `exclude`** (RECOMMENDED) | `query-filters: - exclude: { id: java/ssrf }` | In-version-control; reviewable; matches CONTEXT.md "suppress in config" intent; one-line per rule | Suppresses rule across whole codebase, not just the FP site. Future genuine SSRF elsewhere would be silently suppressed by CodeQL (still caught by SpotBugs+find-sec-bugs Phase 81 gate). |
| **C-01-B — UI dismissals + sast-acceptance.md only** | No `query-filters`; each baseline alert dismissed in Security tab with `won't fix` + rationale comment | Surgical per-instance suppression; matches "suppressed (codeql-config + source-marker)" intent of D-16 less cleanly | Dismissals not visible in code-review or version control; new contributor cannot tell at PR time why a finding doesn't fire. Reduced D-19 "Update-on-Triage" auditability. |
| **C-01-C — `advanced-security/filter-sarif` action** | Add post-analyze filter step with `patterns:` syntax | Restores full per-rule-per-file precision; standard endorsed path | Violates D-06 (minimize 3rd-party actions) — reopens that decision; adds external maintenance surface; action is community-maintained (not GitHub-native). |

**Recommendation: C-01-A.** Rationale:
1. Preserves CONTEXT.md D-01/D-02 "suppress in config" intent.
2. Preserves D-06 (no 3rd-party action).
3. The precision loss is real but mitigated: SpotBugs+find-sec-bugs (Phase 81 gate, already active) catches SSRF (`SSRF_SPRING`, `SSRF`) and PATH_TRAVERSAL_IN at the bytecode tier on every push. A future genuine SSRF would still fail Phase-81 gate even though Phase-85 ignores it.
4. The three target rules (`java/ssrf`, `java/zipslip`, `java/path-injection`) have very narrow fire surfaces in this codebase (1-2 files each).

**Action required before scaffold-commit:** Decision-loop through `/gsd-discuss-phase` to confirm path C-01-A (or pick C-01-B/C-01-C). Update CONTEXT.md D-02 wording accordingly. Adjust scaffold-commit `codeql-config.yml` skeleton (drop `where:` lines).

### C-02: `// codeql[<rule>]` inline source directives are not a real CodeQL mechanism

**Finding (HIGH confidence):**

Neither the `security-extended` query pack nor the CodeQL Java extractor recognizes any `// codeql[...]` inline comment as a per-line suppression directive. The literal text in REQUIREMENTS.md SAST-04 (`// codeql[...]` directive) does not map to any documented CodeQL feature. The only documented per-instance suppression mechanisms are:
- UI dismissal with `won't fix` / `false positive` / `used in tests` reason
- `query-filters` in `codeql-config.yml` (metadata-only — see C-01)
- `advanced-security/filter-sarif` post-analyze filter (3rd-party)

**Sources:**
- [VERIFIED: docs.github.com triaging code scanning alerts](https://docs.github.com/en/code-security/code-scanning/managing-code-scanning-alerts/triaging-code-scanning-alerts-in-pull-requests) — describes UI dismissal flow, no mention of inline directive
- [VERIFIED: Cross-check of `// noqa`, `// nosec`, `// codeql-disable` patterns — none are recognized by CodeQL]

**Impact on Phase 85:**

CONTEXT.md D-03 already correctly characterizes the source marker (`// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md`) as **non-directive** — a code-review signal, not a CodeQL directive. The plan's verification step for SAST-04 must verify:

1. Each baseline finding has been bucketed (fixed / suppressed / accepted) — by inspecting `docs/security/sast-acceptance.md` table rows.
2. Each suppressed finding has a matching `// CodeQL FP:` source marker — by `grep -r '// CodeQL FP:'` count matching `sast-acceptance.md` `suppressed` row count.
3. NO requirement to verify literal `// codeql[...]` directive text — that is a REQUIREMENTS.md wording artifact, not an implementable check.

**Action required:** None. CONTEXT.md D-01/D-03 already absorbed this correction (CONTEXT.md `<out_of_scope>` line 27 explicitly says "// codeql[<rule>] inline source directives — these are NOT a supported CodeQL Code-Scanning suppression mechanism"). This research finding confirms that judgment.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| SAST execution (semantic data-flow analysis) | GitHub Actions hosted runner (`ubuntu-latest`) | — | CodeQL bundle runs on the runner; consumes ~5-15 min CPU; produces SARIF artefact. No JVM agent, no runtime tier. |
| Java compilation for CodeQL database | Maven (`./mvnw compile`) | — | CodeQL Java extractor needs compiled `.class` bytecode in `target/classes`; `-DskipTests` keeps build under 2 min; `--no-transfer-progress` reduces log noise. |
| Lombok annotation processing | Maven (lombok 1.18.46 annotation processor) | CodeQL Java extractor | Lombok runs at compile time; CodeQL now natively supports Lombok-generated bytecode (since 2.14.4, Sept 2023) — no `delombok` step needed. |
| SARIF result upload | `github/codeql-action/analyze@v4` | GitHub Code Scanning service | Action posts SARIF to `/repos/{owner}/{repo}/code-scanning/sarifs`; service ingests, deduplicates with existing alerts, surfaces in Security tab. |
| False-positive suppression — config tier | `.github/codeql/codeql-config.yml` `query-filters` | — | Metadata-only (id, kind, severity) — see C-01. Rule-id-level suppression is whole-codebase scope. |
| False-positive suppression — UI tier | GitHub Security tab dismissal | — | Per-alert; comment + reason recorded; requires D-19 "Update-on-Triage" reflection into `sast-acceptance.md`. |
| False-positive documentation | `docs/security/sast-acceptance.md` | Source markers (`// CodeQL FP:`) | Single source of truth for triage decisions; mirrors `config/spotbugs-exclude.xml` rationale-comment pattern. |
| PR-gate enforcement | Inline-bash step in `codeql.yml` after `analyze@v4` | `gh api` against `/repos/.../code-scanning/alerts` | Set-difference HEAD−BASE on `(rule.id, location.path)` tuples; exits 1 if non-empty (excluding schedule trigger). |
| Branch-protection enforcement | GitHub repo settings → branch protection rules | — | OUT OF SCOPE for Phase 85 per D-11 (operator-hoheit). The workflow merely fails; whether that blocks merge depends on operator post-merge configuration. |
| Auto-update of action version | Mend Renovate (hosted) | `renovate.json` packageRule for `github/codeql-action` | Patch auto-merge after 3-day cooldown; minor/major manual via Dependency Dashboard (per D-29, mirrors Phase 84 DEPS-04). |
| Documentation discoverability | `CLAUDE.md` `## Conventions > CodeQL SAST (Code Scanning)` sub-section | `CLAUDE.md ## References` entries | New contributor sees the suppression workflow at the top of the project guide. |

---

## Standard Stack

### Core

| Component | Version | Purpose | Why Standard |
|-----------|---------|---------|--------------|
| `github/codeql-action/init` | `@v4` (latest patch: 4.35.5, May 15 2026) | Initialize CodeQL database, fetch query pack | Floating major tag — GitHub manages safe rollouts; v3 deprecated Dec 2026. [VERIFIED: github.com/github/codeql-action/releases] |
| `github/codeql-action/analyze` | `@v4` (same patch) | Run queries, build SARIF, upload to Security tab | Companion to `init`; same pin policy. [VERIFIED: github.com/github/codeql-action/releases] |
| CodeQL bundle | 2.25.4 (bundled with action `@v4.35.5`) | The CodeQL CLI + Java extractor + query packs | Auto-updates with action floating tag; Java 25 supported since 2.23.1 (Sept 26 2025). [VERIFIED: github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/] |
| Query suite | `security-extended` | OWASP Top 10 + high-confidence taint queries beyond default `security-and-quality` | Stream-4 research lock; recommended for projects with documented attack surface (SSRF, ZIP-Slip, path traversal). [CITED: research/STACK.md §"Stream 4 — SAST"] |
| Language identifier | `java-kotlin` | Triggers Java + Kotlin extractors (Kotlin unused here but identifier covers both) | Canonical replacement for deprecated `java` identifier. [CITED: research/STACK.md line 441-442; docs.github.com/code-scanning languages] |
| `actions/checkout` | `@v6` | Clone repo into runner | Already used in `ci.yml` line 23 — same major version for D-22 consistency. |
| `actions/setup-java` | `@v5` | Install JDK 25 Temurin + maven cache | Already used in `ci.yml` line 26 — same major version. `cache: 'maven'` reuses `~/.m2/repository` across runs (~10 s warm vs ~2 min cold). |

### Supporting

| Component | Purpose | When to Use |
|-----------|---------|-------------|
| `gh api -X GET /repos/.../code-scanning/alerts` | Fetch alerts for SARIF-diff gate-step | After `analyze@v4`; used to compute HEAD − BASE set-difference on `(rule.id, location.path)`. Pagination via `--paginate`. JSON projection via `--jq`. |
| `--paginate` flag on `gh api` | Auto-page through > 30 alerts | Default page size 30 / max 100. For initial baseline + future drift, use `--paginate` to avoid silent truncation. |
| `comm -23 <(...) <(...)` | POSIX set-difference for HEAD − BASE | Both inputs must be sorted (`sort -u`). Lines in HEAD but not BASE → "new" alerts. |
| `jq` (pre-installed on `ubuntu-latest`) | JSON projection of alert tuples | `gh --jq` is sufficient — no external `jq` install step needed. |
| `actions/upload-artifact@v7` (optional) | Persist SARIF for offline triage | Currently used in `ci.yml`; optional for Phase 85 — Security-tab is the canonical store, but artifact upload aids the baseline-triage step. |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `github/codeql-action` standalone workflow | CodeQL job inside `ci.yml` | Stream-4 research mandates standalone — different permissions, different schedule, different failure semantics. Rejected. |
| `language: java-kotlin` | Deprecated `language: java` | `java` was deprecated in 2024 in favour of `java-kotlin`. Using `java` works today but emits deprecation warning. Use `java-kotlin`. |
| `security-extended` query pack | Default `security-and-quality` | Default pack misses some OWASP Top 10 cross-function queries. Stream-4 chose `security-extended` for the project's documented attack surface. |
| Manual `./mvnw compile` | CodeQL `autobuild` | `autobuild` is heuristic and can fail on Lombok + Playwright compile-scope without explicit `mvn` invocation. Stream-4 locks manual build. |
| Inline-bash SARIF-diff | `advanced-security/filter-sarif` | 3rd-party action violates D-06. Inline-bash keeps the workflow self-contained. (See C-01 — different role; filter-sarif filters BEFORE upload, gate-step diffs AFTER upload.) |
| `gh api` from inline-bash | Custom JS / Python script in `.github/scripts/` | D-06 rejects separate-script approach; inline keeps the parser reviewable in one file. |
| Floating `@v4` tag | SHA-pinned `@<sha>` | D-22 + Phase 84 DEPS-04 consistency. GitHub manages safe rollouts under `@v4`; SHA-pin breaks Renovate group rules. |

**Installation:**

```bash
# Phase 85 has NO npm install / pip install / cargo install / mvn install step.
# All work is config files + workflow YAML + docs.
# Step-by-step ordering (D-12 three-phase choreography):
#   1. Create .github/codeql/codeql-config.yml + .github/workflows/codeql.yml (workflow_dispatch only)
#      + docs/security/sast-acceptance.md (skeleton) + edit renovate.json (D-29 packageRule)
#      + edit CLAUDE.md (D-24 sub-section + D-25 references)
#      → commit: "feat(85): scaffold CodeQL workflow (workflow_dispatch only, gate disabled)"
#   2. gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup
#   3. Inspect Security tab + SARIF artifact → triage commits per D-15
#   4. Final-enable commit: extend on: triggers, drop scaffold-disable, add gate-step
#      → commit: "feat(85): activate CodeQL gate on push + pull_request"
#   5. SAST-06 throwaway-branch deliberate-violation test (D-14) BEFORE milestone PR merge
```

**Version verification (run before scaffold-commit):**

```bash
# Verify @v4 floating tag latest patch (currently 4.35.5)
gh api repos/github/codeql-action/releases/latest --jq '.tag_name'

# Verify Java 25 still supported in current bundle (should print Java 25 in extractor docs link)
curl -s https://api.github.com/repos/github/codeql-action/contents/CHANGELOG.md \
  | jq -r .content | base64 -d | grep -A2 "java-kotlin" | head -20
```

---

## Package Legitimacy Audit

> Phase 85 installs NO external packages (no npm/pip/cargo install). All third-party tooling is GitHub-hosted (`github/codeql-action`, `actions/checkout`, `actions/setup-java`) and pre-installed on the GitHub Actions runner (`gh`, `jq`, `bash`). Slopcheck is therefore N/A.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `github/codeql-action` | GitHub Actions | 4+ years (`@v4` released 2025-10-07) | Standard GH action — millions/week | github.com/github/codeql-action | N/A (GitHub-maintained) | Approved |
| `actions/checkout@v6` | GitHub Actions | Existing project dep | n/a | github.com/actions/checkout | N/A | Approved (already in `ci.yml`) |
| `actions/setup-java@v5` | GitHub Actions | Existing project dep | n/a | github.com/actions/setup-java | N/A | Approved (already in `ci.yml`) |

**Packages removed due to slopcheck [SLOP] verdict:** none — no external packages introduced.
**Packages flagged as suspicious [SUS]:** none.

---

## Architecture Patterns

### System Architecture Diagram

```
GitHub event (push / pull_request / cron / workflow_dispatch)
        │
        ▼
  ┌───────────────────────────────────────────────────────────┐
  │ .github/workflows/codeql.yml                              │
  │                                                            │
  │   [concurrency: cancel-in-progress]                       │
  │                                                            │
  │   job: analyze (ubuntu-latest)                            │
  │     permissions: { security-events: write,                │
  │                    contents: read, actions: read }        │
  │                                                            │
  │   step 1: actions/checkout@v6                             │
  │   step 2: actions/setup-java@v5 (java 25, temurin, maven) │
  │   step 3: codeql-action/init@v4                           │
  │             config-file: .github/codeql/codeql-config.yml │
  │             languages: java-kotlin                        │
  │             queries: security-extended                    │
  │   step 4: ./mvnw compile -DskipTests --no-transfer-progress
  │   step 5: codeql-action/analyze@v4                        │
  │             category: "/language:java-kotlin"             │
  │   step 6 (final-enable only):                             │
  │             gate-step: if: github.event_name != 'schedule'│
  │             inline-bash SARIF-diff                        │
  └───────────────┬───────────────────────────────────────────┘
                  │
                  ▼
  ┌──────────────────────────────────────────────────────────┐
  │ GitHub Code Scanning service                              │
  │   • SARIF upload                                          │
  │   • Alerts persisted (Security tab)                       │
  │   • PR-level annotations on changed files                 │
  └──────────────┬────────────────────────────────────────────┘
                 │
                 ▼  (queried by gate-step via `gh api`)
  ┌──────────────────────────────────────────────────────────┐
  │ Gate-step set-difference                                  │
  │   HEAD alerts (state=open, sev>=high, dismissed=null)     │
  │     keyed on (rule.id, location.path)                     │
  │   minus                                                   │
  │   BASE alerts (same filter, ref=refs/heads/master)        │
  │                                                            │
  │   non-empty → exit 1 (PR gate fail)                       │
  │   empty     → exit 0 (PR gate pass)                       │
  └──────────────────────────────────────────────────────────┘
```

The diagram traces the primary use case (PR opened) from event ingestion through analysis to gate verdict. Schedule-triggered runs skip the gate-step (drift-detection only, alerts visible in Security tab).

### Recommended Project Structure

```
.github/
├── workflows/
│   ├── ci.yml                          # UNCHANGED
│   ├── codeql.yml                      # NEW (Phase 85)
│   ├── release.yml                     # UNCHANGED
│   ├── deploy-site.yml                 # UNCHANGED
│   └── mariadb-migration-smoke.yml     # UNCHANGED
└── codeql/
    └── codeql-config.yml               # NEW (Phase 85)

docs/
└── security/                           # NEW directory (Phase 85)
    └── sast-acceptance.md              # NEW (Phase 85)

# Edited:
CLAUDE.md                               # +D-24 sub-section, +D-25 references
renovate.json                           # +D-29 packageRule

# Triage edits (per baseline finding, in source files):
src/main/java/org/ctc/domain/service/FileStorageService.java  # +// CodeQL FP: java/ssrf marker
src/main/java/org/ctc/backup/service/BackupArchiveService.java # +// CodeQL FP: java/zipslip marker
src/main/java/org/ctc/backup/service/BackupImportService.java  # +// CodeQL FP: java/path-injection marker (if baseline fires)
```

### Pattern 1: Standalone CodeQL workflow

**What:** A dedicated `.github/workflows/codeql.yml` (NOT a job in `ci.yml`) with its own triggers, permissions, and concurrency.

**When to use:** Always for CodeQL on a public repo. Three reasons:
1. `security-events: write` permission must be scoped to the analyze job only.
2. CodeQL runs ~5-15 min — slower than the main CI job; isolating it prevents PR-blocking on CI latency.
3. Cron schedule for drift detection is orthogonal to push/PR triggers.

**Example (skeleton matching CONTEXT.md `<specifics>` block + Stream-4 research):**

```yaml
# Source: github/codeql-action documentation + ci.yml sibling pattern
name: CodeQL SAST

on:
  # Final-enable form:
  push:
    branches: [master]
  pull_request:
    branches: [master, "gsd/v1.11-tooling-and-cleanup"]
  schedule:
    - cron: '0 2 * * 0'   # Sunday 02:00 UTC (D-20)
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read         # workflow-level minimum; job-level adds security-events

jobs:
  analyze:
    name: Analyze (java-kotlin)
    runs-on: ubuntu-latest
    permissions:
      security-events: write
      contents: read
      actions: read

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Setup JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Initialize CodeQL
        uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin
          queries: security-extended
          config-file: ./.github/codeql/codeql-config.yml

      - name: Build for CodeQL (compile only, skip tests)
        run: ./mvnw compile --no-transfer-progress -DskipTests

      - name: Perform CodeQL Analysis
        uses: github/codeql-action/analyze@v4
        with:
          category: "/language:java-kotlin"

      - name: Gate on new HIGH/CRITICAL alerts vs base
        if: github.event_name != 'schedule'
        env:
          GH_TOKEN: ${{ github.token }}
        run: bash .github/codeql/sarif-diff-gate.sh
        # (or inline — see D-06 inline-bash form below)
```

**Note on `-Dspring.profiles.active=dev`:** The CONTEXT.md `<specifics>` skeleton (line 307) includes `-Dspring.profiles.active=dev` on the `mvn compile` step. This is **not necessary** for CodeQL purposes — `mvn compile` does NOT start the Spring context, so `spring.profiles.active` is a no-op at compile time. It does no harm either. Recommend dropping it for cleanliness (one fewer thing to explain), but it is not a defect if retained.

### Pattern 2: `codeql-config.yml` rule-id-only filter (C-01-A)

**What:** Suppress entire rule via `query-filters` `exclude` block. No path scoping.

**When to use:** When the rule fires on a small number of well-understood FP sites and the SpotBugs+find-sec-bugs gate (Phase 81) provides defense-in-depth at the bytecode tier.

**Example:**

```yaml
# Source: docs.github.com customizing-your-advanced-setup-for-code-scanning + C-01 correction
name: CTC Manager CodeQL Config

queries:
  - uses: security-extended

# Per-rule whole-codebase suppressions. See docs/security/sast-acceptance.md for rationale on
# each rule. SpotBugs+find-sec-bugs gate (Phase 81) catches the same patterns at bytecode tier.
query-filters:
  - exclude:
      id: java/ssrf
  - exclude:
      id: java/zipslip
  - exclude:
      id: java/path-injection

# Note: where: <path> is NOT a supported field in CodeQL query-filters schema (research C-01).
# Per-path filtering for compiled languages requires advanced-security/filter-sarif (rejected by D-06).
# Trade: whole-codebase rule suppression; SpotBugs+find-sec-bugs Phase 81 covers the same patterns.
```

### Pattern 3: Inline-bash SARIF-diff gate (D-06, D-07, D-28)

**What:** Post-`analyze@v4` step that queries the Code Scanning REST API for alerts on HEAD ref and BASE ref, computes the set difference on `(rule.id, location.path)` tuples, fails the job if non-empty.

**When to use:** Only on `push` + `pull_request` events (skipped on `schedule` per D-10).

**Example (working template — verified `gh api` parameters):**

```bash
# Source: docs.github.com REST code-scanning + research C-01/D-28
# Place inline in codeql.yml as the gate-step.
set -euo pipefail

OWNER_REPO="${GITHUB_REPOSITORY}"
HEAD_REF="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME}}"   # PR head or branch name
BASE_REF="${GITHUB_BASE_REF:-master}"               # PR base or default

# Fetch open HIGH/CRITICAL alerts for a given ref, dismissed alerts excluded.
# - `ref` accepts `refs/heads/<branch>` OR bare `<branch>` (verified docs.github.com).
# - `severity=critical,high` filters at API tier (faster than --jq filter).
# - `--jq` projects to "rule.id|location.path" tuples for set-difference.
fetch_alerts() {
  local ref="$1"
  gh api -X GET "repos/${OWNER_REPO}/code-scanning/alerts" \
    -f "state=open" \
    -f "severity=critical,high" \
    -f "ref=refs/heads/${ref}" \
    --paginate \
    --jq '
      .[]
      | select(.dismissed_at == null
               and (.rule.security_severity_level // "") as $s
               | $s == "high" or $s == "critical")
      | "\(.rule.id)|\(.most_recent_instance.location.path)"
    ' 2>/dev/null | sort -u || true
}

HEAD_TUPLES=$(fetch_alerts "${HEAD_REF}")
BASE_TUPLES=$(fetch_alerts "${BASE_REF}")

# Set-difference: tuples on HEAD but not on BASE.
NEW_TUPLES=$(comm -23 <(echo "${HEAD_TUPLES}") <(echo "${BASE_TUPLES}") || true)

# First-run edge case: BASE has no alerts yet (Security tab empty for master).
# In that case BASE_TUPLES is empty and ALL HEAD_TUPLES become "new" — strict by design (D-09).
# If HEAD_TUPLES is also empty → empty difference → pass.

if [ -n "${NEW_TUPLES}" ]; then
  echo "::error::New HIGH/CRITICAL CodeQL alerts on ${HEAD_REF} vs ${BASE_REF}:"
  echo "${NEW_TUPLES}" | while IFS='|' read -r rule path; do
    echo "::error file=${path}::${rule}"
  done
  exit 1
fi

echo "No new HIGH/CRITICAL CodeQL alerts on ${HEAD_REF} vs ${BASE_REF}."
```

**Verified API parameter behaviour (HIGH confidence):**
- `ref` accepts `refs/heads/<branch>` form OR bare `<branch>` name [VERIFIED: docs.github.com/rest/code-scanning].
- `pr` (integer) is also accepted as an alternative to `ref` for PR-head scoping. The gate-step uses `ref` for symmetry between push and pull_request events; using `pr` would require branching on `github.event_name`.
- `severity` parameter accepts comma-separated `critical,high,medium,low,warning,note,error`. Combining with `state=open` reduces page count.
- `rule.security_severity_level` is the canonical low/medium/high/critical string. Default jq projection uses this; numeric thresholds (>= 7.0 / >= 9.0 per D-08) map cleanly to the categorical labels.
- `--paginate` is needed because default page size is 30. Even a moderate-noise scan can exceed 30 alerts at baseline. Without `--paginate`, the diff would silently miss alerts on later pages.

### Anti-Patterns to Avoid

- **`paths-ignore` for Java source** — does not work for compiled languages [VERIFIED: github/codeql discussion #11220 + josh-ops.com]. Only effective for JS/TS/Python/Ruby. CONTEXT.md D-21 already rejects this for the right reason (redundant with `-DskipTests`).
- **`// codeql[rule]` inline directives** — do not exist as a CodeQL feature (see C-02). The non-directive `// CodeQL FP:` marker from D-03 is the right pattern.
- **CodeQL `autobuild`** — heuristic; can fail on Lombok-heavy or Playwright-heavy projects. Always use manual `./mvnw compile` per Stream-4 research.
- **Workflow-level `security-events: write`** — exposes the write scope to every job. Must be JOB-level only (D-25 + Stream-4 + SAST-03).
- **Pinning to `@vX.Y.Z`** — breaks Phase 84 Renovate floating-major policy. Use `@v4` per D-22.
- **Suppressing rules during scaffold-commit without prior baseline visibility** — even with C-01-A, the planner should let the first baseline scan surface findings BEFORE locking down `query-filters` entries. Triage commits land AFTER the baseline scan, per D-12.
- **Marketplace 3rd-party actions for path filtering** — D-06 rejects. C-01-A or C-01-B are the only Phase-85-compatible paths.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Java SAST | Custom regex / pattern matcher | CodeQL `security-extended` query pack | Semantic taint-tracking with cross-function data flow; regex misses chained taint paths through service-layer composition. |
| SARIF parsing | Custom Python/Node SARIF parser script | `gh api` against `/code-scanning/alerts` | The Code Scanning service already ingests SARIF, normalizes to alert objects, deduplicates. Re-parsing SARIF locally re-implements that pipeline. |
| Set-difference of alerts | Custom JS / Python | POSIX `comm -23 <(sort -u) <(sort -u)` | Pre-installed on `ubuntu-latest`; deterministic; readable. |
| Cron scheduling | Cron service / Lambda | GitHub Actions `schedule:` trigger | Free; runs on the runner platform; no extra infrastructure. |
| Alert dismissal audit trail | Custom DB / wiki page | `docs/security/sast-acceptance.md` + Update-on-Triage discipline (D-19) | Version-controlled, code-review-visible, single source of truth. |
| PR comment annotations | Custom `gh pr comment` script | `::error::` log annotations from gate-step | GitHub Actions natively surfaces `::error::` lines as PR check annotations. |
| Lombok preprocessing for SAST | `delombok` Maven plugin step | None — CodeQL natively supports Lombok since 2.14.4 (Sept 2023) | Adds build time + a synthetic source tree to maintain. Not needed. |
| BCrypt FP suppression (CONTEXT.md research-pitfall-#13 anticipation) | Pre-staged suppression entry | None — no PasswordEncoder bean exists | D-05 TRACKED DEVIATION; verified live in `SecurityConfig.java`. |

**Key insight:** Phase 85's surface area is small because all the heavy lifting (semantic analysis, SARIF persistence, deduplication, UI rendering, alert lifecycle) is performed by the GitHub Code Scanning service. The project-local work is workflow YAML + 4 small config/docs files. Resist the temptation to wrap or re-implement Code Scanning capabilities — every "improvement" duplicates infrastructure GitHub already maintains.

---

## Runtime State Inventory

> Phase 85 is greenfield-CI (adds new files, edits 2 existing files, no rename or refactor). Per the research playbook this section is omitted. The closest analogue — "what live state is affected" — is the Security tab alert backlog itself. The triage-commit phase (D-12.3) processes that backlog. No data migration is needed: each suppression is a code or config change that is re-evaluated on every subsequent scan.

---

## Common Pitfalls

### Pitfall 1: `query-filters` `where:` field silently ignored

**What goes wrong:** The scaffold-commit `codeql-config.yml` contains `query-filters: - exclude: { id: ..., where: <path> }`. The YAML parses; the action does not error; the filter has no effect. Baseline scan surfaces the SSRF + ZIP-Slip alerts unfiltered.

**Why it happens:** CodeQL's query-filters schema does not have a `where:` field for path scoping (see C-01). The field is silently dropped at filter compilation.

**How to avoid:** Use C-01-A (rule-id-only `exclude`) per research recommendation. Validate by running `gh workflow run codeql.yml` on the scaffold-commit and confirming the rule does NOT fire on the expected FP site — if it does, the filter is unrecognized.

**Warning signs:** Baseline scan produces the SSRF/ZIP-Slip alerts despite the `codeql-config.yml` containing entries that "should" suppress them. Treat as a config-schema mismatch, not as a SpotBugs+find-sec-bugs vs CodeQL detector disagreement.

### Pitfall 2: First-run base-branch has no scan history

**What goes wrong:** When the very first PR is opened after the workflow lands, the BASE ref (master) has no prior CodeQL scan. The gate-step's `fetch_alerts master` returns empty. Set-difference computes "all HEAD alerts are new" → gate fails on the Phase-85 PR itself.

**Why it happens:** Chicken-and-egg per CONTEXT.md D-09. The fix is choreography: scaffold-commit lands first (workflow_dispatch only → manual baseline triage → final-enable). At final-enable, master must already have a clean scan history.

**How to avoid:** Strictly follow D-12 three-phase choreography. Between D-12.3 triage commits and D-12.4 final-enable, run `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` and confirm 0 HIGH/CRITICAL alerts. Only then commit final-enable.

**Warning signs:** Phase-85 PR opens but the CodeQL workflow fails on its own first PR run. Look at the gate-step output: if every HEAD alert is in the "new" set, BASE has no history → choreography skipped a step.

### Pitfall 3: Cron-triggered scan fails the gate

**What goes wrong:** Weekly Sunday-02:00-UTC cron run picks up a newly-introduced finding (e.g., a transitive dependency CVE or a new CodeQL rule). The gate-step runs even on cron and exits 1. Repository owner gets a red workflow notification every Sunday morning.

**Why it happens:** Cron is for drift detection (D-10), not for blocking. Forgetting the `if: github.event_name != 'schedule'` guard on the gate-step causes false-alarm failures.

**How to avoid:** The `if:` guard is in CONTEXT.md `<specifics>` line 377 and is also called out in D-10. Plan verification must include a grep for the guard on the final-enable commit.

**Warning signs:** Sunday-morning workflow failures with the gate-step exit code, despite no recent code changes.

### Pitfall 4: SARIF-diff key collisions on rebase

**What goes wrong:** Developer rebases a PR. The same alert (same rule, same file, same line) gets a new alert number internally. Gate-step compares by alert number and flags as "new".

**Why it happens:** Alert numbers are tied to scan instances. Keying the set-difference on `(alert.number)` is brittle.

**How to avoid:** Key on `(rule.id, most_recent_instance.location.path)` per D-28. This is commit-sha-agnostic — rebases don't poison the diff.

**Warning signs:** Gate-step flags as new findings that visibly existed before the rebase. Check the keying expression in the inline-bash script.

### Pitfall 5: BackupImportService.restoreOneTable false-positive expectation

**What goes wrong:** Plan pre-stages a `java/path-injection` suppression on `BackupImportService` (per CONTEXT.md D-04 line 53). The baseline scan does NOT emit such an alert. The suppression entry becomes dead code.

**Why it happens:** `BackupImportService.restoreOneTable` (lines ~668-) uses `ZipFile.getEntry(entryPath)` where `entryPath` comes from `EntityRef.fileName()` — a **trusted manifest value**, not a tainted external input. CodeQL's taint tracking correctly does not flag a trusted-source path. The `BackupArchiveService.assertEntrySafe` site (lines 608-623) DOES handle untrusted ZIP entry names and IS the genuine FP candidate.

**How to avoid:** Plan pre-stages suppressions ONLY based on baseline-scan output, not on theoretical fire surfaces. The CONTEXT.md scaffold-commit skeleton's `BackupImportService` entry (line 338 of CONTEXT.md) should be marked "tentative, confirm in baseline" — and removed if the baseline shows no fire.

**Warning signs:** A pre-staged suppression that, after baseline, has no corresponding alert in the Security tab. The `sast-acceptance.md` row should be deleted in the triage commits, not left as a phantom suppression.

### Pitfall 6: Playwright compile-scope dependency breaks the `mvn compile` step

**What goes wrong:** The build step `./mvnw compile -DskipTests` pulls in Playwright (compile-scope per CLAUDE.md constraint). If Playwright's transitive `com.microsoft.playwright.driver-bundle` fetches OS-specific binaries on first run, the CI runner's `~/.m2` cache may not have them.

**Why it happens:** Playwright's Maven artifact downloads browser binaries on first invocation. On a cold-cache runner, this can add 1-2 minutes to the build step.

**How to avoid:** `actions/setup-java@v5 cache: 'maven'` (D-26) caches `~/.m2/repository` across runs. First Phase-85 workflow run will be slow (~3-5 min build); subsequent runs warm-cache to ~30 s.

**Warning signs:** First baseline workflow run takes >10 min. Check the build step duration; if it's >2 min, the cache is cold.

### Pitfall 7: `application-{prod,docker}.yml` `password: ${DATABASE_PASSWORD}` false positive for hardcoded credential

**What goes wrong:** CodeQL's `java/hardcoded-password` or similar credential query fires on the `password:` line in `application-prod.yml` / `application-docker.yml`.

**Why it happens:** The `${DATABASE_PASSWORD}` placeholder syntax is Spring's standard env-var injection; CodeQL should recognize it as not-a-literal. **In practice (Phase 81 SpotBugs experience),** the bytecode-tier `HARD_CODE_PASSWORD` detector does NOT fire on placeholder values, because the literal is resolved at runtime via `org.springframework.boot.env.PropertySourcesPropertyResolver`. CodeQL's semantic analysis is at least as good as SpotBugs's — expectation: NO false positive here. **If it does fire,** the plan triages it as `accepted` (documented in sast-acceptance.md) or via per-rule suppression in `codeql-config.yml`.

**How to avoid:** Anticipate but don't pre-stage. Let the baseline confirm.

**Warning signs:** A `java/hardcoded-password` (or similar) alert pointing at `application-*.yml` lines. Easy triage; documented in this pitfall.

### Pitfall 8: `JdbcTemplate.batchUpdate` in `BackupImportService` false-positive for `java/sql-injection`

**What goes wrong:** `BackupImportService.restoreOneTable` calls `EntityRestorer.restore(batch, jdbcTemplate)`, which under the hood uses `JdbcTemplate.batchUpdate(String sql, BatchPreparedStatementSetter)`. The SQL strings are built dynamically per-entity (table name + column list). CodeQL may flag them as `java/sql-injection`.

**Why it happens:** The SQL is built from `EntityRef.tableName()` (trusted, comes from `@Entity` metadata at startup) and a `BackupSchema` columns list (trusted, code-defined). No user-controlled value flows into the SQL string. CodeQL's taint tracking should recognize this — but the dynamic concatenation pattern is a known false-positive surface.

**How to avoid:** If baseline fires `java/sql-injection` on `BackupImportService` or `EntityRestorer`, triage as `suppressed` with rationale "SQL built from trusted Entity metadata, no user input flows in". Add `// CodeQL FP:` source marker + sast-acceptance.md row.

**Warning signs:** `java/sql-injection` alert with location in `backup.service.*` or `backup.serialization.*` packages.

---

## Code Examples

### Common Operation 1: Scaffold-commit `codeql-config.yml` (revised for C-01-A)

```yaml
# Source: docs.github.com workflow-configuration-options-for-code-scanning + research C-01
# Location: .github/codeql/codeql-config.yml

name: CTC Manager CodeQL Config

queries:
  - uses: security-extended

# Per-rule whole-codebase suppressions.
#
# Rationale per rule lives in docs/security/sast-acceptance.md. Each rule below has a
# narrow FP surface in this codebase that is independently caught by SpotBugs +
# find-sec-bugs (Phase 81 gate). The suppression here prevents Code Scanning Security-tab
# noise; the bytecode-tier gate provides defense-in-depth.
#
# IMPORTANT: query-filters does not support per-path filtering for compiled languages.
# The `where: <path>` field from CONTEXT.md D-02 is NOT a valid CodeQL config key.
# See .planning/phases/85-codeql-sast/85-RESEARCH.md C-01 for the full analysis.
query-filters:
  # SSRF in FileStorageService.storeFromUrl — startsWith-based hostname blocklist
  # (FileStorageService.java:125-156) not recognized as sanitizer.
  # SpotBugs SSRF_SPRING,SSRF suppression in config/spotbugs-exclude.xml:217-227 mirrors this.
  - exclude:
      id: java/ssrf

  # ZIP-Slip in BackupArchiveService.assertEntrySafe — PathTraversalGuard.assertWithin
  # (BackupArchiveService.java:608-623) is the sanitizer but CodeQL cannot trace through the
  # static utility delegation. SpotBugs PATH_TRAVERSAL_IN suppression in
  # config/spotbugs-exclude.xml:240-246 mirrors this.
  - exclude:
      id: java/zipslip

  # PATH_INJECTION in BackupArchiveService + FileStorageService — same defense pattern.
  # Confirm baseline-scan emits this rule (vs java/zipslip) before locking in.
  - exclude:
      id: java/path-injection
```

### Common Operation 2: Scaffold-commit `codeql.yml` (workflow_dispatch only)

```yaml
# Source: research/STACK.md §"Stream 4 — SAST" + CONTEXT.md <specifics> + Pattern 1 above
# Location: .github/workflows/codeql.yml
# Scaffold-commit form — D-13. Final-enable form adds push/pull_request/schedule triggers
# and the gate-step.

name: CodeQL SAST

on:
  workflow_dispatch:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  analyze:
    name: Analyze (java-kotlin)
    runs-on: ubuntu-latest
    permissions:
      security-events: write
      contents: read
      actions: read

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Setup JDK 25
        uses: actions/setup-java@v5
        with:
          java-version: '25'
          distribution: 'temurin'
          cache: 'maven'

      - name: Initialize CodeQL
        uses: github/codeql-action/init@v4
        with:
          languages: java-kotlin
          queries: security-extended
          config-file: ./.github/codeql/codeql-config.yml

      - name: Build for CodeQL (compile only, skip tests)
        run: ./mvnw compile --no-transfer-progress -DskipTests

      - name: Perform CodeQL Analysis
        uses: github/codeql-action/analyze@v4
        with:
          category: "/language:java-kotlin"
```

### Common Operation 3: Final-enable triggers + gate-step (D-12.4)

```yaml
# Source: CONTEXT.md <specifics> lines 406-416 + Pattern 3 above + D-10/D-28
# Patches the scaffold codeql.yml to land triggers + gate.

on:
  push:
    branches: [master]
  pull_request:
    branches: [master, "gsd/v1.11-tooling-and-cleanup"]
  schedule:
    - cron: '0 2 * * 0'   # Sunday 02:00 UTC (D-20)
  workflow_dispatch:

# ... (job block unchanged through the analyze step) ...

      - name: Gate on new HIGH/CRITICAL alerts vs base
        if: github.event_name != 'schedule'
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          set -euo pipefail
          OWNER_REPO="${GITHUB_REPOSITORY}"
          HEAD_REF="${GITHUB_HEAD_REF:-${GITHUB_REF_NAME}}"
          BASE_REF="${GITHUB_BASE_REF:-master}"

          fetch_alerts() {
            local ref="$1"
            gh api -X GET "repos/${OWNER_REPO}/code-scanning/alerts" \
              -f "state=open" \
              -f "severity=critical,high" \
              -f "ref=refs/heads/${ref}" \
              --paginate \
              --jq '
                .[]
                | select(.dismissed_at == null
                         and ((.rule.security_severity_level // "") as $s
                              | $s == "high" or $s == "critical"))
                | "\(.rule.id)|\(.most_recent_instance.location.path)"
              ' 2>/dev/null | sort -u || true
          }

          HEAD_TUPLES=$(fetch_alerts "${HEAD_REF}")
          BASE_TUPLES=$(fetch_alerts "${BASE_REF}")
          NEW_TUPLES=$(comm -23 <(echo "${HEAD_TUPLES}") <(echo "${BASE_TUPLES}") || true)

          if [ -n "${NEW_TUPLES}" ]; then
            echo "::error::New HIGH/CRITICAL CodeQL alerts on ${HEAD_REF} vs ${BASE_REF}:"
            echo "${NEW_TUPLES}" | while IFS='|' read -r rule path; do
              echo "::error file=${path}::${rule}"
            done
            exit 1
          fi
          echo "No new HIGH/CRITICAL CodeQL alerts on ${HEAD_REF} vs ${BASE_REF}."
```

### Common Operation 4: `renovate.json` packageRule addition (D-29)

```json
{
  "description": "github/codeql-action patch updates automerge after 3-day cooldown (Phase 85 D-29). Minor/major require Dependency Dashboard approval — major v3→v4-style transitions historically required workflow file edits.",
  "matchPackageNames": ["github/codeql-action"],
  "matchUpdateTypes": ["patch"],
  "automerge": true,
  "minimumReleaseAge": "3 days"
},
{
  "description": "github/codeql-action minor/major bumps via Dependency Dashboard — major (e.g., @v4→@v5) may require workflow file changes; minor may introduce new queries that surface latent findings.",
  "matchPackageNames": ["github/codeql-action"],
  "matchUpdateTypes": ["minor", "major"],
  "dependencyDashboardApproval": true,
  "automerge": false
}
```

**Note:** Insert AFTER the existing "Patch updates automerge after CI passes" catch-all rule in `renovate.json:101-105` is NOT correct — that rule is the generic catch-all and would already auto-merge `github/codeql-action` patches. The new rules must be inserted BEFORE the catch-all so the more-specific match wins. Renovate evaluates `packageRules` top-to-bottom; later rules override earlier ones with the same match. To enforce the 3-day cooldown specifically, place the new rules at the END of `packageRules[]` so they take precedence over the generic patch-automerge rule.

### Common Operation 5: CLAUDE.md `## Conventions` sub-section (D-24)

```markdown
### CodeQL SAST (Code Scanning)

* **Gate:** CodeQL `@v4` runs on push to master, pull_request, and Sunday-02:00-UTC cron via `.github/workflows/codeql.yml`. The inline SARIF-diff gate-step fails PRs that introduce new HIGH/CRITICAL `security-severity` alerts versus the base branch (skipped on weekly cron — drift detection only).
* **Suppressions** live in `.github/codeql/codeql-config.yml` `query-filters` (rule-id-level whole-codebase suppression — CodeQL config does not support per-path filtering for compiled languages). Every suppressed finding requires a `// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md` source marker at the protected method/block AND a matching table row in `docs/security/sast-acceptance.md`. UI dismissals are equally valid but must also be reflected in `sast-acceptance.md` (Update-on-Triage discipline).
* **Acceptance doc** at `docs/security/sast-acceptance.md` is the single source of truth for SAST-Triage decisions: per-pattern sections (SSRF | ZIP-Slip | BCrypt-N/A | Others), per-finding table rows with Alert-ID + Rule + Location + Bucket + Rationale + Source-Marker. Every suppression PR must include a parallel `sast-acceptance.md` edit.
```

Insert AFTER the existing `### Static Analysis (SpotBugs + find-sec-bugs)` block in CLAUDE.md.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `language: java` identifier | `language: java-kotlin` identifier | 2024 | Old identifier still works but emits deprecation warning. Use `java-kotlin` for new workflows. |
| `codeql-action@v3` | `codeql-action@v4` | 2025-10-07 | v3 deprecated December 2026, no further updates. Use `@v4` floating tag. [VERIFIED: github.com/github/codeql-action/releases] |
| Lombok-annotated source files skipped | Lombok natively supported | CodeQL 2.14.4 / Sept 2023 | No `delombok` step needed. Spring Boot + Lombok projects scan correctly. [VERIFIED: github.blog/changelog/2023-09-01-code-scanning-with-codeql-improves-support-for-java-codebases-that-use-project-lombok/] |
| Java 25 unsupported | Java 25 supported | CodeQL 2.23.1 / Sept 26 2025 | Compact source files (JEP 512), module imports, etc. all recognized. Current bundle 2.25.4 includes additional Java improvements. [VERIFIED: github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/] |
| `query-filters` with `paths-ignore` (compiled languages) | `query-filters` metadata-only + `advanced-security/filter-sarif` for path scoping | n/a (always been the case for compiled languages) | Common misconception that `paths-ignore` works for Java — it does not. See C-01. |

**Deprecated / outdated:**

- `language: java` (use `java-kotlin`).
- `codeql-action@v3` (use `@v4`).
- `@SuppressWarnings("CodeQL")` annotations — never supported, ignored by the extractor.
- `// codeql[<rule>]` inline directives — never supported (see C-02).
- `delombok` preprocessing for SAST — unnecessary since 2.14.4.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Baseline scan with C-01-A config will produce no HIGH/CRITICAL findings beyond the SSRF + ZIP-Slip triade | Pitfall 5, Pitfall 7, Pitfall 8 | Triage volume in D-12.3 exceeds expectation; planner may need an additional plan-commit for extra suppressions. Mitigated by D-15 soft-scope (all baseline findings stay within Phase 85). |
| A2 | CodeQL `security-extended` does NOT include a rule named `java/hardcoded-password-in-spring-yaml` or similar that fires on `${ENV}` placeholders | Pitfall 7 | If a false positive fires on `application-*.yml`, triage as `accepted` or per-rule suppress. Easy to handle in D-12.3. |
| A3 | `JdbcTemplate.batchUpdate` in `BackupImportService` does NOT trigger `java/sql-injection` because CodeQL recognizes trusted-source data flow from `@Entity` metadata | Pitfall 8 | If it does fire, triage as `suppressed` with rationale. Adds 1-2 triage commits to D-12.3. |
| A4 | CodeQL's `java/ssrf` query is the canonical rule that fires on `FileStorageService.storeFromUrl` (rather than `java/server-side-request-forgery-from-uri` or a related variant) | Standard Stack, Pattern 2 | If a different rule ID fires, the `codeql-config.yml` `exclude: id: java/ssrf` is a no-op. Re-run baseline, adjust rule ID, re-commit config. 1 extra triage commit. |
| A5 | The chicken-and-egg first-run problem is solved by D-12 choreography (scaffold → manual baseline → triage → final-enable on master with clean state) | Pitfall 2 | If the operator merges Phase-85 PR before running manual baseline on master, the first push-trigger run could find latent alerts and fail. Mitigation: D-12 step ordering is mandatory; document in plan as a hard-prerequisite checkpoint. |
| A6 | The cron schedule `0 2 * * 0` actually fires Sunday 02:00 UTC within ±30 min (GitHub Actions cron is best-effort) | Pattern 1 | If the cron drifts to a peak hour with heavy contention, drift-detection scans may delay 1-2 hours. Acceptable — drift detection is not time-critical. |
| A7 | The renovate.json packageRule insertion order (new rules AFTER the catch-all patch-automerge) achieves the desired override | Common Operation 4 | If the catch-all wins, codeql-action patches auto-merge without 3-day cooldown. Verify via Renovate dry-run on the PR before merging Phase-85 scaffold-commit. |
| A8 | `actions/setup-java@v5 cache: 'maven'` correctly caches `~/.m2/repository` for this codebase (already verified working in `ci.yml`) | Pitfall 6 | If cache miss is structural, every CodeQL run takes 2+ min build → exceeds 15 min total. Mitigation: `ci.yml` already exercises this cache config successfully — same JDK, same Maven version, same lock file. |
| A9 | The `gh api ref=refs/heads/<branch>` parameter scopes alerts to that branch's most-recent-instance — i.e., it returns alerts where the latest scan instance was on that ref | Pattern 3, gate-step | If `ref` semantics differ (e.g., returns alerts visible at the time the ref was last scanned, regardless of where alert was introduced), the set-difference logic is invalid. Mitigation: SAST-06 deliberate-violation test (D-14) is the empirical confirmation; if gate-step doesn't fire on the throwaway PR, the API semantics are different from assumed. |
| A10 | Phase 85 does not introduce any new HIGH-severity finding outside the SSRF/ZIP-Slip triade in the production codebase | Summary, Pitfall 5 | Phase-85 baseline scan finds an unexpected genuine HIGH vulnerability (e.g., a SpEL injection surface that SpotBugs missed). Then it's a real Phase-85-absorbed fix per D-15. Soft-scope OK; affects triage-commit count, not approach. |

**Recommendation:** All 10 assumptions are reasonable but should be confirmed empirically during the D-12.2 baseline-inspection step. The plan must include an explicit "baseline-inspection" task that documents the actual rule IDs fired, the actual file locations, and any deviations from the assumed list — into `85-VERIFICATION.md` BEFORE triage commits land.

---

## Open Questions

1. **Should the renovate.json packageRule placement be at the start or end of `packageRules[]`?**
   - What we know: Renovate evaluates rules top-to-bottom; later rules override earlier ones. The existing generic patch-automerge catch-all (`renovate.json:101-105`) currently applies to `github/codeql-action`.
   - What's unclear: Whether placing the new rules at end (most-specific-last, overrides catch-all) or start (most-specific-first, conventional readability) is the project preference.
   - Recommendation: Place at END so the more-specific rule wins. Document the placement rationale in the rule's `"description"` field. (Common Operation 4 above takes this position.)

2. **Should the scaffold-commit `codeql-config.yml` include the rule-id suppressions, or wait until after baseline?**
   - What we know: D-04 says "pre-stage SSRF + ZIP-Slip triade". D-12.1 says "scaffold commit includes codeql-config.yml with SSRF + ZIP-Slip pre-stage".
   - What's unclear: Whether the pre-staged rule IDs (`java/ssrf`, `java/zipslip`, `java/path-injection`) are the right ones — A4 above flags uncertainty about variant rule IDs.
   - Recommendation: Pre-stage with the canonical IDs from this research. If baseline emits a variant rule ID (e.g., `java/server-side-request-forgery-from-uri`), adjust in a triage commit. The cost of a wrong pre-stage is one extra triage commit; the benefit of pre-staging is keeping the first baseline noise-free.

3. **Should the gate-step inline-bash be extracted to `.github/codeql/sarif-diff-gate.sh` even though D-06 prefers inline?**
   - What we know: D-06 explicitly rejects "separate `.github/scripts/` file" for the parser. The argument: inline keeps the workflow self-contained.
   - What's unclear: If the parser grows beyond ~30 lines during planning (edge-case handling for first-run, rate-limiting, etc.), the inline form becomes unreadable in code review.
   - Recommendation: Stick with inline per D-06 unless the script exceeds 50 lines. If it does, escalate to discuss-phase before plan-execution. (The Pattern 3 template above is ~35 lines and within budget.)

4. **Should the throwaway-branch deliberate-violation test (SAST-06) use SQLi or path-traversal?**
   - What we know: D-14 leaves the choice to the planner. Both `java/sql-injection` (sev 9.8) and `java/path-injection` (sev 7.5) are HIGH-severity.
   - What's unclear: Which one fires fastest and most reliably on a single-line test class.
   - Recommendation: `java/sql-injection`. Single-line pattern:
     ```java
     public static String unsafe(HttpServletRequest req, Statement stmt) throws Exception {
       return stmt.executeQuery("SELECT * FROM users WHERE id = " + req.getParameter("id")).toString();
     }
     ```
     SQLi fires deterministically; path-injection has more sanitizer recognition that can sometimes false-negative.

5. **Does the Phase-85 PR need to include the throwaway-branch test (D-14) as part of `85-VERIFICATION.md`, or is it sufficient to capture the gh-run output URL?**
   - What we know: D-14.6 says "Capture: `gh run view` output (first 30 lines) + Security tab alert screenshot pointer into `85-VERIFICATION.md`".
   - What's unclear: Whether the captured artifact must be the full SARIF or just the run-log excerpt.
   - Recommendation: Run-log excerpt is sufficient. The captured evidence is "gate-step exit code 1 + error annotation with rule.id + path". Full SARIF adds bulk without informational value.

---

## Environment Availability

> Phase 85 runs entirely on GitHub-hosted infrastructure (`ubuntu-latest` runner) — no local-environment dependencies for the workflow execution. Local execution prerequisites for the operator are minimal.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| `gh` CLI | Operator `gh workflow run` invocations (D-12.1, D-12.3) + SAST-06 throwaway draft-PR creation | ✓ (verified in CLAUDE.md Git Workflow section) | gh 2.x | — |
| `git` | Branch operations on `gsd/v1.11-tooling-and-cleanup` | ✓ (project-standard) | git 2.x | — |
| `./mvnw` (Maven wrapper) | Workflow `mvn compile` step on the runner — not locally | ✓ (in repo) | 3.9.x (wrapper-managed) | — |
| `java` (JDK 25 Temurin) | Workflow on the runner — not locally | ✓ on `ubuntu-latest` via `actions/setup-java@v5` | 25 (Temurin) | — |
| GitHub Actions hosted runner | Workflow execution | ✓ (public repo, GitHub-provided) | `ubuntu-latest` | — |
| Mend Renovate App | D-29 packageRule consumption | ✓ (installed in Phase 84) | hosted SaaS | — |
| `jq` | Workflow `gh api --jq` projection | ✓ (pre-installed on `ubuntu-latest`) | 1.6+ | — |

**Missing dependencies with no fallback:** none.

**Missing dependencies with fallback:** none.

**Local-operator verification commands (run before scaffold-commit):**

```bash
# Verify gh CLI authenticated to jegr78/ctc-manager
gh auth status

# Verify @v4 latest patch (informational)
gh api repos/github/codeql-action/releases/latest --jq '.tag_name'

# Verify the SecurityConfig invariant (no PasswordEncoder bean exists)
grep -r "PasswordEncoder\|BCrypt" src/main/java/ | grep -v "test" || echo "OK — no PasswordEncoder, D-05 N/A confirmed"
```

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | None for SAST-XX — Phase 85 has no Java unit/IT/E2E test surface (CI workflow + config files + docs only). Verification is via static-grep checks + workflow-run smoke tests. |
| Config file | n/a |
| Quick run command | `./mvnw verify -Pe2e` (final phase-end smoke — confirms Phase 85 did not regress existing tests) |
| Full suite command | `./mvnw verify -Pe2e` |
| Phase gate | Workflow `gh workflow run codeql.yml` produces 0 new HIGH/CRITICAL findings on `gsd/v1.11-tooling-and-cleanup` + SAST-06 throwaway-branch test fails the gate as expected. |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SAST-01 | `.github/workflows/codeql.yml` exists, runs on push/PR/cron | static-grep + YAML lint | `grep -E "^on:" .github/workflows/codeql.yml && grep -E "schedule|push|pull_request" .github/workflows/codeql.yml` | Wave 0 (codeql.yml does not exist yet) |
| SAST-02 | Manual `./mvnw compile -DskipTests --no-transfer-progress` build step | static-grep | `grep -F "./mvnw compile" .github/workflows/codeql.yml \| grep -F -- "-DskipTests" \| grep -F -- "--no-transfer-progress"` | Wave 0 |
| SAST-03 | Job-level permissions block with `security-events: write` | static-grep + YAML parse | `awk '/^  analyze:/,/^[a-z]/' .github/workflows/codeql.yml \| grep -A4 "permissions:" \| grep -F "security-events: write"` | Wave 0 |
| SAST-04 | Each finding bucketed (fixed/suppressed/accepted) with table row in sast-acceptance.md | markdown-table grep | `grep -c "^\|" docs/security/sast-acceptance.md` ≥ N where N = baseline finding count; cross-check each `// CodeQL FP:` source marker has a matching table row | Wave 0 (sast-acceptance.md does not exist) |
| SAST-05 | SSRF/ZIP-Slip/BCrypt-N/A classified | section grep | `grep -E "^## (SSRF|ZIP-Slip|BCrypt)" docs/security/sast-acceptance.md \| wc -l` ≥ 3 | Wave 0 |
| SAST-06 | Gate fails on deliberate violation (throwaway-branch) | manual / log-capture | Capture `gh run view <run-id>` output in `85-VERIFICATION.md` showing exit 1 + `::error::` annotation with rule.id | Wave 0 (85-VERIFICATION.md will be created by planner) |

### Sampling Rate

- **Per task commit:** `./mvnw test-compile` for any Java edits; static-grep for YAML/config edits.
- **Per wave merge:** `./mvnw verify -Pe2e` after triage-commits land; `gh workflow run codeql.yml` to confirm 0 HIGH/CRITICAL.
- **Phase gate:** Full suite + final-enable workflow run + SAST-06 throwaway test before milestone PR merge.

### Wave 0 Gaps

- [ ] `.github/workflows/codeql.yml` — covers SAST-01, SAST-02, SAST-03 (created in scaffold-commit)
- [ ] `.github/codeql/codeql-config.yml` — covers SAST-04, SAST-05 mechanism (created in scaffold-commit)
- [ ] `docs/security/sast-acceptance.md` — covers SAST-04, SAST-05 evidence (created in scaffold-commit, populated in triage commits)
- [ ] `85-VERIFICATION.md` — covers SAST-06 evidence (created by planner, populated by executor)
- [ ] Test framework install: NONE — no Java test added in Phase 85

---

## Security Domain

> `security_enforcement` is implicit-enabled per project convention (CLAUDE.md "Architectural Principles > production environment is secured" + Phase 81 SpotBugs gate active). Phase 85 IS the security domain phase for v1.11; the section below maps Phase-85 controls to ASVS.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V1 Architecture | yes | Threat modelling is project-implicit; Phase 85 adds the SAST gate as a layered defense. |
| V2 Authentication | no | Phase 85 does not touch auth surface. (Project: HTTP Basic Auth via `httpBasic(Customizer.withDefaults())` in `SecurityConfig.java`; D-05 confirms no PasswordEncoder bean.) |
| V3 Session Management | no | Phase 85 does not touch session surface. |
| V4 Access Control | partial | Phase 85 ENFORCES least-privilege at workflow level: job-only `security-events: write`, workflow-level `contents: read` only. SAST-03 maps directly. |
| V5 Input Validation | yes (downstream) | CodeQL's `security-extended` enforces V5 patterns (taint tracking through input boundaries). Existing project defenses (SSRF blocklist, ZIP-Slip guard, multipart validation) ARE the V5 controls; CodeQL is the auditor. |
| V6 Cryptography | n/a | No new crypto code in Phase 85. |
| V7 Error Handling & Logging | n/a | No new logging in Phase 85. |
| V8 Data Protection | n/a | No new data flows in Phase 85. |
| V10 Malicious Code | yes | Phase 85's purpose: detect known malicious-code patterns in this codebase via CodeQL. |
| V14 Configuration | yes | Workflow YAML hardening (concurrency, permissions, version-pinning policy) — D-22, D-26, D-27, SAST-03 all map here. |

### Known Threat Patterns for Spring Boot 4 / Java 25 / public-repo

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Server-Side Request Forgery (SSRF) | Tampering, Information Disclosure | `FileStorageService.storeFromUrl` hostname blocklist (existing) + CodeQL `java/ssrf` detection + Phase-81 SpotBugs `SSRF_SPRING`,`SSRF` gate (existing). Triple-layer defense. |
| ZIP-Slip path traversal | Tampering | `BackupArchiveService.assertEntrySafe` via `PathTraversalGuard.assertWithin` (existing) + CodeQL `java/zipslip` detection + Phase-81 SpotBugs `PATH_TRAVERSAL_IN` gate. Triple-layer. |
| SQL injection via JDBC / JdbcTemplate | Tampering | Parameterized queries (existing pattern); CodeQL `java/sql-injection` checks; SpotBugs `SQL_INJECTION_*` family. Phase-85 baseline-scan will confirm BackupImportService passes. |
| SpEL / OGNL injection via Thymeleaf | Tampering | Thymeleaf 3.1.5 pin (CVE-2026-40478 mitigated) + SpEL pattern validation in TemplateEditorService (existing); CodeQL `java/spel-expression-injection` checks. |
| XSS in Thymeleaf templates | Tampering | Thymeleaf default-escape + Content-Security-Policy headers (where applicable); CodeQL `java/xss` family. |
| Mass assignment via @ModelAttribute | Tampering | Form DTOs (`MatchdayForm` etc.) instead of JPA entities in controllers (CLAUDE.md "DTOs instead of Entities" principle); CodeQL `java/spring-mass-assignment`. |
| Hardcoded credentials in YAML | Information Disclosure | `${ENV}` placeholder injection in `application-{prod,docker}.yml`; SpotBugs `HARD_CODE_PASSWORD` already passes; CodeQL `java/hardcoded-credential-*` likely passes — confirm in baseline. |
| CSRF on POST endpoints | Spoofing | Spring Security CSRF tokens active on AJAX POSTs + backup POST endpoints (existing); CodeQL `java/spring-csrf-disabled` checks. |
| Workflow permission over-scoping | Elevation of Privilege | Job-level `permissions:` with explicit `security-events: write` only on `analyze` job (Phase-85 SAST-03); workflow-level minimum `contents: read`. Mitigated by config. |

**Phase-85 ASVS net result:** ADDS a third-tier security control (SAST at source-code data-flow level) on top of:
- Tier 1 — runtime defenses in code (SSRF blocklist, ZIP-Slip guard, CSRF tokens, etc.)
- Tier 2 — bytecode-level SAST via SpotBugs + find-sec-bugs (Phase 81 gate)

Triple-layer defense; CodeQL's value is finding cross-function taint paths that Tier-1 and Tier-2 miss.

---

## Sources

### Primary (HIGH confidence)
- [CITED: github.com/github/codeql-action/releases] — `@v4` latest patch `v4.35.5` (May 15 2026) bundles CodeQL 2.25.4
- [CITED: github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/] — Java 25 support confirmation
- [CITED: github.blog/changelog/2023-09-01-code-scanning-with-codeql-improves-support-for-java-codebases-that-use-project-lombok/] — Lombok native support since 2.14.4
- [CITED: codeql.github.com/codeql-query-help/java/java-ssrf/] — `java/ssrf` security-severity 9.1
- [CITED: codeql.github.com/codeql-query-help/java/java-zipslip/] — `java/zipslip` security-severity 7.5
- [CITED: codeql.github.com/codeql-query-help/java/java-path-injection/] — `java/path-injection` security-severity 7.5
- [CITED: docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/customizing-your-advanced-setup-for-code-scanning] — `query-filters` schema (id, kind, problem.severity only; NO `where:` for path)
- [CITED: docs.github.com/en/rest/code-scanning/code-scanning?apiVersion=2022-11-28] — `/code-scanning/alerts` endpoint parameters (state, severity, ref, pr, sort, before, after, paginate)
- [CITED: docs.github.com/en/actions/writing-workflows/choosing-when-your-workflow-runs/events-that-trigger-workflows] — `github.event_name == 'schedule'` semantics
- [VERIFIED via repo read: `.github/workflows/ci.yml` lines 23, 26-30] — `actions/checkout@v6`, `actions/setup-java@v5 cache: 'maven'` already in use
- [VERIFIED via repo read: `src/main/java/org/ctc/admin/SecurityConfig.java`] — no PasswordEncoder bean (D-05 evidence)
- [VERIFIED via repo read: `src/main/java/org/ctc/domain/service/FileStorageService.java:86-156`] — SSRF defense site
- [VERIFIED via repo read: `src/main/java/org/ctc/backup/service/BackupArchiveService.java:608-623`] — ZIP-Slip defense site
- [VERIFIED via repo read: `src/main/java/org/ctc/backup/service/BackupImportService.java:660-672`] — ZipFile.getEntry uses TRUSTED manifest path, may not fire CodeQL
- [VERIFIED via repo read: `config/spotbugs-exclude.xml:217-246`] — Phase-81 rationale templates for SSRF + PATH_TRAVERSAL_IN
- [VERIFIED via repo read: `renovate.json:1-108`] — existing packageRules + insertion-order convention for D-29 addition

### Secondary (MEDIUM confidence — verified with primary source)
- [VERIFIED: github.com/github/codeql/discussions/11220] — maintainer (`aibaars`) guidance on path-specific filtering via `advanced-security/filter-sarif`
- [VERIFIED: josh-ops.com/posts/github-codeql-ignore-files/] — `paths-ignore` does not work for compiled languages, only `filter-sarif`
- [VERIFIED: github.com/github/codeql/blob/main/.github/codeql/codeql-config.yml] — GitHub's own CodeQL config uses `paths-ignore` only, no `query-filters where:` syntax

### Tertiary (LOW confidence — single source, flagged for empirical confirmation)
- [ASSUMED: github.blog/changelog 2026-05-12 CodeQL 2.25.4 release] — current bundle includes additional Java improvements over 2.23.1; specifics not verified against the project's exact rule firing patterns. Will be empirically confirmed at baseline-scan time.
- [ASSUMED: github.com/github/codeql/issues/4984 status post-2023] — issue was open in Jan 2021 about Lombok skipping; resolved in 2023 changelog. Recent comments not fetched (LOW need — changelog confirmation is sufficient).

---

## Metadata

**Confidence breakdown:**
- Action version + bundle: HIGH — `gh api releases` + GitHub Changelog primary
- Canonical rule IDs (`java/ssrf`, `java/zipslip`, `java/path-injection`): HIGH — codeql.github.com query-help page primary
- CodeQL Java 25 + Lombok support: HIGH — GitHub Changelog primary
- `codeql-config.yml` schema: HIGH for the metadata-filter shape; HIGH for the NEGATIVE finding "no `where:` for path filtering" — three independent sources (docs.github.com + maintainer discussion + GitHub's own usage)
- REST API alert parameters (`ref`, `pr`, `state`, `severity`): HIGH after second fetch — initial fetch was incomplete
- SARIF-diff first-run behaviour: MEDIUM — no canonical example exists; pattern is project-specific glue code, validated empirically by SAST-06 throwaway test (D-14)
- Pitfall 5 (`BackupImportService` likely no fire): MEDIUM — based on live code read showing `entryPath` comes from trusted manifest; empirical confirmation at baseline
- Pitfalls 7 + 8 (false-positive anticipations on yaml-placeholder + JdbcTemplate): MEDIUM — pattern recognition from Phase-81 SpotBugs experience; empirical confirmation at baseline

**Research date:** 2026-05-17
**Valid until:** 2026-06-17 (30 days for stable CodeQL bundle; re-check `@v4` latest patch and CodeQL bundle version if Phase 85 planning slips beyond)
