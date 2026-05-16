# Phase 80: OpenRewrite Integration - Context

**Gathered:** 2026-05-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Add `rewrite-maven-plugin` to the build as a **developer-invoked** refactoring tool — never bound to the default `./mvnw verify` lifecycle. Activate the curated `CommonStaticAnalysis` recipe pack via a project-root `rewrite.yml`, with `UpgradeSpringBoot_4_0` explicitly excluded (codebase is already on Boot 4.0.6). Land one mandatory deliverable inside this phase: a single one-shot cleanup commit on the `gsd/v1.11-tooling-and-cleanup` branch produced by `mvn -Prewrite rewrite:run`, after the `rewrite:dryRun` diff has been reviewed for Lombok-entity false positives. Document the `dryRun → run` workflow plus the developer-invoked-only rationale in README "Development".

**In scope:**
- `rewrite-maven-plugin` declared inside a Maven `<profile id="rewrite">` block (NOT in main `<build><plugins>`)
- `rewrite-spring` + `rewrite-migrate-java` recipe packs registered as plugin dependencies
- `rewrite.yml` at project root with `CommonStaticAnalysis` active + `UpgradeSpringBoot_4_0` excluded
- One-shot cleanup commit on v1.11 branch after dryRun review
- README "Development" subsection documenting the workflow

