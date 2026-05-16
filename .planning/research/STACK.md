# Stack Research — v1.11 Tooling Infrastructure

**Domain:** Java 25 / Spring Boot 4 Maven project tooling (OpenRewrite, Clean Code, Renovate, SAST)
**Researched:** 2026-05-16
**Confidence:** HIGH — all versions verified against Maven Central, GitHub Releases, and official docs as of 2026-05-16

---

## Scope

This file covers ONLY the four new tooling streams added in v1.11. The existing stack (Spring Boot 4.0.6,
Java 25, MariaDB, Thymeleaf, JUnit 5, Playwright, JaCoCo 0.8.14, Testcontainers 2.0.5) is unchanged and
not re-researched here.

---

## Stream 1 — OpenRewrite (Recipe-based Refactoring)

### Recommended Coordinates

| Artifact | Group | Version | Role |
|----------|-------|---------|------|
| `rewrite-maven-plugin` | `org.openrewrite.maven` | **6.39.0** | Maven plugin (run/dryRun/discover goals) |
| `rewrite-spring` | `org.openrewrite.recipe` | **6.30.4** | Spring Boot 4 migration recipes (community edition) |
| `rewrite-migrate-java` | `org.openrewrite.recipe` | **3.34.1** | Java version migration (UpgradeToJava25 etc.) |

Version source: OpenRewrite official docs "Latest versions of every OpenRewrite module" —
https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module (verified 2026-05-16).
Plugin latest on Maven Central: 6.39.0 (6.40.0-SNAPSHOT in progress).

### Why OpenRewrite (not manual refactoring)

OpenRewrite applies every transformation as an AST-aware, semantically-correct edit. Recipes are composable
and independently tested by upstream maintainers — `mvn rewrite:dryRun` produces a diff before any file is
modified. For this project, the primary value is: (a) automated application of the "Migrate to Spring Boot
4.0 (Community Edition)" recipe as the Spring Boot 4.x ecosystem matures, and (b) the `UpgradeToJava25`
recipe chain which handles deprecated-API replacements that accumulate silently over time.

### Available Recipes for This Project

**Spring Boot 4 migration** (module `org.openrewrite.recipe:rewrite-spring:6.30.4`):
- `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` — composite recipe; community edition; chains
  Spring Framework 7, Spring Security 7, modular starter renaming
- `org.openrewrite.java.spring.boot4.MigrateToModularStarters` — detects package imports and adds missing
  starters (e.g., `spring-boot-starter-flyway` for Flyway users — this project already has it, so a no-op
  in practice)
- `org.openrewrite.java.spring.boot4.SpringBootProperties_4_0` — renames deprecated property keys
- `org.openrewrite.java.spring.boot4.ReplaceMockBeanAndSpyBean` — `@MockBean`/`@SpyBean` → Mockito
  equivalents

**Java 25 migration** (module `org.openrewrite.recipe:rewrite-migrate-java:3.34.1`):
- `org.openrewrite.java.migrate.UpgradeToJava25` — composite; chains through Java 17/21/24/25 sub-recipes;
  19 child recipes including build-target bump, deprecated-API replacements, `SecurityManager` removal,
  `Process#waitFor(Duration)` migration, unused variable → underscore, `ZipError` → `ZipException`
- `org.openrewrite.java.migrate.UpgradePluginsForJava25` — upgrades Maven plugins in pom.xml to
  Java-25-compatible minimum versions; safe to run as a dry-run audit

**Note on recipe maturity:** Spring Boot 4 recipes in the community edition (rewrite-spring 6.30.x) were
published December 2025 and are still maturing. Always run `mvn rewrite:dryRun` and inspect the diff on a
clean branch before applying with `mvn rewrite:run`. Do not activate recipes in the verify lifecycle.

### pom.xml Integration

Place inside `<build><plugins>` (not `<pluginManagement>` — the plugin needs to be directly invocable as
`mvn rewrite:run`). Recipe modules go as `<dependencies>` inside the `<plugin>` block, not as project
dependencies.

```xml
<plugin>
  <groupId>org.openrewrite.maven</groupId>
  <artifactId>rewrite-maven-plugin</artifactId>
  <version>6.39.0</version>
  <configuration>
    <!-- No activeRecipes at build time — recipes invoked manually via mvn rewrite:run -->
    <exportDatatables>true</exportDatatables>
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
</plugin>
```

