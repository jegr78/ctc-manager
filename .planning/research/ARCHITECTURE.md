# Architecture Research — v1.11 Tooling Infrastructure & Tech-Debt Sweep

**Domain:** Internal admin tool for sports league management (Spring Boot 4 / Maven / GitHub Actions CI)
**Researched:** 2026-05-16
**Confidence:** HIGH (all four tooling integrations verified against official docs and starter workflows; test-wallclock patterns verified against Zalando engineering blog and Spring documentation)

---

## Scope of This Research

Four tooling streams wire into the existing single-module Maven project and its GitHub Actions CI:

1. **OpenRewrite** — Maven plugin invocation, recipe activation, dry-run + apply modes
2. **Clean Code (Checkstyle / PMD / SpotBugs)** — Maven verify-phase enforcement, suppression files, rule-set sourcing
3. **Renovate** — dependency update bot, config file location, grouping strategy, scheduling
4. **SAST (CodeQL / Semgrep)** — GitHub Actions workflow structure, scan triggers, Java-specific configuration

Plus an architectural section on test-wallclock reduction (Phase 79 carried-over debt).

---

## 1. OpenRewrite Integration

### Plugin Coordinates

```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>6.40.0</version>
  ...
</plugin>
```

Current stable version: **6.40.0** (published 2026-05-07). [Confidence: HIGH — verified against Maven Central and official plugin info page.]

### Available Goals

| Goal | What It Does | Forks Lifecycle? |
|------|-------------|-----------------|
| `rewrite:dryRun` | Prints diff to console; generates patch report in `target/`; no file changes | yes |
| `rewrite:dryRunNoFork` | Same as dryRun but does NOT fork the Maven lifecycle | no |
| `rewrite:run` | Applies changes to source files in place | yes |
| `rewrite:runNoFork` | Same as run but does NOT fork the Maven lifecycle | no |
| `rewrite:discover` | Lists available recipes on the classpath | no |

### Configuration Options

| Option | Purpose |
|--------|---------|
| `activeRecipes` | Recipe names to execute (e.g., `org.openrewrite.java.cleanup.CommonStaticAnalysis`) |
| `activeStyles` | Named code-style configurations to apply |
| `configLocation` | Path to `rewrite.yml` / `rewrite.yaml` config file (default: `rewrite.yml` in project root) |
| `failOnDryRunResults` | When `true`, fails the build if dryRun detects any changes — useful as a CI gate |
| `exclusions` | Glob patterns to skip (e.g., `src/main/resources/**` to skip non-Java files) |

### Integration Pattern for CTC

**Recommended approach:** Manual invocation only, NOT bound to the standard lifecycle. Binding `rewrite:dryRun` to `verify` with `failOnDryRunResults=true` would fail every `./mvnw verify` until all violations are fixed — that's hostile during active development. Instead:

- Keep the plugin in `<pluginManagement>` (not `<build><plugins>`) so it's available without auto-executing.
- Activate via a dedicated Maven profile (`-Prewrite`) or explicit goal invocation.
- CI calls `mvn rewrite:dryRun -DfailOnDryRunResults=true` in its own job (not bundled into the existing `build-and-test` job).

```xml
<!-- pom.xml: place in <pluginManagement> NOT in <build><plugins> -->
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>6.40.0</version>
  <configuration>
    <activeRecipes>
      <recipe>org.openrewrite.java.cleanup.CommonStaticAnalysis</recipe>
      <recipe>org.openrewrite.java.format.AutoFormat</recipe>
    </activeRecipes>
    <configLocation>${project.basedir}/rewrite.yml</configLocation>
    <exclusions>
      <exclusion>src/main/resources/**</exclusion>
    </exclusions>
  </configuration>
  <dependencies>
    <!-- Recipe libraries go here when non-core recipes are used -->
    <dependency>
      <groupId>org.openrewrite.recipe</groupId>
      <artifactId>rewrite-static-analysis</artifactId>
      <version>RELEASE</version>
    </dependency>
  </dependencies>
</plugin>
```

**Profile-gated dry-run CI gate** (optional, for drift detection):

```xml
<profile>
  <id>rewrite-check</id>
  <build>
    <plugins>
      <plugin>
        <groupId>org.openrewrite.maven</groupId>
        <artifactId>rewrite-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>rewrite-dry-run-gate</id>
            <phase>verify</phase>
            <goals><goal>dryRunNoFork</goal></goals>
            <configuration>
              <failOnDryRunResults>true</failOnDryRunResults>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</profile>
```

Invoked as `mvn verify -Prewrite-check` in CI.

### New Files Added

| File | Purpose |
|------|---------|
| `rewrite.yml` | Recipe list, style configuration, project-specific overrides |