**Out of scope (deliberate):**
- Binding OpenRewrite to any Maven lifecycle phase (verify/test/compile)
- CI gate that runs `rewrite:dryRun` automatically (PITFALL #10 — kept developer-invoked-only)
- Custom CTC-specific recipes (deferred as REWR-FUTURE-01)
- Java-version migration recipes beyond what `CommonStaticAnalysis` already covers
- Any recipe execution against `src/test/java/` outside what the default recipe configuration covers

</domain>

<decisions>
## Implementation Decisions

### Plugin Wiring

- **D-01:** Plugin declared **inside `<profile id="rewrite">`**, NOT in the main `<build><plugins>` block. Rationale: the phase goal and 3 of 5 success criteria explicitly use `./mvnw -Prewrite rewrite:dryRun` / `./mvnw -Prewrite rewrite:run`. Profile-scoping makes accidental misuse harder (a bare `./mvnw rewrite:run` without `-Prewrite` will fail-fast with "plugin not found" instead of silently mutating sources). This still satisfies REWR-03 ("no `<executions>` binding to default lifecycle") — the plugin has zero executions in any scope.
- **D-02:** Plugin version: defer final pin to the planner/researcher. SUMMARY.md cites 6.39.0 (STACK.md verified) and ARCHITECTURE.md cites 6.40.0; pick the latest stable on Maven Central at execution time, document the chosen version in the plan.
- **D-03:** Plugin dependencies declared in the same profile: `rewrite-spring` 6.30.4 + `rewrite-migrate-java` 3.34.1. Keep both even though we don't activate Spring/migrate recipes today — they're required on the plugin classpath for the `UpgradeSpringBoot_4_0` *exclusion* to be meaningful (you can only exclude a recipe whose pack is on the classpath), and they make future Spring/Java-version recipe activation a one-line `rewrite.yml` edit instead of a pom.xml change.

### Recipe Configuration (`rewrite.yml`)

- **D-04:** Recipe set is **minimal** — only `org.openrewrite.staticanalysis.CommonStaticAnalysis`. Do NOT add `UpgradeToJava25`, modernization recipes, or any other pack in this phase. Rationale: REWR-04 names `CommonStaticAnalysis` as the recipe pack; keep the first integration narrow and defer expansion to REWR-FUTURE-01 once the developer workflow is bedded in. Extra packs widen the one-shot cleanup diff (REWR-05) and Lombok false-positive surface for zero requirements-mandated benefit.
- **D-05:** `rewrite.yml` declares a single declarative recipe `org.ctc.RewriteCleanup` with `recipeList: [org.openrewrite.staticanalysis.CommonStaticAnalysis]` and an explicit `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` entry in the *excluded* list (the YAML schema supports recipe-level exclusion inside a composite recipe). This makes the exclusion self-documenting and inert if Renovate later bumps `rewrite-spring`.
- **D-06:** No pre-emptive package-level exclusions (e.g., `org.ctc.domain.model.*`) in `rewrite.yml`. Lombok false-positive handling is deferred to dryRun review (see D-08). Coarse package exclusion would also block legitimate improvements like unused-import removal in entities.

### One-Shot Cleanup Commit (REWR-05)

- **D-07:** **Single atomic commit** covering the full `CommonStaticAnalysis` cleanup, NOT one-commit-per-sub-recipe. Rationale: `CommonStaticAnalysis` is a curated meta-recipe; the human audit trail is the dryRun patch file (`target/site/rewrite/rewrite.patch`) reviewed before `rewrite:run`. Commit message format: `refactor: apply OpenRewrite CommonStaticAnalysis one-shot cleanup` with body listing the recipe IDs activated and any files manually reverted.
- **D-08:** **Lombok-entity false-positive handling is post-hoc manual revert**, NOT pre-emptive `rewrite.yml` exclusion. Workflow:
  1. Run `./mvnw -Prewrite rewrite:dryRun` → inspect `target/site/rewrite/rewrite.patch`.
  2. If the patch attempts to mark `@Getter @Setter` entities `final`, drop default initializers on `@Builder.Default` fields, or add `@Override` to Lombok-generated methods → identify the offending sub-recipe and add it to `rewrite.yml`'s exclude list. Commit that `rewrite.yml` update.
  3. Re-run `rewrite:dryRun` → if clean, run `rewrite:run`.
  4. If a small number of files still need manual revert (e.g., 1–3 entity files), revert those specific files with `git checkout` BEFORE the atomic refactor commit and note them in the commit body.
- **D-09:** Pre-flight: `./mvnw verify` must be green BEFORE `rewrite:run` (clean working tree, all tests passing). The cleanup commit itself must pass `./mvnw verify` (Surefire + Failsafe + JaCoCo 82 % gate) before being pushed. No coverage regression vs the v1.10 baseline (87.80 %).

### Default-Build Isolation (REWR-03 / Success Criterion 3)

- **D-10:** Verified by inspection + a single smoke-test invocation: `./mvnw -q help:active-profiles` (without `-Prewrite`) must NOT list `rewrite`, and `./mvnw -q help:effective-pom -Dverbose` (no profile flag) must NOT contain `<artifactId>rewrite-maven-plugin</artifactId>`. The planner adds this as a Validation step in the phase plan; no permanent CI guard needed for this phase (a CI guard would be over-engineering for a profile-scoped plugin).

### README Documentation (REWR-06)

- **D-11:** README structure: add a new `### OpenRewrite (developer-invoked refactoring)` subsection under the existing `## Development` (or equivalent) section. Content:
  1. One-paragraph rationale: "developer-invoked, never in CI" with link to this CONTEXT.md.
  2. The two-command workflow (`./mvnw -Prewrite rewrite:dryRun` → inspect `target/site/rewrite/rewrite.patch` → `./mvnw -Prewrite rewrite:run` → `git diff` → commit).
  3. Note pointing to `rewrite.yml` for the active recipe set.
- **D-12:** Also append both commands to CLAUDE.md's `## Commands` block, after `./mvnw verify -Pe2e`. Single line each, no rationale (rationale lives in README + CONTEXT.md).

### Claude's Discretion

- The final `rewrite-maven-plugin` version pin (D-02) — choose latest stable on Maven Central at planning time.
- The exact wording of the README subsection (D-11) — match the surrounding tone.
- Whether the dryRun patch produces zero changes (codebase is already very clean post v1.10) — if so, REWR-05 is satisfied by a documented "no-op dryRun" entry in the phase VERIFICATION.md rather than a refactor commit, and the cleanup commit becomes a `rewrite.yml` + plugin-config commit only. Document this outcome at execute time.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Roadmap & Requirements
- `.planning/ROADMAP.md` §"Phase 80: OpenRewrite Integration" — phase goal, depends-on, 6 requirement IDs, 5 success criteria
- `.planning/REQUIREMENTS.md` §"OpenRewrite (REWR)" — REWR-01..REWR-06 line items
- `.planning/REQUIREMENTS.md` §"Out of Scope" — pins the developer-invoked-only constraint and lists Future Considerations REWR-FUTURE-01
- `.planning/PROJECT.md` §"Current Milestone: v1.11" + §"Out of Scope" — explicit list of what stays out of milestone

### Research (v1.11 milestone)
- `.planning/research/SUMMARY.md` §"Stream 1 — OpenRewrite" + §"Conflict 4: OpenRewrite Lifecycle Binding" + §"Phase 80: OpenRewrite Integration" — recipe selection, version pins, lifecycle-binding constraint, pitfalls
- `.planning/research/STACK.md` — verified plugin coordinates (6.39.0) + plugin dep versions (rewrite-spring 6.30.4, rewrite-migrate-java 3.34.1)
- `.planning/research/PITFALLS.md` — Pitfall 1 (Boot-4 composite recipe), Pitfall 3 (structural recipe interference with Lombok entities), Pitfall 10 (plugin auto-execution)
- `.planning/research/ARCHITECTURE.md` — component map showing `rewrite.yml` at project root + plugin location in `pom.xml`
- `.planning/research/FEATURES.md` — original Phase 999.1 backlog framing

### Codebase Maps (for planning)
- `.planning/codebase/STACK.md` — current pom.xml plugin inventory (where to slot the new profile)
- `.planning/codebase/CONVENTIONS.md` — Maven and Lombok conventions that constrain the cleanup diff
- `.planning/codebase/TESTING.md` §"Test Categorization (@Tag)" — informs which classes the cleanup must NOT touch in ways that break tag inheritance

### Live Build Configuration
- `pom.xml` lines 17–24 (properties: `java.version=25`, `lombok.version=1.18.46`)
- `pom.xml` lines 220–386 (`<build><plugins>` and existing `<profiles>` block at line 388 — the `e2e` profile is the structural pattern to mirror for the new `rewrite` profile)

### External (OpenRewrite docs)
- https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module — version verification at planning time
- https://docs.openrewrite.org/reference/rewrite-maven-plugin — plugin goal/configuration reference
- https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis — sub-recipe inventory (informs D-08 false-positive triage)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`<profile id="e2e">` block in pom.xml lines 388–422** — structural template for the new `<profile id="rewrite">` block (same shape: profile id, `<build><plugins>` containing exactly one plugin, no `<activation>` element so it's strictly opt-in via `-P`).
- **`lombok.version` property (pom.xml:19) and the JEP 498 `--sun-misc-unsafe-memory-access=allow` compiler flag (pom.xml:249)** — Lombok 1.18.46 is the version OpenRewrite recipes will see; the JEP flag stays unchanged (only affects compile/test, not the rewrite plugin's annotation-processor pass).
- **JaCoCo 82 % gate (pom.xml:347)** — the cleanup commit must not regress coverage; `CommonStaticAnalysis`'s `RemoveUnusedPrivateMethods` is the most likely line-count mover, so verify `target/site/jacoco/index.html` after running.

### Established Patterns
- **No `<executions>` block on developer-invoked plugins** — the existing `versions-maven-plugin` (pom.xml:299–302) has no `<executions>`, providing precedent for "plugin available but lifecycle-inert". OpenRewrite follows the same shape inside the rewrite profile.
- **Profile-scoped plugin declaration** — the `e2e` profile (pom.xml:388–422) is the only existing profile; it adds an *execution* to the failsafe plugin. The rewrite profile adds the *entire plugin* — a stricter form of isolation. Document this delta in the planner notes.
- **CLAUDE.md `## Commands` block** — every developer command currently in the project lives there as a fenced `./mvnw …` line. The two new rewrite commands (D-12) slot at the end of that block.

### Integration Points
- **`pom.xml` `<profiles>` block (line 388)** — single insertion point: add `<profile id="rewrite">` directly after the existing `e2e` profile.
- **`rewrite.yml`** — net-new file at repository root, sibling to `pom.xml`, `lombok.config` (does NOT exist yet — Phase 81 creates it), `mvnw`, and `mvnw.cmd`.
- **README.md `## Development` section** — current README has `## Commands` and a setup/architecture section; D-11 adds an OpenRewrite subsection. Check during planning whether `## Development` already exists or needs to be introduced.
- **CLAUDE.md `## Commands` block (CLAUDE.md ~lines 31–60)** — append-only edit; do not reorder existing commands.

### What This Phase Does NOT Touch
- `src/main/java/**` — no source changes from this phase's *plumbing* commits; only the one-shot cleanup commit modifies sources (and only as the recipe pack dictates after dryRun review).
- `src/test/java/**` — `CommonStaticAnalysis` may legitimately clean test code; that's allowed but the same dryRun-review discipline applies (D-08).
- `.github/workflows/**` — NO CI changes in this phase (Pitfall #10 lock).
- `Dockerfile`, `docker-compose*.yml` — untouched.
- Flyway `V*.sql` migrations — untouched (would be a violation anyway per CLAUDE.md).

</code_context>

<specifics>
## Specific Ideas

- The branch for this phase work is `gsd/v1.11-tooling-and-cleanup` (per PROJECT.md tail line). Per CLAUDE.md `## Git Workflow`, the actual implementation work happens on a feature branch off `origin/master`; the planner picks the exact branch name (e.g., `feature/openrewrite-integration`).
- The one-shot cleanup commit (D-07) and the plugin-wiring commit are separate atomic commits — the wiring commit must merge-cleanly before any source-mutating rewrite run.
- Follow CLAUDE.md `## Git Workflow` `## Before PR` discipline: `./mvnw verify` green locally, self-review, then `gh pr create --assignee jegr78`.
- Coverage discipline: capture pre-cleanup coverage from a fresh `./mvnw verify` run before the cleanup commit, then assert post-cleanup coverage ≥ pre-cleanup coverage (or ≥ 82 % gate, whichever is the stricter live floor).

</specifics>

<deferred>
## Deferred Ideas

- **`UpgradeToJava25` and other migrate-java recipes** — REWR-FUTURE-01 captures the broader expansion. Re-evaluate once the developer workflow is bedded in (one milestone of operation).
- **CTC-specific custom recipes** (e.g., enforce `@RequiredArgsConstructor` over manual constructors; enforce `@Slf4j` over manual loggers) — explicitly REWR-FUTURE-01 in REQUIREMENTS.md.
- **CI gate that runs `rewrite:dryRun` and fails the build on a non-empty diff** — explicitly out of scope per the "Out of Scope" table in REQUIREMENTS.md. Revisit only if drift becomes a recurring code-review finding.
- **`UpgradeSpringBoot_4_0` activation** — permanently excluded. Revisit only when migrating FROM Boot 4 to a future Boot version, at which point a NEW phase is required.
- **Spring-recipe activation (e.g., `org.openrewrite.java.spring.boot4.*` non-upgrade recipes)** — possible follow-up; the plugin dependency `rewrite-spring` is already on the classpath (D-03), so activation is a one-line `rewrite.yml` change in a future phase.

</deferred>

---

*Phase: 80-openrewrite-integration*
*Context gathered: 2026-05-16*