Do NOT bind any OpenRewrite goal to the Maven lifecycle (no `<executions>` with `<phase>`). The plugin
rewrites source files in-place — binding it to verify would destructively modify uncommitted code.

Developer workflow:
```bash
# Preview changes without modifying files
./mvnw rewrite:dryRun -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava25

# Apply after reviewing diff
./mvnw rewrite:run -Drewrite.activeRecipes=org.openrewrite.java.migrate.UpgradeToJava25

# Discover all available recipes on the classpath
./mvnw rewrite:discover
```

### CI Integration

None required. OpenRewrite is a developer tool, not a CI gate. Do not add it to ci.yml.

---

## Stream 2 — Clean Code Enforcement

### Recommendation: SpotBugs + find-sec-bugs (NOT Checkstyle, NOT PMD)

**Decision matrix:**

| Tool | Focus | Java 25? | Lombok compatibility | Spring coverage | Verdict |
|------|-------|----------|---------------------|-----------------|---------|
| **SpotBugs 4.9.8** | Bytecode: bug patterns, null, concurrency | Yes (4.9.x explicit) | Needs `lombok.addLombokGeneratedAnnotation=true` config | find-sec-bugs adds 144 Spring patterns | **Recommended** |
| Checkstyle 13.4.2 | Source: formatting, naming conventions | Yes (Java 25 CI added) | False positives on `@Data`/`@Builder` generated code | N/A | Skip — conventions already enforced via CLAUDE.md |
| PMD 7.17.0 | Source: code smells, unused vars, best practices | Yes (PMD 7.16+) | False positives on Lombok-generated classes | N/A | Skip — overlaps with compiler + test discipline |

**Why SpotBugs wins for this specific project:**

1. The project already enforces naming conventions, formatting, and code structure via CLAUDE.md architectural
   principles and CI code reviews. Checkstyle adds zero value on top of this discipline.
2. PMD's highest-value rules (unused vars, empty catch blocks) are already caught by the Java 25 compiler
   (`-Xlint`) and the existing code review process.
3. SpotBugs analyzes compiled bytecode — it catches null dereferences, resource leaks, and security patterns
   that source-only tools (Checkstyle, PMD) cannot see.
4. The `find-sec-bugs` plugin extends SpotBugs with 144 Spring Security patterns directly relevant to this
   project's attack surface: path traversal, SSRF, XSS in Thymeleaf, CSRF bypass, SQL injection in JDBC
   batch, insecure deserialization. It covers the exact vulnerability classes this project already defends
   against — running it provides automated regression protection as new code is added.

### Recommended Coordinates

| Artifact | Group | Version | Role |
|----------|-------|---------|------|
| `spotbugs-maven-plugin` | `com.github.spotbugs` | **4.9.8.3** | Maven plugin |
| `findsecbugs-plugin` | `com.h3xstream.findsecbugs` | **1.14.0** | SpotBugs security extension (OWASP coverage) |

Version sources:
- spotbugs-maven-plugin 4.9.8.3 released 2025-03-29: https://github.com/spotbugs/spotbugs-maven-plugin/releases
- findsecbugs-plugin 1.14.0 released 2024-06-17: https://github.com/find-sec-bugs/find-sec-bugs/releases

**Java 25 support:** spotbugs-maven-plugin 4.9.x explicitly lists Java 11, 17, 21, and 25 as supported JDKs
(both for running the plugin and for analyzed bytecode). find-sec-bugs 1.14.0 upgraded its SpotBugs core to
4.8.3 and added Jakarta support; the analyzed bytecode version ceiling is determined by SpotBugs core (4.9.8
in the Maven plugin), not by findsecbugs.

**Lombok caveat:** SpotBugs will flag Lombok-generated `equals`/`hashCode`/`toString` methods without
additional configuration. Mitigation: add to `lombok.config`:

```
lombok.addLombokGeneratedAnnotation = true
```

This annotates all Lombok-generated methods with `@lombok.Generated`, and SpotBugs skips methods annotated
with `@Generated` by default. No SpotBugs filter rule needed for Lombok with this configuration.

### pom.xml Integration

