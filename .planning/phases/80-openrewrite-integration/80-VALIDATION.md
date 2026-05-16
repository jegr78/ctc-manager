---
phase: 80
slug: openrewrite-integration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-16
---

# Phase 80 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Sourced from `80-RESEARCH.md` §Validation Architecture. Phase 80 is build-config + documentation only — verification is structural (Maven introspection) + grep-based (docs), not new JUnit tests.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + Mockito + Spring Boot Test starters; Maven Surefire 3.x (unit, `forkCount=2`) + Failsafe 3.x (integration, `forkCount=1C` `@Tag("integration")`) + Playwright 1.59.0 (E2E, `-Pe2e` profile) |
| **Config file** | `pom.xml` lines 253–298 (Surefire + Failsafe configuration) + 388–422 (`e2e` profile) |
| **Quick run command** | `./mvnw -q verify` |
| **Full suite command** | `./mvnw -q verify -Pe2e` |
| **Estimated runtime** | ~3 min quick / ~11 min full (v1.10 baseline) |

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -q verify` (Surefire + Failsafe + JaCoCo 82 % bundle gate) and, for plugin-wiring / rewrite.yml tasks, the three structural verification commands listed in §Per-Task Verification Map.
- **After every plan wave:** Phase 80 is small enough to be a single wave (planner discretion: 1–3 plans). The wave gate equals the phase gate.
- **Before `/gsd:verify-work`:** Full suite must be green — `./mvnw -q verify -Pe2e` exit 0 + JaCoCo LINE ratio ≥ 0.82 AND ≥ pre-cleanup baseline.
- **Max feedback latency:** ~180 seconds (`./mvnw -q verify` median wall-clock on developer hardware; CI ~3-5 min median).

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 80-01-01 | 01 | 1 | REWR-03 | — | Default-build isolation: `rewrite-maven-plugin` absent from effective POM without `-Prewrite` | structural smoke | `./mvnw -q help:effective-pom \| grep -c 'rewrite-maven-plugin'` → expected `0` | ✅ existing infra (Maven help plugin) | ⬜ pending |
| 80-01-02 | 01 | 1 | REWR-03 | — | Default-build isolation: `rewrite` profile inactive by default | structural smoke | `./mvnw -q help:active-profiles \| grep -c rewrite` → expected `0` | ✅ existing infra | ⬜ pending |
| 80-01-03 | 01 | 1 | REWR-03 | — | Default verify produces no "Running OpenRewrite" output | structural smoke | `./mvnw -q verify 2>&1 \| grep -ci 'Running OpenRewrite'` → expected `0` | ✅ existing JUnit/Surefire/Failsafe infrastructure | ⬜ pending |
| 80-02-01 | 01/02 | 1 | REWR-01, REWR-04 | — | `mvn -Prewrite rewrite:dryRun` exits 0 and writes either `target/site/rewrite/rewrite.patch` or no patch (clean codebase) | smoke (shell) | `./mvnw -Prewrite rewrite:dryRun && (test -f target/site/rewrite/rewrite.patch \|\| echo clean) && git diff --quiet` | n/a (one-shot shell verification) | ⬜ pending |
| 80-02-02 | 01/02 | 1 | REWR-04 | — | `org.ctc.RewriteCleanup` composite recipe is discoverable on the plugin classpath | smoke (shell) | `./mvnw -Prewrite rewrite:discover -q \| grep -F 'org.ctc.RewriteCleanup'` → expected one line | n/a (rewrite.yml is net-new) | ⬜ pending |
| 80-02-03 | 01/02 | 1 | REWR-04 | — | `rewrite.yml` parses as valid YAML and declares CommonStaticAnalysis active | shell + grep | `grep -F 'org.openrewrite.staticanalysis.CommonStaticAnalysis' rewrite.yml` AND `python3 -c "import yaml,sys; yaml.safe_load(open('rewrite.yml'))"` returns 0 | rewrite.yml is net-new | ⬜ pending |
| 80-03-01 | 03 (conditional) | 2 | REWR-02, REWR-05 | — | `mvn -Prewrite rewrite:run` applied (only if dryRun non-empty); resulting cleanup commit passes full test+coverage gate | full suite | `./mvnw -q verify` exit 0 + JaCoCo `target/site/jacoco/index.html` LINE ratio ≥ 0.82 AND ≥ pre-cleanup baseline (87.80 %) | existing JaCoCo `<rule>` (pom.xml:347) | ⬜ pending |
| 80-03-02 | 03 (conditional) | 2 | REWR-05 | — | If dryRun produces no patch, "no-op dryRun" outcome recorded in 80-VERIFICATION.md instead of a refactor commit | doc grep | `grep -F 'no-op dryRun' .planning/phases/80-openrewrite-integration/80-VERIFICATION.md` (or equivalent) | written at execute time | ⬜ pending |
| 80-04-01 | 04 | 2 | REWR-06 | — | README `## Development` section exists + OpenRewrite subsection present + dryRun/run commands documented | docs grep | `grep -F '## Development' README.md` AND `grep -F 'OpenRewrite (developer-invoked refactoring)' README.md` AND `grep -F './mvnw -Prewrite rewrite:dryRun' README.md` AND `grep -F './mvnw -Prewrite rewrite:run' README.md` | README.md exists; section is new | ⬜ pending |
| 80-04-02 | 04 | 2 | D-12 | — | Two new commands appended to CLAUDE.md `## Commands` block | docs grep | `grep -F './mvnw -Prewrite rewrite:dryRun' CLAUDE.md` AND `grep -F './mvnw -Prewrite rewrite:run' CLAUDE.md` | CLAUDE.md exists; commands are new | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

