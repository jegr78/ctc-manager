# Phase 81: Static Analysis Gate (SpotBugs + find-sec-bugs) - Research

**Researched:** 2026-05-16
**Domain:** Maven build tooling — SpotBugs bytecode static analysis gate on a Lombok-heavy Spring Boot 4 / Java 25 single-module Maven project
**Confidence:** HIGH (plugin versions verified via Maven Central local cache; Lombok config keys verified against official docs; source code line numbers from live file reads; SecurityConfig BCrypt correction from live code inspection)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Plugin Wiring (pom.xml)**
- D-01: `spotbugs-maven-plugin` in main `<build><plugins>` (not a profile). Insertion point: after jacoco block (pom.xml:303-355), before exec-maven-plugin block (pom.xml:356-384).
- D-02: Plugin version: defer final pin to planner. Research-verified as 4.9.8.3 (Maven Central confirmed 2026-05-16). If newer stable patch exists at planning time with no behavior-breaking changes, prefer it; otherwise pin 4.9.8.3.
- D-03: `findsecbugs-plugin` 1.14.0 as `<plugins>` child of SpotBugs plugin block. Same pin-defer rule.
- D-04: No `<argLine>` on SpotBugs plugin block ever. Post-wiring smoke-check: coverage must remain >= 82% (ideally >= 87.80% v1.10 baseline).

**Effort and Threshold**
- D-05: `<effort>Max</effort>` — full bytecode analysis depth.
- D-06: `<threshold>Default</threshold>` for both goals — Medium+HIGH findings block the build.
- D-07: `<failOnError>true</failOnError>` on the `check` goal (default behavior, called out explicitly).

**Exclusion Scope (`config/spotbugs-exclude.xml`)**
- D-08: Hybrid exclusion strategy — 3 class-level excludes (CtcManagerApplication, TestDataService, DemoDataSeeder) + EI_EXPOSE_REP* pattern on `org.ctc.domain.model.*`. Graphic services NOT class-excluded; suppress per-method if needed.
- D-09: Every `<Match>` entry must carry an XML rationale comment + code-comment cross-reference. Planner decides on build-guard feasibility.

**Fix-vs-Suppress Posture**
- D-10: Hybrid posture — HIGH must fix; Medium real bug must fix; Medium intentional suppress with rationale; Medium stylistic suppress with short reason.
- D-11: Anticipated intentional suppressions: SSRF on FileStorageService.storeFromUrl, PATH_TRAVERSAL_IN on BackupImportService.restoreOneTable and FileStorageService.store*, HARD_CODE_PASSWORD on SecurityConfig.passwordEncoder. **NOTE: SecurityConfig does NOT have a BCryptPasswordEncoder bean — see Research Correction C-01 below.**

**Two-Commit STAT-05 Choreography**
- D-12: Plumbing commit (report-only) → baseline inspection → triage commits → gate-flip commit.
- D-13: STAT-06 deliberate-violation test on throwaway branch, reverted before PR merge.

**Documentation (STAT-07)**
- D-14: CLAUDE.md `## Conventions` section gets new paragraph after `### CSS Guidelines`.
- D-15: README one-liner discretionary; CLAUDE.md is authoritative.

### Claude's Discretion
- Final plugin version pins (D-02, D-03) — pick latest stable on Maven Central at planning time.
- Exact file layout of `config/spotbugs-exclude.xml` (grouping strategy).
- Deliberate-violation throwaway branch location (src/main/java vs src/test/java).
- Whether suppression-rationale build-guard (D-09 tail) is worth CI cost.
- Suppression-rationale wording.
- Whether `<plugins>` needs alphabetizing in pom.xml after insertion.

### Deferred Ideas (OUT OF SCOPE)
- Checkstyle / PMD integration (Research Conflict 2 rejection).
- Profile-based plugin invocation (SpotBugs is verify-bound, always-on).
- CI-level separate workflow for SpotBugs.
- Custom SpotBugs detector classes.
- Modifying JaCoCo plugin configuration.
- Changing existing JaCoCo `<excludes>` list.
- `<includeTests>true</includeTests>` scan.
- CodeQL SAST (Phase 85 scope).
- Renovate auto-bumps for SpotBugs plugins (Phase 84 scope).
- `spotbugs:gui` developer-local triage (discretion only).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| STAT-01 | `lombok.config` at project root with `lombok.addLombokGeneratedAnnotation = true` and `lombok.extern.findbugs.addSuppressFBWarnings = true` | Both keys verified against official Lombok configuration docs; syntax confirmed correct for Lombok 1.18.46 |
| STAT-02 | `spotbugs-maven-plugin` wired into `verify` phase after JaCoCo, with `<effort>Max</effort>` and `<threshold>Default</threshold>` | Plugin 4.9.8.3 verified as latest stable on Maven Central; insertion point confirmed at pom.xml line 355 (after jacoco `</plugin>`) |
| STAT-03 | `findsecbugs-plugin` registered as SpotBugs plugin dependency for 144 Spring Security-aware patterns | findsecbugs-plugin 1.14.0 verified as latest stable; plugin-dependency mechanism confirmed |
| STAT-04 | `config/spotbugs-exclude.xml` with documented suppressions for intentional patterns | Exact suppression targets verified in live source code with line numbers; BCrypt suppression updated (see C-01) |
| STAT-05 | Two atomic commits — report-only first, then blocking gate | Goal behavior difference between `spotbugs:spotbugs` and `spotbugs:check` clarified (see Research Finding F-01) |
| STAT-06 | `./mvnw verify` fails on new HIGH violation (deliberate throwaway-branch test) | Throwaway branch approach validated; `src/main/java` confirmed as correct location for deliberate violation (D-13 discretion resolved) |
| STAT-07 | CLAUDE.md Conventions section updated with gate + suppression + lombok.config invariant | CLAUDE.md structure verified; insertion point is after `### CSS Guidelines` block |
</phase_requirements>

---

## Summary

