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

## STAT-05 Triage Table (populated by PLAN 02)

_To be filled in by PLAN 02 executor after running baseline inspection._

---

## STAT-05/2 Gate-Flip Commit Evidence (populated by PLAN 03)

_To be filled in by PLAN 03 executor after flipping goal=spotbugs to goal=check._

---

## STAT-06 Deliberate-Violation Evidence (populated by PLAN 03)

_To be filled in by PLAN 03 executor after throwaway-branch gate validation._