> Task IDs are illustrative — the planner sets the canonical IDs in PLAN.md frontmatter. The mapping above is intentionally coarse-grained because Phase 80 is build-config + docs only; the planner may collapse multiple verification rows into a single task's `<acceptance_criteria>` block.

---

## Wave 0 Requirements

- [x] No new JUnit test files required — all verification is structural (Maven introspection) or grep-based (docs).
- [x] No new framework installation required — JUnit 5 / Surefire / Failsafe / Playwright already in pom.xml.
- [ ] **Optional (NOT recommended):** `src/test/java/org/ctc/build/RewriteProfileIsolationIT.java` parsing `target/effective-pom.xml` to assert absence of `rewrite-maven-plugin`. CONTEXT.md D-10 explicitly opts AGAINST a permanent CI guard for this phase. **Default: skip — match CONTEXT.md D-10.**

*Existing infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| dryRun patch review for Lombok-entity false positives | REWR-04 (success criterion 4) | The dryRun output IS the human review gate (CONTEXT.md D-08). Watch-items: `FinalizePrivateFields`, `FinalClass`, `ExplicitInitialization`, `StaticMethodNotFinal` against `org.ctc.domain.model.*` | 1. `./mvnw -Prewrite rewrite:dryRun`; 2. open `target/site/rewrite/rewrite.patch`; 3. for each hunk under `src/main/java/org/ctc/domain/model/**`, confirm Lombok-generated code is not being modified in a way that breaks Hibernate proxying; 4. if a problematic sub-recipe is identified, copy its parent composite list minus the offender into `rewrite.yml`, commit, re-dryRun; 5. if 1–3 files need manual `git checkout` instead, document them in the cleanup commit body |
| README narrative readability | REWR-06 | Subjective prose quality of the new "OpenRewrite" subsection — beyond grep verification of anchor strings | Read the rendered README on GitHub or via `gh pr view --web`; confirm the dryRun → run workflow is unambiguous to a new contributor and the developer-invoked-only rationale is stated |

---

## Nyquist Dimensions 1–8 Coverage