Phase 81 is a build-configuration phase with no new Java packages and no application logic changes. The entire surface is: two new project-root config files (`lombok.config`, `config/spotbugs-exclude.xml`), one pom.xml plugin block addition, and one CLAUDE.md paragraph. The principal risk is not technical uncertainty — it is triage workload: the actual number of Medium+HIGH findings after Lombok mitigation is unknown until the baseline report runs.

**Critical correction:** D-11 lists `HARD_CODE_PASSWORD` on `SecurityConfig.passwordEncoder` as an anticipated suppression. SecurityConfig.java does NOT contain a `BCryptPasswordEncoder` bean or any `PasswordEncoder` bean. The `securityFilterChain` uses `httpBasic` with no explicit password encoder. This suppression entry should be removed from the pre-staged `spotbugs-exclude.xml`. Only the SSRF and PATH_TRAVERSAL suppressions are needed for the anticipated D-11 set.

**`spotbugs:check` goal behavior clarified:** The `check` goal invokes the `spotbugs` goal first (analysis + report generation), then evaluates the report against the threshold and fails the build if violations exceed the allowed count. This means there is NO functional difference in analysis content between using `<goal>spotbugs</goal>` and `<goal>check</goal>` — both produce `target/spotbugsXml.xml`. The only difference is whether the build fails afterward. The two-commit STAT-05 choreography (D-12) is therefore sound.

**Primary recommendation:** Wire exactly as specified in D-01 through D-13. Verify the BCrypt correction in the pre-staged exclusion file. Budget the triage window generously — the BackupArchiveService (ZIP handling via `ZipInputStream`) and `FileStorageService.storeFromUrl` (URL connection) are the two highest-risk areas for non-Lombok Medium findings.

---

## Research Corrections

### C-01: SecurityConfig Does NOT Have a BCryptPasswordEncoder Bean

**Finding from live code inspection:** `SecurityConfig.java` (the `@Profile({"prod","docker"})` class) contains only one `@Bean`: a `SecurityFilterChain` that uses `httpBasic`. There is no `BCryptPasswordEncoder` bean, no `PasswordEncoder` bean, and no `@Value`-injected password property.

**Impact on D-11:** The pre-staged `config/spotbugs-exclude.xml` should NOT include a `HARD_CODE_PASSWORD` suppression for `SecurityConfig.passwordEncoder`. That method does not exist.

**Secondary class found:** `OpenSecurityConfig.java` (`@Profile({"dev","local"})`) also contains only a `SecurityFilterChain` bean — no password encoder.

**Revised anticipated suppressions for pre-staging:**
1. `SSRF` on `FileStorageService.storeFromUrl` — CONFIRMED (line 86-103)
2. `PATH_TRAVERSAL_IN` on `BackupArchiveService.assertEntrySafe` (via `PathTraversalGuard.assertWithin`) — see F-02 below
3. `PATH_TRAVERSAL_IN` on `FileStorageService.validatePathWithinUploadDir` (called from `store`, `storeImage`, `storeFromUrl`, `delete`) — CONFIRMED
4. ~~`HARD_CODE_PASSWORD` on `SecurityConfig.passwordEncoder`~~ — **REMOVE; method does not exist**

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Static analysis execution | Build / Maven lifecycle | — | SpotBugs runs as a bytecode scanner during `verify`; no JVM agent, no runtime tier |
| Exclusion filter | Build config (`config/`) | — | `spotbugs-exclude.xml` is consumed by the Maven plugin at analysis time |
| Lombok false-positive mitigation | Build config (`lombok.config`) | — | Lombok reads this at annotation-processor time; affects bytecode generation |
| Gate enforcement | Maven `verify` phase | CI `ci.yml` | `spotbugs:check` fails the build; CI runs `./mvnw verify` |
| Suppression documentation | `CLAUDE.md` Conventions | XML comments in exclusion file | Convention ensures future contributors understand the suppression rationale |

---

## Standard Stack

### Core (Phase 81 only — Maven plugin scope, not application dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spotbugs-maven-plugin` | 4.9.8.3 | Bytecode static analysis; `verify`-phase gate | Latest stable on Maven Central [VERIFIED: Maven Central local cache + GitHub releases page — 4.9.8.3 is `<release>` and `<latest>` in metadata] |
| `findsecbugs-plugin` | 1.14.0 | 144 Spring Security-aware patterns (SSRF, path traversal, XXE, etc.) | Latest stable on Maven Central [VERIFIED: Maven Central local cache + GitHub releases page — 1.14.0 is `<release>` and `<latest>` in metadata] |

No application `<dependencies>` change. Both are build-time-only plugin-level dependencies.

**Version verification (as of 2026-05-16):**

```
spotbugs-maven-plugin: 4.9.8.3  (released 2025-03-29, Maven Central <release>)
findsecbugs-plugin:    1.14.0   (released 2024-06-17, Maven Central <release>)
```

Both confirmed present in local Maven cache:
- `~/.m2/repository/com/github/spotbugs/spotbugs-maven-plugin/4.9.8.3/`
- `~/.m2/repository/com/h3xstream/findsecbugs/findsecbugs-plugin/1.14.0/`

No newer stable patches exist beyond these versions as of research date. **D-02/D-03 pin-defer rule: pin both at these versions.**

### Package Legitimacy Audit

These are Maven artifacts, not npm packages. `slopcheck` was not available in this environment. Standard registry verification performed via Maven Central metadata:

| Package | Registry | Age | Source Repo | Disposition |
|---------|----------|-----|-------------|-------------|
| `com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3` | Maven Central | 8+ years | github.com/spotbugs/spotbugs-maven-plugin | Approved — canonical SpotBugs Maven integration |
| `com.h3xstream.findsecbugs:findsecbugs-plugin:1.14.0` | Maven Central | 10+ years | github.com/find-sec-bugs/find-sec-bugs | Approved — standard security pattern library for SpotBugs |

*slopcheck was unavailable; these packages are confirmed through well-known, long-established open source projects with public GitHub repositories and large user communities.*

---