The `rewrite.yml` lives at the project root (alongside `pom.xml`). No `.openrewrite/` sub-directory is needed for a single-module project.

### Data/Config Flow

```
developer / CI
    ↓ mvn rewrite:dryRun (manual) OR mvn verify -Prewrite-check (CI gate)
rewrite-maven-plugin
    ↓ reads rewrite.yml (activeRecipes, activeStyles)
    ↓ forks Maven lifecycle to process-test-classes (source loaded)
    ↓ applies recipe visitors to AST
    ↓ dryRun: writes diffs to target/rewrite/rewrite.patch
    ↓ run: overwrites source files
```

### Build Lifecycle Attachment

`rewrite:dryRun` and `rewrite:run` fork the lifecycle to `process-test-classes` before executing — they do NOT attach to the standard `verify` phase unless explicitly bound via `<executions>`. The `NoFork` variants avoid the secondary fork and are safer for profile-gated executions.

---

## 2. Clean Code (Checkstyle / PMD / SpotBugs) Integration

### Plugin Coordinates

| Tool | GroupId | ArtifactId | Current Version |
|------|---------|-----------|----------------|
| Checkstyle | `org.apache.maven.plugins` | `maven-checkstyle-plugin` | **3.6.0** |
| PMD | `org.apache.maven.plugins` | `maven-pmd-plugin` | **3.28.0** (2025-10-07) |
| SpotBugs | `com.github.spotbugs` | `spotbugs-maven-plugin` | **4.9.8.3** |

[Confidence: HIGH — all three verified against official Maven plugin sites.]

### Lifecycle Binding Recommendation

| Tool | Recommended Phase | Why |
|------|------------------|-----|
| Checkstyle | `verify` | Runs after compile — javac catches syntax errors first; avoids duplicate failure messages |
| PMD | `verify` | Same rationale — bytecode not needed, but `verify` gives compiler priority |
| SpotBugs | `verify` | **Requires compiled bytecode** — cannot run earlier than `compile`; `verify` is canonical |

All three bound to `verify` gives a consistent experience: `./mvnw verify` runs them all after tests pass. The existing JaCoCo `check` goal is also bound to `verify`, so the full quality gate runs together.

### pom.xml Integration Pattern

```xml
<!-- In <build><plugins> (not pluginManagement) — executes on ./mvnw verify -->

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <version>3.6.0</version>
  <executions>
    <execution>
      <id>checkstyle-gate</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <configLocation>config/checkstyle.xml</configLocation>
        <suppressionsLocation>config/checkstyle-suppressions.xml</suppressionsLocation>
        <failsOnError>true</failsOnError>
        <includeTestSourceDirectory>false</includeTestSourceDirectory>
      </configuration>
    </execution>
  </executions>
</plugin>

<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-pmd-plugin</artifactId>
  <version>3.28.0</version>
  <executions>
    <execution>
      <id>pmd-gate</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <rulesets>
          <ruleset>config/pmd.xml</ruleset>
        </rulesets>
        <failOnViolation>true</failOnViolation>
        <failurePriority>3</failurePriority>
        <!-- Only fail on HIGH + MEDIUM priority violations; LOW = warning only -->
        <includeTests>false</includeTests>
      </configuration>
    </execution>
  </executions>
</plugin>

<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>4.9.8.3</version>
  <executions>
    <execution>
      <id>spotbugs-gate</id>
      <phase>verify</phase>
      <goals><goal>check</goal></goals>
      <configuration>
        <effort>Max</effort>
        <threshold>Medium</threshold>
        <excludeFilterFile>config/spotbugs-exclude.xml</excludeFilterFile>
      </configuration>
    </execution>
  </executions>
</plugin>
```

### New Files Added

| File | Purpose |
|------|---------|
| `config/checkstyle.xml` | Checkstyle rule set (Google or custom, with CTC-appropriate relaxations) |
| `config/checkstyle-suppressions.xml` | File/line-level suppressions for generated code or irreducible exceptions |
| `config/pmd.xml` | PMD rule set referencing categories (`rulesets/java/bestpractices.xml`, etc.) |
| `config/spotbugs-exclude.xml` | SpotBugs exclusion filter — e.g., suppress `EI_EXPOSE_REP2` for `@Getter` Lombok collections |

A `config/` directory at project root keeps all rule files separate from `src/`. This mirrors the convention used by Apache, Spring Framework, and Google's own open-source Java projects.

### Interaction with Existing Surefire/Failsafe/JaCoCo

The three analysis tools bind to `verify` **after** Failsafe's `default-it` execution (also at `verify`) because Maven executes `<executions>` within the same phase in declaration order, and Failsafe is declared before the new plugins. Verify the declaration order in `pom.xml` when wiring:

