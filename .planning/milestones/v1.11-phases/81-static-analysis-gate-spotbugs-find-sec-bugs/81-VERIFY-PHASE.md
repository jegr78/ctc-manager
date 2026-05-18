---
phase: 81-static-analysis-gate-spotbugs-find-sec-bugs
verified: 2026-05-16T18:00:00Z
status: passed
score: 6/6 success criteria verified
verdict: COMPLETE
---

# Phase 81: Static Analysis Gate — Goal Verification Verdict

**Phase Goal:** Every `./mvnw verify` run enforces a bytecode-level clean-code gate that catches
null dereferences, resource leaks, and 144 Spring Security-aware patterns — with Lombok false
positives fully suppressed before the gate goes live.

**Verified:** 2026-05-16
**Verdict:** COMPLETE

---

## Success Criteria Verification

### SC#1 — lombok.config + `mvn spotbugs:check` clean on domain.model

**Status: VERIFIED**

| Check | Evidence |
|-------|----------|
| `lombok.config` exists at project root | File present: 3 lines |
| `lombok.addLombokGeneratedAnnotation = true` | Line 2 of `lombok.config` |
| `lombok.extern.findbugs.addSuppressFBWarnings = true` | Line 3 of `lombok.config` |
| `config.stopBubbling = true` | Line 1 (belt-and-braces per SPECIFICS) |
| Zero violations on `org.ctc.domain.model.*` | `target/spotbugsXml.xml` shows 0 `<BugInstance>` entries total (grep -c returns 0); domain.model covered by D-08 layer 2 package filter AND lombok-generated `@SuppressFBWarnings` |

---

### SC#2 — Two-commit STAT-05 choreography in git history

**Status: VERIFIED**

| Check | Evidence |
|-------|----------|
| STAT-05/1 plumbing commit exists | `1f020a23` — `feat(81): wire SpotBugs report-only baseline (STAT-01..STAT-04, STAT-05/1)` — 2026-05-16T14:44:10+02:00 |
| STAT-05/1 had `<goal>spotbugs</goal>` (report-only) | Commit diff flipped to `<goal>check</goal>` in `64fdb7ba`, confirming original was report-only |
| STAT-05/2 gate-flip commit exists | `64fdb7ba` — `feat(81): activate SpotBugs blocking gate (STAT-05/2, STAT-06, STAT-07)` — 2026-05-16T16:27:36+02:00 |
| STAT-05/1 committed BEFORE STAT-05/2 | `git log --oneline --reverse --grep="STAT-05"` confirms `1f020a23` appears before `64fdb7ba` |
| Triage commits between them | 6 triage commits (`90b27435`, `08c8ed08`, `6d3d9602`, `119f35a4`, `750cb8ab`, `acd5184d`) land between the two STAT-05 commits |

---

### SC#3 — spotbugs-exclude.xml rationale invariant

**Status: VERIFIED**

| Check | Evidence |
|-------|----------|
| `config/spotbugs-exclude.xml` exists | File present, 249 lines |
| `<Match>` element count | 25 elements |
| Every `<Match>` has preceding `<!--` comment | Python-verified: "ALL 25 `<Match>` elements have a preceding comment block" |
| No `@SuppressWarnings("all")` anywhere in `src/` | `grep -r '@SuppressWarnings("all")' src/` returns 0 matches |
| WR-02 stale line-number references fixed | Commit `5579be6b` updated `TeamCardService.java:104-106` and `BackupArchiveService.java:500-505`; current source lines confirmed correct (getParent() at line 106 and 504 respectively) |

---

### SC#4 — Gate blocks on deliberate HIGH-priority violation

**Status: VERIFIED**

Evidence is in `81-VERIFICATION.md` "STAT-06 Deliberate-Violation Evidence" section:

| Check | Evidence |
|-------|----------|
| Throwaway branch used | `throwaway/stat-06-validation` (branched from wave 3 worktree) |
| Deliberate violation file | `src/main/java/org/ctc/_validation_marker/DeliberateNullDereference.java` |
| Violation type triggered | `NP_ALWAYS_NULL` (Priority=1 HIGH) |
| Exit code | `1` (BUILD FAILURE) |
| SpotBugs output captured | "High: Null pointer dereference of o in org.ctc._validation_marker.DeliberateNullDereference.trigger()" with specific line reference |
| File placed in `src/main/java/` | Confirmed — per RESEARCH.md Pitfall F (SpotBugs only scans `${project.build.outputDirectory}` without `<includeTests>`) |
| Throwaway branch deleted | `git branch --list 'throwaway/*'` returns empty |
| `_validation_marker/` absent on current branch | `ls src/main/java/org/ctc/_validation_marker/` returns "not found" |

---

### SC#5 — JaCoCo line coverage >= 82%

**Status: VERIFIED**

