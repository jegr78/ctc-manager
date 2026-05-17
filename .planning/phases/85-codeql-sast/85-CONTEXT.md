# Phase 85: CodeQL SAST - Context

**Gathered:** 2026-05-17
**Status:** Ready for planning

<domain>
## Phase Boundary

Stand up a standalone `.github/workflows/codeql.yml` workflow (GitHub `codeql-action@v4`, `language: java-kotlin`, query suite `security-extended`) that uploads SARIF results to the GitHub Security tab and gates pull-request merges on **new** HIGH/CRITICAL security-severity findings. The workflow is completely separate from `.github/workflows/ci.yml` (per Stream-4 research recommendation), uses a manual `./mvnw compile --no-transfer-progress -DskipTests` build step (not autobuild — protects Lombok annotation processing and Playwright compile-scope deps), and ships with a pre-staged `.github/codeql/codeql-config.yml` carrying `query-filters` for the three known false-positive sites (SSRF in `FileStorageService`, ZIP-Slip defenses in `BackupArchiveService` + `BackupImportService`). Triage choreography mirrors Phase 81 STAT-05: scaffold (workflow_dispatch-only) → baseline scan → live triage commits (each Alert either fixed or suppressed with rationale) → final-enable commit that turns on `push` + `pull_request` + `schedule` triggers and activates the inline-bash SARIF gate-step. The Phase-85 PR is verified by a throwaway-branch deliberate-violation test (SAST-06) before merge.

**In scope:**
- `.github/workflows/codeql.yml` standalone workflow with three triggers (push to master, pull_request, weekly cron Sunday 02:00 UTC) and a job-level `permissions: { security-events: write, contents: read, actions: read }` block (NOT workflow-level)
- `.github/codeql/codeql-config.yml` pre-staged with `query-filters` covering SSRF + ZIP-Slip triade
- Inline-bash SARIF-diff gate step in `codeql.yml` after `codeql-action/analyze@v4`: queries `gh api .../code-scanning/alerts` for PR-branch vs base-branch, fails the job when set-difference contains alerts with `security-severity >= 7.0` and `state=open` and `dismissed_at=null`
- Three-phase rollout choreography (scaffold → baseline-triage → final-enable) on the milestone branch `gsd/v1.11-tooling-and-cleanup` — NO new feature branch
- Live triage of every HIGH/CRITICAL CodeQL alert from the baseline scan per Phase-81-D-10 decision tree (fix vs suppress with rationale)
- `docs/security/sast-acceptance.md` net-new file in net-new `docs/security/` directory: per-pattern Markdown sections (SSRF | ZIP-Slip | BCrypt-N/A | Others) with per-finding tables (Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker-Link)
- Non-directive source markers `// CodeQL FP: <rule-id> — <short reason>; see docs/security/sast-acceptance.md` at every suppression site (code-review marker only — CodeQL has no source-level suppression directive)
- `renovate.json` packageRule for `github/codeql-action`: patch automerge after 3 days, minor manual-review (Phase-84 consistency)
- CLAUDE.md `## Conventions` extension: new sub-section "CodeQL SAST (Code Scanning)" (3 bullets) + 2 new entries in `## References` (sast-acceptance.md + codeql.yml)
- SAST-06 deliberate-violation verification: throwaway-branch `throwaway/sast-06-validation` with deliberate SQLi or path-traversal pattern in `src/main/java/org/ctc/_sast_validation/`, draft-PR opened, CodeQL must fail the gate; logs captured into `85-VERIFICATION.md`, branch + PR deleted before merge

**Out of scope (deliberate):**
- Adding CodeQL to `ci.yml` — Stream-4 research mandates standalone workflow; `ci.yml` stays unchanged
- Repository branch-protection rule configuration ("Required status checks: CodeQL Analysis / sast-gate") — Phase-85 ships only workflow + parser; **branch-protection toggling stays operator hoheit** (manual post-merge in GitHub Repo Settings, analogous to Phase 84 Mend-Renovate App-install)
- BCrypt false-positive suppression — `SecurityConfig.java` uses `httpBasic(Customizer.withDefaults())` with credentials in `application-{prod,docker}.yml`; **no `PasswordEncoder` bean exists in the codebase** (TRACKED DEVIATION from SC#4 / SAST-05). Documented as N/A section in `sast-acceptance.md` so future operators understand the SC-vs-reality mismatch
- `// codeql[<rule>]` inline source directives — these are NOT a supported CodeQL Code-Scanning suppression mechanism for the `security-extended` query suite; SAST-04 wording reconciled to "non-directive source marker + `codeql-config.yml` query-filter + `sast-acceptance.md` rationale entry"
- Custom CodeQL queries / query suites — only `security-extended` query pack
- Source-level annotations for finding suppression — replaced by `codeql-config.yml` query-filter mechanism
- New feature branches — all Phase-85 commits land directly on `gsd/v1.11-tooling-and-cleanup` per Memory invariant (`feedback_milestone_branch`)
- Modifying existing `ci.yml`, `release.yml`, `deploy-site.yml`, `mariadb-migration-smoke.yml` workflow files
- Changes to `pom.xml` plugin or dependency configuration (CodeQL operates on already-compiled bytecode through `./mvnw compile`, no Maven plugin needed)
- Changes to `lombok.config` (Phase-81 invariant)
- New SpotBugs filter entries (Phase-81 territory — overlap with CodeQL only at the documentation level inside `sast-acceptance.md`)

</domain>

<decisions>
## Implementation Decisions

### Suppression Mechanism (Area 1)

- **D-01:** **Hybrid suppression strategy** combining three discoverable layers:
  1. `.github/codeql/codeql-config.yml` with `query-filters` (version-controlled, surgical, reviewable)
  2. Non-directive source markers `// CodeQL FP: <rule-id> — <short reason>; see docs/security/sast-acceptance.md` at each suppression site (code-review discoverability)
  3. `docs/security/sast-acceptance.md` with per-finding rationale + Alert-ID linkage (audit trail)
  Mirrors Phase 81 D-09 "rationale-as-comment + filter file + code-cross-reference" pattern.
- **D-02 (REVISED 2026-05-17 per 85-RESEARCH C-01):** **`query-filters` rule-id-only `exclude`, whole-codebase scope.** Schema correction: GitHub's `codeql-config.yml` `query-filters` does NOT support a `where:` field for per-file filtering (verified via docs.github.com + github/codeql discussion #11220 + GitHub's own `.github/codeql/codeql-config.yml`). The original "rule@where" skeleton would parse but silently fail to suppress. The correct schema is `query-filters: - exclude: { id: <rule-id> }` which removes the rule from the entire run. **Per-file suppression for compiled Java is officially only achievable via `advanced-security/filter-sarif`** — explicitly rejected per D-06 (Phase-84 3rd-party-action minimization policy). Whole-codebase rule exclude is acceptable for this codebase because:
  1. **Defense-in-depth via Phase 81 SpotBugs + find-sec-bugs gate** already covers the same files at the bytecode tier with rationale comments in `config/spotbugs-exclude.xml` (SSRF on `FileStorageService`, PATH_TRAVERSAL_IN on `FileStorageService` + `BackupArchiveService`).
  2. **Narrow code surface:** the codebase has exactly ONE outbound HTTP fetcher (`FileStorageService.storeFromUrl`) and exactly TWO ZIP-extraction code-paths (`BackupArchiveService` + `BackupImportService`). Any future SSRF or ZIP-Slip surface would have to be a deliberate addition that SpotBugs would catch FIRST during `./mvnw verify`, BEFORE CodeQL ran on PR.
  3. **Source markers (D-03) + sast-acceptance.md (D-16) + Update-on-Triage discipline (D-19)** ensure the suppression intent is discoverable from code review even though the codeql-config.yml entry has no `where:` field.
  If a future phase adds a new SSRF/ZIP-Slip surface that the SpotBugs gate misses, the right response is: (a) add a SpotBugs filter entry for that file, (b) keep the CodeQL whole-codebase exclude unchanged, (c) document in sast-acceptance.md. Reopening the codeql-config schema for per-file filtering means accepting the `advanced-security/filter-sarif` 3rd-party-action dependency — a v1.12+ decision.
