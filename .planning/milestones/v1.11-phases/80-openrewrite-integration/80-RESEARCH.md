# Phase 80: OpenRewrite Integration — Research

**Researched:** 2026-05-16
**Domain:** Maven build tooling — developer-invoked recipe-driven refactoring (Java 25 / Spring Boot 4.0.6)
**Confidence:** HIGH (plugin coordinates verified live against Maven Central; YAML schema and recipe-exclusion behaviour confirmed against official OpenRewrite issue tracker; existing pom.xml structure verified line-by-line)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Plugin Wiring**
- **D-01:** Plugin declared **inside `<profile id="rewrite">`**, NOT in the main `<build><plugins>` block. A bare `./mvnw rewrite:run` without `-Prewrite` will fail-fast with "plugin not found" instead of silently mutating sources. Satisfies REWR-03 ("no `<executions>` binding to default lifecycle") — the plugin has zero executions in any scope.
- **D-02:** Plugin version: defer final pin to the planner/researcher. SUMMARY.md cites 6.39.0 (STACK.md verified) and ARCHITECTURE.md cites 6.40.0; pick the latest stable on Maven Central at planning time.
- **D-03:** Plugin dependencies declared in the same profile: `rewrite-spring` 6.30.4 + `rewrite-migrate-java` 3.34.1.

**Recipe Configuration (`rewrite.yml`)**
- **D-04:** Recipe set is **minimal** — only `org.openrewrite.staticanalysis.CommonStaticAnalysis`.
- **D-05:** `rewrite.yml` declares a single declarative recipe `org.ctc.RewriteCleanup` with `recipeList: [org.openrewrite.staticanalysis.CommonStaticAnalysis]` and an explicit `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` entry in the *excluded* list.
- **D-06:** No pre-emptive package-level exclusions (e.g., `org.ctc.domain.model.*`) in `rewrite.yml`.

**One-Shot Cleanup Commit (REWR-05)**
- **D-07:** Single atomic commit covering the full `CommonStaticAnalysis` cleanup, NOT one-commit-per-sub-recipe. Commit message: `refactor: apply OpenRewrite CommonStaticAnalysis one-shot cleanup`.
- **D-08:** Lombok-entity false-positive handling is post-hoc manual revert, NOT pre-emptive `rewrite.yml` exclusion. Workflow: dryRun → inspect patch → if false-positives, add offending sub-recipe to exclude list and commit, re-dryRun, run; manual `git checkout` on 1–3 files allowed before the atomic refactor commit.
- **D-09:** Pre-flight `./mvnw verify` must be green; cleanup commit itself must pass `./mvnw verify` (Surefire + Failsafe + JaCoCo 82 % gate). No coverage regression vs the v1.10 baseline (87.80 %).

**Default-Build Isolation (REWR-03 / Success Criterion 3)**
- **D-10:** Verified by inspection: `./mvnw -q help:active-profiles` (without `-Prewrite`) must NOT list `rewrite`, and `./mvnw -q help:effective-pom` (no profile flag) must NOT contain `<artifactId>rewrite-maven-plugin</artifactId>`. No permanent CI guard needed.

**README Documentation (REWR-06)**
- **D-11:** README structure: add a new `### OpenRewrite (developer-invoked refactoring)` subsection under the existing `## Development` section. Content: one-paragraph rationale, two-command workflow, pointer to `rewrite.yml`.
- **D-12:** Append both commands to CLAUDE.md's `## Commands` block, after `./mvnw verify -Pe2e`.

### Claude's Discretion

- The final `rewrite-maven-plugin` version pin (D-02) — choose latest stable on Maven Central at planning time.
- The exact wording of the README subsection (D-11) — match the surrounding tone.
- Whether the dryRun patch produces zero changes (codebase is already very clean post v1.10) — if so, REWR-05 is satisfied by a documented "no-op dryRun" entry in the phase VERIFICATION.md rather than a refactor commit, and the cleanup commit becomes a `rewrite.yml` + plugin-config commit only.

### Deferred Ideas (OUT OF SCOPE)

- `UpgradeToJava25` and other migrate-java recipes — REWR-FUTURE-01.
- CTC-specific custom recipes (e.g., enforce `@RequiredArgsConstructor`, enforce `@Slf4j`) — REWR-FUTURE-01.
- CI gate that runs `rewrite:dryRun` and fails on a non-empty diff — explicitly out of scope per REQUIREMENTS.md "Out of Scope" table.
- `UpgradeSpringBoot_4_0` activation — permanently excluded.
- Spring-recipe activation (e.g., `org.openrewrite.java.spring.boot4.*` non-upgrade recipes) — possible follow-up phase; classpath already prepared via D-03.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REWR-01 | Developer can invoke `./mvnw -Prewrite rewrite:dryRun` to preview recipe-driven changes without modifying source files | Plugin coordinates verified; goal binding is on-demand only (no `<executions>`); profile-scoped per D-01. See §Implementation Targets §REWR-01. |
| REWR-02 | Developer can invoke `./mvnw -Prewrite rewrite:run` to apply approved recipes, producing a reviewable diff | Same plugin block as REWR-01; `rewrite:run` writes in place to `src/main/java/**` so the changes appear as a normal `git diff`. See §Implementation Targets §REWR-02. |
| REWR-03 | `rewrite-maven-plugin` wired with no `<executions>` binding so it never runs during the default `verify` lifecycle | D-01 over-delivers on this requirement: NOT in `<build><plugins>` at all — only inside `<profile id="rewrite">`. Verified safe by §Verification Approach. |
| REWR-04 | A project-level `rewrite.yml` activates the `CommonStaticAnalysis` recipe pack and explicitly excludes `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` | See §Recipe Selection Detail. Critical finding: `UpgradeSpringBoot_4_0` is NOT a sub-recipe of `CommonStaticAnalysis` (they are in separate recipe packs), so "exclusion" is documentary/self-enforcing rather than a technical filter. The recipe is excluded from `activeRecipes`, which is OpenRewrite's only mechanism for opting recipes in. |
| REWR-05 | `CommonStaticAnalysis` applied once as a one-shot cleanup commit on the v1.11 branch with the diff reviewed against Lombok-generated false positives | D-07 (single atomic commit) + D-08 (post-hoc false-positive workflow). Note: CONTEXT.md "Claude's Discretion" allows a documented "no-op dryRun" outcome if the codebase is already clean (codebase entered v1.11 at 87.80 % JaCoCo + clean code). See §Open Questions. |
| REWR-06 | README "Development" section documents the OpenRewrite invocation pattern (dryRun then run) and the deliberate decision to keep it developer-invoked | README **does not currently have a `## Development` section** — the planner must introduce it. See §README + CLAUDE.md Edits. |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

