---
phase: 81
type: verification
created: 2026-05-16
---

# Phase 81: Static Analysis Gate — Verification Evidence

## STAT-05/1 Plumbing Commit Evidence

**Date:** 2026-05-16
**Branch:** worktree-agent-a1d32f1510dd20e7d (phase 81 Wave 1 executor branch; merged onto gsd/v1.11-tooling-and-cleanup by orchestrator)
**Commit SHA:** 1f020a232bce99d1f16d91b1f0714cad7400eda7
**Commit message:** `feat(81): wire SpotBugs report-only baseline (STAT-01..STAT-04, STAT-05/1)`

### New Files Committed

| File | Status | Purpose |
|------|--------|---------|
| `lombok.config` | created | Lombok APT directives — suppresses SpotBugs false positives on generated code |
| `config/spotbugs-exclude.xml` | created | SpotBugs FindBugsFilter — 3 class-level + 1 package-pattern + 3 intentional suppressions |
| `pom.xml` | modified | spotbugs-maven-plugin 4.9.8.3 + findsecbugs-plugin 1.14.0 plugin block + spotbugs-annotations provided dep |

### JaCoCo Coverage Smoke Check (Pitfall #7 Protection)

**Command:** `./mvnw verify`
**Exit code:** 0
**Result:** PASS