```
verify phase execution order (declaration order within pom.xml):
1. jacoco:report           (existing)
2. jacoco:check            (existing — 82% gate)
3. failsafe:verify         (existing — IT verification)
4. checkstyle:check        (new)
5. pmd:check               (new)
6. spotbugs:check          (new)
```

If declaration order causes all three analysis goals to run before JaCoCo check, the build fails for a coverage reason but shows analysis output too — acceptable. The recommended order above runs analysis after coverage gating.

### Handling Existing Violations (Bootstrap Strategy)

The project has ~17k LOC with zero existing Checkstyle/PMD/SpotBugs configuration. A naïve `failOnViolation=true` on first activation will produce hundreds of violations and block `./mvnw verify`. Use this staged approach:

1. Run each tool in report-only mode first (`checkstyle:checkstyle`, `pmd:pmd`, `spotbugs:spotbugs`) to inventory violations.
2. Configure `failurePriority=1` (SpotBugs HIGH only) or `maxAllowedViolations=N` as a ratchet.
3. Suppress known-clean-but-flagged patterns via the suppression files.
4. Tighten the gate over subsequent milestones.

For v1.11, start with PMD `failurePriority=2` (HIGH violations only) and SpotBugs `threshold=High`. Checkstyle is stylistic — start with a liberal Google style config and suppress the most disruptive rules (import ordering, Javadoc requirements).

---

## 3. Renovate Integration

### Configuration File Location

Renovate searches for config files in this order (stops at first match):

1. `renovate.json` (project root) ← **recommended for this project**
2. `renovate.json5`
3. `.github/renovate.json`
4. `.github/renovate.json5`
5. `.renovaterc`, `.renovaterc.json`, `.renovaterc.json5`

**Recommendation: use `renovate.json` at the project root.** It is the most discoverable location, matches what GitHub's Dependency Graph UI expects, and is the canonical default that Renovate's Getting Started docs demonstrate.

[Confidence: HIGH — verified against official Renovate configuration-options docs.]

### How Renovate Runs

Renovate is not a Maven plugin. It runs as a GitHub App or self-hosted bot:

1. **GitHub App (recommended):** Install the Renovate GitHub App on the repository. Renovate's cloud infrastructure runs the bot on schedule and on PR events. No GitHub Actions runner cost, no CI time consumed.
2. **Self-hosted via GitHub Actions:** A dedicated `renovate.yml` workflow uses the `renovatebot/github-action` action. Runs on a cron schedule.

For CTC (single-person project), the GitHub App approach is simpler — no workflow file needed.

### Recommended `renovate.json` for CTC

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": ["config:recommended"],
  "enabledManagers": ["maven", "github-actions"],
  "schedule": ["before 6am on Monday"],
  "prConcurrentLimit": 5,
  "packageRules": [
    {
      "description": "Group Spring Boot starters together",
      "matchPackagePatterns": ["^org\\.springframework\\.boot:"],
      "groupName": "Spring Boot",
      "automerge": false
    },
    {
      "description": "Group Google API client libraries",
      "matchPackagePatterns": ["^com\\.google\\."],
      "groupName": "Google API clients"
    },
    {
      "description": "Group Testcontainers",
      "matchPackagePatterns": ["^org\\.testcontainers:"],
      "groupName": "Testcontainers"
    },
    {
      "description": "Automerge patch-only dependency updates after CI passes",
      "matchUpdateTypes": ["patch"],
      "automerge": true,
      "automergeType": "pr",
      "automergeSchedule": ["after 9am and before 5pm on weekdays"]
    },
    {
      "description": "Pin GitHub Actions to SHA",
      "matchManagers": ["github-actions"],
      "pinDigests": true
    }
  ],
  "vulnerabilityAlerts": {
    "enabled": true,
    "labels": ["security"]
  }
}
```

### New Files Added

| File | Purpose |
|------|---------|
| `renovate.json` | Renovate bot configuration at project root |

No workflow file is needed if using the GitHub App approach. If self-hosted:

| File | Purpose |
|------|---------|
| `.github/workflows/renovate.yml` | Cron-scheduled workflow running `renovatebot/github-action` |

### Grouping Strategy Rationale

CTC has three natural dependency groups:

1. **Spring Boot** — all `org.springframework.boot:*` should be upgraded together (auto-imported BOM coordinates).
2. **Google API clients** — `google-api-client`, `google-api-services-*`, `google-auth-library-*` share a release cadence.
3. **Testcontainers** — already managed via BOM; Renovate should update the BOM version as a group.

Everything else (Lombok, Jsoup, commons-text, Playwright, Guava) gets individual PRs.

### Data/Config Flow

```
Renovate bot (GitHub App or cron workflow)
    ↓ reads renovate.json from master branch
    ↓ scans pom.xml for dependency versions
    ↓ checks Maven Central / registry for newer versions
    ↓ creates PRs against master (one per group or one per dependency)
