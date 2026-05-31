# Phase 112: Unused Import Cleanup & Regression Guard - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning
**Source:** Targeted decision (guard mechanism) + codebase scout — no full discuss-phase needed (2-requirement mechanical phase)

<domain>
## Phase Boundary

Two deliverables, both tooling/quality-gate level — no behavioural application code change:

1. **IMP-01 — Cleanup:** Remove every unused package import across `src/main/java` and `src/test/java` via OpenRewrite's `RemoveUnusedImports` recipe (not hand-edited).
2. **IMP-02 — Regression guard:** A Checkstyle `UnusedImports` check wired into the standard `verify` lifecycle (runs locally + CI) that fails the build when a new unused import is introduced, plus a CLAUDE.md doc entry so future phases/subagents inherit the rule.

Runs LAST in v1.15 (after 106, 108-111 are merged/verified) so the cleaned import set is final and not re-dirtied by later phases.
</domain>

<decisions>
## Implementation Decisions

### Guard mechanism (LOCKED — user decision 2026-05-31)
- **Checkstyle `UnusedImports`** via `maven-checkstyle-plugin`, NOT OpenRewrite-dryRun-as-gate and NOT CI-only.
  - Rationale: fast (~1-2s/build), precise, industry-standard for exactly this; keeps the build-perf profile the user cares about. OpenRewrite-dryRun-as-gate was rejected because it parses the whole tree on every build (tens of seconds) and would convert `rewrite.yml` from a developer-invoked one-shot into a build-time enforcer.
- Minimal Checkstyle config — only `UnusedImports` and `RedundantImport` modules. Do NOT pull in the full Sun/Google checks (would flood the build with unrelated style violations).
- Config file lives at `config/checkstyle.xml` (mirrors the existing `config/spotbugs-exclude.xml` convention).
- Bind to the **`validate`** phase with `failOnViolation=true` (severity `error`) — consistent with the existing exec-plugin build-guards (`assumptions-fence`, `no-rerun-guard`, `template-fragment-call-guard`) that already run in `validate`. Runs inside the normal `./mvnw verify` lifecycle, no opt-in profile.
- Scope the check to `src/main/java` AND `src/test/java` (Checkstyle's default source roots already cover both via `sourceDirectories`/`testSourceDirectories` — verify both are included).

### Cleanup mechanism (LOCKED)
- Use OpenRewrite recipe **`org.openrewrite.java.RemoveUnusedImports` IN ISOLATION** — do NOT run the existing `org.ctc.RewriteCleanup` recipe.
  - **LANDMINE:** `rewrite.yml`'s `org.ctc.RewriteCleanup` activates `org.openrewrite.staticanalysis.CommonStaticAnalysis` (~70 sub-recipes). Running it would change far more than imports and break Success Criterion 3 ("commit changes only import lines, no behavioural diff").
  - Run isolated, e.g. `./mvnw -Prewrite rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.RemoveUnusedImports` (overrides the pom's `<activeRecipes>`), OR add a dedicated `org.ctc.RemoveUnusedImportsOnly` recipe to `rewrite.yml`. Planner picks the cleaner of the two; the `-Drewrite.activeRecipes` override is the lower-footprint choice and needs no new recipe.
  - `RemoveUnusedImports` is in `rewrite-static-analysis` (already on the `-Prewrite` classpath transitively via `rewrite-spring` — confirmed in pom.xml comment). No new dependency.
- The cleanup commit must touch ONLY `import` lines. Verify with `git diff` that no non-import lines changed before committing.

### Documentation (LOCKED — IMP-02 explicit)
- Add a Checkstyle entry to CLAUDE.md under "Static Analysis" (alongside SpotBugs + CodeQL) stating: unused imports are gated by Checkstyle in `validate`; future phases must not introduce them; cleanup is via `RemoveUnusedImports` in isolation. This is what makes subagents inherit the rule (they read CLAUDE.md).
- README "## Development" dev-tooling note is nice-to-have, not blocking. Wiki not required (internal quality gate, not a user-facing feature).

### Ordering (LOCKED)
- Cleanup FIRST (clean tree), guard SECOND (so the new Checkstyle check passes on the already-clean tree — adding the guard before cleanup would red the build), docs THIRD, then the `clean verify -Pe2e` gate.

### Coverage / behaviour
- No JaCoCo impact (imports are not executable lines). 82% line gate must still pass.
- No Flyway, no entity, no template, no controller change.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Build / tooling config
- `pom.xml` (lines ~455-531) — existing `exec-maven-plugin` build-guards bound to `validate` (the pattern to mirror for wiring + failure messaging); SpotBugs plugin config (~428-455); `rewrite` profile (~572-606).
- `rewrite.yml` — existing OpenRewrite recipes; the `org.ctc.RewriteCleanup` LANDMINE (do NOT activate for the cleanup).
- `config/spotbugs-exclude.xml` — convention for where static-analysis config files live (`config/checkstyle.xml` mirrors this).

### Conventions
- `CLAUDE.md` "Static Analysis (SpotBugs + find-sec-bugs)" and "CodeQL SAST" sections — the doc location/format the new Checkstyle entry must match. Also "Build & Test Discipline" (clean verify is source of truth) and "No Comment Pollution".
</canonical_refs>

<specifics>
## Specific Ideas

- Active milestone branch: `gsd/v1.15-ci-and-race-defaults`. Inline-sequential execution (no worktree subagents) per CLAUDE.md "Subagent Rules".
- Suggested task split: (1) isolated `RemoveUnusedImports` run + import-only-diff verification + commit; (2) `config/checkstyle.xml` (minimal) + `maven-checkstyle-plugin` wired to `validate` with failOnViolation + commit; (3) CLAUDE.md (+ optional README) doc; (4) `./mvnw clean verify -Pe2e` gate.
- After the cleanup run, re-run `RemoveUnusedImports` in dryRun to confirm zero pending changes (Success Criterion 1 proof).
- Checkstyle plugin version: pick a current stable `maven-checkstyle-plugin` + Checkstyle core compatible with Java 25 / Spring Boot 4 toolchain (planner to confirm latest at plan time).
</specifics>

<deferred>
## Deferred Ideas

- Broader Checkstyle rule set (naming, whitespace, Javadoc) — explicitly OUT of scope; only import hygiene. A full style gate is a separate future decision.
- README/Wiki user-facing docs — only CLAUDE.md is required for this phase.
</deferred>

---

*Phase: 112-unused-import-cleanup*
*Context gathered: 2026-05-31 via targeted decision + codebase scout*
