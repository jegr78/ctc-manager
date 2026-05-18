---
phase: "81-static-analysis-gate-spotbugs-find-sec-bugs"
plan: "01"
subsystem: "build-config"
tags:
  - spotbugs
  - find-sec-bugs
  - static-analysis
  - lombok-config
  - maven-plugin

dependency_graph:
  requires:
    - "phase-80 (pom.xml baseline after OpenRewrite integration)"
  provides:
    - "spotbugs-maven-plugin 4.9.8.3 wired in report-only mode"
    - "config/spotbugs-exclude.xml with 7 <Match> entries"
    - "lombok.config with addSuppressFBWarnings + addLombokGeneratedAnnotation"
    - "spotbugs-annotations 4.9.8 provided dependency"
    - "target/spotbugsXml.xml baseline report (220 findings pre-triage)"
  affects:
    - "pom.xml (plugin insertion + provided dependency)"
    - "every Maven verify run (SpotBugs now executes report-only)"

tech_stack:
  added:
    - "spotbugs-maven-plugin 4.9.8.3"
    - "findsecbugs-plugin 1.14.0 (plugin-level dependency)"
    - "spotbugs-annotations 4.9.8 (provided compile scope)"
  patterns:
    - "Lombok config file (lombok.config) at project root"
    - "SpotBugs FindBugsFilter XML (config/spotbugs-exclude.xml)"
    - "Plugin-level detector-pack extension (findsecbugs via <dependencies>)"

key_files:
  created:
    - "lombok.config"
    - "config/spotbugs-exclude.xml"
    - ".planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md"
  modified:
    - "pom.xml"

decisions:
  - "spotbugs-annotations:4.9.8 added as provided dep — required for lombok.extern.findbugs.addSuppressFBWarnings compile-time annotation emission (Rule 3 auto-fix)"
  - "D-08 layer 2 covers org.ctc.domain.model.* only; org.ctc.admin.dto.* EI_EXPOSE_REP findings (220 total) remain for PLAN 02 triage"
  - "goal=spotbugs (report-only) confirmed — no build failure even with 220 findings"
  - "No <argLine> in SpotBugs block — Pitfall #7 safe; @{argLine} count remains 3"

metrics:
  duration: "~15 minutes (compile fix + verify run)"
  completed: "2026-05-16"
  tasks_completed: 4
  tasks_total: 4
  files_modified: 4
---

# Phase 81 Plan 01: Wire SpotBugs Report-Only Baseline Summary

**One-liner:** SpotBugs 4.9.8.3 + find-sec-bugs 1.14.0 wired report-only into main build with lombok.config false-positive mitigation and 7-entry spotbugs-exclude.xml, JaCoCo at 88.04%.

## What Was Built

Four files changed in two atomic commits to implement the STAT-05/1 plumbing:

1. **`lombok.config`** — Three Lombok APT directives at project root:
   - `config.stopBubbling = true` (authoritative config for whole subtree)
   - `lombok.addLombokGeneratedAnnotation = true` (JaCoCo respects @lombok.Generated)
   - `lombok.extern.findbugs.addSuppressFBWarnings = true` (emits @SuppressFBWarnings on generated methods)

2. **`config/spotbugs-exclude.xml`** — SpotBugs FindBugsFilter with 7 `<Match>` entries:
   - Layer 1 (3): Class-level excludes for `CtcManagerApplication`, `TestDataService`, `DemoDataSeeder`
   - Layer 2 (1): `EI_EXPOSE_REP,EI_EXPOSE_REP2` on `org.ctc.domain.model` package
   - D-11 revised (3): SSRF on `FileStorageService.storeFromUrl`, PATH_TRAVERSAL_IN on `FileStorageService`, PATH_TRAVERSAL_IN on `BackupArchiveService`
   - All entries have XML rationale comments with file:line cross-references (D-09 mandate)
   - NO HARD_CODE_PASSWORD, NO SecurityConfig, NO BackupImportService entries (RESEARCH.md C-01 + F-02 corrections applied)