Bind to the `verify` phase so `./mvnw verify` includes SpotBugs on every CI build. Place in
`<build><plugins>` (version-pin explicitly — Spring Boot parent does not manage this plugin).

```xml
<plugin>
  <groupId>com.github.spotbugs</groupId>
  <artifactId>spotbugs-maven-plugin</artifactId>
  <version>4.9.8.3</version>
  <configuration>
    <effort>Max</effort>
    <threshold>Medium</threshold>
    <failOnError>true</failOnError>
    <!-- Exclude Playwright graphic services and test classes -->
    <excludeFilterFile>spotbugs-exclude.xml</excludeFilterFile>
  </configuration>
  <dependencies>
    <dependency>
      <groupId>com.h3xstream.findsecbugs</groupId>
      <artifactId>findsecbugs-plugin</artifactId>
      <version>1.14.0</version>
    </dependency>
  </dependencies>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

**Exclusion filter file** (`spotbugs-exclude.xml` at project root) must cover:

- Graphic service classes excluded from JaCoCo (same list): `AbstractGraphicService`, `TeamCardService`,
  `LineupGraphicService`, `ResultsGraphicService`, `SettingsGraphicService`, `OverlayGraphicService`,
  `MatchResultsGraphicService`, `PowerRankingsGraphicService`, `PlayoffRoundOverviewGraphicService`,
  `PlayoffRoundScheduleGraphicService`, `PlayoffRoundResultsGraphicService` — Playwright-dependent,
  produce false positives for resource-leak patterns (Playwright manages its own lifecycle)
- Test classes (`**/*Test.class`, `**/*IT.class`) — test utilities are not production security concerns;
  test code may intentionally use patterns SpotBugs would flag in production

Minimal `spotbugs-exclude.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter
  xmlns="https://github.com/spotbugs/filter/3.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="https://github.com/spotbugs/filter/3.0.0
                      https://raw.githubusercontent.com/spotbugs/spotbugs/master/spotbugs/etc/findbugsfilter.xsd">

  <!-- Playwright graphic services: Playwright manages its own resource lifecycle -->
  <Match>
    <Package name="~org\.ctc\.admin\.service\.(Abstract|Team|Lineup|Results|Settings|Overlay|MatchResults|PowerRankings|PlayoffRound.*).*"/>
  </Match>

  <!-- Test classes: security analysis not meaningful for test utilities -->
  <Match>
    <Package name="~org\.ctc\..*"/>
    <Source name="~.*(Test|IT)\.java"/>
  </Match>