CI (existing build-and-test job)
    ↓ triggered by Renovate PR
    ↓ ./mvnw verify (unit + integration tests)
    ↓ if green: automerge (for patch updates) or manual review (for minor/major)
```

Renovate PRs go through the same CI gate as developer PRs — no special treatment needed.

---

## 4. SAST (CodeQL / Semgrep) Integration

### CodeQL

**Recommended approach: CodeQL GitHub Advanced Setup** — a standalone `codeql.yml` workflow file, NOT integrated into the existing `ci.yml`. This keeps the two workflows independently cancellable and the SAST analysis does not block the main build.

#### New File

| File | Purpose |
|------|---------|
| `.github/workflows/codeql.yml` | Dedicated CodeQL analysis workflow |

#### CodeQL Workflow Structure

```yaml
name: CodeQL Analysis

on:
  push:
    branches: [ master, main ]
  pull_request:
    branches: [ master, main ]
  schedule:
    - cron: '0 3 * * 1'   # Weekly Monday 03:00 UTC (scheduled full scan)

permissions:
  actions: read
  contents: read
  security-events: write  # required: writes results to Security tab

jobs:
  analyze:
    name: CodeQL Java Analysis
    runs-on: ubuntu-latest

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
        uses: github/codeql-action/init@v3
        with:
          languages: java-kotlin
          # Use default queries + security-extended for broader coverage
          queries: security-extended

      - name: Build with Maven (manual build — autobuild cannot handle Playwright CLI setup)
        run: ./mvnw compile -DskipTests --no-transfer-progress

      - name: Perform CodeQL Analysis
        uses: github/codeql-action/analyze@v3
        with:
          category: "/language:java-kotlin"
```

**Why manual build instead of autobuild:** The existing CI runs `./mvnw verify` with Playwright browser installation as a separate step. CodeQL's autobuild would attempt `mvn compile` without the Playwright CLI setup, which fails when Lombok annotation processing triggers classpath issues. Using `./mvnw compile -DskipTests` is safe and explicit.

**Why `security-extended` queries:** The default query set catches obvious CVEs; `security-extended` adds injection, path traversal, and SSRF patterns — all directly relevant to CTC's existing security hardening work (SSRF defense, path traversal defense in `FileStorageService`, ZIP-Slip defense in backup import).

**Permissions:** `security-events: write` is mandatory for CodeQL to post results to the Security tab. The existing `ci.yml` has `pull-requests: write` (for JaCoCo comment) — those permissions are separate and must be declared per-workflow.

### Semgrep (Alternative / Complement to CodeQL)

Semgrep can run alongside CodeQL or as a lighter alternative. It requires no repository setup — the OSS variant (`semgrep scan --config auto`) needs no token.

#### New File (if adding Semgrep)

| File | Purpose |
|------|---------|
| `.github/workflows/semgrep.yml` | Dedicated Semgrep scan workflow |
| `.semgrepignore` | Files/patterns to exclude from Semgrep scans |

#### Semgrep Workflow Structure

```yaml
name: Semgrep SAST

on:
  pull_request: {}
  push:
    branches: [ master ]
  schedule:
    - cron: '20 17 * * *'   # daily at 17:20 UTC

permissions:
  contents: read

jobs:
  semgrep:
    runs-on: ubuntu-latest
    if: github.actor != 'dependabot[bot]' && github.actor != 'renovate[bot]'
    container:
      image: semgrep/semgrep
    steps:
      - uses: actions/checkout@v6
      - name: Semgrep scan (OSS, no token required)
        run: semgrep scan --config auto --config p/java --config p/owasp-top-ten --error
```

**CodeQL vs Semgrep decision:** Use CodeQL as the primary SAST tool — it is free for public repositories, deeply integrated into GitHub Security tab, and the Java query set is battle-tested. Add Semgrep if CodeQL misses a specific rule category the team wants (e.g., OWASP Top 10 custom rules). For v1.11, start with CodeQL only.

### Data/Config Flow

```
push / PR / weekly schedule
    ↓
.github/workflows/codeql.yml
    ↓ github/codeql-action/init — sets up CodeQL database
    ↓ ./mvnw compile -DskipTests — builds bytecode for analysis
    ↓ github/codeql-action/analyze — runs queries against database
    ↓ results uploaded to GitHub Security tab (SARIF format)
    ↓ if PR: Code Scanning alerts appear inline on PR diff
    ↓ if push to master: alerts appear in Security → Code scanning