The planner MUST honour these directives — none can be violated by Phase 80 implementation:

| Directive | Phase 80 Implication |
|-----------|----------------------|
| Test coverage ≥ 82 % (JaCoCo BUNDLE rule, pom.xml:347) | Cleanup commit (if non-empty) must keep coverage ≥ 82 %; D-09 also requires no regression vs v1.10 baseline 87.80 %. |
| Flyway: never modify existing `V*.sql` migrations | Phase 80 does not touch SQL. `rewrite.yml` should exclude `src/main/resources/db/migration/**` via plugin `<exclusions>` only if `CommonStaticAnalysis` is shown to scan non-Java files (it does not — recipes target `.java` only — so no exclusion needed). |
| OSIV stays enabled | No impact. |
| Playwright remains compile-scope | No impact (`CommonStaticAnalysis` recipes that propose dependency scope changes are not present in the recipe pack). |
| No breaking changes to existing URLs/endpoints | No impact (Phase 80 is build-config only). |
| Git workflow: feature branch off `origin/master`, PR to master via `gh pr create --assignee jegr78`, Conventional Commits | Cleanup commit message format dictated by D-07: `refactor: apply OpenRewrite CommonStaticAnalysis one-shot cleanup`. Plugin-wiring commit should use `chore(build):` or `feat(build):` prefix. |
| Subagents: opus/sonnet only, no `git stash`/`git checkout`/`git reset`/branch switching | Cleanup task in particular must NEVER be delegated to a haiku subagent; D-08's "manual `git checkout` on 1–3 files" is an orchestrator-only action. |
| Atomic tasks: each PLAN task individually executable | The plugin-wiring commit (D-01..D-03) and the cleanup commit (D-07) are SEPARATE atomic commits — the wiring must merge-clean before any source-mutating rewrite run. |
| Tag tests by category (`@Tag`) | No new tests in Phase 80 (build-config only). If a verification IT is added, it must be `@Tag("integration")` and named `*IT.java`. |

## Phase 80 Research Summary

Phase 80 wires `rewrite-maven-plugin` 6.39.0 (latest stable on Maven Central as of 2026-05-07, verified live) into a new `<profile id="rewrite">` block in `pom.xml`, mirroring the structural shape of the existing `<profile id="e2e">` (pom.xml:388–422). A net-new `rewrite.yml` at the repo root defines a single declarative composite recipe `org.ctc.RewriteCleanup` whose `recipeList` activates only `org.openrewrite.staticanalysis.CommonStaticAnalysis`. The plugin has zero `<executions>` and exists only inside the profile, so `./mvnw verify` without `-Prewrite` cannot trigger it — directly satisfying REWR-03 and Success Criterion 3. Documentation work spans README (introduce a new `## Development` section — currently absent) and a two-line CLAUDE.md `## Commands` append (D-12).

