# Phase 80: OpenRewrite Integration — Pattern Map

**Mapped:** 2026-05-16
**Files analyzed:** 4 (1 created net-new, 3 edited)
**Analogs found:** 3 / 4 (one file is net-new with no in-repo analog)

---

## File Classification

| File | New / Modified | Role | Data Flow | Closest Analog | Match Quality |
|------|----------------|------|-----------|----------------|---------------|
| `pom.xml` | modified | build config (Maven profile, plugin wiring) | invocation-on-demand (developer `-P` flag) | `pom.xml` lines 388–422 (`<profile id="e2e">`) | exact — same project file, same `<profiles>` sibling block |
| `rewrite.yml` | NEW (net-new) | declarative composite-recipe config (OpenRewrite YAML schema) | read by `rewrite-maven-plugin` at goal-execution time | none — no Maven/build YAML config exists at repo root | no analog (net-new format) |
| `README.md` | modified | user-facing documentation (developer onboarding) | rendered by GitHub / read by humans | `README.md` lines 77–113 (`## Playwright Setup` section) | role-match (same shape: prose paragraph → fenced bash block → trailing note) |
| `CLAUDE.md` | modified | agent instructions (commands index) | read by Claude Code at every session start | `CLAUDE.md` lines 30–63 (`## Commands` block) | exact — same project file, append-only edit |

---

## Pattern Assignments

### 1. `pom.xml` — append new `<profile id="rewrite">` after the existing `<profile id="e2e">`

**Role:** Maven profile that scopes the `rewrite-maven-plugin` to opt-in `-Prewrite` invocations so it cannot fire during the default `./mvnw verify` lifecycle.

**Data flow:** Activated only when a developer passes `-Prewrite` on the Maven CLI; goals `rewrite:dryRun` / `rewrite:run` write to `target/site/rewrite/rewrite.patch` (dryRun) or in place to `src/main/java/**` (run).

**Analog:** `pom.xml` lines 388–422 — the existing `<profile id="e2e">` block. It is the **only** profile in the project today and therefore the canonical structural template for any new profile.

**Match quality:** exact. Same project file; the new profile slots in as a sibling inside the same `<profiles>` block, after the `e2e` `</profile>` closing tag and before `</profiles>` on line 422.

**Code excerpt to mirror — `pom.xml:388–422` verbatim:**
```xml
	<profiles>
		<profile>
			<id>e2e</id>
			<build>
				<plugins>
					<plugin>
						<groupId>org.apache.maven.plugins</groupId>
						<artifactId>maven-failsafe-plugin</artifactId>
						<executions>
							<execution>
								<id>e2e-it</id>
								<goals>
									<goal>integration-test</goal>
									<goal>verify</goal>
								</goals>
								<configuration>
									<!-- JEP 498 escape: silence Lombok #3959 Permit/Unsafe warnings until upstream fix lands -->
									<argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar</argLine>
									<!-- Tag-based test routing (see TESTING.md "Test Categorization"):
									     <groups>e2e</groups> selects only @Tag("e2e") tests; the broad <includes>
									     pattern is required because Failsafe's default discovery (**/*IT.java)
									     does not match the org.ctc.e2e.*E2ETest.java naming convention.
									     Single fork (default) — Playwright requires a single Spring context per port. -->
									<includes>
										<include>**/e2e/**/*Test.java</include>
									</includes>
									<groups>e2e</groups>
								</configuration>
							</execution>
						</executions>
					</plugin>
				</plugins>
			</build>
		</profile>
	</profiles>
```