| Dim | Property | How Verified | Reference |
|-----|----------|--------------|-----------|
| **D1 Correctness** | `rewrite.yml` parses against the OpenRewrite YAML schema; `rewrite:dryRun` exits 0; produced `target/site/rewrite/rewrite.patch` (if any) is a well-formed unified diff | `./mvnw -Prewrite rewrite:dryRun` exit code = 0; if patch file present, `git apply --check target/site/rewrite/rewrite.patch` returns 0 | OpenRewrite Maven plugin docs |
| **D2 Idempotency** | Repeated `mvn rewrite:dryRun` on an unchanged source tree produces a byte-identical patch file | `sha256sum target/site/rewrite/rewrite.patch` taken twice across two `dryRun` invocations matches; if both runs produce no patch file, that is equally idempotent | OpenRewrite is deterministic given a fixed recipe set and fixed source |
| **D3 Boundary conditions** | (a) Clean source tree → empty patch (file absent or zero hunks); (b) sub-recipe removed from composite `recipeList` → next dryRun's patch is a strict subset of the previous patch; (c) plugin invoked WITHOUT `-Prewrite` → fail-fast with `Could not find goal 'dryRun' in plugin … on project ctc-manager` | (a) tested by running dryRun on master before any edits; (b) tested by removing one sub-recipe and re-dryRunning; (c) tested by `./mvnw rewrite:dryRun` (no `-Prewrite`) → expected non-zero exit + the "plugin not found" message | 80-RESEARCH.md §Recipe Selection Detail |
| **D4 Concurrency** | None — single developer, manual invocation, no parallel execution path | n/a (no concurrent invocation by design) | CONTEXT.md domain section |
| **D5 Failure modes** | (a) Maven Central network failure during plugin resolution → plugin/dep download retried by Maven; error surfaces as build failure with a clear message; (b) OpenRewrite version drift between local and CI → out of scope (per PITFALLS Pitfall 10, OpenRewrite never runs in CI) | (a) acceptable — Maven's standard `<repositories>` retry policy applies, no extra resilience needed; (b) structurally impossible — CI doesn't run OpenRewrite | PITFALLS.md Pitfall 10 |
| **D6 Performance** | Zero impact on default `./mvnw verify` wall-clock vs v1.10 baseline | Structural verification only (CONTEXT.md D-10): three commands listed in §Per-Task Verification Map. No wall-clock benchmark required because the plugin is not in the default build's effective POM | CONTEXT.md D-10 |
| **D7 Security (recipe pack supply chain)** | Plugin and recipe-pack dependencies resolved from Maven Central only; no custom Maven repository added; recipe-pack versions pinned (no `RELEASE` / no `LATEST`) | `pom.xml` `<repositories>` block unchanged (defaults to Maven Central only); plugin + 2 dependencies have explicit versions (6.39.0 / 6.30.4 / 3.34.1). Future Renovate phase (Phase 84) will manage version bumps via PRs that hit the existing CI gate | 80-RESEARCH.md §Maven Profile Structure |
| **D8 Validation** | Post-cleanup `./mvnw verify` is green (Surefire + Failsafe + JaCoCo 82 % BUNDLE rule); coverage does not regress vs v1.10 baseline 87.80 % | `./mvnw -q verify` exit 0 + `target/site/jacoco/index.html` LINE ratio ≥ 0.82 AND ≥ pre-cleanup baseline | CLAUDE.md §"Test Coverage"; v1.10 STATE.md baseline |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies (Wave 0 says "none required — existing infrastructure covers all"; the planner must confirm every task's `<acceptance_criteria>` references the commands above)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify (Phase 80 has ≤ ~6 tasks; sampling continuity is structurally satisfied — every task either has a shell-grep or a `./mvnw verify` step)
- [ ] Wave 0 covers all MISSING references (none — see above)
- [ ] No watch-mode flags (n/a — Maven is one-shot)
- [ ] Feedback latency < 300 s for the quick gate (`./mvnw -q verify` ~180 s on dev hardware; ✓)
- [ ] `nyquist_compliant: true` set in frontmatter after VERIFICATION.md confirms all Dimensions 1–8

**Approval:** pending — flip `nyquist_compliant: true` after `80-VERIFICATION.md` records actual outcomes for all 8 dimensions and the three structural verification commands all return expected values.