```

---

## 5. Build Order and CI Architecture

### Existing CI Job Structure

```
ci.yml
├── build-and-test        (./mvnw verify -Dspring.profiles.active=dev)
│   ├── Surefire unit tests   (forkCount=2)
│   ├── Failsafe ITs          (forkCount=1)
│   ├── JaCoCo report + check (82% gate)
│   └── Playwright E2E        (-Pe2e)
├── dockerfile-noble-pin-guard
└── docker-build              (needs: dockerfile-noble-pin-guard)
```

### Proposed CI Job Structure After v1.11

```
ci.yml (modified)
├── build-and-test           (unchanged — existing job)
│   ├── Surefire unit tests
│   ├── Failsafe ITs
│   ├── JaCoCo check (82% gate)
│   ├── checkstyle:check      (NEW — added to verify phase)
│   ├── pmd:check             (NEW — added to verify phase)
│   ├── spotbugs:check        (NEW — added to verify phase)
│   └── Playwright E2E
├── dockerfile-noble-pin-guard  (unchanged)
└── docker-build                (unchanged)

codeql.yml (NEW — separate workflow file)
└── analyze
    ├── Initialize CodeQL
    ├── ./mvnw compile -DskipTests
    └── Perform analysis → Security tab

renovate.json (NEW — bot config, no workflow needed for GitHub App approach)
```

### Suggested Phase Build Order (Across 4 Tooling Streams)

**Recommended sequence:** Clean Code → OpenRewrite → Renovate → SAST

| Order | Stream | Rationale |
|-------|--------|-----------|
| 1st | **Clean Code (Checkstyle/PMD/SpotBugs)** | Establishes the baseline quality gate. If OpenRewrite is run first, it may generate code that Checkstyle/PMD then flags — ordering enforcement BEFORE automated refactoring means OpenRewrite recipes can be configured to produce compliant output. |
| 2nd | **OpenRewrite** | With the enforcement gate in place, OpenRewrite recipes can be selected and tuned to not introduce new violations. The `rewrite:dryRun` output can be inspected against the Checkstyle/PMD rules already active. |
| 3rd | **Renovate** | Dependency updates come after the build is clean so incoming Renovate PRs hit a stable, passing CI. Renovate touching `pom.xml` will trigger all existing gates — clean code must be in place first so that gate is meaningful, not just noisy. |
| 4th | **SAST (CodeQL)** | CodeQL analysis is additive — it doesn't block `./mvnw verify` and runs in a separate workflow. It can be activated at any point, but placing it last means the codebase has already been improved by Clean Code + OpenRewrite, reducing false-positive noise in the initial SAST report. |

**Why NOT enforce-after-rewrite:** If OpenRewrite runs first without Checkstyle/PMD gates, developers cannot distinguish "violations from OpenRewrite output" from "pre-existing violations." Clean Code first establishes a zero-baseline.

### Where Each Tool Hooks Into the Build/Verify Lifecycle

```
Maven lifecycle phases:
validate  → exec-maven-plugin:template-fragment-call-guard  (existing)
compile   →
test      → Surefire (unit tests, forkCount=2, excludedGroups=integration,e2e,flaky)
package   →
verify    → Failsafe default-it (ITs, forkCount=1, groups=integration)
           → JaCoCo report
           → JaCoCo check (82% gate)
           → Failsafe verify (IT result verification)
           → [NEW] checkstyle:check
           → [NEW] pmd:check
           → [NEW] spotbugs:check

Separate invocations (not bound to verify):
mvn rewrite:dryRun         (manual or -Prewrite-check profile)
mvn rewrite:run            (manual: developer applies changes)