**Key decision touchpoints for the planner:**
1. Plugin version **6.39.0** is the verified latest stable (ARCHITECTURE.md's "6.40.0" citation is incorrect — Maven Central lastUpdated is 2026-05-07, latest=6.39.0).
2. The plugin dependency set in D-03 (`rewrite-spring:6.30.4` + `rewrite-migrate-java:3.34.1`) transitively pulls in `rewrite-static-analysis:2.34.1` — so `CommonStaticAnalysis` is on the classpath without explicit declaration.
3. README has no `## Development` section today — the planner must create it as a new top-level section (D-11 reads "subsection under the existing `## Development`" but the section does not exist).

**Biggest implementation-shape risk:** OpenRewrite has **no built-in mechanism to exclude a sub-recipe from a composite** [VERIFIED: openrewrite/rewrite#1714 closed wontfix, maintainer comment 2022 by `traceyyoshima`: "I recommend copying the recipe and removing [the unwanted sub-recipe]"]. CONTEXT.md D-05 phrasing — "the YAML schema supports recipe-level exclusion inside a composite recipe" — is technically **incorrect**. The exclusion of `UpgradeSpringBoot_4_0` works *only* because that recipe is in a different pack (`rewrite-spring`) and is never listed in `activeRecipes` — it is self-enforcing and documentary. If the planner needs to remove a sub-recipe FROM `CommonStaticAnalysis` (e.g., post-D-08 to silence a Lombok false positive), the only viable mechanism is **inlining the sub-recipe list minus the offender** into the composite. See §Recipe Selection Detail for the YAML shape.

**Primary recommendation:** Two atomic commits on a single feature branch (`feature/openrewrite-integration` or similar) off `origin/master`: (1) plugin wiring + `rewrite.yml` + docs; (2) cleanup commit IF dryRun produces a non-empty patch — otherwise the phase ships as commit (1) only, and the no-op dryRun is recorded in the phase VERIFICATION.md per CONTEXT.md "Claude's Discretion".

## Architectural Responsibility Map

Phase 80 has no application code change — it operates on the build tier only. Tier mapping is included for completeness so the planner can verify task assignment.

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Recipe execution (AST rewriting) | Build (Maven plugin) | — | Owned by `rewrite-maven-plugin` running inside the developer's Maven process; no runtime impact. |
| Recipe selection / exclusion | Config (`rewrite.yml`) | — | Declarative recipe file at repo root; sibling to `pom.xml`. |
| Profile activation | Build (Maven `-P` flag) | — | `<profile id="rewrite">` in pom.xml, opt-in only. |
| Workflow documentation | Docs (README + CLAUDE.md) | — | README "Development" subsection (D-11); CLAUDE.md `## Commands` append (D-12). |
| Default-build isolation | Build (Maven lifecycle) | — | No `<executions>`; plugin lives outside main `<build>` — verified via `help:effective-pom`. |

## Implementation Targets

### REWR-01 / REWR-02 — Plugin wiring inside `<profile id="rewrite">`

**File changed:** `pom.xml`
**Where:** Inside the existing `<profiles>` block (currently contains only the `e2e` profile at lines 388–422). Add the new `rewrite` profile **after** the `e2e` profile, before `</profiles>`.

**Shape (illustrative — full XML in §Maven Profile Structure):**
```xml
<profile>
  <id>rewrite</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.openrewrite.maven</groupId>
        <artifactId>rewrite-maven-plugin</artifactId>
        <version>6.39.0</version>
        <configuration>
          <activeRecipes>
            <recipe>org.ctc.RewriteCleanup</recipe>
          </activeRecipes>
          <configLocation>${project.basedir}/rewrite.yml</configLocation>
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.openrewrite.recipe</groupId>
            <artifactId>rewrite-spring</artifactId>
            <version>6.30.4</version>
          </dependency>
          <dependency>
            <groupId>org.openrewrite.recipe</groupId>
            <artifactId>rewrite-migrate-java</artifactId>
            <version>3.34.1</version>
          </dependency>
        </dependencies>
        <!-- NO <executions> block — plugin is invocation-only -->
      </plugin>
    </plugins>
  </build>
</profile>
```

**Acceptance:** `./mvnw -Prewrite rewrite:dryRun` runs and writes `target/site/rewrite/rewrite.patch` (or produces no patch if the codebase is clean). `./mvnw -Prewrite rewrite:run` modifies files in place. Without `-Prewrite`, `./mvnw rewrite:dryRun` fails with `Could not find goal 'dryRun' in plugin …` because the plugin is not in the default build.

### REWR-03 — No lifecycle binding (verified at the structural level)

**File checked:** `pom.xml` after the wiring edit.
**Verification:**
- Without `-Prewrite`: `./mvnw -q help:active-profiles` shows no `rewrite` profile listed.
- Without `-Prewrite`: `./mvnw -q help:effective-pom | grep -c rewrite-maven-plugin` returns `0`.
- Without `-Prewrite`: `./mvnw -q verify` log does NOT contain the string `Running OpenRewrite`.

### REWR-04 — `rewrite.yml` at project root

**File created:** `rewrite.yml` (sibling to `pom.xml`).
**Shape:** see §Recipe Selection Detail below.

### REWR-05 — One-shot cleanup commit

**Sequence:**
1. Ensure clean working tree.
2. Run `./mvnw verify` — must pass (D-09).
3. Run `./mvnw -Prewrite rewrite:dryRun`.
4. Inspect `target/site/rewrite/rewrite.patch` (file may not exist if no changes — that's a valid outcome per CONTEXT.md "Claude's Discretion").
5. **If non-empty:** triage Lombok false positives (D-08); for each offending sub-recipe, edit `rewrite.yml` to remove it from the composite's `recipeList` (since OpenRewrite does NOT support sub-recipe exclusion, see §Recipe Selection Detail). Commit the `rewrite.yml` edit. Re-dryRun. Repeat until the patch is acceptable. Then `./mvnw -Prewrite rewrite:run`. If 1–3 entity files still need manual revert, `git checkout` those specific files. Commit:
   ```
   refactor: apply OpenRewrite CommonStaticAnalysis one-shot cleanup

   Active composite: org.ctc.RewriteCleanup
   Recipes applied: org.openrewrite.staticanalysis.CommonStaticAnalysis (minus <list>)
   Files manually reverted: <list, or "none">
   ```
6. **If empty:** the phase ships as the wiring commit only. Document the no-op outcome in `80-VERIFICATION.md`.
7. Final gate: `./mvnw verify` must pass before push (Surefire + Failsafe + JaCoCo ≥ 82 % BUNDLE).

### REWR-06 — README + CLAUDE.md documentation

**Files changed:** `README.md`, `CLAUDE.md` — see §README + CLAUDE.md Edits below.

## Recipe Selection Detail

### Critical Finding: Sub-Recipe Exclusion Is Not Supported

[VERIFIED: openrewrite/rewrite#1714, closed wontfix, maintainer `traceyyoshima` comment 2022]

OpenRewrite has **no mechanism** — neither in YAML, plugin XML, nor CLI — to exclude a specific sub-recipe from a composite recipe. The maintainer-recommended workaround is to "copy the recipe and remove [the sub-recipe]" — i.e., inline the desired sub-recipes minus the unwanted one.

The CONTEXT.md D-05 phrasing — *"the YAML schema supports recipe-level exclusion inside a composite recipe"* — is therefore **technically incorrect**. The `UpgradeSpringBoot_4_0` exclusion works for a different reason: **`UpgradeSpringBoot_4_0` is NOT a sub-recipe of `CommonStaticAnalysis`**. They are in completely separate recipe packs:
- `CommonStaticAnalysis` lives in `org.openrewrite.recipe:rewrite-static-analysis:2.34.1` and bundles ~70 cleanup sub-recipes (see [CommonStaticAnalysis docs](https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis) — none are Spring Boot upgrade recipes).
- `UpgradeSpringBoot_4_0` lives in `org.openrewrite.recipe:rewrite-spring:6.30.4`.

Because OpenRewrite ONLY runs recipes listed in `<activeRecipes>` (it is whitelist-only — verified in [Maven plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin) — `"No recipe is run unless explicitly turned on with this setting"`), `UpgradeSpringBoot_4_0` is excluded simply by NOT listing it in `activeRecipes`. The "exclusion" list in `rewrite.yml` is purely **documentary / self-enforcing**: a tripwire that signals intent to any future maintainer who reads the file.

**Implication for the planner:** The D-08 false-positive workflow ("if a sub-recipe misbehaves, add it to `rewrite.yml`'s exclude list") needs to be reinterpreted. Since exclusion-from-composite is not supported, the only valid actions when a sub-recipe of `CommonStaticAnalysis` produces a false positive are:
- **(a) Inline workaround (recommended):** Replace `org.openrewrite.staticanalysis.CommonStaticAnalysis` in the composite's `recipeList` with the full list of its ~70 sub-recipes minus the offender. The full sub-recipe list is enumerated in the [CommonStaticAnalysis docs](https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis). Document the rationale in a YAML comment.
- **(b) Manual revert (per D-08 final fallback):** `git checkout` 1–3 entity files before the atomic refactor commit. Acceptable when the false positive affects only a small number of files.

### The `rewrite.yml` Shape

**File:** `rewrite.yml` at repository root (sibling to `pom.xml`, `mvnw`, `mvnw.cmd`, `README.md`).

**Shape:**
```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: org.ctc.RewriteCleanup
displayName: CTC Manager — common static analysis cleanup
description: >
  Developer-invoked one-shot cleanup pack for CTC Manager.
  Activates only org.openrewrite.staticanalysis.CommonStaticAnalysis (≈70 sub-recipes).
  See README "## Development" for the dryRun → run workflow.

# OpenRewrite is whitelist-only: any recipe not listed here is inert by construction
# (https://docs.openrewrite.org/reference/rewrite-maven-plugin —
#  "No recipe is run unless explicitly turned on with this setting").
recipeList:
  - org.openrewrite.staticanalysis.CommonStaticAnalysis

# Documentary exclusion list — recipes that are deliberately NOT activated for this project.
# OpenRewrite does not support recipe-level exclusion inside a composite
# (https://github.com/openrewrite/rewrite/issues/1714 wontfix). These entries exist as a
# tripwire for future maintainers: do NOT add them to recipeList above.
#
# org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0  — codebase already on Boot 4.0.6;
#   this recipe migrates FROM Boot 3 and would produce confusing diffs (see PITFALLS Pitfall 1).
```

**`activeRecipes` element in `pom.xml`** activates only the composite:
```xml
<configuration>
  <activeRecipes>
    <recipe>org.ctc.RewriteCleanup</recipe>
  </activeRecipes>
  <configLocation>${project.basedir}/rewrite.yml</configLocation>
</configuration>
```

**Verification at planning time:** The planner should run `./mvnw -Prewrite rewrite:discover` once after wiring to confirm `org.ctc.RewriteCleanup` appears in the discovery output and `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` is present in the classpath (proving the rewrite-spring dependency is correctly loaded for the future-stagging argument in D-03).

## Maven Profile Structure

Drop the following directly after the existing `<profile id="e2e">` block in pom.xml (i.e., after line 421 `</profile>`, before line 422 `</profiles>`). Mirror the existing structural style (4-tab indent, `<id>`-only opt-in, no `<activation>` element).

```xml
<profile>
  <id>rewrite</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.openrewrite.maven</groupId>
        <artifactId>rewrite-maven-plugin</artifactId>
        <version>6.39.0</version>
        <configuration>
          <activeRecipes>
            <recipe>org.ctc.RewriteCleanup</recipe>
          </activeRecipes>
          <configLocation>${project.basedir}/rewrite.yml</configLocation>
          <exportDatatables>false</exportDatatables>
        </configuration>
        <dependencies>
          <dependency>
            <groupId>org.openrewrite.recipe</groupId>
            <artifactId>rewrite-spring</artifactId>
            <version>6.30.4</version>
          </dependency>
          <dependency>
            <groupId>org.openrewrite.recipe</groupId>
            <artifactId>rewrite-migrate-java</artifactId>
            <version>3.34.1</version>
          </dependency>
          <!-- rewrite-static-analysis:2.34.1 (the home of CommonStaticAnalysis)
               is pulled in transitively by rewrite-spring:6.30.4 — no explicit
               declaration required. Verified against rewrite-spring-6.30.4.pom
               on Maven Central. -->
        </dependencies>
      </plugin>
    </plugins>
  </build>
</profile>
```

**Verified plugin coordinates (live, 2026-05-16):**

| Coordinate | Verified | Source |
|------------|----------|--------|
| `org.openrewrite.maven:rewrite-maven-plugin:6.39.0` | ✓ | `curl https://repo1.maven.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/maven-metadata.xml` → `<latest>6.39.0</latest>`, `lastUpdated=20260507012722` [VERIFIED: Maven Central] |
| `org.openrewrite.recipe:rewrite-spring:6.30.4` | ✓ | `<latest>6.30.4</latest>` [VERIFIED: Maven Central] |
| `org.openrewrite.recipe:rewrite-migrate-java:3.34.1` | ✓ | `<latest>3.34.1</latest>` [VERIFIED: Maven Central] |
| `org.openrewrite.recipe:rewrite-static-analysis:2.34.1` | ✓ (transitive via rewrite-spring) | `rewrite-spring-6.30.4.pom` declares it as a direct dependency [VERIFIED: Maven Central POM inspection] |

**Note on ARCHITECTURE.md citation of "6.40.0":** That version does NOT exist on Maven Central as of 2026-05-16. The latest is **6.39.0** (last published 2026-05-07). ARCHITECTURE.md was either a typo or a snapshot/pre-release expectation. The planner should pin **6.39.0**. If 6.40.0 ships between Phase 80 planning and execution, re-verify against Maven Central before pinning.

**Structural mirror of the `e2e` profile (pom.xml:388–422):**
- Both profiles: `<id>name</id>` only, NO `<activation>` element → strictly opt-in via `-P<name>`.
- Both profiles: a single `<build><plugins><plugin>…</plugin></plugins></build>` block.
- Difference: the `e2e` profile adds an EXECUTION to an already-declared plugin (failsafe). The `rewrite` profile adds the ENTIRE plugin — a stricter form of isolation, and the correct choice for a plugin that must never run in the default lifecycle.

## README + CLAUDE.md Edits

### README.md — new `## Development` section

**Current state of README.md (verified 2026-05-16):** The file has these top-level sections in order:
1. `# CTC Manager` (title)
2. `## Tech Stack`
3. `## Features`
4. `## Backup & Restore`
5. `## Quick Start`
6. `## Playwright Setup (Team Cards + E2E Tests)`
7. `## Documentation`

**There is NO `## Development` section.** D-11's wording — "add a new `### OpenRewrite (developer-invoked refactoring)` subsection under the existing `## Development` (or equivalent) section" — must be reinterpreted: the planner creates the `## Development` section as part of this phase.

**Proposed placement:** Insert `## Development` **after `## Playwright Setup`** and **before `## Documentation`**. This groups developer-tooling content together (Playwright is also a developer setup step).

**Proposed wording (illustrative — match surrounding tone, ~80-120 words):**

```markdown
## Development

### OpenRewrite (developer-invoked refactoring)

CTC Manager wires the [OpenRewrite](https://docs.openrewrite.org/) Maven plugin
into a dedicated `rewrite` profile, NOT the default `verify` lifecycle. Recipes
are run on demand by the developer and never as part of CI — this avoids any
risk of silent in-place source mutation during a build.

The active recipe set is defined in [`rewrite.yml`](./rewrite.yml). Today it
activates only `org.openrewrite.staticanalysis.CommonStaticAnalysis`.

Workflow:

```bash
# 1. Preview changes (writes target/site/rewrite/rewrite.patch if non-empty)
./mvnw -Prewrite rewrite:dryRun

# 2. Inspect the patch file — confirm no Lombok-entity false positives
cat target/site/rewrite/rewrite.patch

# 3. Apply the recipes to source files in place
./mvnw -Prewrite rewrite:run

# 4. Review with git diff, then commit normally
git diff
```

Without `-Prewrite` the plugin is not on the build, so a plain `./mvnw verify`
adds zero overhead.
```

### CLAUDE.md — append two lines to `## Commands` block

**Current state of CLAUDE.md `## Commands` block (verified at the top of this conversation):** Last fenced bash command is the Docker production line `docker compose -f docker-compose.prod.yml up -d`. Per D-12, the two new lines go after `./mvnw verify -Pe2e` (currently around line 35 of the project CLAUDE.md). Single-line each, no rationale text.

**Proposed insertion (right after the `./mvnw verify -Pe2e` line):**

```bash
# OpenRewrite: preview recipe-driven refactoring (no file changes)
./mvnw -Prewrite rewrite:dryRun

# OpenRewrite: apply recipes to source files in place
./mvnw -Prewrite rewrite:run
```

This is append-only — do not reorder existing commands (per code_context note in CONTEXT.md).

## Verification Approach for Success Criterion 3

Success Criterion 3 reads: *"`./mvnw verify` (without `-Prewrite`) produces no 'Running OpenRewrite' output and adds zero seconds versus the v1.10 baseline."*

**Decision (per CONTEXT.md D-10): structural verification only — no wall-clock benchmark required.**

**Three structural verification commands** (the planner adds these as explicit acceptance steps in PLAN.md):

```bash
# 1. Confirm the rewrite profile is opt-in only
./mvnw -q help:active-profiles | grep -i rewrite
# Expected: no output (exit non-zero is fine — grep returns 1 on no match)

# 2. Confirm the plugin is not in the default effective POM
./mvnw -q help:effective-pom | grep -c 'rewrite-maven-plugin'
# Expected: 0

# 3. Confirm verify does not run the plugin
./mvnw -q verify 2>&1 | grep -i 'Running OpenRewrite'
# Expected: no output
```

**Rationale for skipping wall-clock benchmarking:** A plugin with no `<executions>` declared inside a profile that is not activated cannot, by Maven's lifecycle semantics, add measurable wall-clock time to `./mvnw verify`. The structural verification above proves the negative condition definitively. A wall-clock comparison would be:
- (a) noisy (run-to-run variance dominates < 1 second of plugin classloading overhead);
- (b) over-engineered for a profile-scoped plugin (CONTEXT.md D-10 explicitly chooses structural verification);
- (c) duplicative of the structural check — if the plugin is not on the effective POM, it cannot run.

**Optional additional confirmation (planner discretion):** `./mvnw -Prewrite help:active-profiles | grep -i rewrite` — confirms the profile DOES activate when `-Prewrite` is passed. This is the positive complement of check 1.

## CTC-Specific Pitfalls Recap

Three pitfalls from the milestone PITFALLS.md apply directly to Phase 80. Restated below in Phase-80 language with specific mitigations.

### Pitfall 80-A: `UpgradeSpringBoot_4_0` accidentally activated against an already-Boot-4 codebase

[Origin: PITFALLS.md §Pitfall 1]

**Phase 80 scenario:** A future maintainer reads the recipe inventory in OpenRewrite docs, decides "let's upgrade with this", and adds `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` to either `rewrite.yml` `recipeList` OR to pom.xml `<activeRecipes>`. The composite recipe scans pom.xml, sees `<parent><version>4.0.6</version>`, and instead of being a no-op may emit a diff that re-adds `spring-boot-starter-web` or removes one of the modular starters (`spring-boot-starter-webmvc`, `spring-boot-starter-thymeleaf`). This silently regresses the v1.10 modular-starter discipline.

**Phase 80 mitigation:** The `rewrite.yml` documentary exclusion list (see §Recipe Selection Detail) calls out `UpgradeSpringBoot_4_0` BY NAME with the reason. A grep-time check before any `rewrite:run` invocation: `grep -n UpgradeSpringBoot_4_0 rewrite.yml pom.xml` should show the recipe only in the documentary exclusion comment of `rewrite.yml`, never in `activeRecipes` or `recipeList`.

**Verification:** `./mvnw -Prewrite rewrite:dryRun` and inspect the patch for any pom.xml diff. If pom.xml is touched at all by the dryRun, abort and re-investigate.

### Pitfall 80-B: Structural recipes from `CommonStaticAnalysis` incorrectly modify Lombok-annotated entities

[Origin: PITFALLS.md §Pitfall 3]

**Phase 80 scenario:** OpenRewrite operates on source-LST without running the Lombok annotation processor, so it does not see the bytecode that `@Getter`/`@RequiredArgsConstructor`/`@NoArgsConstructor` generate. Of the 70 `CommonStaticAnalysis` sub-recipes, the highest-risk ones for CTC entities are:
- `FinalizePrivateFields` — would mark JPA `private UUID id;` as `final`, breaking Hibernate's no-arg construction.
- `RemoveExtraSemicolons`, `UnnecessaryParentheses` — safe on entities.
- `FinalClass` — would mark `@Entity` classes `final`, breaking Hibernate proxying.
- `ExplicitInitialization` — may strip `= new ArrayList<>()` initializers on `@OneToMany` collections, breaking JPA invariants.
- `StaticMethodNotFinal` — flips methods between `static` and instance with no Lombok awareness.

**Phase 80 mitigation (per D-08):** dryRun review is the gate. The planner MUST include an explicit acceptance step in PLAN.md: *"After dryRun, manually inspect every diff hunk under `src/main/java/org/ctc/domain/model/*.java`."* If a sub-recipe wants to modify entities in a problematic way, the workaround is either:
- (a) Inline `CommonStaticAnalysis` minus the offending sub-recipe in the composite (see §Recipe Selection Detail "Inline workaround");
- (b) `git checkout` the affected entity file BEFORE the atomic refactor commit (D-08 final fallback). Limit: 1–3 files. If more, switch to option (a).

**Specific entity-package marker for the planner:** Note that `lombok.config` does NOT exist yet (Phase 81 creates it). For Phase 80, the only Lombok-awareness OpenRewrite has is whatever its visitor logic detects natively. There is NO `@LombokGenerated` marker on getters/setters at Phase 80 execution time. This makes the dryRun review the SOLE defence — there is no other safety net.

### Pitfall 80-C: Plugin in default `<build>` would auto-execute on every `mvn verify`

[Origin: PITFALLS.md §Pitfall 10 + §Pitfall 19]

**Phase 80 scenario:** A well-meaning future PR moves the plugin from the `rewrite` profile to the main `<build><plugins>` block (perhaps to "make it easier to invoke" — `./mvnw rewrite:run` without `-Prewrite`). Without `<executions>` the plugin still does not auto-run, but a subsequent change adding `<executions>` with `<phase>verify</phase>` would silently mutate source on every CI build.

**Phase 80 mitigation (per D-01):** The plugin lives EXCLUSIVELY inside `<profile id="rewrite">`. A bare `./mvnw rewrite:run` fails fast with "plugin not found" — there is no path that runs the plugin without explicit `-Prewrite`. The structural verification in §Verification Approach proves this property after each pom.xml edit.

**Note on PITFALLS.md §Pitfall 10 wording:** That pitfall is phrased as "plugin CAN be in `<build><plugins>` as long as there are no `<executions>`" — i.e., it argues that profile isolation is over-engineering. CONTEXT.md D-01 explicitly OVERRIDES this and chooses the stricter form: profile-only. The CONTEXT.md rationale ("makes accidental misuse harder") is the controlling decision; the milestone-research view is documentary background. **Plan accordingly.**

## Runtime State Inventory

Phase 80 is build-config and documentation only — no application runtime state is migrated, no databases are touched, no external services are reconfigured. Confirming the five categories explicitly per the research protocol:

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 80 does not touch any database, file, or cache | None |
| Live service config | None — Phase 80 does not touch any deployed service config (no n8n, no Datadog, no Tailscale ACL, no Cloudflare Tunnel) | None |
| OS-registered state | None — Phase 80 does not register any OS-level task, daemon, or service | None |
| Secrets and env vars | None — Phase 80 does not introduce or rename any env var or secret | None |
| Build artifacts / installed packages | The `rewrite-maven-plugin` and its dependencies (`rewrite-spring`, `rewrite-migrate-java`, transitive `rewrite-static-analysis`) will be downloaded into the developer's `~/.m2/repository` on first `./mvnw -Prewrite ...` invocation. This is the standard Maven cache behaviour — no action required. | None — Maven manages cache. |

## Validation Architecture

> Phase 80 is build-config + documentation only. `workflow.nyquist_validation` defaults to enabled — section included per the standard requirement. Validation framework reuses the existing JUnit 5 / Surefire / Failsafe / Playwright stack with no new test infrastructure.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito + Spring Boot Test starters; Maven Surefire 3.x (unit, `forkCount=2`) + Failsafe 3.x (integration, `forkCount=1C` `@Tag("integration")`) + Playwright 1.59.0 (E2E, `-Pe2e` profile) |
| Config file | `pom.xml` lines 253–298 (Surefire + Failsafe configuration) + 388–422 (`e2e` profile) |
| Quick run command | `./mvnw -q verify` (Surefire + Failsafe + JaCoCo, no Playwright) |
| Full suite command | `./mvnw -q verify -Pe2e` (above + Playwright E2E) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| REWR-01 | `./mvnw -Prewrite rewrite:dryRun` produces a patch file (or no patch on clean codebase) without modifying source | smoke (shell) | `./mvnw -Prewrite rewrite:dryRun && (test -f target/site/rewrite/rewrite.patch \|\| echo "clean")` + `git diff --quiet` to confirm no source mutation | n/a (one-shot shell verification, not a JUnit test) |
| REWR-02 | `./mvnw -Prewrite rewrite:run` applies recipes and produces a reviewable `git diff` | smoke (shell, run only during phase execution) | `./mvnw -Prewrite rewrite:run && git diff --stat` | n/a (one-shot during cleanup commit) |
| REWR-03 | Plugin never runs during default `verify` lifecycle (structural) | smoke (shell) | `./mvnw -q help:effective-pom \| grep -c 'rewrite-maven-plugin'` → expected `0`; `./mvnw -q verify 2>&1 \| grep -i 'Running OpenRewrite'` → expected empty | n/a (structural verification — see §Verification Approach) |
| REWR-04 | `rewrite.yml` parses and the `org.ctc.RewriteCleanup` composite is discoverable | smoke (shell) | `./mvnw -Prewrite rewrite:discover -q \| grep 'org.ctc.RewriteCleanup'` → expected one line | n/a (rewrite.yml is net-new) |
| REWR-05 | Cleanup commit (if non-empty) passes the full test+coverage gate | full suite | `./mvnw -q verify` → green + JaCoCo `target/site/jacoco/index.html` LINE ratio ≥ 0.82 (BUNDLE rule in pom.xml:347); compare to pre-cleanup baseline | existing JaCoCo `<rule>` block; no new test file required |
| REWR-06 | README "## Development" section exists and contains the OpenRewrite subsection | docs grep | `grep -F '## Development' README.md` AND `grep -F 'OpenRewrite (developer-invoked refactoring)' README.md` AND `grep -F './mvnw -Prewrite rewrite:dryRun' README.md` | README.md exists; section is new |

**Manual verification (no automation):**
- D-12 — two new commands in CLAUDE.md `## Commands` block. Grep verification: `grep -F './mvnw -Prewrite rewrite:dryRun' CLAUDE.md` and `grep -F './mvnw -Prewrite rewrite:run' CLAUDE.md`.

### Sampling Rate

- **Per task commit:** Plugin-wiring commit: `./mvnw -q verify` + the three structural verification commands (§Verification Approach). Cleanup commit (if any): `./mvnw -q verify` + JaCoCo coverage spot-check. README/CLAUDE.md commits: grep the expected anchors.
- **Per wave merge:** N/A — Phase 80 is small enough to be a single wave (planner discretion: 1–3 plans).
- **Phase gate:** `./mvnw -q verify -Pe2e` green from a clean checkout (final pre-PR step per CLAUDE.md §"Before PR"); coverage ≥ 82 % BUNDLE LINE ratio; the three structural verification commands all produce expected outputs.

### Wave 0 Gaps

- [ ] No new JUnit test files required — all verification is structural (Maven introspection) or grep-based (docs).
- [ ] No new framework installation required — JUnit 5 / Surefire / Failsafe / Playwright already in pom.xml.
- [ ] **Optional:** the planner may add a smoke IT under `src/test/java/org/ctc/build/RewriteProfileIsolationIT.java` that parses `target/effective-pom.xml` and asserts the absence of `rewrite-maven-plugin` — this would convert structural verification into a CI guard. CONTEXT.md D-10 explicitly opts AGAINST a permanent CI guard for this phase, so this IT is OPTIONAL. If added, tag `@Tag("integration")` and name `*IT.java` per CLAUDE.md convention. **Default recommendation: skip — match CONTEXT.md D-10.**

*(If no gaps: "None — existing test infrastructure covers all phase requirements." — applies here.)*

### Nyquist Dimensions 1–8 Coverage

| Dim | Property | How Verified | Reference |
|-----|----------|--------------|-----------|
| **D1 Correctness** | `rewrite.yml` parses against the OpenRewrite YAML schema; `rewrite:dryRun` exits 0; produced `target/site/rewrite/rewrite.patch` (if any) is a well-formed unified diff | `./mvnw -Prewrite rewrite:dryRun` exit code = 0; if patch file present, `git apply --check target/site/rewrite/rewrite.patch` returns 0 | OpenRewrite Maven plugin docs |
| **D2 Idempotency** | Repeated `mvn rewrite:dryRun` on an unchanged source tree produces a byte-identical patch file | `sha256sum target/site/rewrite/rewrite.patch` taken twice across two `dryRun` invocations matches; if both runs produce no patch file, that is equally idempotent | OpenRewrite is deterministic given a fixed recipe set and fixed source; verified empirically during phase execution |
| **D3 Boundary conditions** | (a) Clean source tree → empty patch (file absent or zero hunks); (b) sub-recipe removed from composite `recipeList` → next dryRun's patch is a strict subset of the previous patch; (c) plugin invoked WITHOUT `-Prewrite` → fail-fast with `Could not find goal 'dryRun' in plugin … on project ctc-manager` | (a) tested by running dryRun on master before any edits; (b) tested by removing one sub-recipe and re-dryRunning; (c) tested by `./mvnw rewrite:dryRun` (no `-Prewrite`) → expected non-zero exit + the "plugin not found" message | §Recipe Selection Detail |
| **D4 Concurrency** | None — single developer, manual invocation, no parallel execution path | n/a (no concurrent invocation by design) | CONTEXT.md domain section |
| **D5 Failure modes** | (a) Maven Central network failure during plugin resolution → plugin/dep download retried by Maven; error surfaces as build failure with a clear message; (b) OpenRewrite version drift between local and CI → out of scope (per Pitfall 80-C, OpenRewrite never runs in CI) | (a) acceptable — Maven's standard `<repositories>` retry policy applies, no extra resilience needed; (b) structurally impossible — CI doesn't run OpenRewrite | PITFALLS Pitfall 10 |
| **D6 Performance** | Zero impact on default `./mvnw verify` wall-clock vs v1.10 baseline | Structural verification only (CONTEXT.md D-10); see §Verification Approach. No wall-clock benchmark required because the plugin is not in the default build's effective POM | CONTEXT.md D-10 |
| **D7 Security (recipe pack supply chain)** | Plugin and recipe-pack dependencies resolved from Maven Central only; no custom Maven repository added; recipe-pack versions pinned (no `RELEASE` / no `LATEST`) | `pom.xml` `<repositories>` block unchanged (defaults to Maven Central only); plugin + 2 dependencies have explicit versions (6.39.0 / 6.30.4 / 3.34.1). Future Renovate phase (Phase 84) will manage version bumps via PRs that hit the existing CI gate | §Maven Profile Structure |
| **D8 Validation** | Post-cleanup `./mvnw verify` is green (Surefire + Failsafe + JaCoCo 82 % BUNDLE rule); coverage does not regress vs v1.10 baseline 87.80 % | `./mvnw -q verify` exit 0 + `target/site/jacoco/index.html` LINE ratio ≥ 0.82 AND ≥ pre-cleanup baseline | CLAUDE.md §"Test Coverage"; v1.10 STATE.md baseline |

## Open Questions for Planner

These are per-task decisions the planner must make in PLAN.md beyond what CONTEXT.md locks.

1. **Should the planner add a `./mvnw -Prewrite rewrite:discover` verification step?**
   - **What it gives:** A transparent inventory of every recipe ID on the classpath, written to `target/site/rewrite/recipes.yml`. Useful for confirming that `org.ctc.RewriteCleanup` is correctly registered and that `UpgradeSpringBoot_4_0` IS present (proving the documentary exclusion is meaningful, not stale).
   - **Cost:** ~30 seconds of one-time runtime during phase execution. No commit overhead.
   - **Recommendation:** **Yes** — include as a one-shot acceptance step in the plugin-wiring plan. Capture the output in `80-VERIFICATION.md` (or commit `target/site/rewrite/recipes.yml` to the phase dir as a reproducibility artifact, planner's choice).

2. **Where does the cleanup commit (D-07) live — on the feature branch or directly on `gsd/v1.11-tooling-and-cleanup`?**
   - CLAUDE.md §"Git Workflow" says branch off `origin/master` to a feature branch; STATE.md says the milestone branch is `gsd/v1.11-tooling-and-cleanup`.
   - **Recommendation:** Feature branch off `origin/master` (per CLAUDE.md). Branch name: `feature/openrewrite-integration`. Squash-merge to master after PR review (per CLAUDE.md §"After PR"). The milestone branch is a tracking pointer, not an execution branch.
   - **Confirm with user only if** the v1.11 workflow has shifted to direct-on-milestone-branch commits — if so, the planner needs the orchestrator to clarify.

3. **What is the planner's expectation if the dryRun patch is empty?**
   - CONTEXT.md "Claude's Discretion" explicitly allows a no-op outcome: REWR-05 then becomes a documented "no-op dryRun" entry in `80-VERIFICATION.md`, and Phase 80 ships as the wiring commit only.
   - **Recommendation:** Make this a branch in PLAN.md: a conditional plan slot ("Plan 03 — Cleanup commit IF dryRun non-empty, ELSE documented no-op"). This avoids a stuck plan slot if the codebase is already clean.

4. **Should the planner inline the optional `RewriteProfileIsolationIT` smoke test?**
   - CONTEXT.md D-10 explicitly chooses NOT to add a permanent CI guard for default-build isolation.
   - **Recommendation:** Skip — match CONTEXT.md D-10. Phase 81 (SpotBugs gate) and Phase 84 (Renovate) will introduce CI activity that hits the existing structural integrity of pom.xml from different angles. If isolation regression ever becomes a recurring finding, revisit in a future phase.

5. **Are there any sub-recipes of `CommonStaticAnalysis` that the planner should pre-emptively exclude based on documented Lombok+JPA interaction, BEFORE the dryRun?**
   - PITFALLS.md does not exhaustively enumerate them. Pitfall 80-B identifies four high-risk candidates: `FinalizePrivateFields`, `FinalClass`, `ExplicitInitialization`, `StaticMethodNotFinal`.
   - CONTEXT.md D-06 says "no pre-emptive package-level exclusions" and D-08 chooses post-hoc handling.
   - **Recommendation:** **Follow CONTEXT.md D-06/D-08** — do NOT pre-emptively trim `CommonStaticAnalysis`. The dryRun review IS the gate. Pre-emptive trimming widens the bikeshed surface and contradicts the locked decisions. The four high-risk sub-recipes are documented in this RESEARCH.md as known watch-items for the dryRun review step.

## Sources

### Primary (HIGH confidence)

- [OpenRewrite Maven plugin reference](https://docs.openrewrite.org/reference/rewrite-maven-plugin) — `activeRecipes` is whitelist-only; `<exclusions>` is for file-path glob exclusion, not recipe-level exclusion
- [OpenRewrite YAML format reference](https://docs.openrewrite.org/reference/yaml-format-reference) — declarative recipe schema, `recipeList`
- [OpenRewrite CommonStaticAnalysis recipe inventory](https://docs.openrewrite.org/recipes/staticanalysis/commonstaticanalysis) — full list of ~70 sub-recipes
- [OpenRewrite issue #1714: Add a way to exclude some recipes from a compound recipe](https://github.com/openrewrite/rewrite/issues/1714) — CLOSED WONTFIX; maintainer recommends inlining the desired sub-recipes minus the offender
- [Maven Central — rewrite-maven-plugin metadata](https://repo1.maven.org/maven2/org/openrewrite/maven/rewrite-maven-plugin/maven-metadata.xml) — `<latest>6.39.0</latest>`, `lastUpdated=20260507012722` (queried 2026-05-16)
- [Maven Central — rewrite-spring 6.30.4 POM](https://repo1.maven.org/maven2/org/openrewrite/recipe/rewrite-spring/6.30.4/rewrite-spring-6.30.4.pom) — declares `rewrite-static-analysis:2.34.1` + `rewrite-migrate-java:3.34.1` as direct dependencies (transitive availability for the plugin classpath)
- Project files (read-only): `pom.xml` (lines 1–424), `CLAUDE.md`, `README.md`, `.planning/STATE.md`, `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/phases/80-openrewrite-integration/80-CONTEXT.md`, `.planning/research/SUMMARY.md`, `.planning/research/STACK.md`, `.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md`, `.planning/research/FEATURES.md`, `.planning/codebase/STACK.md`, `.planning/codebase/CONVENTIONS.md`, `.planning/codebase/TESTING.md`

### Secondary (MEDIUM confidence)

- [OpenRewrite latest versions of every module](https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module) — used to corroborate Maven Central queries

### Tertiary (LOW confidence — flagged for verification at planning time)

- ARCHITECTURE.md's "6.40.0" citation for `rewrite-maven-plugin` — CONTRADICTED by Maven Central live query (latest is 6.39.0). Planner should pin **6.39.0** unless re-verification at planning time shows otherwise.

## Metadata

**Confidence breakdown:**
- Standard stack (plugin coordinates, dep versions): **HIGH** — live-verified against Maven Central 2026-05-16
- Architecture (profile structure, file placement): **HIGH** — existing pom.xml structure inspected line-by-line; `e2e` profile is the proven template
- Pitfalls (Lombok / `UpgradeSpringBoot_4_0` / lifecycle binding): **HIGH** — cross-verified between milestone PITFALLS.md and OpenRewrite issue #1714 maintainer guidance
- YAML schema and recipe exclusion semantics: **HIGH** — directly verified against the openrewrite/rewrite issue tracker. **This is the single most-corrected finding versus CONTEXT.md** (D-05's "the YAML schema supports recipe-level exclusion inside a composite recipe" is incorrect — see §Recipe Selection Detail).
- README/CLAUDE.md current state: **HIGH** — files read in this session
- Discretion on no-op dryRun outcome: **MEDIUM** — depends on whether the post-v1.10 codebase actually produces a non-empty diff; resolves at phase execution time (acknowledged in CONTEXT.md "Claude's Discretion")

**Research date:** 2026-05-16
**Valid until:** 2026-06-15 (30 days — stable area). Re-verify `rewrite-maven-plugin` latest version against Maven Central if Phase 80 planning slips beyond that.

## Assumptions Log

No claims in this RESEARCH.md are tagged `[ASSUMED]` — every load-bearing finding is either `[VERIFIED]` against Maven Central / OpenRewrite docs / OpenRewrite issue tracker, or `[CITED]` against the project's own CONTEXT.md / CLAUDE.md / pom.xml. The single notable correction is the recipe-exclusion mechanism (CONTEXT.md D-05 wording is incorrect — see §Recipe Selection Detail), and that correction is itself `[VERIFIED: openrewrite/rewrite#1714]`. No user confirmation needed before planning.

---

## RESEARCH COMPLETE

Phase 80 (OpenRewrite Integration) — ready to plan. Critical implementation-shape finding for the planner: OpenRewrite has **no built-in sub-recipe exclusion mechanism** in YAML, plugin XML, or CLI (`activeRecipes` is whitelist-only; openrewrite/rewrite#1714 is CLOSED WONTFIX). The CONTEXT.md D-05 "exclude `UpgradeSpringBoot_4_0`" works because it is in a different recipe pack from `CommonStaticAnalysis` — exclusion is documentary/self-enforcing rather than a technical filter. README has no `## Development` section today (D-11 requires creating it). Plugin pin: **6.39.0** (verified live, ARCHITECTURE.md's "6.40.0" does not exist on Maven Central).