## Architecture Patterns

### System Architecture Diagram

```
./mvnw verify
    │
    ├── maven-surefire-plugin          (unit tests, @excludedGroups: integration,e2e,flaky)
    ├── maven-failsafe-plugin          (integration tests, @groups: integration)
    ├── jacoco:prepare-agent           (initialize phase — sets argLine for JaCoCo agent)
    ├── jacoco:report                  (verify phase — generates target/site/jacoco/)
    ├── jacoco:check                   (verify phase — enforces 82% line coverage gate)
    ├── failsafe:verify                (verify phase — surfaces integration test failures)
    └── spotbugs:check     [NEW]       (verify phase — invokes spotbugs:spotbugs first,
                                        then evaluates threshold and fails on Medium+HIGH)
                │
                ├── Reads: target/classes/**/*.class  (compiled bytecode, no test classes)
                ├── Uses: findsecbugs-plugin 1.14.0   (plugin-dep, 144 security patterns)
                ├── Reads: config/spotbugs-exclude.xml (filter file)
                ├── Reads: lombok.config              (Lombok annotation suppression)
                └── Writes: target/spotbugsXml.xml    (findings report)
```

### Recommended Project Structure (new files only)

```
ctc-manager/
├── lombok.config              # NEW — project root, sibling to pom.xml
├── config/
│   └── spotbugs-exclude.xml  # NEW — tooling config directory
├── pom.xml                   # MODIFIED — spotbugs plugin block added at line 355
└── CLAUDE.md                 # MODIFIED — Conventions section addition
```

### Pattern 1: SpotBugs Plugin Block in pom.xml

Declared in main `<build><plugins>` block, directly after the closing `</plugin>` tag of the `jacoco-maven-plugin` block (currently at pom.xml line 355), and before the `exec-maven-plugin` block (line 356).

```xml
<!-- Source: https://spotbugs.github.io/spotbugs-maven-plugin/ -->
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.9.8.3</version>
    <configuration>
        <effort>Max</effort>
        <threshold>Default</threshold>
        <failOnError>true</failOnError>
        <excludeFilterFile>${project.basedir}/config/spotbugs-exclude.xml</excludeFilterFile>
    </configuration>
    <executions>
        <execution>
            <id>spotbugs-check</id>
            <phase>verify</phase>
            <goals>
                <!-- STAT-05/1: start with <goal>spotbugs</goal> (report-only)
                     STAT-05/2: flip to <goal>check</goal> (blocking gate) -->
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
    <dependencies>
        <!-- STAT-03: find-sec-bugs plugin dependency — 144 Spring Security-aware patterns -->
        <dependency>
            <groupId>com.h3xstream.findsecbugs</groupId>
            <artifactId>findsecbugs-plugin</artifactId>
            <version>1.14.0</version>
        </dependency>
    </dependencies>
</plugin>
```

**Note:** The `findsecbugs-plugin` uses `<dependencies>` (plugin-level), not project-level `<dependencies>`. This is SpotBugs' specific detector-pack extension mechanism. [CITED: https://find-sec-bugs.github.io/bugs.htm]

### Pattern 2: lombok.config Content

```
# Source: https://projectlombok.org/features/configuration
config.stopBubbling = true
lombok.addLombokGeneratedAnnotation = true
lombok.extern.findbugs.addSuppressFBWarnings = true
```

`config.stopBubbling = true` is belt-and-braces — marks this as the authoritative root config so Lombok stops looking in parent directories. Required when the file is at the project root (which is the Maven module root). [VERIFIED: projectlombok.org/features/configuration]

### Pattern 3: spotbugs-exclude.xml Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<FindBugsFilter
    xmlns="https://github.com/spotbugs/filter/3.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://github.com/spotbugs/filter/3.0.0
                        https://raw.githubusercontent.com/spotbugs/spotbugs/master/spotbugs/etc/findbugsfilter.xsd">

    <!-- ========== Class-level excludes (D-08 layer 1) ========== -->

    <!-- Spring Boot entry point — no business logic, always excluded from analysis -->
    <Match>
        <Class name="org.ctc.CtcManagerApplication"/>
    </Match>

    <!-- @Profile("dev") test-data provider — E2E tested only, not subject to static analysis gate -->
    <Match>
        <Class name="org.ctc.admin.TestDataService"/>
    </Match>

    <!-- @Profile("dev,demo") demo-data seeder — manual-test-only, not subject to gate -->
    <Match>
        <Class name="org.ctc.admin.DemoDataSeeder"/>
    </Match>

    <!-- ========== Pattern-on-package excludes (D-08 layer 2) ========== -->

    <!-- EI_EXPOSE_REP / EI_EXPOSE_REP2 on all 24 JPA entities:
         Lombok @Getter generates return this.field for @OneToMany collections.
         SpotBugs flags this as exposing internal mutable state. For an OSIV-enabled
         JPA application, returning the Hibernate-proxy list IS the intended behavior —
         a defensive copy would break lazy loading. Belt-and-braces with
         lombok.extern.findbugs.addSuppressFBWarnings=true in lombok.config (which
         emits @SuppressFBWarnings at method level); this filter catches synthetic
         accessor methods that Lombok cannot annotate. See CLAUDE.md Conventions
         section for the lombok.config invariant. -->
    <Match>
        <Package name="org.ctc.domain.model"/>
        <Bug pattern="EI_EXPOSE_REP,EI_EXPOSE_REP2"/>
    </Match>

    <!-- ========== Intentional-pattern suppressions (D-11) ========== -->

    <!-- FileStorageService.storeFromUrl(): SSRF hostname blocklist (validateHostname method,
         lines 125-153) implements a blocklist via if/startsWith chains covering localhost,
         127.x, 10.x, 192.168.x, 169.254.x, and 172.16-31.x ranges.
         find-sec-bugs cannot recognize startsWith-chain blocklists as SSRF sanitizers
         (only allowlist-style sanitizers are recognized). The defense is intentional.
         See FileStorageService.java:87-103 and :125-153 for the full defense implementation. -->
    <Match>
        <Class name="org.ctc.domain.service.FileStorageService"/>
        <Method name="storeFromUrl"/>
        <Bug pattern="SSRF_SPRING,SSRF"/>
    </Match>

    <!-- FileStorageService path-traversal defenses: validatePathWithinUploadDir (line 156-162)
         and validateNoPathTraversal (line 164-169) use toAbsolutePath().normalize().startsWith()
         and contains("..") checks respectively. find-sec-bugs PATH_TRAVERSAL_IN detects the
         unresolved path usage at the call sites before the validation call — it cannot trace
         the defense through separate private methods. The defense is intentional and unit-tested.
         See FileStorageService.java:156-169 for the full defense implementation. -->
    <Match>
        <Class name="org.ctc.domain.service.FileStorageService"/>
        <Bug pattern="PATH_TRAVERSAL_IN"/>
    </Match>

    <!-- BackupArchiveService path-traversal defenses: assertEntrySafe() (line 608-623) delegates
         to PathTraversalGuard.assertWithin() which calls toAbsolutePath().normalize().startsWith().
         find-sec-bugs cannot trace the defense through the delegated utility class.
         See BackupArchiveService.java:608-623 and PathTraversalGuard.java:55-78. -->
    <Match>
        <Class name="org.ctc.backup.service.BackupArchiveService"/>
        <Bug pattern="PATH_TRAVERSAL_IN"/>
    </Match>