</FindBugsFilter>
```

**Phase ordering vs JaCoCo:** SpotBugs' `check` goal binds to `verify` by default (same as JaCoCo's
`check` execution). Maven runs goals in declaration order within the same phase — declare SpotBugs plugin
AFTER JaCoCo in `pom.xml` to ensure JaCoCo gate runs first and SpotBugs sees the post-test bytecode.

### CI Integration

SpotBugs runs inside `./mvnw verify` — no separate ci.yml job needed. It fails the build exactly like the
JaCoCo gate. The existing `Build and Unit/Integration Tests` step in ci.yml already runs `./mvnw verify`.

---

## Stream 3 — Renovate Automated Dependency Updates

### Recommendation: Mend Renovate GitHub App (not self-hosted GitHub Action)

**Decision:**

| Criterion | GitHub App (Mend-hosted) | Self-hosted GitHub Action |
|-----------|--------------------------|--------------------------|
| Cost | Free (all repo types) | Free (uses GitHub Actions minutes) |
| Token needed | App installation token (managed by Mend) | PAT with `repo` + `workflow` scope required; GITHUB_TOKEN insufficient |
| Infrastructure | Zero — Mend manages scheduling and updates | Must configure cron schedule + manage renovatebot/github-action version |
| Single-repo fit | Ideal | Over-engineered for one repository |
| Workflow file updates | Yes (built-in `github-actions` manager) | Yes, but needs PAT with `workflow` scope |

For a single-repository project on GitHub, the Mend Renovate GitHub App is unambiguously correct: free,
zero-ops, auto-updating, and creates PRs against `master` exactly like the self-hosted variant.

**The self-hosted GitHub Action (`renovatebot/github-action@v46.1.14` as of 2026-05-11) exists** and is
viable — but it requires storing a Classic PAT with `repo` + `workflow` scopes as a repository secret,
and a cron-scheduled workflow job. This is unnecessary operational overhead for a single repo.

### Installation

Install the free Mend Renovate GitHub App: https://github.com/marketplace/renovate

Select only this repository. On first run, Renovate creates an onboarding PR with a `renovate.json` at the
repository root. Merge the PR to activate.

### renovate.json Configuration

```json
{
  "$schema": "https://docs.renovatebot.com/renovate-schema.json",
  "extends": [
    "config:recommended"
  ],
  "enabledManagers": ["maven", "github-actions"],
  "schedule": ["before 6am on monday"],
  "dependencyDashboard": true,
  "dependencyDashboardTitle": "Renovate Dependency Dashboard",
  "labels": ["dependencies"],
  "packageRules": [
    {
      "matchManagers": ["maven"],
      "matchPackagePatterns": ["^org\\.springframework\\.boot"],
      "groupName": "Spring Boot",
      "automerge": false
    },
    {
      "matchManagers": ["maven"],
      "matchPackagePatterns": ["^org\\.openrewrite"],
      "groupName": "OpenRewrite",
      "automerge": false
    },
    {
      "matchManagers": ["maven"],
      "matchPackagePatterns": ["^com\\.microsoft\\.playwright"],
      "groupName": "Playwright",
      "automerge": false
    },
    {
      "matchManagers": ["maven"],
      "matchPackagePatterns": ["^org\\.testcontainers"],
      "groupName": "Testcontainers",
      "automerge": false
    },
    {
      "matchManagers": ["maven"],
      "matchUpdateTypes": ["patch"],
      "automerge": true,
      "automergeType": "pr"
    },
    {
      "matchManagers": ["github-actions"],
      "automerge": false
    }
  ]
}
```

**Key decisions in this config:**

- `enabledManagers: ["maven", "github-actions"]` — covers `pom.xml` and all `.github/workflows/*.yml` files.
  Current workflow actions that will receive version PRs: `actions/checkout@v6`, `actions/setup-java@v5`,
  `madrapps/jacoco-report@v1.7.2`, `actions/upload-artifact@v7`, `github/codeql-action@v4`
- Spring Boot, OpenRewrite, Playwright, and Testcontainers grouped as manual-review PRs — these require test
  runs and compatibility verification before merging
- Maven patch updates: automerge enabled — safe for libraries like `commons-text`, `jsoup`, `google-api-client`
- GitHub Actions updates: manual review — action version bumps can change behavior
- `schedule: ["before 6am on monday"]` — weekly batch minimizes PR noise

### What Renovate Discovers in This pom.xml

Renovate's Maven manager parses all `<version>` elements, including:

- Parent BOM: `spring-boot-starter-parent:4.0.6`
- Pinned `thymeleaf:3.1.5.RELEASE` in `<dependencyManagement>`
- `testcontainers.version:2.0.5` (property-style version)
- `playwright.version:1.59.0`
- `lombok.version:1.18.46`
- `commons-text:1.15.0`, `jsoup:1.22.2`, `guava:33.4.8-jre`
- Google API libraries: `google-api-client:2.9.0`, `google-api-services-sheets`, `google-api-services-calendar`, `google-auth-library-oauth2-http:1.46.0`
- Plugins not managed by Spring Boot parent: `jacoco-maven-plugin:0.8.14`, `rewrite-maven-plugin:6.39.0`, `spotbugs-maven-plugin:4.9.8.3`

**Schema requirement:** The existing `pom.xml` already declares the standard Maven POM namespace
(`xmlns="http://maven.apache.org/POM/4.0.0"`) — Renovate's Maven manager parses it without additional config.

### CI Integration

None. The Mend Renovate App runs on its own schedule, independent of ci.yml. It creates PRs against
`master`; the existing CI workflow validates them automatically.

---

## Stream 4 — SAST (Security Static Analysis)

### Recommendation: CodeQL via GitHub Actions (not Semgrep CE)

**Decision matrix:**

| Criterion | CodeQL v4 | Semgrep CE |
|-----------|-----------|------------|
| Java 25 support | Yes — CodeQL 2.23.1 (2025-09-23) added explicit Java 25 support | Unconfirmed for Java 25 specifically |
| Taint tracking | Full cross-function and cross-file data-flow analysis | Single-function only after Dec 2024 licensing change |
| Cost for this (public) repo | **Free** — GitHub Code Scanning is free for public repos | Free (CE), but key pro rules paywalled |
| GitHub native integration | Results in Security tab + PR annotations, no extra config | Requires separate SARIF upload step |
| False-positive rate | Lower (semantic analysis with query libraries) | Higher (pattern matching, no cross-file context) |
| Scan time | 5-15 min for ~17k LOC | ~10-30 sec |
| Maintenance | Zero — GitHub manages CodeQL bundles | Requires rule maintenance / version pinning |
| Spring framework coverage | `security-extended` query pack covers Spring MVC injection, XSS, SSRF, path traversal | Spring-specific rules paywalled in Semgrep Pro |

**Why CodeQL wins:**

This repository is public on GitHub — CodeQL Code Scanning is completely free with full semantic analysis.
Semgrep CE after December 2024 cannot track taint across function boundaries, which means it misses exactly
the vulnerability class this project defends against (chained multi-step data flows: SSRF, ZIP-Slip, path
traversal through multiple service layers). CodeQL's cross-file data-flow analysis catches these chains.

The 5-15 minute scan time runs in a parallel CI job and does not block the main `build-and-test` job.

**Action version to use:** `github/codeql-action@v4`

v4 was released 2025-10-07, runs on Node.js 24. Current patch: `v4.35.5` (released 2026-05-15, bundles
CodeQL 2.25.4). v3 is deprecated December 2026 with no further updates. Use the `v4` floating tag — GitHub
manages safe rollouts of patch updates.

**Java 25 support confirmation (HIGH confidence):** GitHub Changelog 2025-09-26 explicitly states: "CodeQL
2.23.1 adds support for Java 25, TypeScript 5.9 and Swift 6.1.3 — The Java extractor and QL libraries now
support Java 25, with support for Java 25 compact source files (JEP 512), new predicates to identify
implicitly declared classes, and support for Java 25 module import declarations."
Source: https://github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/

### GitHub Actions Workflow Job

Add a new `codeql-analysis` job to `.github/workflows/ci.yml`. It must be a separate job (not a step in
`build-and-test`) because CodeQL requires the `security-events: write` permission which must not be granted
to the entire workflow.

```yaml
codeql-analysis:
  name: CodeQL SAST
  runs-on: ubuntu-latest
  permissions:
    actions: read
    contents: read
    security-events: write   # required to upload SARIF to GitHub Security tab

  strategy:
    fail-fast: false
    matrix:
      language: [ 'java-kotlin' ]

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
        languages: ${{ matrix.language }}
        queries: security-extended

    - name: Build for CodeQL (compile only, skip tests)
      run: ./mvnw compile --no-transfer-progress -Dspring.profiles.active=dev -DskipTests

    - name: Perform CodeQL Analysis
      uses: github/codeql-action/analyze@v4
      with:
        category: "/language:${{ matrix.language }}"
```

**Key configuration decisions:**

- Language `java-kotlin` is the current canonical identifier replacing the deprecated `java` value; covers
  all Java source (this project has no Kotlin but the identifier is correct per GitHub docs)
- `queries: security-extended` enables OWASP Top 10 + high-confidence extended queries beyond the default
  `security-and-quality` pack; appropriate for this project's documented attack surface
- Build step: `./mvnw compile --no-transfer-progress -DskipTests` — compiles all sources for CodeQL's
  database without running 1883+ tests; keeps the job under 10 minutes for this codebase size
- Job-level `permissions` block: grants `security-events: write` only to this job, not to the entire
  workflow. The existing top-level `permissions: { contents: read, pull-requests: write }` is unchanged.
- No `needs:` dependency — runs in parallel with `build-and-test` and `dockerfile-noble-pin-guard`

### What CodeQL Will Analyze

CodeQL traces data flows through all `src/main/java/org/ctc/**` source. High-value call chains for this
project:

- `BackupController` multipart upload → `BackupImportService` → ZIP-Slip path check (regression: ZipBomb
  cap + startsWith(uploadDir) guard)
- `FileStorageService.storeFromUrl()` SSRF chain → hostname blocklist → URL fetch (regression: private-IP
  block)
- `TemplateEditorController` → `TemplateEditorService` → SpEL pattern validation
- `RaceAttachmentService` path resolution → `Content-Disposition` sanitization
- `BackupImportService` → `JdbcTemplate.batchUpdate` native SQL (SQL injection in parameterized queries is
  a non-issue — CodeQL will confirm)

Findings appear in the GitHub Security tab → Code scanning alerts, and as PR inline annotations on changed
files.

---

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|-------------|
| **Checkstyle** | Project already enforces naming/formatting via CLAUDE.md + code reviews. Lombok-generated code produces false positives requiring extensive suppression config. No net value for this codebase. | SpotBugs (analyzes behavioral bugs, not style) |
| **PMD** | Overlaps with Java compiler warnings and the existing test discipline. Most useful PMD rules are already enforced by architecture conventions. Lombok false positives require suppression maintenance. | SpotBugs |
| **Semgrep CE** | Single-function taint tracking only after Dec 2024 licensing change; cannot trace the multi-step data flows (SSRF chain, ZIP-Slip) relevant to this codebase. Spring-specific security rules paywalled in Pro. | CodeQL |
| **Mend Renovate self-hosted action** | Requires a Classic PAT with `repo` + `workflow` scopes stored as secrets, manual cron scheduling, and manual Renovate version management — all unnecessary for a single-repo project when the free hosted app exists. | Mend Renovate GitHub App |
| **FindBugs / FindBugs Maven Plugin** | Abandoned since 2016; superseded by SpotBugs. No support for Java 8+ bytecode format. Not on Maven Central for current Java. | SpotBugs |
| **SonarQube / SonarCloud community** | Requires external SonarQube server or SonarCloud account (free tier has limitations on private repos). Adds external service dependency. Java analysis quality is comparable to but not deeper than CodeQL for this project's threat model. | CodeQL (GitHub-native, free for public repos) |
| **OpenRewrite bound to `verify`** | OpenRewrite's `run` goal modifies source files in-place. Binding to the `verify` phase would destructively rewrite uncommitted code on every `./mvnw verify` call. | Invoke manually: `mvn rewrite:dryRun` then `mvn rewrite:run` |
| **`rewrite-java` as a project dependency** | `rewrite-java` is an internal OpenRewrite library module, not a consumer-facing artifact. Adding it as a `<dependency>` (not a plugin dependency) does nothing useful. | `rewrite-maven-plugin` with recipe module `<dependencies>` |
| **`codeql-action@v3`** | Deprecated December 2026; no new query packs or language features backported. Java 25 improvements (CodeQL 2.23.1+) available only in v4. | `github/codeql-action@v4` |
| **Checkstyle 13.3.0+** | Checkstyle 13.3.0 raised its minimum JDK requirement to Java 21 for running the tool (not for analyzed source). This is compatible with our setup, but the style enforcement value is still zero given existing conventions. | SpotBugs |

---

## Version Compatibility Matrix

| Tool | Version | Runs on JVM | Analyzes | Notes |
|------|---------|-------------|----------|-------|
| `rewrite-maven-plugin` | 6.39.0 | Java 11+ (Maven JVM) | Java 25 sources | Plugin runs in Maven process; recipe modules analyze any Java version source |
| `rewrite-spring` | 6.30.4 | — | — | Plugin dependency; must share major version with rewrite-maven-plugin (both 6.x) |
| `rewrite-migrate-java` | 3.34.1 | — | — | Plugin dependency; 3.x pairs with plugin 6.x per OpenRewrite versioning scheme |
| `spotbugs-maven-plugin` | 4.9.8.3 | Java 11+ | Java 25 bytecode | Explicit Java 25 JDK support listed in plugin docs |
| `findsecbugs-plugin` | 1.14.0 | — | — | SpotBugs plugin; compatible with SpotBugs 4.8.x and 4.9.x core |
| `codeql-action` | v4 (v4.35.5) | Node.js 24 (GitHub runner) | Java 25 source | Java 25 explicit since CodeQL 2.23.1; current bundle 2.25.4 |
| Renovate GitHub App | hosted (v46.x) | Mend infrastructure | `pom.xml`, `*.yml` | Self-updating; always current |

**Plugin conflict check against existing pom.xml:**

- `rewrite-maven-plugin` — no lifecycle binding; invoked only via explicit `mvn rewrite:*` commands; no
  conflict with Surefire, Failsafe, JaCoCo, exec-maven-plugin, or spring-boot-maven-plugin
- `spotbugs-maven-plugin` bound to `verify` phase; runs AFTER `test` (Surefire) and AFTER
  `integration-test` (Failsafe); concurrent with JaCoCo `check` execution — declare SpotBugs after JaCoCo
  in `pom.xml` to control ordering within the verify phase
- CodeQL — separate CI job; zero pom.xml impact
- Renovate — no pom.xml changes; runs outside CI

---

## Implementation Checklist

### pom.xml Changes Required

1. Add `rewrite-maven-plugin` 6.39.0 to `<build><plugins>` with recipe module dependencies inside the
   plugin block (no lifecycle binding, no `<executions>`)
2. Add `spotbugs-maven-plugin` 4.9.8.3 to `<build><plugins>` with `<goal>check</goal>` execution and
   `findsecbugs-plugin` dependency — place AFTER the JaCoCo plugin declaration

### New Files at Repository Root

1. `spotbugs-exclude.xml` — exclusion filter (graphic service packages + test classes)
2. `lombok.config` — add `lombok.addLombokGeneratedAnnotation = true` (check if file exists first; if it
   does, append the line rather than overwrite)
3. `renovate.json` — created by Renovate onboarding PR; content specified above; merge the PR

### ci.yml Changes Required

1. Add `codeql-analysis` job (new independent parallel job with job-level `permissions` block including
   `security-events: write`)

---

## Sources

- OpenRewrite latest module versions: https://docs.openrewrite.org/reference/latest-versions-of-every-openrewrite-module (HIGH)
- OpenRewrite Maven plugin docs: https://docs.openrewrite.org/reference/rewrite-maven-plugin (HIGH)
- UpgradeToJava25 recipe: https://docs.openrewrite.org/recipes/java/migrate/upgradetojava25 (HIGH)
- Spring Boot 4 recipes: https://docs.openrewrite.org/recipes/java/spring/boot4 (HIGH)
- Maven Central rewrite-maven-plugin: https://central.sonatype.com/artifact/org.openrewrite.maven/rewrite-maven-plugin (HIGH)
- SpotBugs Maven plugin releases: https://github.com/spotbugs/spotbugs-maven-plugin/releases (HIGH)
- SpotBugs Java 25 JDK support: https://spotbugs.readthedocs.io/en/latest/maven.html (HIGH)
- find-sec-bugs releases: https://github.com/find-sec-bugs/find-sec-bugs/releases (HIGH)
- CodeQL Java 25 support announcement: https://github.blog/changelog/2025-09-26-codeql-2-23-1-adds-support-for-java-25-typescript-5-9-and-swift-6-1-3/ (HIGH)
- codeql-action v4 release: https://github.com/github/codeql-action/releases (v4.35.5 on 2026-05-15) (HIGH)
- codeql-action v3 deprecation: https://github.blog/changelog/2025-10-28-upcoming-deprecation-of-codeql-action-v3/ (HIGH)
- Mend Renovate GitHub App pricing: https://github.com/marketplace/renovate ($0/month, public + private) (HIGH)
- Renovate GitHub Action token requirements: https://github.com/renovatebot/github-action (HIGH)
- Renovate Maven manager: https://docs.renovatebot.com/modules/manager/maven/ (HIGH)
- Renovate running options: https://docs.renovatebot.com/getting-started/running/ (HIGH)
- Semgrep CE limitations post-Dec 2024: https://konvu.com/compare/semgrep-vs-codeql (MEDIUM — verified against Semgrep's own disclosures)
- PMD Java 25 support: https://pmd.github.io/2025/07/25/PMD-7.16.0/ (HIGH)
- maven-pmd-plugin 3.28.0: https://github.com/apache/maven-pmd-plugin/releases (HIGH)
- maven-checkstyle-plugin 3.6.0 Lombok exclusion: https://maven.apache.org/plugins/maven-checkstyle-plugin/examples/suppressions-filter.html (HIGH — used only to document the "avoid" decision)

---

*Stack research for: v1.11 Tooling Infrastructure & Tech-Debt Sweep (4 new tooling streams)*
*Researched: 2026-05-16*