GitHub Actions separate workflow:
codeql.yml → mvn compile -DskipTests + codeql analyze
```

---

## 6. Test Wallclock Reduction — Architectural Options

### Current State

Phase 79 achieved 16.85% wallclock reduction (target was ≥ 30%). The current setup:

- Surefire: `forkCount=2` (two parallel JVM forks), `reuseForks=true`, plain unit tests
- Failsafe default-it: `forkCount=1C` (1 per CPU core), `@Tag("integration")` Spring-context tests
- 1652 Surefire unit tests + 231 Failsafe ITs + 36 Playwright E2E

The remaining 13+ percentage points require reducing Spring Application Context startup cost, which dominates wallclock time in the IT layer.

### Option A: Audit and Eliminate `@DirtiesContext` Usages

**What:** Find every `@DirtiesContext` in the test suite and replace with `@Transactional` rollback or test-data prefix isolation.

**How it helps:** Each `@DirtiesContext` forces a full Spring context restart. A single such annotation can add 5-15 seconds of context startup overhead per affected test class.

**How to find them:**
```bash
grep -rn "@DirtiesContext" src/test/java/
```

**Cost:** Low — pure test refactoring, no production code change. Each instance is independent.

**Expected gain:** Depends on count. If there are 5+ `@DirtiesContext` usages, eliminating them could save 30-75 seconds of context restart overhead in CI.

**Recommended action for v1.11:** Do this audit first — it is zero-risk and has immediate ROI.

### Option B: Replace Full `@SpringBootTest` with Test Slices

**What:** Replace `@SpringBootTest(webEnvironment=NONE)` tests that only test the JPA layer with `@DataJpaTest`. Replace tests that only test the web layer with `@WebMvcTest`. Full `@SpringBootTest` loads every bean — test slices load only what is needed.

**Key slices available:**

| Slice Annotation | Loads | Good For |
|-----------------|-------|----------|
| `@DataJpaTest` | JPA layer only (H2 auto-configured) | Repository tests, entity queries |
| `@WebMvcTest` | MVC layer only (no DB) | Controller unit tests with mocked services |
| `@JsonTest` | Jackson only | Serialization / deserialization tests |

**Cost:** Medium. Each test class converted requires adding `@MockBean` for everything outside the slice. With OSIV enabled and the current architecture (controllers delegate entirely to services), `@WebMvcTest` slices require mocking all service dependencies — which is significant for controllers with 5-10 service injections.

**Constraint specific to CTC:** The existing ITs in `TemplateRenderingSmokeIT` use `@SpringBootTest` to verify that ALL template renderings return HTTP 200 — this cannot be converted to a slice because it tests full integration. Keep full `@SpringBootTest` for these cross-cutting smoke tests.

**Expected gain:** Significant for repository-focused tests. If 30+ ITs become `@DataJpaTest`, each saves the startup cost of the full web context (~2-4 seconds per class that gets its own context key).

**Recommended action for v1.11:** Identify candidates (ITs that only assert JPA behavior, no web layer assertions) and convert selectively. Do NOT attempt a bulk conversion.

### Option C: Consolidate `@MockBean` Declarations to Reduce Context Variations

**What:** Spring's context cache key includes the set of `@MockBean` declarations. If two test classes use the same `@SpringBootTest` but different `@MockBean` sets, Spring starts two separate contexts. Extracting shared mock configurations into a base class or `@TestConfiguration` reduces unique cache keys.

**How to detect:** After running `./mvnw verify`, examine the JVM startup log (set `logging.level.org.springframework.test.context=DEBUG`) to count distinct context loads.

**Cost:** Low-to-medium. Creating a base class for IT tests is straightforward. The risk is that shared mock state causes test interference if mocks are not reset between tests.

**Recommended action for v1.11:** Add `logging.level.org.springframework.test.context=DEBUG` to `application-test.yml` (or CI argLine), run the IT suite, count unique context starts. If more than 5 unique contexts are started, consolidation is worthwhile.

### Option D: Test Module Split (Heavy — Architectural Change)

**What:** Split `src/test/java` into two Maven modules: one for unit tests (no Spring context) and one for integration tests (Spring context, Testcontainers). Run them in parallel as separate Maven executions.

**How it helps:** Pure unit tests (no Spring) have zero context startup cost. If separated into a module that Surefire runs without any Spring infrastructure, they complete much faster. The IT module runs in parallel.

**Cost:** HIGH. Converting a single-module project to multi-module requires:
- New top-level `pom.xml` as an aggregator.
- Two child modules (`ctc-manager-core` + `ctc-manager-tests` or similar).
- Moving all test source files to the new module.
- Re-configuring Surefire, Failsafe, JaCoCo across both modules.
- Updating CI to handle the multi-module build artifact.
- Renovate and other tools need to be re-configured for multi-module POM structure.

**Constraint specific to CTC:** The existing `pom.xml` is deeply single-module (one Failsafe execution for ITs, one for E2E, one JaCoCo config, one Playwright dependency). The phase 79 `forkCount` work was optimized for single-module. A module split is a multi-milestone effort, not a v1.11 task.

**Recommended action for v1.11:** Do NOT attempt a module split. Pursue Options A, B, and C first; revisit module split only if they yield less than 20% additional improvement.

### Option E: Failsafe `forkCount` Increase for ITs

**What:** The current Failsafe `default-it` uses `forkCount=1C` (1 fork per CPU core). GitHub Actions `ubuntu-latest` runners have 2 cores (4 vCPUs). Increasing to `forkCount=2C` doubles the IT parallelism.

**Constraint:** The current Spring context architecture means each fork gets its own context. If tests share H2 in-memory state (the dev profile uses `jdbc:h2:mem:ctcdb`), two forks on the same named H2 database collide. The solution is to use `jdbc:h2:mem:${random}` per-fork or to migrate ITs to use Testcontainers with dynamic ports (already done for `BackupImportMariaDbSmokeIT`).

**Risk:** HIGH if any ITs rely on a named H2 URL. Check `application-dev.yml` for the H2 URL config before increasing forkCount.

**Expected gain:** Up to 2× IT wallclock if all ITs are independent. Real gain is lower due to startup overlap.

**Recommended action for v1.11:** Audit H2 URL configuration. If the dev profile uses `jdbc:h2:mem:` with a named database, switch to `jdbc:h2:mem:;DB_CLOSE_DELAY=-1` (anonymous, unique per connection) before increasing forkCount.

### Wallclock Reduction Priority Order for v1.11

1. **Option A (DirtiesContext audit)** — zero risk, immediate return, do first.
2. **Option C (context key consolidation)** — audit first (free), implement if count > 5 unique contexts.
3. **Option B (selective slice conversion)** — selectively apply to obvious candidates (repository-only ITs).
4. **Option E (forkCount audit)** — check H2 URL, increase if safe.
5. **Option D (module split)** — defer to v1.12+.

---

## 7. System Overview — Post-v1.11 Component Map

```
ctc-manager/ (project root)
├── pom.xml                          # MODIFIED: +Checkstyle/PMD/SpotBugs plugins in <build>
│                                    #           +OpenRewrite plugin in <pluginManagement>
│                                    #           +rewrite-check profile
├── rewrite.yml                      # NEW: OpenRewrite recipe + style config
├── renovate.json                    # NEW: Renovate bot config
├── config/
│   ├── checkstyle.xml               # NEW: Checkstyle rule set
│   ├── checkstyle-suppressions.xml  # NEW: Checkstyle suppressions
│   ├── pmd.xml                      # NEW: PMD rule set
│   └── spotbugs-exclude.xml         # NEW: SpotBugs exclusion filter
├── .github/
│   └── workflows/
│       ├── ci.yml                   # MODIFIED: Checkstyle/PMD/SpotBugs run inside verify
│       ├── codeql.yml               # NEW: CodeQL analysis workflow
│       ├── deploy-site.yml          # unchanged
│       ├── mariadb-migration-smoke.yml  # unchanged
│       └── release.yml             # unchanged
└── src/...                          # Java sources — no new packages for tooling streams
```

No new Java source packages are added. All four tooling streams operate on config files and CI workflow files — they are infrastructure, not application code.

---

## 8. Anti-Patterns to Avoid

### Anti-Pattern 1: Binding `rewrite:run` to the Verify Phase

**What:** `<execution><phase>verify</phase><goals><goal>run</goal></goals></execution>` in `<build><plugins>`.
**Why wrong:** Every `./mvnw verify` silently modifies source files. CI checks out code, runs verify, modifies files, but the working tree is never committed — changes are lost. Locally, developers discover uncommitted changes after every build run.
**Instead:** Keep `rewrite:run` exclusively as a manual invocation or a dedicated `mvn rewrite:run` step. Bind only `rewrite:dryRunNoFork` with `failOnDryRunResults=true` to a gating profile.

### Anti-Pattern 2: Wildcard `*.xml` Rule Sources for All Checkstyle Rules

**What:** Starting with `sun_checks.xml` or `google_checks.xml` unmodified and applying to all source files.
**Why wrong:** Spring Boot projects with Lombok generate source code during compilation that Checkstyle also sees. Lombok-generated getters/setters violate Javadoc and magic-number rules. The project will produce hundreds of violations before the first real one is investigated.
**Instead:** Start with a permissive subset and use `<suppressionsLocation>` to exclude generated sources (`target/generated-sources/**`) and test sources (`src/test/java/**`).

### Anti-Pattern 3: SpotBugs Without an Exclude Filter for Lombok-Generated Code

**What:** Running SpotBugs at `threshold=Low` on Lombok-annotated entities.
**Why wrong:** SpotBugs flags `@Getter` on `Collection` fields as `EI_EXPOSE_REP2` (exposing internal representation). CTC has 20 entities all using `@Getter` — this produces ~60 suppressed-but-visible false positives that drown out real findings.
**Instead:** Start `spotbugs-exclude.xml` with `<Bug pattern="EI_EXPOSE_REP2"/>` in the filter and expand exclusions only based on investigated findings.

### Anti-Pattern 4: Grouping ALL Dependencies Together in Renovate

**What:** `"groupName": "all dependencies"` with `"matchPackagePatterns": ["*"]`.
**Why wrong:** A single PR touching Spring Boot + Lombok + Playwright + all Google APIs at once is unreviable. If one upgrade breaks something, the entire group PR fails and must be individually bisected.
**Instead:** Group by release-cadence alignment (Spring Boot starters together, Google APIs together, Testcontainers together) and leave single-dependency libraries as individual PRs.

### Anti-Pattern 5: Adding CodeQL to the Existing `build-and-test` Job

**What:** Adding CodeQL `init` + `analyze` steps inside the existing `ci.yml` `build-and-test` job.
**Why wrong:** CodeQL initialization + analysis adds 5-15 minutes to the CI job. It blocks the Playwright E2E tests, the Docker build, and the JaCoCo coverage comment from landing while SAST analysis runs. SAST findings are advisory, not blocking on PRs.
**Instead:** Separate `codeql.yml` workflow. It runs independently, does not block the PR merge gate (unless explicitly required via branch protection rules), and can be scheduled weekly for full scans without impacting every push.

---

## 9. Integration Points Summary

| Tool | pom.xml Change | New Files | CI Change | Lifecycle Phase |
|------|---------------|-----------|-----------|----------------|
| Checkstyle | `<build><plugins>` — `maven-checkstyle-plugin` | `config/checkstyle.xml`, `config/checkstyle-suppressions.xml` | Runs inside existing `build-and-test` job (via `./mvnw verify`) | `verify` |
| PMD | `<build><plugins>` — `maven-pmd-plugin` | `config/pmd.xml` | Runs inside existing `build-and-test` job | `verify` |
| SpotBugs | `<build><plugins>` — `spotbugs-maven-plugin` | `config/spotbugs-exclude.xml` | Runs inside existing `build-and-test` job | `verify` |
| OpenRewrite | `<pluginManagement>` — `rewrite-maven-plugin` + `<profile id="rewrite-check">` | `rewrite.yml` | Optional standalone job (`mvn verify -Prewrite-check`) | Manual / `verify` via profile |
| Renovate | none | `renovate.json` | Renovate PRs trigger existing `build-and-test` | External bot — no Maven phase |
| CodeQL | `mvn compile -DskipTests` called inside workflow | `.github/workflows/codeql.yml` | New standalone workflow | Not Maven (GitHub Actions) |

---

## Sources

- [OpenRewrite Maven plugin docs](https://docs.openrewrite.org/reference/rewrite-maven-plugin) — goals, `failOnDryRunResults`, configuration options (HIGH confidence)
- [OpenRewrite plugin-info.html](https://openrewrite.github.io/rewrite-maven-plugin/plugin-info.html) — current version 6.40.0, lifecycle fork behavior (HIGH confidence)
- [Apache Maven Checkstyle Plugin](https://maven.apache.org/plugins/maven-checkstyle-plugin/usage.html) — version 3.6.0, `configLocation`, `suppressionsLocation`, `failsOnError` (HIGH confidence)
- [Apache Maven PMD Plugin FAQ](https://maven.apache.org/plugins/maven-pmd-plugin/faq.html) — version 3.28.0, `failOnViolation`, `failurePriority` (HIGH confidence)
- [SpotBugs Maven Plugin — Violation Checking](https://spotbugs.github.io/spotbugs-maven-plugin/examples/violationChecking.html) — version 4.9.8.3, verify-phase integration (HIGH confidence)
- [Renovate Configuration Options](https://docs.renovatebot.com/configuration-options/) — file search order, `schedule`, `packageRules`, `automerge` (HIGH confidence)
- [Renovate Java/Maven docs](https://docs.renovatebot.com/java/) — Maven pom.xml scanning, settings.xml registry support (HIGH confidence)
- [GitHub CodeQL starter workflow](https://github.com/actions/starter-workflows/blob/main/code-scanning/codeql.yml) — canonical workflow structure, permissions, triggers (HIGH confidence)
- [GitHub CodeQL for compiled languages](https://docs.github.com/en/code-security/code-scanning/creating-an-advanced-setup-for-code-scanning/codeql-code-scanning-for-compiled-languages) — manual build vs autobuild, Java-Kotlin language key (HIGH confidence)
- [Semgrep CI sample configs](https://semgrep.dev/docs/semgrep-ci/sample-ci-configs) — OSS vs cloud-managed, container image, diff-aware PR scanning (HIGH confidence)
- [Zalando Engineering Blog — Spring Boot Test Optimization](https://engineering.zalando.com/posts/2023/11/mastering-testing-efficiency-in-spring-boot-optimization-strategies-and-best-practices.html) — 60% improvement case study, `@DirtiesContext` impact, `@MockBean` context key impact (MEDIUM confidence — 2023, still applicable to Spring Boot 4)
- [Optimizing Spring Integration Tests at Scale — JAVAPRO](https://javapro.io/2025/12/17/optimizing-spring-integration-tests-at-scale/) — test slicing, context caching patterns (MEDIUM confidence)

---

*Architecture research for: CTC Manager v1.11 — Tooling Infrastructure (OpenRewrite, Checkstyle/PMD/SpotBugs, Renovate, CodeQL) + Test Wallclock Reduction*
*Researched: 2026-05-16*