</FindBugsFilter>
```

### Anti-Patterns to Avoid

- **Adding `<argLine>` to the SpotBugs plugin block:** SpotBugs is a bytecode scanner, not a JVM agent. Any `<argLine>` would overwrite JaCoCo's late-evaluated `@{argLine}` and drop coverage to 0% (Pitfall #7). SpotBugs uses `<jvmArgs>` if JVM flags are needed (they are not for this phase).
- **Adding SpotBugs inside a Maven `<profile>`:** This would make it developer-opt-in only, defeating STAT-02. The verify-bound, always-on gate is the explicit goal.
- **Using `<goal>check</goal>` before Lombok mitigation is in place:** The gate would immediately fail on 40-80 `EI_EXPOSE_REP*` findings from Lombok-generated getters. `lombok.config` and `spotbugs-exclude.xml` must be committed FIRST.
- **Using `@SuppressWarnings("all")` on a class:** Suppresses real future bugs. Use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"})` with a reason string, or `config/spotbugs-exclude.xml` `<Match>` entries with rationale comments.

---

## Research Findings

### F-01: `spotbugs:check` Goal Invokes `spotbugs:spotbugs` First

**Finding:** The `spotbugs:check` goal invokes the `spotbugs` goal first (analysis + `target/spotbugsXml.xml` generation), then reads the XML output to evaluate the threshold and fail on violations. [CITED: https://spotbugs.github.io/spotbugs-maven-plugin/check-mojo.html]

**Impact on D-12 choreography:** Both `<goal>spotbugs</goal>` (report-only) and `<goal>check</goal>` (blocking) produce the same `target/spotbugsXml.xml` report content. The gate-flip commit (STAT-05/2) changes only one XML element. The choreography is sound.

**Sequencing concern for D-12 step 2 (baseline inspection):** After the plumbing commit, use `./mvnw spotbugs:spotbugs -DskipTests` (or `./mvnw verify` with the report-only goal) to generate the baseline report without executing the full test suite on every triage iteration. Between triage commits use `./mvnw spotbugs:spotbugs -DskipTests` for fast feedback. Only the final gate-flip commit needs a full `./mvnw verify -Pe2e`.

### F-02: ZIP Handling — PATH_TRAVERSAL Defense Is in BackupArchiveService, Not BackupImportService

**Finding from live code inspection:** The CONTEXT.md D-11 lists `PATH_TRAVERSAL_IN` on `BackupImportService.restoreOneTable`. The live code shows `restoreOneTable` uses `ZipFile.getEntry(entryPath)` with a pre-known entry name from `EntityRef.fileName()` — no user-controlled entry name processing occurs there. The actual ZIP path-traversal defense lives in `BackupArchiveService.assertEntrySafe()` (lines 608-623), which delegates to `PathTraversalGuard.assertWithin()`. find-sec-bugs will flag the `ZipInputStream.getNextEntry()` → `entry.getName()` taint source in `BackupArchiveService`'s read methods, where the `assertEntrySafe()` call cannot be recognized as a sanitizer.

**Revised suppression target:** `BackupArchiveService` (not `BackupImportService`) for `PATH_TRAVERSAL_IN`.

**Additional note:** `BackupImportService.restoreAll()` uses `new ZipFile(staged.toFile())` — the `ZipFile(File)` constructor uses the platform's default charset for entry names. On any modern JVM running with `file.encoding=UTF-8` (which Java 25 enforces by default), this is safe. However, find-sec-bugs may not flag this specific pattern, so it is low-priority to monitor.

### F-03: SSRF Defense Pattern — IP Range Checks via `startsWith`, Not `Set.contains`

**Finding from live code inspection:** `FileStorageService.validateHostname()` (lines 125-153) uses a series of `hostname.startsWith("127.")`, `hostname.startsWith("10.")`, etc. checks plus `"localhost".equals(hostname)` and a 172.16-31 range numeric parse. This is a blocklist, not a blocklist of `Set.contains` as CONTEXT.md describes. The defensive pattern is still unrecognized by find-sec-bugs — the finding will fire on the `URI.create(sourceUrl).toURL().openStream()` call at line 98, because the sanitizer (validateHostname at line 91) is in a separate private method and find-sec-bugs cannot trace through it.

**Suppression scope:** `SSRF_SPRING` and `SSRF` on `FileStorageService.storeFromUrl` method. The `SSRF_SPRING` variant is the Spring-aware pattern from find-sec-bugs; `SSRF` is the base SpotBugs pattern. List both to cover either firing.

### F-04: Graphic Services — 17 Files, Not 11

**Finding from live code listing:** The `src/main/java/org/ctc/admin/service/` directory contains 17 graphic-related files (including `Abstract*` parent classes not listed in D-08/D-11 CONTEXT.md):
- `AbstractGraphicService.java`
- `AbstractMatchdayGraphicService.java`
- `AbstractPlayoffRoundGraphicService.java`
- `LineupGraphicService.java`
- `MatchResultsGraphicService.java`
- `MatchdayOverviewGraphicService.java`
- `MatchdayResultsGraphicService.java`
- `MatchdayScheduleGraphicService.java`
- `OverlayGraphicService.java`
- `PlayoffRoundOverviewGraphicService.java`
- `PlayoffRoundResultsGraphicService.java`
- `PlayoffRoundResultsGraphicService.java`
- `PowerRankingsGraphicService.java`
- `RaceGraphicService.java`
- `ResultsGraphicService.java`
- `SettingsGraphicService.java`
- `TeamCardService.java`

D-08 layer 3 explicitly preserves all of these as NOT class-excluded. The additional abstract classes (`AbstractMatchdayGraphicService`, `AbstractPlayoffRoundGraphicService`) may also fire findings. Triage them per D-10 on a per-finding basis, not with a blanket class-level exclusion.

**JaCoCo excludes reference:** The JaCoCo `<excludes>` list in pom.xml (lines 308-323) covers 11 graphic services (excluding the newer `AbstractMatchdayGraphicService`, `AbstractPlayoffRoundGraphicService`, `MatchdayOverviewGraphicService`, `MatchdayResultsGraphicService`, `MatchdayScheduleGraphicService`, `RaceGraphicService` which are not in the JaCoCo exclusion list). Per D-08, SpotBugs exclusions deliberately diverge from JaCoCo exclusions.

### F-05: No Existing SuppressFBWarnings or SuppressWarnings Annotations in Source

**Finding:** `grep -rn "SuppressFBWarnings\|SuppressWarnings\|spotbugs\|findbugs"` across all main source files returns zero results. The codebase has no pre-existing SpotBugs suppressions. The Phase 81 `lombok.config` and `config/spotbugs-exclude.xml` are truly net-new.

### F-06: No Existing `lombok.config` or `config/` Directory

**Finding:** `ls lombok.config config/ 2>/dev/null` returns "Neither lombok.config nor config/ exists". Confirmed net-new files.

### F-07: pom.xml Insertion Point Confirmed at Line 355

**Finding:** `grep -n "jacoco\|exec-maven" pom.xml` confirms:
- JaCoCo block starts at line 304 (`<groupId>org.jacoco`), closes at line 355 (`</plugin>`)
- exec-maven-plugin (template-fragment-call-guard) starts at line 356
- SpotBugs block inserts at line 355/356 (between the two closing/opening tags)
- pom.xml currently ends at line 459

CONTEXT.md's line numbers (303-355 JaCoCo, 356-384 exec-maven) are accurate.

### F-08: `argLine` in pom.xml — Three Occurrences, All Using `@{argLine}` Late-Evaluation

**Finding:** pom.xml has `@{argLine}` at lines 258, 289, and 405:
- Line 258: Surefire `<argLine>@{argLine} --sun-misc-unsafe-memory-access=allow -javaagent:...`
- Line 289: Failsafe default-it execution same pattern
- Line 405: Failsafe e2e-it execution same pattern

All three are safe. No plugin in the current pom.xml uses a plain `<argLine>` string. D-04 must ensure SpotBugs does not become the first plain-argLine offender.

### F-09: `ZipFile(File)` Charset — Low Risk on Java 25

**Finding:** `BackupImportService.restoreAll()` uses `new ZipFile(staged.toFile())`. The `ZipFile(File)` constructor uses platform default charset for ZIP entry names. On Java 25 with `-Dfile.encoding=UTF-8` (the Java 25 default), this is fine for the backup ZIPs which are written by the same application using `java.util.zip.ZipOutputStream`. If find-sec-bugs fires `DM_DEFAULT_ENCODING` on this constructor, suppress with rationale: "backup ZIPs are produced by the same JVM; entry names are ASCII-only entity table names; platform encoding is UTF-8 on all production JVMs (Java 25 default)."

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Bytecode null-deref / resource-leak detection | Custom AST visitor | `spotbugs-maven-plugin` + SpotBugs core | SpotBugs interprocedural analysis handles complex cases including exception paths and Hibernate proxy patterns |
| Security pattern scanning (SSRF, XXE, SQL injection) | Custom grep / regex | `findsecbugs-plugin` 1.14.0 | 144 Spring Security-aware patterns maintained by security researchers; handles framework-specific patterns (Spring `@RestController`, `JdbcTemplate`, etc.) |
| Lombok false-positive suppression | Per-method `@SuppressFBWarnings` on every entity | `lombok.extern.findbugs.addSuppressFBWarnings=true` in `lombok.config` | One-line config covers all Lombok-generated methods across all 24 entities automatically |
| Exclusion-filter XML | Inline `@SuppressFBWarnings` in source | `config/spotbugs-exclude.xml` with rationale comments | Keeps suppression rationale auditable in one place; source code stays clean |

---

## Baseline Violation Surface Estimation

The actual count cannot be determined without running SpotBugs. Based on codebase inspection:

### High-Confidence Findings (will fire, pre-staged suppressions cover them)

| Pattern | Source | Confidence | Coverage |
|---------|--------|------------|---------|
| `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` | 24 JPA entities × ~2 collection fields each | HIGH — 40-80 findings | Covered by D-08 layer 2 + lombok.config |
| `SSRF` / `SSRF_SPRING` | `FileStorageService.storeFromUrl` (line 98) | HIGH — 1-2 findings | Covered by D-11 pre-staged suppression |
| `PATH_TRAVERSAL_IN` | `BackupArchiveService.assertEntrySafe` call sites | HIGH — 3-5 findings (multiple `ZipInputStream.getNextEntry()` call sites) | Covered by F-02 revised suppression |
| `PATH_TRAVERSAL_IN` | `FileStorageService.validatePathWithinUploadDir` call sites | HIGH — 3-4 findings | Covered by revised D-11 suppression |

### Medium-Confidence Findings (may fire, triage required)

| Pattern | Likely Source | Confidence | Action |
|---------|---------------|------------|--------|
| `OS_OPEN_STREAM` | `BackupArchiveService.openHardened()` returning `ZipInputStream` to caller | MEDIUM | Inspect actual finding; service uses try-with-resources at call sites, may not fire |
| `DMI_RANDOM_USED_ONLY_ONCE` | None found — no `new Random()` in codebase | LOW — unlikely to fire | — |
| `RR_NOT_CHECKED` | Return value of `Files.deleteIfExists()` in `tryDeletePartialAutoBackup`, `tryCleanupUploadsNew`, `deleteStagingFile` | MEDIUM | These are best-effort cleanup methods; suppress with rationale "intentional best-effort cleanup — failure is logged" |
| `DM_DEFAULT_ENCODING` | `new ZipFile(staged.toFile())` in `BackupImportService` | LOW-MEDIUM | See F-09; suppress with charset rationale |
| `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` | `file.getOriginalFilename()` calls without null-check before use | MEDIUM | Inspect actual findings; some call sites are null-checked, others pass to sanitize() which handles null |
| `SE_BAD_FIELD` | Non-serializable fields in `@Transactional` service classes | LOW | Spring services are not serialized in this project |
| `HARD_CODE_PASSWORD` | Spring Security YAML properties (if `@Value("${spring.security.user.password}")` is present) | LOW — not found in source scan | Only relevant if found in report |
| Graphic service patterns | String-building HTML in `AbstractGraphicService` (for Playwright HTML emit) | MEDIUM | Playwright HTML construction may trigger `XSS_REQUEST_PARAMETER_TO_SEND_ERROR` or `DM_DEFAULT_ENCODING` if charset not explicit |

### Low-Confidence Findings (unlikely, monitor)

| Pattern | Why Unlikely |
|---------|-------------|
| `SQL_INJECTION` | All database access via Spring Data JPA / `JdbcTemplate.batchUpdate()` with parameterized arrays; no string concatenation in queries except `wipeAllTables()` which uses `SAFE_TABLE_NAME` pattern guard |
| `PREDICTABLE_RANDOM` | No `new Random()` or `Math.random()` in source — only `UUID.randomUUID()` (cryptographically secure) |
| `XXE` | No `DocumentBuilder` or `SAXParser` usage found |
| `UNVALIDATED_REDIRECT` | All redirects are hard-coded string constants in controllers |

### Budget Estimate

- **Pre-staged suppressions cover:** ~50-90 findings (EI_EXPOSE_REP* dominate)
- **Triage window findings (triage required):** Estimate 5-15 Medium findings beyond pre-staged suppressions
- **Real bugs requiring code fixes:** Expect 0-3 (codebase is mature and well-reviewed; most likely candidates are best-effort cleanup `deleteIfExists` unchecked return values and `getOriginalFilename()` null paths)
- **Unknown findings from graphic services:** Highly variable; these 17 files are Playwright-heavy and may have encoding/resource patterns SpotBugs is sensitive to

---

## Common Pitfalls

### Pitfall A: Adding argLine to SpotBugs Plugin Block (Pitfall #7)
**What goes wrong:** JaCoCo coverage drops to 0% because `@{argLine}` late-evaluation is overwritten.
**Why it happens:** Developer copies JaCoCo plugin structure which has `<argLine>`.
**How to avoid:** SpotBugs requires NO `<argLine>`. Use `<jvmArgs>` if JVM tuning is ever needed.
**Warning signs:** `target/jacoco.exec` is 0 bytes; `target/site/jacoco/index.html` shows 0% coverage after SpotBugs wiring.

### Pitfall B: Wrong Phase for D-12 Step 3 Fast Feedback
**What goes wrong:** Running `./mvnw verify` for every triage commit takes 90-120 seconds per iteration; triage becomes slow.
**How to avoid:** Use `./mvnw spotbugs:spotbugs -DskipTests` between triage commits (skips compilation and all tests, re-runs SpotBugs analysis only, produces updated `target/spotbugsXml.xml`). Reserve `./mvnw verify` for post-wave checks. Final gate is `./mvnw verify -Pe2e`.

### Pitfall C: Gate-Flip Before Zero-Finding Baseline
**What goes wrong:** Flipping to `<goal>check</goal>` while Medium findings remain fails the build on `./mvnw verify`.
**How to avoid:** D-12 step 3 mandate: `./mvnw spotbugs:spotbugs -DskipTests` must produce ZERO Medium+HIGH findings before the gate-flip commit is created.

### Pitfall D: BCrypt Suppression Entry in spotbugs-exclude.xml (Research Correction C-01)
**What goes wrong:** Pre-staging a `HARD_CODE_PASSWORD` suppression for `SecurityConfig.passwordEncoder` would be dead code (method does not exist). Could mask a real future finding if someone adds a password encoder bean.
**How to avoid:** Do NOT include the HARD_CODE_PASSWORD suppression entry. SecurityConfig uses `httpBasic` with no explicit `PasswordEncoder` bean.

### Pitfall E: PATH_TRAVERSAL Suppression on BackupImportService Instead of BackupArchiveService (Research Finding F-02)
**What goes wrong:** CONTEXT.md D-11 cites `BackupImportService.restoreOneTable` as the suppression target. The actual path traversal defense/detection is in `BackupArchiveService.assertEntrySafe()`.
**How to avoid:** Pre-stage the suppression on `BackupArchiveService`, not `BackupImportService`.

### Pitfall F: Deliberate Violation in src/test/java Doesn't Trigger the Gate
**What goes wrong:** SpotBugs only scans `${project.build.outputDirectory}` (main classes) by default. D-05 does NOT set `<includeTests>true</includeTests>`.
**How to avoid:** D-13 discretion resolution — the deliberate-violation class for STAT-06 validation MUST go in `src/main/java/` (e.g., `org/ctc/_validation_marker/ViolationMarker.java`). A class in `src/test/java/` will NOT be scanned and the gate will not fail.

---

## Two-Commit Choreography Validation

**D-12 is sound** — confirmed by F-01 finding. Detailed sequencing:

**Commit 1 — Plumbing (STAT-05/1):**
- Files: `lombok.config`, `config/spotbugs-exclude.xml`, `pom.xml` (plugin block with `<goal>spotbugs</goal>`)
- Verification: `./mvnw verify` succeeds (report-only, no build failure even if findings exist)
- Output: `target/spotbugsXml.xml` populated

**Between commits 1 and 2 (no commit):**
- Run: `./mvnw verify` once to generate baseline
- Read: `target/spotbugsXml.xml` and `target/site/spotbugs.html`
- Triage: Apply D-10 decision tree to every finding
- Document: Triage table in `81-VERIFICATION.md`

**Triage commits (N commits, one per pattern-family or file):**
- Run between commits: `./mvnw spotbugs:spotbugs -DskipTests` (fast, no test re-run)
- Continue until: ZERO Medium+HIGH findings in report

**Commit 2 — Gate-flip (STAT-05/2):**
- Files: `pom.xml` single-line change `<goal>spotbugs</goal>` → `<goal>check</goal>`
- Verification: `./mvnw verify` succeeds (zero findings, gate passes)
- Followed by: `./mvnw verify -Pe2e` (final phase gate per STAT-06 + CLAUDE.md e2e obligation)

**Sequencing concern: `spotbugs:spotbugs` goal binding in Commit 1**

The `spotbugs:spotbugs` goal, when declared in a `<plugin><executions>` block bound to the `verify` phase, runs SpotBugs analysis AND generates `target/spotbugsXml.xml` AND `target/site/spotbugs.html`. It does NOT fail the build on findings (unlike `check`). The `check` goal invokes `spotbugs` first, then evaluates. Both goals produce identical report content. The plan is correct.

---

## Validation Architecture

**Note:** nyquist_validation is enabled (`.planning/config.json` has no explicit `false` for `workflow.nyquist_validation`).

This phase has no unit-testable behavior in the traditional sense — it is a build-configuration change. Validation is observable through build behavior, not test assertions.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (inherited) — no new test framework |
| Config file | `pom.xml` (existing Surefire/Failsafe/JaCoCo config) |
| Quick analysis command | `./mvnw spotbugs:spotbugs -DskipTests` |
| Full suite command | `./mvnw verify -Pe2e` |

### Phase Requirements → Validation Map

| Req ID | Behavior | Validation Type | Observable Assertion |
|--------|----------|-----------------|----------------------|
| STAT-01 | `lombok.config` exists with correct keys | File inspection | `cat lombok.config` shows both Lombok keys |
| STAT-02 | SpotBugs runs on `./mvnw verify` | Build behavior | `target/spotbugsXml.xml` exists after `./mvnw verify`; JaCoCo coverage still >= 82% |
| STAT-03 | find-sec-bugs patterns active | Build log inspection | `./mvnw spotbugs:spotbugs -DskipTests -X` log shows `findsecbugs-plugin` loaded |
| STAT-04 | `config/spotbugs-exclude.xml` covers intentional patterns | File inspection + build behavior | SSRF/PATH_TRAVERSAL suppressions in file; `./mvnw spotbugs:spotbugs -DskipTests` shows zero findings for pre-staged patterns |
| STAT-05 | Two-commit choreography executed | Git log | `git log --oneline` shows plumbing commit, N triage commits, gate-flip commit |
| STAT-06 | Gate blocks on HIGH violation | Throwaway-branch build failure | `./mvnw verify` exit code 1 with SpotBugs failure message on throwaway branch |
| STAT-07 | CLAUDE.md updated | File inspection | `grep -A 10 "CSS Guidelines" CLAUDE.md` shows new SpotBugs paragraph |

### Sampling Rate

- **Per triage commit:** `./mvnw spotbugs:spotbugs -DskipTests` (fast SpotBugs only, no test re-run)
- **After gate-flip commit:** `./mvnw verify` (full build without E2E)
- **Phase gate:** `./mvnw verify -Pe2e` (one final run with E2E tests)

### Wave 0 Gaps

None — no new test files are required. The deliberate-violation STAT-06 test is on a throwaway branch that is discarded before PR merge. The validation is fully observable through build behavior.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Phase does not touch auth logic |
| V3 Session Management | No | Phase does not touch session logic |
| V4 Access Control | No | Phase does not touch access control |
| V5 Input Validation | Yes (indirectly) | SpotBugs + find-sec-bugs gates enforce that new code does not introduce unvalidated path traversal, SSRF, or injection |
| V6 Cryptography | No | No cryptographic operations added |

### Known Threat Patterns for SpotBugs + find-sec-bugs

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SSRF (Server-Side Request Forgery) | Spoofing / Information Disclosure | Hostname blocklist (existing) + SSRF suppression with rationale |
| PATH_TRAVERSAL_IN | Tampering / Elevation | `startsWith(canonicalBase)` defense (existing) + suppression with rationale |
| ZIP-Slip | Tampering | `PathTraversalGuard.assertWithin()` (existing) + suppression with rationale |
| EI_EXPOSE_REP (mutable collection exposure) | Tampering | Hibernate proxy pattern is intentional; Lombok config suppression |

---

## Environment Availability

No external dependencies beyond the project's own build tools.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 JDK | SpotBugs bytecode analysis | ✓ | 25.0.2 (Homebrew) | — |
| Maven 3.9+ | Plugin execution | ✓ | 3.9.9 (mvn) | `./mvnw` wrapper 3.9.14 |
| `spotbugs-maven-plugin:4.9.8.3` | STAT-02 | ✓ | 4.9.8.3 (cached in `~/.m2`) | — |
| `findsecbugs-plugin:1.14.0` | STAT-03 | ✓ | 1.14.0 (cached in `~/.m2`) | — |

**Missing dependencies with no fallback:** None.

---

## Open Questions

1. **Graphic service Medium findings — volume unknown**
   - What we know: 17 graphic service files use Playwright HTML/CSS string construction; may trigger `DM_DEFAULT_ENCODING` or `XSS` patterns
   - What's unclear: Whether find-sec-bugs `XSS_REQUEST_PARAMETER_TO_SEND_ERROR` fires on Playwright Page.setContent() calls; whether AbstractGraphicService HTML emission patterns are flagged
   - Recommendation: Let baseline report determine; budget 3-8 additional triage entries for graphic services

2. **`ZipFile(File)` charset finding severity**
   - What we know: `new ZipFile(staged.toFile())` in `BackupImportService` may trigger `DM_DEFAULT_ENCODING`; safe on Java 25 UTF-8 default
   - What's unclear: Whether find-sec-bugs or SpotBugs actually fires on this specific pattern
   - Recommendation: If it fires, suppress with the F-09 rationale

3. **`Files.deleteIfExists()` return value findings**
   - What we know: `tryDeletePartialAutoBackup`, `tryCleanupUploadsNew`, `deleteStagingFile` ignore the boolean return of `Files.deleteIfExists()`; SpotBugs `RR_NOT_CHECKED` may fire
   - What's unclear: Whether SpotBugs 4.9.8.3 specifically fires on `Files.deleteIfExists()` ignoring boolean (vs. `File.delete()` which is more commonly flagged)
   - Recommendation: If it fires, suppress with rationale "intentional best-effort cleanup; delete failure is already caught by IOException handler / logged at WARN"

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| No static analysis gate (Phase 80 and prior) | SpotBugs `verify`-phase gate with find-sec-bugs | Phase 81 | Every `./mvnw verify` now includes bytecode analysis |
| Manual code review for null deref / resource leaks | SpotBugs automated detection | Phase 81 | Automated catch of structural bugs |
| No Lombok false-positive mitigation | `lombok.config` with `addSuppressFBWarnings=true` + XML filter | Phase 81 | Clean gate without noise |

**Deprecated/outdated:**
- `spotbugs-maven-plugin` 4.7.x: Old versions had known Lombok false-positive issues (#3471 in SpotBugs issue tracker). 4.9.8.3 is the current stable; use it.
- `findsecbugs-plugin` 1.12.x: Missing Jakarta EE annotation support (added in 1.13.0). Use 1.14.0 for Spring Boot 4 / Jakarta namespaces.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `spotbugs:check` failing the build with `<threshold>Default</threshold>` blocks on Medium+HIGH findings | Standard Stack / D-06 | Gate could block only on HIGH (if Default means HIGH), requiring threshold adjustment |
| A2 | Graphic service files do not use `System.out`, `e.printStackTrace()`, or non-UTF8 byte operations | Baseline Violation Estimation | Additional suppressions needed for graphic services |
| A3 | `BackupImportService.restoreOneTable` will NOT trigger `PATH_TRAVERSAL_IN` because it uses `ZipFile.getEntry()` with a controlled key (not `ZipInputStream.getNextEntry().getName()`) | Finding F-02 | If find-sec-bugs traces through `ZipFile.getInputStream()`, additional suppression needed on `BackupImportService` |
| A4 | No `@Value("-injected property that looks like a password)` exists in the codebase beyond what was found | C-01 / SecurityConfig inspection | A BCrypt-related `@Value` field somewhere else could trigger `HARD_CODE_PASSWORD` |

---

## Sources

### Primary (HIGH confidence)
- Maven Central local cache — `spotbugs-maven-plugin` 4.9.8.3 metadata (`<release>`, `<latest>`)
- Maven Central local cache — `findsecbugs-plugin` 1.14.0 metadata (`<release>`, `<latest>`)
- https://github.com/spotbugs/spotbugs-maven-plugin/releases — GitHub releases listing (4.9.8.3 as latest, 2025-03-29)
- https://github.com/find-sec-bugs/find-sec-bugs/releases — GitHub releases listing (1.14.0 as latest, 2024-06-17)
- https://projectlombok.org/features/configuration — Lombok config key verification for `lombok.addLombokGeneratedAnnotation` and `lombok.extern.findbugs.addSuppressFBWarnings`
- Live file reads: `pom.xml`, `FileStorageService.java`, `BackupImportService.java`, `BackupArchiveService.java`, `SecurityConfig.java`, `OpenSecurityConfig.java`, `PathTraversalGuard.java`
- https://spotbugs.github.io/spotbugs-maven-plugin/check-mojo.html — `check` goal invokes `spotbugs` goal first

### Secondary (MEDIUM confidence)
- `.planning/research/PITFALLS.md` — Pitfalls 4, 7, 12, 13, 14 (pre-existing v1.11 milestone research)
- `.planning/research/SUMMARY.md` — Stream 2 SpotBugs strategy, Conflict 2 scope decision

### Tertiary (LOW confidence — derived from codebase pattern inspection)
- Baseline violation surface estimates (Section "Baseline Violation Surface Estimation") — cannot be confirmed without running SpotBugs; estimates based on pattern inspection

---

## Metadata

**Confidence breakdown:**
- Plugin versions: HIGH — verified against Maven Central metadata and GitHub releases
- Lombok config syntax: HIGH — verified against official Lombok docs
- pom.xml insertion point: HIGH — verified by reading live pom.xml
- Suppression targets: HIGH for SSRF/PATH_TRAVERSAL (live source read); CORRECTED for BCrypt (SecurityConfig has no passwordEncoder bean)
- Baseline violation estimate: MEDIUM — pattern inspection without SpotBugs execution
- Two-commit choreography: HIGH — `check` goal behavior verified against official goal docs

**Research date:** 2026-05-16
**Valid until:** 60 days (SpotBugs and find-sec-bugs have stable, infrequent release cadences)