**Structural properties to copy from this analog (these are the load-bearing patterns):**
- The profile uses **`<id>` only** with **no `<activation>` element** → strictly opt-in via `-P<id>`. The new `rewrite` profile MUST also omit `<activation>`.
- Indentation is **literal tabs** (`\t`), one tab per nesting level inside `<profiles>`. The new profile MUST match: `\t\t<profile>` (two tabs at profile level, three tabs at `<id>` / `<build>`, etc.). Spaces or mixed indentation would diverge from the surrounding file.
- The profile contains a single `<build><plugins><plugin>…</plugin></plugins></build>` block. The new profile follows the same shape.
- HTML/XML comments use `<!-- … -->` and live inside `<configuration>` to explain non-obvious choices (the `e2e` profile uses them for the JEP 498 escape and the tag-routing rationale). The new profile should include a comment explaining why `rewrite-static-analysis` is NOT declared explicitly (transitive via `rewrite-spring`).

**Secondary analog (precedent for "plugin available but lifecycle-inert"):** `pom.xml:299–302` — the `versions-maven-plugin` declaration has **no `<executions>` block**, proving the project already accepts the "plugin on classpath, never auto-runs" pattern:
```xml
				<plugin>
					<groupId>org.codehaus.mojo</groupId>
					<artifactId>versions-maven-plugin</artifactId>
				</plugin>
```
The new `rewrite-maven-plugin` declaration takes this further (no `<executions>` AND scoped inside a profile).