- **D-03:** **Source marker format is fixed:** `// CodeQL FP: <rule-id> — <one-line reason>; see docs/security/sast-acceptance.md`. One line directly above the protected method or block. Example: `// CodeQL FP: java/ssrf — startsWith-blocklist sanitizer not recognized; see docs/security/sast-acceptance.md`. The marker is NOT a CodeQL directive — it is a code-review signal only.
- **D-04:** **Pre-stage ONLY the SSRF + ZIP-Slip triade** in codeql-config.yml before the baseline scan:
  - `java/ssrf` (or equivalent rule ID surfaced by the baseline) on `FileStorageService.storeFromUrl`
  - `java/path-injection` (or equivalent) on `BackupArchiveService.assertEntrySafe` chain
  - `java/path-injection` (or equivalent) on `BackupImportService` ZIP-extraction chain
  Any additional baseline findings go through the live Phase-81-D-10 decision tree (HIGH=fix; Medium real-bug=fix; Medium intentional=suppress+rationale; Medium stylistic=suppress short-reason; not-triageable=escalate). Anticipated overreach is rejected: no preventive Lombok / generated-source / test-code excludes.
- **D-05 (TRACKED DEVIATION from SC#4 / SAST-05):** **BCrypt false-positive triage is dropped from Phase 85 scope.** The `SecurityConfig.java` declares `httpBasic(Customizer.withDefaults())` and the `application-{prod,docker}.yml` files store credentials directly — **no `BCryptPasswordEncoder` or any `PasswordEncoder` bean exists in `src/main/java/`** (verified via grep). The Phase-81 research-pitfall-#13 anticipation about a `BCrypt.passwordEncoder` bean never materialized in code. `sast-acceptance.md` carries an explicit `## BCrypt Password Hashing (Not Applicable)` section documenting the SC-vs-reality mismatch so future readers understand SAST-05 sub-item is intentionally unfulfilled rather than overlooked.

### PR Gate Enforcement (Area 2)

- **D-06:** **Custom SARIF-diff gate via inline-bash step** in `codeql.yml`, placed directly after `codeql-action/analyze@v4`. NOT a marketplace 3rd-party action (Phase-84 Renovate-policy consistency: minimize 3rd-party-action exposure), NOT a separate `.github/scripts/` file (the parser is ~20 lines of `gh api` + `jq` + set-difference — inline keeps the workflow self-contained and reviewable). Mirrors the `ci.yml` Playwright-install inline-script pattern.
- **D-07:** **"New" finding definition:** an alert is "new" when it appears `open` on the PR-branch but is NOT `open` on the base-branch (master or milestone branch). Parser logic: call `gh api repos/${OWNER}/${REPO}/code-scanning/alerts?state=open&severity=critical,high&ref=refs/heads/<branch>` for both `head` and `base`, key each alert by `(rule.id, most_recent_instance.location.path)` (commit-sha-agnostic so rebases don't poison the diff), compute set-difference `head - base`. Exit 1 if non-empty.
- **D-08:** **Severity axis = `security-severity` HIGH or CRITICAL** (numeric `>= 7.0` for HIGH, `>= 9.0` for CRITICAL — CVSS-aligned, matches ROADMAP SC#5 wording). NOT `level=error` (which would catch non-security quality queries). Non-security findings with `level=warning` still upload to the Security tab but never fail the gate.
- **D-09:** **First-run / chicken-and-egg:** the gate is **always active** — even on the first push to master. Combined with D-12 below, this means the final-enable commit MUST land on master with a clean baseline. Phase-85 PR is the choreographed mechanism to satisfy this invariant: scaffold + triage commits land first (workflow disabled, no gate), only the final-enable commit turns on the triggers + gate.
- **D-10:** **Gate trigger matrix:**
  - `pull_request` → gate ACTIVE (diff-against-base)
  - `push` to master → gate ACTIVE (diff-against-previous-master-state)
  - `schedule` (weekly cron) → gate SKIPPED (SARIF upload only — drift detection via Security tab, not via red workflow runs in noisy weekly cluster)
  Implementation: `if: github.event_name != 'schedule'` on the gate-step.
- **D-11:** **Repository branch-protection-rule configuration is OUT OF SCOPE for Phase 85.** The Phase-85 PR ships only `codeql.yml` + `codeql-config.yml` + sast-acceptance.md + CLAUDE.md updates + renovate.json packageRule. The "Required status checks: CodeQL Analysis / sast-gate" branch-protection toggle stays operator-hoheit (manual post-merge step in GitHub Repo Settings, analogous to Phase 84 Mend-Renovate-App install). Consequence: SAST-06 verification is reduced to "workflow fails on deliberate violation" (sufficient to prove the gate logic works); the additional "merge is actually blocked" depends on the operator's post-merge branch-protection toggle.

### Gate Rollout Choreography (Area 3)

- **D-12:** **Three-phase commit choreography** on `gsd/v1.11-tooling-and-cleanup`:
  1. **Scaffold commit** — `codeql.yml` with `on: workflow_dispatch:` only (NO push/PR/schedule triggers), `codeql-config.yml` with SSRF + ZIP-Slip pre-stage, `docs/security/sast-acceptance.md` skeleton with empty pattern sections, CLAUDE.md Conventions + References updates, renovate.json packageRule. Commit message: `feat(85): scaffold CodeQL workflow (workflow_dispatch only, gate disabled) (SAST-01..SAST-04, SAST-06/1)`. Manual `gh workflow run codeql.yml --ref gsd/v1.11-tooling-and-cleanup` produces the first SARIF upload to the Security tab.
  2. **Baseline inspection (no commit)** — Inspect baseline findings via Security tab + SARIF artifact. Categorize per Phase-81-D-10 decision tree. Document the triage table inside `85-VERIFICATION.md` (created by planner, populated by executor).
  3. **Triage commits (N commits, one per finding-cluster or per file)** — Either source-code fixes (with their `// CodeQL FP:` source marker omitted because the finding is fixed, not suppressed) OR new `query-filters` entries in `codeql-config.yml` + matching `// CodeQL FP:` source markers + matching `sast-acceptance.md` table row. Each commit message: `fix(85): triage <rule-id> on <ClassName>` or `chore(85): suppress <rule-id> on <ClassName> (FP rationale)`. After all triage commits: another manual `gh workflow run` must produce ZERO HIGH/CRITICAL findings.
  4. **Final-enable commit** — `codeql.yml` triggers extended to `on: { push: { branches: [master] }, pull_request: { branches: [master] }, schedule: [{ cron: '0 2 * * 0' }] }` + gate-step `if:` condition removed (gate now active). Commit message: `feat(85): activate CodeQL gate on push + pull_request (SAST-01, SAST-06/2)`.
- **D-13:** **Scaffold-disable mechanism = `on: workflow_dispatch:` only.** The scaffold-commit YAML literally has `on:\n  workflow_dispatch:` with no other event keys. Clean semantic, transparent in code review, prevents accidental push-trigger runs during triage phase.
- **D-14:** **SAST-06 deliberate-violation test = throwaway-branch, pre-merge of the Phase-85 PR.** After the final-enable commit lands, but BEFORE the Phase-85 PR is merged:
  1. `git switch -c throwaway/sast-06-validation`
  2. Create `src/main/java/org/ctc/_sast_validation/SastMarker.java` with a deliberate SQLi or path-traversal pattern (e.g., `String sql = "SELECT * FROM x WHERE id = " + req.getParameter("id");` + `Statement.execute(sql)`)
  3. `git push -u origin throwaway/sast-06-validation`
  4. `gh pr create --draft --base gsd/v1.11-tooling-and-cleanup --title "throwaway: SAST-06 verification (DO NOT MERGE)"`
  5. Wait for CodeQL workflow to run on the draft PR → expect the gate-step to exit 1 with `java/sql-injection` or `java/path-injection` HIGH alert
  6. Capture: `gh run view` output (first 30 lines) + Security tab alert screenshot pointer into `85-VERIFICATION.md`
  7. `gh pr close <num>` + `git push origin --delete throwaway/sast-06-validation`
  8. `git switch gsd/v1.11-tooling-and-cleanup` and ensure NO sast_validation/ artifact lands in the milestone branch
  Mirrors Phase-81-D-13 throwaway-branch pattern.
- **D-15:** **Soft scope — all baseline-emitted HIGH/CRITICAL findings remain within Phase 85.** No hard cap on triage-commit count. If the baseline produces unexpected findings beyond the SSRF + ZIP-Slip triade (e.g., a real SQL-injection suspicion in `BackupImportService.batchUpdate` or a previously-unknown XSS surface in Thymeleaf template helpers), they are triaged inside Phase 85 with the D-10 decision tree. Rationale: the codebase is already clean (Phase-81 SpotBugs gate active + 87.80% JaCoCo + multi-cycle hardening); surprises are unlikely. If they appear, Phase-85 absorbs them rather than spawning a Phase-85b.

### Acceptance Doc Structure & Scope (Area 4)

- **D-16:** **Format = per-pattern Markdown sections + per-finding tables.** Top-level sections:
  - `## SSRF (Server-Side Request Forgery)`
  - `## ZIP-Slip (Archive Path Traversal)`
  - `## BCrypt Password Hashing (Not Applicable)` ← contains the D-05 deviation explanation
  - `## Other Triaged Findings` ← catch-all for live-triage results from baseline
  Each section (except BCrypt-N/A) contains a Markdown table:
  ```markdown
  | Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
  |----------|------|----------|--------|-----------|---------------|
  | #1234    | java/ssrf | FileStorageService.storeFromUrl:87 | suppressed | startsWith blocklist not recognized as sanitizer | FileStorageService.java:86 |
  ```
  Buckets: `fixed` (commit-linked), `suppressed` (codeql-config + source-marker), `accepted` (no fix, no suppress, documented).
- **D-17:** **Location = new top-level `docs/security/` directory.** Sibling to `docs/uat/`, `docs/superpowers/`. Mirrors Phase-83-QUAL-05 `docs/uat/UAT-02-legacy-season-smoke.md` placement pattern. Future security-related artifacts (threat models, additional acceptance docs) live here.
- **D-18:** **BCrypt section explicitly documents the SC-vs-reality deviation:**
  ```markdown
  ## BCrypt Password Hashing (Not Applicable)

  ROADMAP SC#4 and REQUIREMENTS SAST-05 anticipated a BCrypt-related CodeQL
  false-positive triage based on Phase 81 research-pitfall #13. The actual
  codebase does NOT declare a `PasswordEncoder` bean — `SecurityConfig.java`
  uses `httpBasic(Customizer.withDefaults())` with credentials in
  `application-{prod,docker}.yml`. CodeQL emits no BCrypt-related findings.
  This sub-item is intentionally unfulfilled — TRACKED DEVIATION in
  `85-CONTEXT.md` D-05.
  ```
- **D-19:** **Update-on-Triage discipline.** Every CodeQL suppression (whether via `codeql-config.yml` query-filter edit, new source marker, or UI dismissal) MUST be accompanied by a parallel commit-edit to `docs/security/sast-acceptance.md` adding the table row. CLAUDE.md "Conventions > CodeQL SAST" sub-section codifies this (D-24).

### Schedule + Path-Ignore (Area 5)

- **D-20:** **Cron schedule = Sunday 02:00 UTC.** `schedule: [{ cron: '0 2 * * 0' }]`. Separates the CodeQL weekly drift-scan from the Phase-84 Renovate Monday-morning cluster (Mend Renovate runs "before 6am UTC Monday"). Sunday-night cadence catches transitive-dep / new-detector drift without colliding with Renovate runner load.
- **D-21:** **No explicit `paths-ignore` in `codeql-config.yml`.** The Java extractor only analyzes compiled output of `./mvnw compile -DskipTests`; `src/test/java`, `target/`, `build/generated-sources/` are not compiled and thus implicitly out-of-scope. Adding redundant `paths-ignore` would create dual-truth maintenance load. Trust the CodeQL defaults + the build-step's `-DskipTests` flag.

### codeql-action Versioning + Renovate Interaction (Area 6)

- **D-22:** **Floating `@v4` tag, Renovate-managed.** Both `github/codeql-action/init@v4` and `github/codeql-action/analyze@v4`. Consistent with the rest of `ci.yml` (`actions/checkout@v6`, `actions/setup-java@v5`). Phase 84 Renovate-policy (DEPS-04) permits this floating-major pattern for GitHub-maintained actions because GitHub manages safe rollouts under the `v4` tag.

### Branch & Documentation (Area 7)

- **D-23 (HARD-LOCKED, NOT A QUESTION):** **All Phase-85 commits land on the milestone branch `gsd/v1.11-tooling-and-cleanup`** per PROJECT.md footer and `feedback_milestone_branch.md` Memory invariant ("alle Änderungen auf dem Meilenstein Branch durchführen. Keine neue Feature Branches anlegen"). No `feature/codeql-sast` branch, no `feature/85-codeql-sast`, no sub-workstream branch. Phase-81-CONTEXT precedent (`feature/spotbugs-gate`) is a historical mis-pattern and explicitly NOT a precedent for Phase 85. Subagent prompts must echo this branch name and forbid `git switch`, `git checkout -b`, and `git stash`.
- **D-24:** **CLAUDE.md `## Conventions` extension** — new sub-section `### CodeQL SAST (Code Scanning)`, inserted after the existing `### Static Analysis (SpotBugs + find-sec-bugs)` block, three bullets:
  1. **Gate:** CodeQL `@v4` runs on push to master, pull_request, and Sunday-02:00-UTC cron via `.github/workflows/codeql.yml`. The inline SARIF-diff gate-step fails PRs that introduce new HIGH/CRITICAL `security-severity` alerts versus the base branch (skipped on weekly cron — drift detection only).
  2. **Suppressions** live in `.github/codeql/codeql-config.yml` `query-filters`. Every suppressed finding requires a `// CodeQL FP: <rule-id> — <reason>; see docs/security/sast-acceptance.md` source marker at the protected method/block AND a matching table row in `docs/security/sast-acceptance.md`. UI dismissals are equally valid but must also be reflected in `sast-acceptance.md` (Update-on-Triage discipline).
  3. **Acceptance doc** at `docs/security/sast-acceptance.md` is the single source of truth for SAST-Triage decisions: per-pattern sections (SSRF | ZIP-Slip | BCrypt-N/A | Others), per-finding table rows with Alert-ID + Rule + Location + Bucket + Rationale + Source-Marker. Every suppression PR must include a parallel `sast-acceptance.md` edit.
- **D-25:** **CLAUDE.md `## References` extension** — append two entries to the existing References block:
  - `* SAST Acceptance: docs/security/sast-acceptance.md`
  - `* SAST Workflow: .github/workflows/codeql.yml`
  Consistent with the existing `* Testing: .planning/codebase/TESTING.md`-style references.

### Performance + Caching (Area 8)

- **D-26:** **Maven cache via `actions/setup-java@v5` `cache: 'maven'`** — identical pattern to `ci.yml`. Auto-caches `~/.m2/repository`, ~10s warm vs ~2min cold. No additional `actions/cache` for CodeQL bundle (1-2 GB, hit-rate fragile against floating `@v4` bundle updates — marginal gain).
- **D-27:** **Concurrency block analog to ci.yml** — `concurrency: { group: '${{ github.workflow }}-${{ github.ref }}', cancel-in-progress: true }`. PR rapid-fire-pushes auto-cancel older runs, preventing Security-tab noise and saving runner-minutes.

### Round-3 Detail Clarifications (Area 9)

- **D-28:** **SARIF-diff edge-case handling:** Parser strict-filter is `state=open AND dismissed_at=null AND security-severity >= 7.0`. Diff is keyed on `(rule.id, most_recent_instance.location.path)` — commit-sha-agnostic, so rebases don't poison the diff and dismissed-via-UI alerts don't re-fail the gate. Brand-new rules (rule.id not present in base) appear as net-new alerts and correctly fail the gate (security-extended pack adding new rules SHOULD be evaluated). Closed-by-fix alerts (`state=closed_by_fix`) are excluded from both head and base sets.
- **D-29:** **Renovate `packageRule` for `github/codeql-action`** must be added to `renovate.json` as part of the scaffold commit:
  ```json
  {
    "matchPackageNames": ["github/codeql-action"],
    "matchUpdateTypes": ["patch"],
    "automerge": true,
    "minimumReleaseAge": "3 days"
  },
  {
    "matchPackageNames": ["github/codeql-action"],
    "matchUpdateTypes": ["minor", "major"],
    "dependencyDashboardApproval": true
  }
  ```
  Mirrors the Phase-84 GitHub-Actions strategy (DEPS-04). Patch updates auto-merge after 3-day cooldown; minor/major need manual approval via Dependency Dashboard.

### Claude's Discretion

- Exact baseline `query-filters` entries in `codeql-config.yml` — planner inspects the actual SSRF + ZIP-Slip alert IDs surfaced by the first `gh workflow run` (the live rule-IDs may be `java/ssrf`, `java/server-side-request-forgery`, `java/path-injection`, `java/zipslip`, or variant — the actual baseline tells us).
- Exact inline-bash text of the SARIF-diff gate step — planner pulls from `gh api` schema + writes the script; ~15-25 lines.
- Exact CLAUDE.md sub-section wording — keep terse, match the existing CSS-Guidelines / SpotBugs-section tone.
- Exact `_sast_validation` package + class name for SAST-06 throwaway test — planner picks SQLi vs path-traversal based on which rule fires fastest in the deliberate-violation throwaway.
- Whether to add a `gh workflow run` invocation example to `85-VERIFICATION.md` for future operators — discretion.
- Plan-commit count and granularity — planner decides whether scaffold + triage-commits + final-enable land as 1 plan or split per choreography phase. Given Phase-81 used multiple plans per choreography step, planner can mirror.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 85: CodeQL SAST" — goal, depends-on (Phase 80 + Phase 81), 6 requirement IDs (SAST-01..SAST-06), 5 success criteria
- `.planning/REQUIREMENTS.md` §"Security SAST (SAST)" — SAST-01..SAST-06 line items
- `.planning/PROJECT.md` §"Current Milestone: v1.11" — milestone scope, branch invariant `gsd/v1.11-tooling-and-cleanup`; §"Current State (after v1.10 shipped 2026-05-16)" — security hardening baseline (SSRF blocklist, ZIP-Slip defenses, no PasswordEncoder bean)

### Research (v1.11 milestone)
- `.planning/research/SUMMARY.md` §"Stream 4 — SAST (CodeQL)" — CodeQL vs Semgrep decision matrix, `security-extended` query pack rationale, three false-positive triage anticipation
- `.planning/research/STACK.md` §"Stream 4 — SAST (Security Static Analysis)" — full GitHub-Actions job template, language=`java-kotlin` rationale, Java 25 support confirmation (CodeQL 2.23.1+), `codeql-action@v4` floating-tag policy
- `.planning/research/STACK.md` §"What CodeQL Will Analyze" — high-value call chains (BackupController → BackupImportService → ZIP-Slip, FileStorageService SSRF, TemplateEditorController SpEL)
- `.planning/research/ARCHITECTURE.md` — Component map showing CodeQL stays in separate workflow file (NOT in ci.yml)
- `.planning/research/PITFALLS.md` — Pitfall #13 (BCrypt false-positive anticipation — RECONCILED in D-05 as N/A for this codebase), Pitfall #12 (SSRF false-positive on startsWith blocklist), Pitfall #14 (ZIP-Slip startsWith(toRealPath()) defense not recognized)

### Prior Phase Context (carry-forward decisions)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-CONTEXT.md` — D-09/D-11 source-cross-reference + filter-file + rationale pattern (templated for D-01/D-02/D-03); D-10 fix-vs-suppress decision tree (templated for D-15 live triage); D-12 four-stage choreography (templated for D-12 three-phase choreography); D-13 throwaway-branch deliberate-violation test (templated for D-14)
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md` — SAST-06 deliberate-violation pattern reference
- `.planning/phases/84-renovate-integration/84-CONTEXT.md` — Renovate-policy DEPS-04 (GitHub Actions: patch automerge + manual review minor); branch-protection-as-operator-hoheit pattern (templated for D-11)
- `.planning/phases/83-quality-and-polish-sweep/83-CONTEXT.md` — `docs/uat/UAT-02-legacy-season-smoke.md` placement pattern (templated for `docs/security/sast-acceptance.md` location decision D-17)

### Codebase Maps (for planning)
- `.planning/codebase/ARCHITECTURE.md` — 3-tier service architecture; identifies controllers (input boundary) and services (data-flow chains CodeQL traces)
- `.planning/codebase/CONVENTIONS.md` §"Lombok Usage" — Lombok-generated bytecode considerations (already mitigated by Phase-81 `lombok.config`, no Phase-85 action needed)
- `.planning/codebase/STACK.md` — current `ci.yml` action versions (`actions/checkout@v6`, `actions/setup-java@v5`, `madrapps/jacoco-report@v1.7.2`) for D-22 consistency check
- `.planning/codebase/TESTING.md` — confirms `src/test/java` is compiled only under `./mvnw test`, NOT under `./mvnw compile -DskipTests` → CodeQL Java extractor sees only `src/main/java` (D-21 rationale)

### Live Workflow Configuration
- `.github/workflows/ci.yml` — sibling-pattern reference: top-level `permissions`, `concurrency` block, `actions/setup-java@v5 cache: 'maven'`, Java 25 Temurin (D-26 + D-27 templates)
- `.github/workflows/release.yml` — sibling-pattern reference for standalone workflows
- `.github/workflows/deploy-site.yml` — sibling-pattern reference for separate-job-trigger workflows
- `.github/workflows/mariadb-migration-smoke.yml` — sibling-pattern reference for separate workflows

### Security Hardening Code (intentional suppressions reference)
- `src/main/java/org/ctc/domain/service/FileStorageService.java` lines 87-103 + 125-153 — `storeFromUrl` + `validateHostname` SSRF defense (D-04 pre-stage target)
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` lines 608-623 — `assertEntrySafe` ZIP-Slip defense via `PathTraversalGuard.assertWithin` (D-04 pre-stage target)
- `src/main/java/org/ctc/backup/service/BackupImportService.java` ZIP-extraction chain — additional ZIP-Slip defense site (D-04 pre-stage target)
- `src/main/java/org/ctc/admin/SecurityConfig.java` — Basic Auth via `httpBasic(Customizer.withDefaults())`, NO `PasswordEncoder` bean (D-05 deviation evidence)

### Existing Suppression Patterns (mirror, do not duplicate)
- `config/spotbugs-exclude.xml` lines 215-247 — SpotBugs SSRF + PATH_TRAVERSAL_IN suppressions on `FileStorageService` + `BackupArchiveService` with full rationale comments. Phase 85 mirrors these rationale texts for CodeQL Alert IDs into `sast-acceptance.md`, but uses different mechanism (codeql-config.yml query-filters, not XML <Match>) and different file (`docs/security/sast-acceptance.md`, not `config/spotbugs-exclude.xml`).
- `lombok.config` (project root) — Phase-81 invariant; do NOT modify

### Memory (project-local user feedback)
- `~/.claude/projects/-Users-jegr-Documents-github-ctc-manager/memory/feedback_milestone_branch.md` — **Branch-strategy hard-lock** (D-23 source of truth)
- `~/.claude/projects/-Users-jegr-Documents-github-ctc-manager/memory/feedback_pr_workflow.md` — PR-workflow standards (gh CLI, code-review before PR, CI check after PR)
- `~/.claude/projects/-Users-jegr-Documents-github-ctc-manager/memory/feedback_no_local_git_tags.md` — Phase-85 PR must not push local git tags (CI Release Workflow handles tagging)
- `~/.claude/projects/-Users-jegr-Documents-github-ctc-manager/memory/feedback_clean_build_only.md` — `./mvnw verify -Pe2e` is the final phase-verification gate (no skip-flags)

### External (GitHub CodeQL + Action docs)
- https://github.com/github/codeql-action/releases — `@v4` floating tag verification at planning time
- https://docs.github.com/en/code-security/code-scanning/customizing-your-advanced-setup-for-code-scanning/customizing-your-advanced-setup-for-code-scanning — `codeql-config.yml` schema (`query-filters`, `paths-ignore`, `queries`)
- https://codeql.github.com/codeql-query-help/java/ — full list of Java security-extended query IDs for D-04 baseline-mapping
- https://docs.github.com/en/rest/code-scanning/code-scanning?apiVersion=2022-11-28 — `gh api .../code-scanning/alerts` schema (state, security-severity, dismissed_at fields) for D-28 parser logic
- https://docs.github.com/en/code-security/code-scanning/managing-code-scanning-alerts/triaging-code-scanning-alerts-in-pull-requests — PR-level alert behavior (head vs base diff semantics)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`.github/workflows/ci.yml` (D-26 + D-27 template)** — provides the boilerplate: `actions/checkout@v6`, `actions/setup-java@v5` with `cache: 'maven'`, top-level `concurrency` block with `cancel-in-progress: true`, top-level `permissions: { contents: read, pull-requests: write }`. Phase 85 mirrors these structural patterns into the new standalone `codeql.yml`, but adds a JOB-level `permissions: { security-events: write, contents: read, actions: read }` block (not workflow-level — Stream 4 research mandate).
- **`.github/workflows/release.yml` + `deploy-site.yml` + `mariadb-migration-smoke.yml`** — confirms the project already has multiple sibling-workflow files; adding `codeql.yml` as a 4th sibling is structurally consistent.
- **`config/spotbugs-exclude.xml` lines 215-247 (rationale-text template)** — the Phase-81 SSRF + PATH_TRAVERSAL_IN suppression rationale texts are nearly verbatim reusable for the CodeQL-equivalent entries in `sast-acceptance.md`. Pattern: cite the defense code-block location + explain why the FP-detector cannot recognize it as a sanitizer.

### Established Patterns
- **Standalone workflow file for separate-concern CI (Phase 78 + this phase)** — Phase 78 introduced `dockerfile-noble-pin-guard` as a CI-job inside ci.yml AND a standalone workflow concept for related concerns. Phase 85's `codeql.yml` is the cleanest expression: completely separate file because (a) different permissions, (b) different schedule, (c) different failure semantics.
- **Three-phase rollout choreography (Phase 81 D-12 template)** — scaffold-disabled → baseline-inspection → triage commits → final-enable. Mirrored in D-12 with adjusted vocabulary for the workflow_dispatch-disabled vs `goal=spotbugs` report-only state.
- **Throwaway-branch deliberate-violation test (Phase 81 D-13 template)** — `git switch -c throwaway/<test-id>`, deliberate-violation in net-new package, draft PR, gate fails, capture-and-cleanup. Mirrored in D-14.
- **Per-finding rationale + code-cross-reference (Phase 81 D-09 template)** — every Phase-81 SpotBugs `<Match>` has a Maven-XML comment with rationale + code-line cross-reference. Phase 85 mirrors this as: every `codeql-config.yml` query-filter entry has a YAML comment with rationale + a `sast-acceptance.md` table row, AND a `// CodeQL FP:` source marker at the protected site.
- **Operator-hoheit boundary for repo-settings changes (Phase 84 D template)** — Phase 84 stopped at "renovate.json exists + Mend Renovate App installed via Repo Settings". Phase 85 follows the same boundary: workflow + gate-step + Phase-PR; branch-protection-rule stays operator-hoheit (D-11). Reduces Phase-85 PR scope and avoids cross-cutting Repo-Settings interactions during execution.
- **English-only file content + Conventional Commits + branch from origin/master** — all CLAUDE.md disciplines apply unchanged.

### Integration Points
- **`.github/workflows/codeql.yml`** — net-new file. Sibling to existing 4 workflow files. NO modification to `ci.yml` (Stream-4 mandate).
- **`.github/codeql/codeql-config.yml`** — net-new file in net-new `.github/codeql/` directory. Standard GitHub-recognized location.
- **`docs/security/sast-acceptance.md`** — net-new file in net-new `docs/security/` directory. Sibling to existing `docs/uat/`, `docs/superpowers/`, `docs/site/`.
- **`renovate.json`** — existing (created in Phase 84). Phase-85 scaffold-commit edits it to add the `github/codeql-action` packageRule (D-29).
- **`CLAUDE.md`** — existing. Phase-85 scaffold-commit appends new sub-section under `## Conventions` (after the existing Static-Analysis SpotBugs sub-section) and 2 new entries to `## References`.
- **Source code suppression markers** — single-line comments inserted at the protected method/block in the relevant `.java` files during triage commits (FileStorageService.java, BackupArchiveService.java, BackupImportService.java).

### What This Phase Does NOT Touch
- `pom.xml` — CodeQL operates on `./mvnw compile` output, no Maven plugin or dependency change needed.
- `lombok.config` — Phase-81 invariant. Untouched.
- `config/spotbugs-exclude.xml` — overlap with CodeQL only at documentation level (rationale-text reuse), no XML edit.
- Flyway V*.sql migrations — completely unrelated.
- Existing test classes — CodeQL findings drive triage commits in `src/main/java`, NOT in `src/test/java`.
- `src/main/resources/` — no resource changes.
- `Dockerfile` + `docker-compose*.yml` — Phase-78/84 territory.
- `.github/workflows/ci.yml`, `release.yml`, `deploy-site.yml`, `mariadb-migration-smoke.yml` — no modifications.
- Repository branch-protection rules — out of scope per D-11 (operator-hoheit).

</code_context>

<specifics>
## Specific Ideas

- **Branch (HARD-LOCKED):** `gsd/v1.11-tooling-and-cleanup` per PROJECT.md footer + Memory invariant. Phase-PR target: `master` per CLAUDE.md `## Git Workflow` (milestone-PR-to-master pattern; v1.11 ships as one PR at milestone close, not per-phase).
- **Initial scaffold-commit `.github/workflows/codeql.yml` skeleton:**
  ```yaml
  name: CodeQL SAST
  on:
    workflow_dispatch:  # final-enable commit adds push/pull_request/schedule
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
        - uses: actions/checkout@v6
        - uses: actions/setup-java@v5
          with:
            java-version: '25'
            distribution: 'temurin'
            cache: 'maven'
        - uses: github/codeql-action/init@v4
          with:
            languages: java-kotlin
            queries: security-extended
            config-file: ./.github/codeql/codeql-config.yml
        - name: Build for CodeQL
          run: ./mvnw compile --no-transfer-progress -DskipTests -Dspring.profiles.active=dev
        - uses: github/codeql-action/analyze@v4
          with:
            category: "/language:java-kotlin"
        # Gate step added in final-enable commit:
        # - name: Gate on new HIGH/CRITICAL alerts
        #   if: github.event_name != 'schedule'
        #   env:
        #     GH_TOKEN: ${{ github.token }}
        #   run: |
        #     # inline-bash SARIF-diff per D-06/D-07/D-08/D-28
  ```
- **Initial scaffold-commit `.github/codeql/codeql-config.yml` skeleton:**
  ```yaml
  name: ctc-manager-codeql-config
  queries:
    - uses: security-extended
  query-filters:
    # SSRF: FileStorageService.storeFromUrl uses startsWith hostname blocklist (lines 125-153)
    # which CodeQL/find-sec-bugs cannot recognize as a sanitizer. See docs/security/sast-acceptance.md.
    - exclude:
        id: java/ssrf
        where: src/main/java/org/ctc/domain/service/FileStorageService.java
    # ZIP-Slip: BackupArchiveService.assertEntrySafe (lines 608-623) delegates to
    # PathTraversalGuard.assertWithin which CodeQL cannot trace. See docs/security/sast-acceptance.md.
    - exclude:
        id: java/path-injection
        where: src/main/java/org/ctc/backup/service/BackupArchiveService.java
    # ZIP-Slip: BackupImportService ZIP-extraction chain — same pattern as above.
    - exclude:
        id: java/path-injection
        where: src/main/java/org/ctc/backup/service/BackupImportService.java
  # Note: actual rule IDs (java/ssrf vs java/server-side-request-forgery etc.) confirmed against
  # the baseline workflow_dispatch run before final-enable commit. Adjust above if baseline shows
  # different rule IDs.
  ```
- **Initial scaffold-commit `docs/security/sast-acceptance.md` skeleton:**
  ```markdown
  # SAST Acceptance Log

  CodeQL Code-Scanning false-positive triage decisions for `jegr78/ctc-manager`.
  See CLAUDE.md "Conventions > CodeQL SAST (Code Scanning)" for the workflow.

  Buckets:
  - **fixed** — finding addressed by a source-code change (link to commit)
  - **suppressed** — finding is intentional/false-positive, suppressed via `.github/codeql/codeql-config.yml` query-filter + source marker
  - **accepted** — finding documented as known risk, neither fixed nor suppressed

  ## SSRF (Server-Side Request Forgery)
  | Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
  |----------|------|----------|--------|-----------|---------------|
  | TBD-baseline | java/ssrf | FileStorageService.storeFromUrl:87 | suppressed | startsWith-based hostname blocklist (FileStorageService.java:125-153) not recognized as sanitizer by CodeQL; intentional defense, unit-tested | FileStorageService.java:86 |

  ## ZIP-Slip (Archive Path Traversal)
  | Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
  |----------|------|----------|--------|-----------|---------------|
  | TBD-baseline | java/path-injection | BackupArchiveService.assertEntrySafe:608 | suppressed | startsWith(toRealPath()) defense via PathTraversalGuard.assertWithin not traceable by CodeQL; intentional defense, IT-tested | BackupArchiveService.java:607 |
  | TBD-baseline | java/path-injection | BackupImportService:TBD | suppressed | Same defense pattern as BackupArchiveService | BackupImportService.java:TBD |

  ## BCrypt Password Hashing (Not Applicable)

  ROADMAP SC#4 and REQUIREMENTS SAST-05 anticipated a BCrypt-related CodeQL false-positive triage based on Phase 81 research-pitfall #13. The actual codebase does NOT declare a `PasswordEncoder` bean — `SecurityConfig.java` uses `httpBasic(Customizer.withDefaults())` with credentials in `application-{prod,docker}.yml`. CodeQL emits no BCrypt-related findings. This sub-item is intentionally unfulfilled — TRACKED DEVIATION in `85-CONTEXT.md` D-05.

  ## Other Triaged Findings

  *(populated during baseline triage commits per Phase-81-D-10 decision tree)*
  ```
- **Final-enable commit gate-step inline-bash sketch (per D-06/D-07/D-08/D-28):**
  ```bash
  - name: Gate on new HIGH/CRITICAL security alerts
    if: github.event_name != 'schedule'
    env:
      GH_TOKEN: ${{ github.token }}
    run: |
      set -euo pipefail
      OWNER_REPO="${{ github.repository }}"
      HEAD_REF="${{ github.head_ref || github.ref_name }}"
      BASE_REF="${{ github.base_ref || 'master' }}"
      # Fetch open HIGH/CRITICAL alerts (security-severity >= 7.0), excluding UI-dismissed ones
      fetch_alerts() {
        gh api -X GET "repos/${OWNER_REPO}/code-scanning/alerts" \
          -f state=open -f severity=critical,high \
          --paginate --jq '
            .[] | select(.dismissed_at == null
              and (.rule.security_severity_level // "" | IN("high","critical")))
            | "\(.rule.id)|\(.most_recent_instance.location.path)"
          '
      }
      HEAD_ALERTS=$(GH_BRANCH="$HEAD_REF" fetch_alerts | sort -u || true)
      BASE_ALERTS=$(GH_BRANCH="$BASE_REF" fetch_alerts | sort -u || true)
      NEW_ALERTS=$(comm -23 <(echo "$HEAD_ALERTS") <(echo "$BASE_ALERTS") || true)
      if [ -n "$NEW_ALERTS" ]; then
        echo "::error::New HIGH/CRITICAL CodeQL alerts introduced on ${HEAD_REF}:"
        echo "$NEW_ALERTS"
        exit 1
      fi
      echo "No new HIGH/CRITICAL alerts vs ${BASE_REF}."
  ```
  Planner refines: `gh api` `branch` filter parameter vs `ref` parameter, exact jq projection, error-handling on `gh: rate-limited`, fallback when base-branch has no prior scan (first PR after first master-scan).
- **Final-enable commit `on:` block:**
  ```yaml
  on:
    push:
      branches: [master]
    pull_request:
      branches: [master, "gsd/v1.11-tooling-and-cleanup"]
    schedule:
      - cron: '0 2 * * 0'  # Sunday 02:00 UTC
    workflow_dispatch:
  ```
- **`renovate.json` packageRule addition (scaffold-commit):** per D-29 JSON block.
- **CLAUDE.md `## Conventions` sub-section text:** per D-24, inserted after the existing `### Static Analysis (SpotBugs + find-sec-bugs)` block.
- **CLAUDE.md `## References` additions:** per D-25, append at end of existing References block.
- **Subagent prompt branch-discipline boilerplate (for every Phase-85 subagent):** "Active branch: `gsd/v1.11-tooling-and-cleanup`. Do NOT: `git stash`, `git checkout`, `git reset`, `git switch`, `git checkout -b`, branch creation. If the task seems to need a different branch, report `NEEDS_CONTEXT` instead. Implement ONLY the task assigned."
- **Final phase-end verification command (per `feedback_clean_build_only`):** `./mvnw verify -Pe2e` once, after the final-enable commit, before the SAST-06 throwaway-branch test. NO skip-flags. Captured in `85-VERIFICATION.md`.

</specifics>

<deferred>
## Deferred Ideas

- **Repository branch-protection rules ("Required status checks: CodeQL Analysis")** — operator-hoheit per D-11. Documented as post-merge manual step in milestone-summary, not a Phase-85 commit.
- **`paths-ignore` defensive excludes for `target/`, `build/generated-sources/`, `src/test/java/`** — rejected per D-21 (redundant with CodeQL Java-extractor defaults + `-DskipTests` build step). Reopen only if a future baseline run shows unexpected scanning of non-`src/main/java` paths.
- **SHA-pinned `github/codeql-action`** — rejected per D-22 (breaks Phase-84 floating-major Renovate-policy). Reopen only if GitHub Security advisories disclose a codeql-action compromise.
- **`actions/cache` for the CodeQL bundle (~1-2 GB)** — rejected per D-26 (fragile hit-rate against floating `@v4` updates). Reopen only if cold-cache scan-time becomes a CI bottleneck.
- **Daily cron schedule (vs weekly Sunday 02:00 UTC)** — rejected per D-20 (weekly suffices; PRs already trigger per-commit scans). Reopen only if transitive-dep drift detection lags become operationally painful.
- **Custom CodeQL queries / query-suite for project-specific patterns** — out of v1.11 scope. Possible future enhancement: a `ctc-manager` query suite that adds Spring-OSIV-aware rules or `RaceLineup`-as-source-of-truth structural checks. Phase 86+ candidate, low priority.
- **Marketplace 3rd-party SARIF-filter action (e.g., `advanced-security/filter-sarif`)** — rejected per D-06 (Phase-84 minimum-3rd-party-action policy + inline-bash is simpler).
- **CodeQL on `src/test/java`** — out of scope per D-21 + CodeQL-Java-extractor default behavior. Reopen if test-code-specific CVEs become a concern (low likelihood for an admin-only Spring Boot app).
- **Multi-language CodeQL scan (e.g., also `yaml`, `actions`)** — out of scope. `language: java-kotlin` only. The GitHub-native YAML linter already covers actions workflows.
- **Operator runbook for triaging new alerts** — partially covered by CLAUDE.md "Conventions > CodeQL SAST" + sast-acceptance.md. A standalone `docs/security/runbook-codeql.md` could be added in a follow-up phase if the triage volume warrants it.
- **Spring Security PasswordEncoder bean (would re-enable SAST-05 BCrypt triage)** — completely out of scope; would be its own architectural change driven by a real authentication requirement, not a SAST gate concern.

</deferred>

---

*Phase: 85-codeql-sast*
*Context gathered: 2026-05-17*