| Check | Evidence |
|-------|----------|
| `target/site/jacoco/jacoco.csv` exists | Present, used for calculation |
| Line coverage computed from CSV | 7449 covered / 8452 total = **88.13%** (>= 82% minimum, >= 87% preferred) |
| `@{argLine}` count in pom.xml | `grep -c '@{argLine}' pom.xml` returns **3** (unchanged from pre-Phase-81 — Pitfall #7 safe) |
| No `<argLine>` in SpotBugs plugin block | `awk '/<artifactId>spotbugs-maven-plugin/,/<\/plugin>/' pom.xml | grep -q '<argLine>'` returns PASS |

Coverage reported across multiple PLAN verification runs: 88.04% (after PLAN 01), 88.03% (after PLAN 02 triage), 88.47% (after PLAN 03 with E2E), 88.13% (current CSV snapshot). All values are >= 82% minimum and >= 87% preferred.

---

### SC#6 — CLAUDE.md Conventions documentation

**Status: VERIFIED**

| Check | Evidence |
|-------|----------|
| `### Static Analysis (SpotBugs + find-sec-bugs)` heading present | Found at CLAUDE.md line 219 |
| Placement after `### CSS Guidelines` | `### CSS Guidelines` is at line 215; SpotBugs section follows immediately at line 219 |
| Gate is active on `./mvnw verify` | Bullet 1: "spotbugs-maven-plugin 4.9.8.3 + findsecbugs-plugin 1.14.0 run on every `./mvnw verify` (Medium+HIGH findings block the build)" |
| Suppressions in `config/spotbugs-exclude.xml` | Bullet 2: "Live in `config/spotbugs-exclude.xml`. Every `<Match>` entry MUST have an XML rationale comment..." |
| No `@SuppressWarnings("all")` ban | Bullet 2: "No `@SuppressWarnings("all")` ever — use targeted `@SuppressFBWarnings({"SPECIFIC_CODE"}, justification="...")`" |
| `lombok.config` invariant documented | Bullet 3: "Do NOT remove or modify the two SpotBugs-related lines without a new phase that re-baselines suppressions" |

---

## Critical Invariant Checks

| Invariant | Check | Result |
|-----------|-------|--------|
| Pitfall #7: no `<argLine>` in spotbugs plugin block | `awk '/<artifactId>spotbugs-maven-plugin/,/<\/plugin>/' pom.xml \| grep -q '<argLine>'` | **PASS** |
| Pitfall #7: `@{argLine}` count = 3 | `grep -c '@{argLine}' pom.xml` | **PASS** (returns 3) |
| D-01: SpotBugs in main `<build><plugins>`, NOT a profile | `awk '/<profile/,/<\/profile>/' pom.xml \| grep -q 'spotbugs-maven-plugin'` | **PASS** (not found in any profile) |
| D-08 layer 2 extended (user-approved) | `81-VERIFICATION.md` "Strategy approved by user before triage start" | **PASS** (documented in VERIFICATION.md triage table section) |
| RESEARCH.md C-01: no SecurityConfig/passwordEncoder/HARD_CODE_PASSWORD suppression | `grep -c 'SecurityConfig\|passwordEncoder\|HARD_CODE_PASSWORD' config/spotbugs-exclude.xml` | **PASS** (returns 0) |
| RESEARCH.md F-02: PATH_TRAVERSAL_IN on BackupArchiveService, not BackupImportService.restoreOneTable | BackupImportService entries in exclude.xml | **PASS** — BackupImportService has only NP_NULL_ON_SOME_PATH entries; PATH_TRAVERSAL_IN is on FileStorageService and BackupArchiveService only |
| STAT-06 throwaway branch in `src/main/java/` (Pitfall F) | `81-VERIFICATION.md` evidence section | **PASS** — "src/main/java/org/ctc/_validation_marker/" explicitly stated in VERIFICATION.md |

---

## Requirements Coverage

All 7 requirement IDs are accounted for across the 3 plans:

| Requirement | Description | Covered By | Status |
|-------------|-------------|-----------|--------|
| STAT-01 | `lombok.config` with two SpotBugs directives | Plan 01 (`1f020a23`) | SATISFIED |
| STAT-02 | `spotbugs-maven-plugin` in main build, effort=Max, threshold=Default | Plan 01 (`1f020a23`) | SATISFIED |
| STAT-03 | `findsecbugs-plugin` 1.14.0 as plugin-dep, 144 patterns | Plan 01 (`1f020a23`) | SATISFIED |
| STAT-04 | `config/spotbugs-exclude.xml` with rationale on every entry | Plans 01+02 (`1f020a23`, `90b27435`, `5579be6b`) | SATISFIED |
| STAT-05 | Two atomic commits: report-only → blocking gate | Plans 01+03 (`1f020a23`, `64fdb7ba`) | SATISFIED |
| STAT-06 | Gate blocks on deliberate HIGH violation (throwaway proof) | Plan 03 (`64fdb7ba`, `b12ac7f3`) | SATISFIED |
| STAT-07 | CLAUDE.md Conventions updated with gate + suppression + lombok.config | Plan 03 (`64fdb7ba`) | SATISFIED |

---

## Required Artifacts Status

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `lombok.config` | Two SpotBugs directives + stopBubbling | VERIFIED | 3 lines, both directives present |
| `config/spotbugs-exclude.xml` | 25+ Match entries with rationale | VERIFIED | 25 entries, all with preceding XML comments; WR-02 stale line-numbers fixed |
| `pom.xml` SpotBugs block | `<goal>check</goal>` bound to verify phase | VERIFIED | `check` goal active in `spotbugs-check` execution, no `<argLine>` |
| `CLAUDE.md` Conventions | `### Static Analysis` section after CSS Guidelines | VERIFIED | Present at line 219 |
| `81-VERIFICATION.md` | All 4 sections populated | VERIFIED | STAT-05/1 plumbing, triage table, STAT-05/2 gate-flip, STAT-06 deliberate-violation evidence |
| `target/spotbugsXml.xml` | 0 BugInstance entries | VERIFIED | 0 matches on `grep -c '<BugInstance'` |
| `target/site/jacoco/jacoco.csv` | >= 82% line coverage | VERIFIED | 88.13% current |

---

## Code Review Surface Area

**Status:** `81-REVIEW.md` status `issues_found` — 0 Critical, 3 Warnings, 2 Info

| Finding | Severity | Disposition |
|---------|----------|-------------|
| WR-01: `givenMultiPhaseSeason_whenAggregateAcrossPhases` test exercises fallback path not priority-filter path | Warning | Informational — no behavior regression (production code was using `resolveTeamFromLineup` before Phase 81; the DLS fix just removed dead code). Phase 82 test-quality scope. |
| WR-02: Stale line-number references in spotbugs-exclude.xml comments | Warning | **FIXED** — commit `5579be6b` updates TeamCardService reference to `:104-106` and BackupArchiveService to `:500-505`. Verified correct against live source lines. |
| WR-03: Non-volatile mutable fields in `TemplatePreviewService` lazy-init caches | Warning | Pre-existing concern, not Phase 81 scope. Phase 81 only added `@SuppressFBWarnings` annotation; the lazy fields existed before. |
| IN-01: `containsSpringElTypeAccess` only skips ASCII space, not all whitespace | Info | Functional safety net present; pre-existing; out of Phase 81 scope. |
| IN-02: Fully-qualified `ClassPathResource` references in two graphic services | Info | Style inconsistency; out of Phase 81 scope. |

---

## Anti-Patterns Scan

Files modified by Phase 81:

| File | Pattern Check | Result |
|------|---------------|--------|
| `pom.xml` | TBD/FIXME/XXX/TODO markers | 0 matches |
| `lombok.config` | Any stubs or placeholders | N/A (2-directive config file) |
| `config/spotbugs-exclude.xml` | Blanket suppressions / missing rationale | PASS — 25 entries, all with rationale |
| `CLAUDE.md` | Placeholders | 0 matches |
| 10 graphic services (UTF-8 fixes) | Empty implementations | PASS — functional fixes applied |
| `DriverRankingService.java` | Dead local store removed | PASS — actual fix |
| `MatchService.java` | `leg % 2 != 0` fix | PASS — actual fix |
| `MatchdayGeneratorService.java` | Duplicate branch merge | PASS — actual fix |

No `@SuppressWarnings("all")` in any Phase 81 modified file. No unresolved TBD/FIXME/XXX markers. Targeted `@SuppressFBWarnings("PATTERN_NAME", justification="...")` are per-method with specific rationale — not blanket suppression.

---

## Phase Goal Achievement Assessment

The phase goal is: _"Every `./mvnw verify` run enforces a bytecode-level clean-code gate that catches null dereferences, resource leaks, and 144 Spring Security-aware patterns — with Lombok false positives fully suppressed before the gate goes live."_

Each component of this goal is observable in the codebase:

1. **`./mvnw verify` runs the gate** — `spotbugs-maven-plugin` is in main `<build><plugins>` bound to the `verify` phase with `<goal>check</goal>`. Confirmed by pom.xml lines 367-399.

2. **Bytecode-level analysis** — `<effort>Max</effort>` is set; SpotBugs is a bytecode scanner (not source), and the plugin scans `target/classes`.

3. **Catches null dereferences, resource leaks** — Standard SpotBugs patterns enabled; `<threshold>Default</threshold>` catches Medium+HIGH.

4. **144 Spring Security-aware patterns** — `findsecbugs-plugin` 1.14.0 wired as plugin-dependency.

5. **Lombok false positives fully suppressed** — `lombok.config` with `addSuppressFBWarnings=true` + `config/spotbugs-exclude.xml` D-08 layer 2 package filters covering all 24 JPA entities and all service/DTO inner-record classes. `target/spotbugsXml.xml` has 0 BugInstance entries on clean tree.

6. **Gate was suppressed-clean BEFORE going live** — Two-commit STAT-05 choreography confirmed in git history: plumbing commit `1f020a23` (report-only) landed before gate-flip commit `64fdb7ba`, with 6 triage commits between them eliminating all findings.

---

## Summary

All 6 success criteria pass. All 7 critical invariants pass. All 7 STAT-NN requirements satisfied. The code review found 0 Critical issues; WR-02 (the only D-09 invariant violation) was fixed before phase closure. Phase goal is observably achieved in the codebase.

---

**verdict: COMPLETE**

_Verified: 2026-05-16T18:00:00Z_
_Verifier: Claude (gsd-verifier / goal-backward)_