**Differences to apply (deltas the executor must make versus the `e2e` analog):**
1. **`<id>` changes from `e2e` to `rewrite`.**
2. **Different plugin coordinates:** declare `org.openrewrite.maven:rewrite-maven-plugin:6.39.0` (NOT `maven-failsafe-plugin`). Version 6.39.0 is the verified-live latest stable on Maven Central as of 2026-05-07 (see RESEARCH.md §Maven Profile Structure; ARCHITECTURE.md's "6.40.0" does not exist on Maven Central).
3. **No `<executions>` block at all** — the `e2e` profile adds an EXECUTION to an already-declared plugin; the `rewrite` profile adds the ENTIRE plugin with zero executions. This is a stricter form of isolation and is required by D-01 / REWR-03.
4. **`<configuration>` contains `<activeRecipes>`, `<configLocation>`, `<exportDatatables>false</exportDatatables>`** — not failsafe `<argLine>` / `<includes>` / `<groups>`.
5. **Add a `<dependencies>` block inside `<plugin>`** declaring `rewrite-spring:6.30.4` and `rewrite-migrate-java:3.34.1` (per D-03). The `e2e` profile has no plugin-level `<dependencies>` block, so this shape is new — but it is a standard Maven plugin construct and uses the same `<groupId>/<artifactId>/<version>` triple as ordinary dependency declarations.
6. **Inline XML comment** explaining that `rewrite-static-analysis:2.34.1` (home of `CommonStaticAnalysis`) is pulled transitively via `rewrite-spring` and therefore not declared explicitly — verified against `rewrite-spring-6.30.4.pom` on Maven Central (see RESEARCH.md §Maven Profile Structure).
7. **Insertion point:** directly after the existing `</profile>` closing tag at line 421, before `</profiles>` at line 422. Do NOT touch any other line of pom.xml in the plugin-wiring commit.

**Reference shape (from RESEARCH.md §Maven Profile Structure — the planner can use this verbatim as the executor's target):**
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
							<!-- rewrite-static-analysis:2.34.1 (home of CommonStaticAnalysis) is
							     pulled in transitively via rewrite-spring:6.30.4 — no explicit
							     declaration required. Verified against rewrite-spring-6.30.4.pom
							     on Maven Central. -->
						</dependencies>
					</plugin>
				</plugins>
			</build>
		</profile>
```

---

### 2. `rewrite.yml` — NEW file at repo root

**Role:** Declarative OpenRewrite YAML config defining the composite recipe `org.ctc.RewriteCleanup`. The composite activates only `org.openrewrite.staticanalysis.CommonStaticAnalysis` and uses YAML comments as a documentary tripwire against future activation of `UpgradeSpringBoot_4_0`.

**Data flow:** Loaded by `rewrite-maven-plugin` when `<configLocation>${project.basedir}/rewrite.yml</configLocation>` is resolved. Read-only during Maven goal execution; never read at application runtime.

**Analog:** **NONE — net-new file format.** The repo has no other Maven-tool YAML config at the root. The conceptually closest neighbours at the repo root are:
- `pom.xml` (XML, not YAML) — the build configuration.
- `compose.yaml` and `docker-compose.prod.yml` (YAML, but Docker Compose schema — semantically unrelated to OpenRewrite).
- `mvnw` / `mvnw.cmd` (shell wrappers).
- `lombok.config` — **does NOT exist yet** (created by Phase 81, NOT Phase 80).

There is no existing pattern in this repo for an OpenRewrite YAML config. The planner must instruct the executor to follow the **OpenRewrite YAML schema** documented at `https://docs.openrewrite.org/reference/yaml-format-reference` rather than mirror an internal analog.

**Match quality:** no analog. Mark this file explicitly in the "No Analog Found" table below.

**Reference shape (from RESEARCH.md §Recipe Selection Detail — the planner can use this verbatim as the executor's target):**
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

**Differences to apply (deltas — i.e., what is load-bearing for the executor to get right):**
1. **Mandatory header:** `---` triple-dash document start, then `type: specs.openrewrite.org/v1beta/recipe` (the v1beta schema URI; without it the plugin refuses to load the file).
2. **`name: org.ctc.RewriteCleanup`** must match exactly the `<recipe>` element value inside `pom.xml` `<activeRecipes>` (case-sensitive). A drift between these two strings means `rewrite:run` fails with "recipe not found".
3. **`recipeList:` contains exactly one item** — `org.openrewrite.staticanalysis.CommonStaticAnalysis`. Do NOT add `UpgradeToJava25`, `UpgradeSpringBoot_4_0`, or any other recipe in this phase (D-04).
4. **The documentary exclusion is a YAML comment block**, not a YAML field. OpenRewrite has no `excludedRecipes` field; D-05's "the YAML schema supports recipe-level exclusion inside a composite" is technically incorrect per RESEARCH.md §Recipe Selection Detail and openrewrite/rewrite#1714 (wontfix). The exclusion is self-enforcing: simply never adding the recipe to `recipeList` is what excludes it. The comment block is the documentation/tripwire.
5. **File location:** repo root (sibling to `pom.xml`, `mvnw`, `README.md`). NOT `src/main/resources/rewrite.yml` and NOT `.openrewrite/rewrite.yml`.
6. **Indentation:** two-space YAML indent (the standard for `compose.yaml` in this repo).

---

### 3. `README.md` — insert new `## Development` section with OpenRewrite subsection

**Role:** Developer-facing onboarding documentation describing the OpenRewrite dryRun → run workflow and the deliberate decision to keep the plugin out of CI.

**Data flow:** Rendered by GitHub on the repo landing page; read by humans (project newcomers, future maintainers).

**Analog:** `README.md` lines 77–113 — the existing `## Playwright Setup (Team Cards + E2E Tests)` section. This is the closest "developer-setup tooling" section in the file: same target audience (developers, not end users), same general shape (one-sentence rationale → fenced bash block → trailing prose notes).

**Match quality:** role-match. Different tool (Playwright vs OpenRewrite) but same documentation pattern.

**Current README section order (verified 2026-05-16):**
1. `# CTC Manager` (title)
2. `## Tech Stack`
3. `## Features`
4. `## Backup & Restore`
5. `## Quick Start`
6. `## Playwright Setup (Team Cards + E2E Tests)`
7. `## Documentation`

**There is no `## Development` section today.** D-11's phrasing ("under the existing `## Development` section") must be read as "create the section" — the planner must instruct the executor to introduce it.

**Insertion point:** between section 6 (`## Playwright Setup`) and section 7 (`## Documentation`). Rationale: both Playwright Setup and Development are developer-tooling content, so they cluster naturally; `## Documentation` (which points readers off to the wiki) stays the last top-level section.

**Code excerpt to mirror — `README.md:77–113` (the Playwright Setup section, verbatim, as the tone/shape template):**
```markdown
## Playwright Setup (Team Cards + E2E Tests)

Playwright needs a Chromium browser installed locally for team card generation and E2E tests.

### Install Chromium

```bash
# All platforms (macOS, Linux, Windows) — via Maven:
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
```

This downloads the Chromium binary to the platform-specific cache directory:

| Platform | Cache Location |
|----------|---------------|
| macOS | `~/Library/Caches/ms-playwright/` |
| Linux | `~/.cache/ms-playwright/` |
| Windows | `%LOCALAPPDATA%\ms-playwright\` |

### Linux: Additional OS Dependencies

On Linux (Debian/Ubuntu), Chromium requires native libraries:

```bash
# Install dependencies (via Playwright)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install-deps chromium"

# Or manually (Debian/Ubuntu)
sudo apt-get install -y libnss3 libatk-bridge2.0-0 libdrm2 libxkbcommon0 \
    libgbm1 libpango-1.0-0 libcairo2 libasound2t64 libxshmfence1
```

macOS and Windows include these dependencies natively — no extra setup needed.

### Docker

The Dockerfile handles Chromium installation automatically during the build.
```

**Patterns to copy from this analog:**
- **One H2 (`##`) section title with a parenthetical clarification** when the audience-fit is non-obvious (e.g., `## Playwright Setup (Team Cards + E2E Tests)`).
- **One-sentence prose lede** under the H2 explaining what the tool is and why it matters, before any commands.
- **H3 subsections (`###`)** for distinct sub-workflows; each H3 starts with a short prose paragraph, then a fenced ` ```bash ` block of copy-pastable commands.
- **Inline code with backticks** for filenames, env var names, paths, and CLI invocations.
- **Plain-prose closing sentence** at the end of each subsection that captures a "this is the bottom line" observation (e.g., "The Dockerfile handles Chromium installation automatically during the build.").
- **No emoji, no decorative dividers**, no front-matter — the existing README is dense, English-language, and command-first.

**Differences to apply (deltas the planner instructs the executor to make):**
1. **New top-level section:** `## Development` (no parenthetical — the section is the umbrella for multiple developer subsections, and the H3 within it carries the specificity).
2. **One H3 subsection in this phase only:** `### OpenRewrite (developer-invoked refactoring)`. Future phases may add sibling H3s (e.g., `### SpotBugs` in Phase 81) under the same `## Development` umbrella.
3. **Lede paragraph** (~50–80 words) covers: (a) what OpenRewrite is + link to docs; (b) that this project wires it into a `rewrite` profile, NOT the default `verify` lifecycle; (c) the deliberate choice to keep it developer-invoked and never in CI.
4. **Pointer paragraph** referencing `rewrite.yml` for the active recipe set.
5. **Workflow code block** showing the four-step sequence with `# 1.` … `# 4.` comments (dryRun → inspect patch → run → git diff). Mirror the Playwright section's "commented bash block" pattern.
6. **Closing prose sentence** stating that without `-Prewrite` the plugin is not on the build, so `./mvnw verify` adds zero overhead. This is the OpenRewrite section's equivalent of the Playwright section's "The Dockerfile handles Chromium installation automatically" closer.
7. **Do not touch** any line of the existing sections 1–6 or section 7. The edit is pure insertion between line 113 (last line of Playwright Setup) and line 115 (`## Documentation`).

**Reference shape (planner may hand this to the executor as a starting point; small tone tweaks acceptable per CONTEXT.md "Claude's Discretion"):**
````markdown
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
````

---

### 4. `CLAUDE.md` — append two `./mvnw -Prewrite …` commands to the `## Commands` block

**Role:** Agent-instruction index of every developer CLI invocation in the project. Read by Claude Code (and any other agent) on every session start; serves as the authoritative command surface area.

**Data flow:** Static read by the agent runtime on startup; never written at runtime.

**Analog:** `CLAUDE.md` lines 30–63 — the existing `## Commands` block. Same project file; the edit is append-only inside the same fenced bash block.

**Match quality:** exact. Same file, same block.

**Code excerpt to mirror — `CLAUDE.md:30–63` (the `## Commands` block, verbatim):**
````markdown
## Commands

```bash
# Start Dev Mode
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Dev Mode with GT7 demo data (cars, tracks, images)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo

# Run tests (Unit + Integration + JaCoCo Coverage)
./mvnw verify

# Run tests including Playwright E2E
./mvnw verify -Pe2e

# Open Coverage Report
open target/site/jacoco/index.html

# Local with MariaDB
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# Install Playwright Chromium (for Team Card generation + E2E tests)
./mvnw exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"

# Docker: Local environment (App + MariaDB)
docker compose up --build -d
docker compose down

# Docker: Build image only
docker compose build

# Docker: Production (external DB, configure .env)
docker compose -f docker-compose.prod.yml up -d
```
````

**Patterns to copy from this analog (these are the load-bearing per-command conventions):**
- **One single-line `#` comment per command**, English, concise, ≤ 80 characters. Comments use sentence-case with a colon when introducing a category (`Docker: Local environment …`) or plain prose when describing the action (`Start Dev Mode`).
- **One blank line between adjacent command groups** (the comment + its command lines form one group; Docker has multi-line groups when commands are paired like `up && down`).
- **No prose paragraphs inside the fenced block** — rationale lives in surrounding markdown sections (e.g., `## Architectural Principles`), never inside `## Commands`.
- **No `$` prompt prefix** on commands.

**Differences to apply (deltas the executor must make versus the analog):**
1. **Insertion point — D-12 lock:** the two new commands are appended after the `./mvnw verify -Pe2e` line (currently `CLAUDE.md:43`), and before the `# Open Coverage Report` comment (currently `CLAUDE.md:45`). CONTEXT.md `<code_context>` "Established Patterns" allows either "at end of block" or "after `verify -Pe2e`"; D-12 explicitly says **after `./mvnw verify -Pe2e`**. Honour the lock.
2. **Exactly two new commands, each with its own one-line `#` comment.** No additional prose, no rationale text inside the fenced block. Rationale lives in README.md `## Development` (file 3) and CONTEXT.md.
3. **Do NOT reorder existing commands.** `<code_context>` "Integration Points" specifies append-only. Even within the insertion span, the two new lines must be inserted as a contiguous pair without splitting any existing comment from its command.
4. **Match the comment style:** English, sentence-case, parenthetical clarification where the verb-only form is ambiguous. Example: `# OpenRewrite: preview recipe-driven refactoring (no file changes)` mirrors the `# Docker: Local environment (App + MariaDB)` pattern (tool-name prefix + colon + short description + parenthetical).

**Reference shape (planner can hand this to the executor as the literal addition, slotted directly between line 43 and line 45 of the current CLAUDE.md):**
````markdown
# Run tests including Playwright E2E
./mvnw verify -Pe2e

# OpenRewrite: preview recipe-driven refactoring (no file changes)
./mvnw -Prewrite rewrite:dryRun

# OpenRewrite: apply recipes to source files in place
./mvnw -Prewrite rewrite:run

# Open Coverage Report
open target/site/jacoco/index.html
````
(Only the four lines of new content — comment + command, twice — plus the surrounding blank lines are net-new; the `verify -Pe2e` and `Open Coverage Report` lines are shown for positional context only and must not be re-edited.)

---

## Shared Patterns

### Tabs-not-spaces in `pom.xml`
**Source:** entire `pom.xml` (verified at lines 388–422 and 295–302).
**Apply to:** the new `<profile id="rewrite">` block.
**Excerpt evidence:**
```xml
	<profiles>          ← 1 tab
		<profile>       ← 2 tabs
			<id>e2e</id>  ← 3 tabs
```
Any space-indented insertion will diverge visually from the surrounding file and may trip code-review.

### Conventional Commits with scope where useful
**Source:** `CLAUDE.md` `## Git Workflow` section.
**Apply to:** every commit in this phase.
- Plugin-wiring + `rewrite.yml` + docs commit: `chore(build): wire OpenRewrite plugin into rewrite profile` (or `feat(build):` — research recommends `chore(build):` per its "no application code change" framing).
- Cleanup commit (only if dryRun produces non-empty patch): `refactor: apply OpenRewrite CommonStaticAnalysis one-shot cleanup` (locked by D-07).

### Pre-PR verification gate
**Source:** `CLAUDE.md` `## Git Workflow` → `## Before PR`.
**Apply to:** every commit in this phase before push.
- `./mvnw verify` must be green locally before pushing.
- Coverage ≥ 82 % BUNDLE LINE ratio (JaCoCo gate, `pom.xml:347`).
- Self-review via `superpowers:code-reviewer` before opening the PR.

### Branch off `origin/master`, not local `master`
**Source:** `CLAUDE.md` `## Git Workflow` → "Always fetch before branching".
**Apply to:** the feature branch for this phase (recommended: `feature/openrewrite-integration`).
- `git fetch origin && git checkout -b feature/openrewrite-integration origin/master`.
- The milestone tracking branch `gsd/v1.11-tooling-and-cleanup` is a pointer, not the execution branch.

---

## No Analog Found

| File | Role | Data Flow | Reason no analog exists |
|------|------|-----------|-------------------------|
| `rewrite.yml` | declarative composite-recipe config (OpenRewrite YAML v1beta schema) | read by `rewrite-maven-plugin` at goal execution | No Maven-tool YAML config exists at the repo root. `lombok.config` (closest spiritual neighbour) does NOT exist yet — Phase 81 creates it. The planner must direct the executor to the OpenRewrite YAML schema docs rather than mirror an in-repo pattern. |

---

## Metadata

**Analog search scope:** `pom.xml`, `README.md`, `CLAUDE.md`, repo root for sibling config files.
**Files scanned:** 4 (CONTEXT.md, RESEARCH.md, pom.xml, README.md, CLAUDE.md — plus targeted reads at pom.xml lines 295–302 and 380–424).
**Pattern extraction date:** 2026-05-16
**Phase directory:** `.planning/phases/80-openrewrite-integration/`

---

## PATTERN MAPPING COMPLETE

**Phase:** 80 — OpenRewrite Integration
**Files classified:** 4 (1 net-new, 3 modified)
**Analogs found:** 3 / 4

### Coverage
- Files with exact analog: 2 (`pom.xml` → `e2e` profile; `CLAUDE.md` → `## Commands` block)
- Files with role-match analog: 1 (`README.md` → `## Playwright Setup` section)
- Files with no analog: 1 (`rewrite.yml` — net-new YAML at repo root; follow OpenRewrite v1beta schema)

### Key Patterns Identified
- Maven profiles in this project are `<id>`-only, no `<activation>` → strictly opt-in via `-P<id>`; new `rewrite` profile mirrors this exactly.
- Developer-invoked plugins live without `<executions>` (precedent: `versions-maven-plugin` at `pom.xml:299–302`); the new plugin goes further by also scoping to a profile.
- README developer-tooling sections follow a fixed shape: H2 title with parenthetical → 1-sentence lede → H3 subsections, each with a prose paragraph + fenced bash block + trailing prose sentence (template: `## Playwright Setup`).
- `CLAUDE.md` `## Commands` block uses one-line `#` comments + verb-first action; new commands appended after `./mvnw verify -Pe2e` per D-12, never reorder existing entries.

### File Created
`.planning/phases/80-openrewrite-integration/80-PATTERNS.md`

### Ready for Planning
Pattern mapping complete. Planner can now reference analog patterns and the four reference shapes (pom.xml profile, rewrite.yml, README section, CLAUDE.md additions) directly in PLAN.md actions.