| Metric | Value |
|--------|-------|
| JaCoCo line coverage | **88.04%** |
| Minimum required | 82% |
| v1.10 baseline | 87.80% |
| Delta from baseline | +0.24pp |
| `target/site/jacoco/index.html` | exists |
| `@{argLine}` count in pom.xml | 3 (unchanged — Pitfall #7 safe) |

### SpotBugs Baseline Report

**Command:** `./mvnw verify` (report-only, goal=spotbugs)
**Exit code:** 0

| Metric | Value |
|--------|-------|
| `target/spotbugsXml.xml` size | 664,906 bytes |
| `<BugInstance>` count (post-filter) | 220 |
| Pre-staged suppressions active | EI_EXPOSE_REP* on `org.ctc.domain.model.*`, SSRF, PATH_TRAVERSAL_IN (FileStorageService), PATH_TRAVERSAL_IN (BackupArchiveService) |

**Note:** 220 findings remain for triage in PLAN 02. The pre-staged D-08 layer 2 exclusion covers
`org.ctc.domain.model.*` entities only. Additional EI_EXPOSE_REP* findings are present in
`org.ctc.admin.dto.*` (record/DTO classes) and `org.ctc.admin.controller.*` (inner record classes)
— these also need suppression or code fixes. See PLAN 02 triage table (below).

### Rule 3 Auto-fix Applied

**Issue:** `lombok.extern.findbugs.addSuppressFBWarnings=true` in `lombok.config` causes Lombok to
emit `@edu.umd.cs.findbugs.annotations.SuppressFBWarnings` on generated methods. This annotation
class must be on the javac classpath. Without it, `PlayoffMatchup.java` (and all entities) fail
to compile with: `error: package edu.umd.cs.findbugs.annotations does not exist`.

**Fix:** Added `com.github.spotbugs:spotbugs-annotations:4.9.8` as a `<scope>provided</scope>`
compile dependency. This is compile-only (not packaged in the JAR). The version 4.9.8 matches
the spotbugs-maven-plugin 4.9.8.3 annotations jar available in the local Maven cache.

---

## STAT-05 Triage Table (PLAN 02)

**Baseline (after PLAN 01):** 220 BugInstance entries (10 Priority=1 HIGH, 210 Priority=2 Medium).
**Final state (after PLAN 02):** 0 BugInstance entries.

**Strategy approved by user before triage start:** Option 1 — extend the D-08 layer 2 architectural pattern filter to cover the same Lombok/record false-positive shape across all service packages' inner record/DTO classes, plus the top-level record/DTO packages. CONTEXT.md D-08 deliberately scoped this to `org.ctc.domain.model.*` only; the runtime baseline showed 197 of 220 findings (89.5%) are the same EI_EXPOSE_REP* false-positive shape on inner record-like classes in `org.ctc.domain.service.*`, `org.ctc.admin.service.*`, `org.ctc.backup.service.*`, `org.ctc.dataimport.*`, `org.ctc.gt7sync.*`, plus top-level record packages (`admin.dto`, `backup.dto`, `backup.schema`, `backup.audit`, `backup.event`, `sitegen.model`) and inner records in `admin.controller.*`. Architectural-filter expansion is structurally identical to the original D-08 layer 2 rationale.

### Pattern-Family Dispositions

| # | Pattern | Count | Priority | D-10 Disposition | Action Taken | Commit |
| - | ------- | ----- | -------- | ---------------- | ------------ | ------ |
| 1 | EI_EXPOSE_REP / EI_EXPOSE_REP2 | 197 | 2 | architectural-filter (D-08 extension) | Added 8 new `<Match>` entries in `config/spotbugs-exclude.xml` — package + inner-class regex patterns covering all service-DTO carriers and top-level record packages. Every entry has D-09 rationale comment. | `90b27435` |
| 2 | DM_DEFAULT_ENCODING | 10 | 1 (HIGH) | fix (D-10/1) | Added `StandardCharsets.UTF_8` argument to `Files.readString` / `new String(bytes)` calls in 10 graphic services' `loadDefaultTemplate` / `buildPlaceholderCard` methods. HTML/CSS/SVG templates are UTF-8 by project convention. | `6d3d9602` |
| 3 | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | 9 | 2 | suppress (D-10/3) | Each call path was inspected and confirmed to operate on paths guaranteed non-null by upstream guards (configured upload dir + uploaded-file presence checks). Added `@SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "...")` with method-specific rationale at the call site. | `119f35a4` |
| 4 | DLS_DEAD_LOCAL_STORE | 1 | 2 | fix (D-10/2) | Removed unused `phaseTeamByTeamId` local in `DriverRankingService.calculateRankingForPhase` — the per-phase team lookup was vestigial; `resolveTeamFromLineup` covers team attribution per CLAUDE.md "RaceLineup is Source of Truth". Cascading test cleanup in `DriverRankingServiceTest`. | `750cb8ab`, `acd5184d` |
| 5 | IM_BAD_CHECK_FOR_ODD | 1 | 2 | fix (D-10/2) | Changed `leg % 2 == 1` to `leg % 2 != 0` in `MatchService.createMatchWithLegs`. Equivalent for non-negative `leg` values; `!= 0` is the idiomatic safe form. | `750cb8ab` |
| 6 | DB_DUPLICATE_BRANCHES | 1 | 2 | fix (D-10/2) | Merged identical `else if (bDiff > aDiff)` and `else` branches in `MatchdayGeneratorService.balanceHomeAway`. Both executed `homeCounts[a]++; awayCounts[b]++`. | `750cb8ab` |
| 7 | VA_FORMAT_STRING_USES_NEWLINE | 1 | 2 | suppress (D-10/4 stylistic) | `TemplatePreviewService.buildPlaceholderCard` emits SVG inside a Base64 data URI; SVG/XML requires `\n` literally. `%n` would emit `\r\n` on Windows and corrupt the Base64 payload. Suppression has rationale comment. | `750cb8ab` |
| — | D-11 cross-reference comments | 4 | — | doc | Added inline rationale comments to `FileStorageService` (SSRF blocklist) and `BackupArchiveService.assertEntrySafe` so D-09 cross-references resolve to source lines. | `08c8ed08` |

### Wave 2 Commits Summary

| Commit | Type | Lines | Touch |
| ------ | ---- | ----- | ----- |
| `90b27435` | chore | `config/spotbugs-exclude.xml` +47 lines | Phase A — architectural filter extension |
| `08c8ed08` | chore | 3 source files (FileStorageService, BackupArchiveService) | D-09 cross-reference comments |
| `6d3d9602` | fix  | 10 graphic service files | UTF-8 charset on template I/O |
| `119f35a4` | chore | 4 files (BackupArchiveService, BackupImportService, SiteGeneratorService, TemplateWriter, TeamCardService, TeamProfilePageGenerator) | NP_NULL_ON_SOME_PATH suppressions |
| `750cb8ab` | fix  | 3 services + 1 filter | Misc Medium fixes + 1 stylistic suppress |
| `acd5184d` | test | DriverRankingServiceTest | Mockito strict-stubbing cleanup |

### Final Verification (PLAN 02)

| Check | Command | Result |
| ----- | ------- | ------ |
| SpotBugs report-only | `./mvnw spotbugs:spotbugs -DskipTests` | exit 0, **0 BugInstance entries** in `target/spotbugsXml.xml` |
| Full verify | `./mvnw verify` | exit 0, BUILD SUCCESS (~9 min 22 s) |
| Tests | surefire | 1381 tests passed, 0 errors, 4 skipped |
| JaCoCo coverage | `target/site/jacoco/jacoco.csv` | **88.03%** line coverage (≥ 82% min, ≈ v1.10 baseline 87.80%) |
| JaCoCo argLine integrity (Pitfall #7) | `grep -c '@{argLine}' pom.xml` | **3** (unchanged from PLAN 01) |
| Spotbugs exclude rationale invariant (D-09) | every `<Match>` has preceding XML comment | confirmed by visual inspection of `config/spotbugs-exclude.xml` |
| `@SuppressWarnings("all")` ban | `grep -r '@SuppressWarnings("all")' src/main src/test` | **0 matches** (only targeted `@SuppressFBWarnings("PATTERN_NAME")` entries) |

---

## STAT-05/2 Gate-Flip Commit Evidence

**Date:** 2026-05-16
**Branch:** worktree-agent-a0dc1ee49574f227d (phase 81 Wave 3 executor branch)
**Commit SHA:** 64fdb7bac7862f16c5ca831b08b31af144cafc8f
**Commit message:** `feat(81): activate SpotBugs blocking gate (STAT-05/2, STAT-06, STAT-07)`

### pom.xml Diff (1-word change)

```diff
-   <!-- STAT-05/1: report-only — flip to <goal>check</goal> after baseline triage -->
-   <goal>spotbugs</goal>
+   <!-- STAT-05/2: blocking gate — fails build on Medium+HIGH violations -->
+   <goal>check</goal>
```

### Verification Metrics

| Check | Command | Result |
|-------|---------|--------|
| `<goal>check</goal>` present | grep in SpotBugs block | **PASS** |
| `<goal>spotbugs</goal>` absent | grep in SpotBugs block | **PASS** |
| `<argLine>` absent from SpotBugs block | grep in SpotBugs block | **PASS** (Pitfall #7 invariant) |
| `@{argLine}` count in pom.xml | `grep -c '@{argLine}' pom.xml` | **3** (unchanged) |
| `./mvnw verify` with blocking gate | exit code | **0 (BUILD SUCCESS)** |
| `./mvnw verify -Pe2e` final phase gate | exit code | **0 (BUILD SUCCESS)** |
| Total time (`-Pe2e`) | elapsed | 11:10 min |
| JaCoCo line coverage | `target/site/jacoco/jacoco.csv` | **88.47%** (≥ 82% min, +0.67pp vs v1.10 baseline 87.80%) |
| SpotBugs BugInstance count (clean tree) | `target/spotbugsXml.xml` | **0** Medium+HIGH |
| Surefire (unit) tests | results | **1381 passed**, 0 errors, 4 skipped |
| Failsafe IT (integration) tests | results | **231 passed**, 0 errors, 3 skipped |
| Failsafe E2E (Playwright) tests | results | **36 passed**, 0 errors, 0 skipped |

### Files Changed in Gate-Flip Commit

```
 CLAUDE.md | 6 ++++++
 pom.xml   | 4 ++--
 2 files changed, 8 insertions(+), 2 deletions(-)
```

### CLAUDE.md Conventions Addition (STAT-07)

New `### Static Analysis (SpotBugs + find-sec-bugs)` sub-section appended after `### CSS Guidelines`:
- Bullet 1 — **Gate:** plugin versions + `./mvnw verify` blocking on Medium+HIGH, no separate CI job
- Bullet 2 — **Suppressions:** `config/spotbugs-exclude.xml`, rationale comment requirement, no `@SuppressWarnings("all")`
- Bullet 3 — **`lombok.config` invariant:** `addSuppressFBWarnings=true`, do NOT remove without re-baseline phase

---

## STAT-06 Deliberate-Violation Evidence

**Date:** 2026-05-16
**Throwaway branch:** `throwaway/stat-06-validation` (branched from `worktree-agent-a0dc1ee49574f227d`)
**Deliberate-violation file:** `src/main/java/org/ctc/_validation_marker/DeliberateNullDereference.java`
**SpotBugs pattern triggered:** `NP_ALWAYS_NULL` (Priority=1 HIGH)

### Violation Code

```java
package org.ctc._validation_marker;

public class DeliberateNullDereference {
    public String trigger() {
        Object o = null;
        return o.toString();  // NP_ALWAYS_NULL — line 13
    }
}
```

**Location:** `src/main/java/org/ctc/_validation_marker/` (NOT `src/test/java/` — per RESEARCH.md Pitfall F, SpotBugs only scans `${project.build.outputDirectory}` since `<includeTests>` is not set).

### `./mvnw verify -DskipTests` Failure Output

**Exit code:** 1 (BUILD FAILURE)

```
[INFO] --- spotbugs:4.9.8.3:check (spotbugs-check) @ ctc-manager ---
[INFO] BugInstance size is 1
[INFO] Error size is 0
[INFO] Total bugs: 1
[ERROR] High: Null pointer dereference of o in org.ctc._validation_marker.DeliberateNullDereference.trigger()
        [org.ctc._validation_marker.DeliberateNullDereference]
        Dereferenced at DeliberateNullDereference.java:[line 13] NP_ALWAYS_NULL

[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  8.998 s
[ERROR] Failed to execute goal com.github.spotbugs:spotbugs-maven-plugin:4.9.8.3:check (spotbugs-check)
        on project ctc-manager: failed with 1 bugs and 0 errors
```

### Cleanup Confirmation

| Check | Result |
|-------|--------|
| `git switch worktree-agent-a0dc1ee49574f227d` | DONE |
| `git branch -D throwaway/stat-06-validation` | DONE — branch deleted |
| `git branch --list 'throwaway/*'` | **empty** — no throwaway branches remain |
| `DeliberateNullDereference.java` absent | **CONFIRMED** — file not present on worktree branch |
| `_validation_marker/` directory absent | **CONFIRMED** — directory not present on worktree branch |
| Throwaway branch pushed to origin | **NEVER** — `git push` was not invoked |
| Post-cleanup `./mvnw verify -Pe2e` | exit 0, **BUILD SUCCESS** (no NP_ALWAYS_NULL on clean tree)