3. **`pom.xml`** — Two changes:
   - `spotbugs-maven-plugin 4.9.8.3` block inserted between jacoco and exec-maven-plugin in main `<build><plugins>` (not in a profile), with `goal=spotbugs` (report-only), `effort=Max`, `threshold=Default`, `failOnError=true`, no `<argLine>` (Pitfall #7)
   - `spotbugs-annotations 4.9.8` added as provided dependency (Rule 3 auto-fix — required for Lombok to emit @SuppressFBWarnings at compile time)

4. **`81-VERIFICATION.md`** — Initialized with STAT-05/1 plumbing evidence and placeholder sections for PLAN 02 + 03.

## Commits

| Hash | Type | Message |
|------|------|---------|
| `1f020a23` | feat | `feat(81): wire SpotBugs report-only baseline (STAT-01..STAT-04, STAT-05/1)` |
| `b486b335` | docs | `docs(81): initialize 81-VERIFICATION.md with plumbing-commit evidence` |

## Wave 0 Gate Results

| Gate | Command | Result |
|------|---------|--------|
| SpotBugs runs | `./mvnw spotbugs:spotbugs -DskipTests` | PASS (exit 0) |
| `spotbugsXml.xml` exists | `test -s target/spotbugsXml.xml` | PASS (664,906 bytes) |
| `./mvnw verify` exits 0 | report-only goal | PASS |
| JaCoCo coverage >= 82% | jacoco.csv parse | PASS (88.04%, +0.24pp vs v1.10 baseline) |
| No `<argLine>` in SpotBugs block | grep check | PASS (0 occurrences) |
| `@{argLine}` count = 3 | grep -c | PASS (3, unchanged) |

## SpotBugs Baseline

- **Plugin version:** 4.9.8.3
- **findsecbugs-plugin version:** 1.14.0
- **Plumbing commit SHA:** `1f020a232bce99d1f16d91b1f0714cad7400eda7`
- **JaCoCo line coverage:** 88.04%
- **`target/spotbugsXml.xml` size:** 664,906 bytes
- **`<BugInstance>` count (post-filter):** 220
- **No `<argLine>` in SpotBugs block:** confirmed

The 220 remaining findings are the input for PLAN 02 triage. The pre-staged D-08 layer 2
exclusion covers `org.ctc.domain.model.*` entities. The majority of remaining findings are
`EI_EXPOSE_REP*` on `org.ctc.admin.dto.*` record classes and `org.ctc.admin.controller.*`
inner record classes — these are candidates for package-pattern suppression in PLAN 02.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added spotbugs-annotations provided dependency**

- **Found during:** Task 3 / Task 4 (compile phase)
- **Issue:** `lombok.extern.findbugs.addSuppressFBWarnings = true` in `lombok.config` instructs
  Lombok to emit `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings` on every generated method.
  This annotation class is in the `com.github.spotbugs:spotbugs-annotations` JAR. Without it on
  the javac classpath, all entity classes fail to compile:
  `PlayoffMatchup.java:[43,14] error: package edu.umd.cs.findbugs.annotations does not exist`
- **Fix:** Added `com.github.spotbugs:spotbugs-annotations:4.9.8` with `<scope>provided</scope>`
  to `pom.xml` project dependencies. This is compile-only and not packaged in the final JAR.
  Version 4.9.8 matches the spotbugs-annotations artifact already present in the local Maven
  cache (the minor version does not need to match the plugin patch version exactly).
- **Files modified:** `pom.xml` (single dependency entry added)
- **Commit:** `1f020a23` (included in the same plumbing commit as the other changes)

**2. [Rule 3 - Observation] 220 findings vs estimated 50-90**

- The RESEARCH.md baseline estimate was 50-90 findings (dominated by EI_EXPOSE_REP* on 24 JPA
  entities). The actual count is 220 because the D-08 layer 2 package filter covers only
  `org.ctc.domain.model.*`. The `org.ctc.admin.dto.*` package contains Java record classes
  (graphic data DTOs) with `List<>` fields, and `org.ctc.admin.controller.*` has inner record
  classes — both generate `EI_EXPOSE_REP*` findings not covered by the pre-staged filter.
- **No action taken in PLAN 01** (this is report-only mode; PLAN 02 triages all findings).
- **Impact:** PLAN 02 triage scope is larger than estimated. The additional findings are almost
  certainly all `EI_EXPOSE_REP*` false positives on DTO record accessors — a package-pattern
  extension to the `spotbugs-exclude.xml` in PLAN 02 is the expected fix.

## Forbidden Strings Confirmation

The following forbidden strings are absent from `config/spotbugs-exclude.xml`:
- `HARD_CODE_PASSWORD` — absent (RESEARCH.md C-01 correction applied)
- `SecurityConfig` — absent
- `passwordEncoder` — absent
- `BackupImportService` — absent (F-02 correction: PATH_TRAVERSAL_IN targets BackupArchiveService)

## Known Stubs

None. This plan creates build configuration files only; no UI rendering or data flow stubs exist.

## Threat Flags

None. No new network endpoints, auth paths, file access patterns, or schema changes introduced.
The SpotBugs plugin runs at build time only; `spotbugs-annotations` is provided-scope and absent
from the runtime JAR.

## Self-Check: PASSED

Files verified:
- `lombok.config` exists: FOUND
- `config/spotbugs-exclude.xml` exists: FOUND
- `.planning/phases/81-static-analysis-gate-spotbugs-find-sec-bugs/81-VERIFICATION.md` exists: FOUND
- `pom.xml` contains `spotbugs-maven-plugin`: FOUND
- `target/spotbugsXml.xml` exists after verify: FOUND

Commits verified:
- `1f020a23` feat plumbing commit: FOUND
- `b486b335` docs verification commit: FOUND
