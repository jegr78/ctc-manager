---
phase: 112-unused-import-cleanup
plan: 01
subsystem: infra
tags: [checkstyle, openrewrite, maven, static-analysis, build-gate, imports]

requires:
  - phase: 111-log-injection-remediation-codeql-cwe-117
    provides: clean merged v1.15 tree so the cleaned import set is final
provides:
  - One-time removal of all unused package imports across src/main/java + src/test/java
  - Checkstyle UnusedImports + RedundantImport gate bound to the validate phase (runs in every verify)
  - CLAUDE.md documentation so future phases/subagents inherit the no-unused-imports rule
affects: [all future phases, subagent dispatch]

tech-stack:
  added: [maven-checkstyle-plugin 3.6.0, checkstyle core 13.5.0]
  patterns: [validate-phase build-guard via maven-checkstyle-plugin mirroring the spotbugs/exec-guard convention]

key-files:
  created: [config/checkstyle.xml]
  modified: [pom.xml, CLAUDE.md, 45 src files via RemoveUnusedImports, 9 test files (static-import deletions), AdminDropdownRenderingIT.java (Javadoc fix)]

key-decisions:
  - "processJavadoc=true is correct and required: false produced 25 false positives on Jackson MixIn + IT classes that reference imports only via {@link}/@see"
  - "OpenRewrite RemoveUnusedImports is conservative with static imports; 9 genuinely-unused statics removed by hand (import-only deletions)"
  - "Checkstyle core overridden to 13.5.0 because the plugin's bundled 9.3 cannot parse Java 25 sources"

patterns-established:
  - "Unused-import hygiene is a validate-phase hard gate, not advisory — no opt-in profile, covers both source roots"
  - "One-shot cleanup is RemoveUnusedImports IN ISOLATION (-Drewrite.activeRecipes), never the broad org.ctc.RewriteCleanup"

requirements-completed: [IMP-01, IMP-02]

duration: ~25min
completed: 2026-05-31
---

# Phase 112: Unused Import Cleanup & Regression Guard Summary

**Stripped every unused import from the codebase and wired a Checkstyle validate-phase gate so it can never silently regress — green under the full clean verify -Pe2e gate.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 4 of 4 completed
- **Files modified:** config/checkstyle.xml (new), pom.xml, CLAUDE.md, 45 src files (OpenRewrite), 9 test files (static imports), 1 test file (Javadoc)
- **Commits:** 5 production commits

## Accomplishments

### IMP-01 — Cleanup
- Ran `org.openrewrite.java.RemoveUnusedImports` IN ISOLATION (`-Drewrite.activeRecipes` override, never `org.ctc.RewriteCleanup`). 45 files cleaned; wildcard imports expanded to concrete types. Import-only diff, dryRun-converged (zero pending changes). Commit `d6048812`.
- Removed 9 genuinely-unused static imports that OpenRewrite left behind (callers use the fully-qualified form). Commit `a4314f40`.

### IMP-02 — Regression guard
- `config/checkstyle.xml`: minimal ruleset — `UnusedImports` (processJavadoc=true) + `RedundantImport` only, severity error.
- `pom.xml`: `maven-checkstyle-plugin` 3.6.0 with Checkstyle core 13.5.0 override (Java 25), bound to `validate`, `failOnViolation=true`, `includeTestSourceDirectory=true`. Commit `1562b75d`.
- Documented in CLAUDE.md under Static Analysis. Commit `21192683`.

## Deviations from plan

The plan assumed `RemoveUnusedImports` would fully clean the tree and that the Checkstyle gate would pass immediately. Two divergences surfaced when the gate first ran (handled inline — mechanical, low blast radius, import-only / doc-only):

1. **9 unused static imports** (`org.mockito.ArgumentMatchers.any/.startsWith`, `PhaseTestFixtures`) — OpenRewrite is conservative with static imports. Verified genuinely unused (callers use FQN), removed by hand. Documented the limitation in CLAUDE.md.
2. **1 malformed Javadoc** (`AdminDropdownRenderingIT:32`) — raw `<Enum, String>` in a Javadoc comment crashed Checkstyle's javadoc parser under `processJavadoc=true`. Root-cause fix: wrapped the generic in `{@code}` (commit `73065bc7`). Empirically confirmed `processJavadoc=true` is the correct setting — `false` produced 25 false positives on `{@link}`-only references (Jackson MixIns + ITs).

## Verification

- Isolated `RemoveUnusedImports` dryRun: zero pending changes (convergence proven).
- Cleanup commits are import-only diffs (no behavioural change).
- Negative proof: injected `import java.util.concurrent.atomic.AtomicLong;` into one main + one test file → `./mvnw validate` failed naming BOTH source roots; reverting greened it.
- `./mvnw clean verify -Pe2e`: **exit 0**. Checkstyle 0 violations; SpotBugs BugInstance 0; tests 1819 + 537 + 116 = **2472** (≥ 2416 baseline); JaCoCo "All coverage checks have been met" (≥ 82%).

## Success Criteria

- IMP-01 ✅ zero unused imports across both source roots; isolated dryRun clean.
- IMP-02 ✅ Checkstyle gate bound to `validate`, runs in standard `verify` (no opt-in), documented in CLAUDE.md.
- ✅ Cleanup commits import-only; clean verify -Pe2e green; coverage unchanged (≥ 82%); SpotBugs 0; test count ≥ 2416.
