# Phase 89: PERF Instrumentation & Lever 1 (Per-Fork Backup-Staging-Dir) - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-19
**Phase:** 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
**Areas discussed:** Plan-Struktur, Fork-Number-Injektion, Cache-Key-Fingerprint-Surface, Failsafe-forkCount + Assertion-IT, Production-Verhalten, Coverage-Gates, Docs-Updates, Cleanup-Race-Verification

---

## Gray Area Selection (multiselect)

| Option | Description | Selected |
|--------|-------------|----------|
| Plan-Struktur (parallel vs. sequentiell) | Conflict ROADMAP "parallel-runnable" vs. memory [[inline-sequential-execution]] | ✓ |
| Fork-Number-Injektion in app.backup.staging-dir | How `${surefire.forkNumber}` enters the Spring property | ✓ |
| Cache-Key-Fingerprint-Surface (PERF-02) | Where to extract `MergedContextConfiguration.hashCode()` | ✓ |
| Failsafe-forkCount-Elevation-Policy + Assertion-IT-Shape | pom.xml policy + PERF-01 SC#1 IT shape | ✓ |

---

## Plan-Struktur

| Option | Description | Selected |
|--------|-------------|----------|
| 2 Plans, sequentiell inline, 1 Wave (PERF-01 → PERF-02) | aligns with [[inline-sequential-execution]] + [[wave-pause]] | |
| 2 Plans, sequentiell inline, PERF-02 first | reverse order — instrumentation first | |
| 3 Plans (89-01 PERF-01, 89-02 PERF-02, 89-03 Wave-4-Messung) | atomic separation Refactor / Instrumentation / Measurement | ✓ |

**User's choice:** 3 Plans with dedicated Wave-4 measurement as separate atomic plan.
**Notes:** Cleaner commit history; each plan gets its own SUMMARY.md; reverting Wave-4 doesn't touch refactor or listener code.

### Plan-Struktur — Wave-4-Gate Strictness

| Option | Description | Selected |
|--------|-------------|----------|
| Honest reporting, kein Reduktions-Gate | Phase 86 D-15 pattern; matches local-run-variance reality | ✓ |
| Hartes Lokal-Gate: ≥5% Reduktion vs. 10:24 | Statistically fragile (66s spread in Phase 86 post-audit) | |
| Hartes Lokal-Gate: ≥15% Reduktion vs. 10:24 | More ambitious, same variance weakness | |

**User's choice:** Honest reporting, defer CI-authoritative to Phase 91 PERF-06.
**Notes:** Local hardware varies; CI is source-of-truth per Phase 86 D-11.

---

## Fork-Number-Injektion

| Option | Description | Selected |
|--------|-------------|----------|
| Failsafe systemPropertyVariables in pom.xml | Minimal Java code, explicit in build config | ✓ |
| Spring-Placeholder direkt in application.yml | Test concern leaks into main config | |
| Test-only EnvironmentPostProcessor | Cleanest separation, most code | |

**User's choice:** pom.xml `systemPropertyVariables` (D-03).
**Notes:** application.yml stays untouched, supports D-14 production-default contract.

### Fork-Number-Injektion — Path-Schema

| Option | Description | Selected |
|--------|-------------|----------|
| `data/${profile}/backup-staging-fork-${surefire.forkNumber}` | Hyphen-separator, fork-number visible | ✓ |
| `data/${profile}/backup-staging/fork-${surefire.forkNumber}` | Per-fork subdir under parent | |
| `data/${profile}/backup-staging-${UUID}` | Per-test UUID — incompatible with reuseForks=true | |

**User's choice:** Hyphen-suffix form with `surefire.forkNumber` (D-04).
**Notes:** Default `<surefire.forkNumber>0</surefire.forkNumber>` in pom.xml prevents trailing-hyphen collapse for IDE/non-fork invocations.

### Fork-Number-Injektion — Surefire scope

| Option | Description | Selected |
|--------|-------------|----------|
| Both Surefire AND Failsafe set the value | Consistent per-fork isolation in both phases | ✓ |
| Only Failsafe | Surefire test-side races possible if any Surefire test reads the property | |
| Both + Spring Boot Maven Plugin | Overkill for test-wallclock goal | |

**User's choice:** Both Surefire AND Failsafe set the value (D-05).
**Notes:** Surefire already runs forkCount=2 and some `@Tag("unit")` tests boot Spring contexts that consume `app.backup.staging-dir`.

### Fork-Number-Injektion — Legacy path cleanup

| Option | Description | Selected |
|--------|-------------|----------|
| Plan-01 SUMMARY documents one-shot rm of legacy dir | Cleanest, no code | ✓ |
| New `LegacyStagingDirSweeper` bean | Defensive, mild YAGNI smell | |
| Ignore — devs clean up themselves | Minimum effort, may confuse | |

**User's choice:** One-shot rm in Plan-01 SUMMARY (D-06).
**Notes:** Data dir is gitignored; sweeper bean would be YAGNI given the per-fork dir is the only path BackupStagingCleanup ever sees from Plan-01 merge onwards.

---

## Cache-Key-Fingerprint-Surface (PERF-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Hybrid: Initializer behält Count, new TestExecutionListener fingerprintet | Two listeners, two signals | ✓ |
| Switch to TestExecutionListener for both | Refactor existing listener, larger blast radius | |
| Proxy via context.getId() + Environment-Hash | Doesn't match actual Spring TCF cache key | |

**User's choice:** Hybrid surface (D-07).
**Notes:** ApplicationContextInitializer doesn't receive MergedContextConfiguration; only TestExecutionListener has TestContext access to it.

### Fingerprint-Surface — Marker file

| Option | Description | Selected |
|--------|-------------|----------|
| Same file `context-loads-{PID}.txt`, extended format | REQUIREMENTS wording explicit | ✓ |
| New file `context-fingerprints-{PID}.txt` | Cleaner separation but contradicts REQUIREMENTS L22 | |
| JSON Lines | Heavier tooling for small win | |

**User's choice:** Same file, extended format (D-08).
**Notes:** Header line `total <N>` (backward-compat for existing aggregator), followed by `<hex-hash>\t<mcc-display>` per event.

### Fingerprint-Surface — Aggregator location

| Option | Description | Selected |
|--------|-------------|----------|
| `scripts/test-perf/aggregate-fingerprints.sh` + docs reference | Versioned, shellcheck-able | ✓ |
| Inline-bash block in docs/test-performance.md | Literal REQUIREMENTS phrasing | |
| Both | Maximum surface, maintenance overhead | |

**User's choice:** Real shell script + docs usage reference (D-09).

### Fingerprint-Surface — Hash algorithm

| Option | Description | Selected |
|--------|-------------|----------|
| `Integer.toHexString(mergedContextConfiguration.hashCode())` | Matches Spring TCF ContextCache 1:1 | ✓ |
| SHA-256 over MCC.toString() | Doesn't match Spring's bucketing | |
| Full toString + separate hashCode column | Verbose, large file size | |

**User's choice:** Spring's native `hashCode()` in hex (D-10).
**Notes:** Display column = MCC.toString() truncated to ~200 chars.

---

## Failsafe-forkCount + Assertion-IT

| Option | Description | Selected |
|--------|-------------|----------|
| pom.xml permanent `<forkCount>2</forkCount><reuseForks>true</reuseForks>` | Mirror Surefire config, immediate CI benefit | ✓ (clarified to default-it only) |
| Keep pom.xml at 1, 3-seed run uses `-Dfailsafe.forkCount=2` ad-hoc | Minimal pom change, no CI benefit | |
| Aggressive `<forkCount>1C</forkCount>` (1 per CPU) | Risky on test isolation + memory | |
| `<forkCount>2</forkCount>` for default-it, e2e-it stays at 1 | Explicit Playwright-safe variant | |

**User's choice:** permanent `<forkCount>2</forkCount><reuseForks>true</reuseForks>` on default-it (D-11).

### Failsafe-forkCount — e2e-it confirmation

| Option | Description | Selected |
|--------|-------------|----------|
| default-it=2, e2e-it=1 (Playwright-constraint) | Existing pom.xml comment from Phase 86 | ✓ |
| Both at forkCount=2 | Breaks Playwright random-port assumption | |

**User's choice:** default-it=2, e2e-it=1 (D-11 clarified).

### Assertion-IT shape

| Option | Description | Selected |
|--------|-------------|----------|
| Single-fork self-assertion + 3-seed suite-run as cross-fork proof | Pragmatic, lightweight | ✓ |
| Parameterized ParallelComputer/JUnit-Pioneer | Thread-level not Process-level — misleading | |
| ProcessBuilder spawning mvn in test | Overkill, CI-fragile | |

**User's choice:** Single-fork self-assertion + 3-seed empirical proof (D-12).
**Notes:** Cross-fork-collision is proven by suite-run not by clever Java; matches Surefire/Failsafe process-level isolation.

### 3-seed verification scope

| Option | Description | Selected |
|--------|-------------|----------|
| Exactly the 7 ROADMAP-SC#2 ITs | Tight focused scope | |
| All backup-subtree ITs (`src/test/java/org/ctc/backup/**`) | Broader confidence, ~9-12 min run | ✓ |
| Entire default-it suite × 3 seeds | Maximum confidence, ~36-54 min budget | |

**User's choice:** All `org.ctc.backup.**` ITs (D-13).
**Notes:** Plan-01 SUMMARY enumerates each covered IT.

---

## Production-Verhalten

| Option | Description | Selected |
|--------|-------------|----------|
| application.yml unchanged (`data/${profile}/backup-staging`) | Production-default contract preserved | ✓ |
| application.yml becomes `…-fork-${surefire.forkNumber:default}` | Ugly fallback in prod | |
| Production must set BACKUP_STAGING_DIR explicitly | Breaking change for deployment | |

**User's choice:** application.yml stays untouched (D-14).
**Notes:** Zero production breaking change; per-fork override only fires during Maven phases.

---

## Coverage-Gates

| Option | Description | Selected |
|--------|-------------|----------|
| Standard: JaCoCo ≥ 88.88%, SpotBugs 0, CodeQL exit 0 | ROADMAP SC#5 + Phase-86 baseline | ✓ |
| Loose: JaCoCo ≥ 88.0%, SpotBugs 0, CodeQL exit 0 | Unneeded slack | |
| Strict: JaCoCo ≥ 89.0% | Wrong metric for PERF phase | |

**User's choice:** Standard gates (D-15).

---

## Docs-Updates

| Option | Description | Selected |
|--------|-------------|----------|
| docs/test-performance.md (PERF-02 Forensics + Wave-4 + Forward-Path) | Mandatory per ROADMAP SC#3-5 | ✓ |
| README.md Test-Performance section pointer | Matches Phase-86 pattern | ✓ |
| CLAUDE.md Commands section: 3-seed verification | Modest value; current discipline already documents `-Dit.test` | |
| docs/operations/import-runbook.md per-fork path hint | Operator-facing; production unchanged | |

**User's choice:** docs/test-performance.md + README.md (D-16).

---

## Backup-Cleanup-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| New `BackupStagingCleanupRaceIT` proves race-freedom under forkCount=2 | Dedicated assertion | ✓ |
| Extend existing `BackupStagingCleanupIT` with per-fork assertion | Minimal invasive, mixes class concerns | |
| No explicit IT — 3-seed suite run covers empirically | Race-freedom not directly asserted | |

**User's choice:** New `BackupStagingCleanupRaceIT` (D-17).
**Notes:** Writes test files into both own-fork dir AND a sibling dummy `-fork-99` dir; asserts cleanup only touches own-fork files.

---

## Claude's Discretion

Captured in CONTEXT.md `<decisions> → Claude's Discretion`:
- Exact prose of `docs/test-performance.md § PERF-02 Forensics` section + Top-5 cluster output format.
- Whether per-fork-path string in pom.xml lives in `<properties>` (named) or is literally repeated in Surefire + Failsafe configurations.
- Whether `ContextCacheKeyFingerprintListener` registers via `META-INF/spring.factories` or `@TestExecutionListeners(MERGE_WITH_DEFAULTS)` base class.
- `MergedContextConfiguration.toString()` truncation length (target: marker file ≤ ~10KB per fork).
- `BackupStagingCleanupRaceIT` sibling-fork-dir name (`-fork-99` is suggestion).
- Listener filename (`ContextCacheKeyFingerprintListener` vs. extending the `ContextLoadCount…` family naming).

---

## Deferred Ideas

Captured in CONTEXT.md `<deferred>`:
- Testcontainers `withReuse(true)` wiring → Phase 90 PERF-04.
- Shared `@ContextConfiguration` cluster consolidation → Phase 90 PERF-03 (needs PERF-02 data).
- CI-authoritative 5-run wallclock re-harvest → Phase 91 PERF-06.
- Aggressive `<forkCount>1C</forkCount>` → potential Phase-90 follow-up.
- `CLAUDE.md` 3-seed verification command shortcut → deferred (current `-Dit.test` discipline sufficient).
- JaCoCo gate increase 88.88% → 89.0% → rejected (wrong metric for PERF phase).
- Legacy `data/dev/backup-staging/` startup sweeper bean → rejected as YAGNI.

---

## 2026-05-19 — Revision After Plan 89-01 Attempt 1 Flake Diagnostic

**Trigger:** Plan 89-01 Attempt 1 executed inline; Seed 1234 Failsafe verification surfaced 3 regressions. Working tree reverted (commit `ec4732c7`), `89-FLAKE-DIAGNOSTIC.md` written. Re-discuss session opened to revise CONTEXT.md decisions before re-planning.

### Re-Discuss Area Selection (multiselect)

| Option | Description | Selected |
|--------|-------------|----------|
| A: Per-fork-Mechanik (D-04 korrigieren, RQ-1 widerlegt) | Maven eager-substitution defeats Surefire late-binding | ✓ |
| B: `app.backup.import-backups-dir` Scope-Extension | Second shared filesystem path not covered | ✓ |
| C: ImportLockedPostRejectorIT Lock-Timeout | Lock-acquire fails in 10s under reuseForks=true+forkCount=2 | ✓ |
| D: BackupStagingDirPerForkIT Test 2 Redesign | Vacuous-pass because `surefire.forkNumber` not exposed | ✓ |

---

### Area A — Per-fork Injektion (D-04 → D-04R + D-04R.2)

| Option | Description | Selected |
|--------|-------------|----------|
| Projekt-Property raus + `surefire.forkNumber` als JVM-Property injizieren | D-14 bleibt zu, Test 2 wird scharf | ✓ |
| D-14 öffnen, application.yml auf Spring-Default-Syntax `${surefire.forkNumber:0}` | Single source of truth, aber breaking change in import-backups-dir | |
| forkCount=2 droppen, Plan 89-01 zu PERF-02-only umbauen | Konservativ aber bricht Roadmap-Lever-1 | |

**Result → D-04R, D-04R.2:** pom.xml `<properties>` block ENTHÄLT KEIN `surefire.forkNumber`. `<systemPropertyVariables>` in Surefire + Failsafe `default-it` enthalten zusätzlich `<surefire.forkNumber>${surefire.forkNumber}</surefire.forkNumber>` als JVM-System-Property-Exposition. Maven lässt `${surefire.forkNumber}` unresolved durch; Surefire substituiert at fork-dispatch. Test 2-Parity-Check funktioniert.

---

### Area B — `app.backup.import-backups-dir` Scope (D-18 NEW)

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror staging-dir Pattern via pom.xml | Zweiter `<systemPropertyVariables>`-Eintrag, application.yml unverändert | ✓ |
| BackupImportService auf TestPropertySource-overrides refactorn | Production-Code-Anfassen, invasive | |
| Nur `AutoBackupBeforeImportPathIT` mit `@DynamicPropertySource`+`@TempDir` flicken | Latentes Risiko bei neuen ITs | |

**Result → D-18:** Pom.xml `<systemPropertyVariables>` blocks (Surefire + Failsafe `default-it`) bekommen zweiten Eintrag: `<app.backup.import-backups-dir>data/${spring.profiles.active:dev}/import-backups-fork-${surefire.forkNumber}</app.backup.import-backups-dir>`. application.yml unverändert (D-14 bleibt). Plan 89-01 Task 1 wächst auf 3 properties × 2 plugins = 6 `<systemPropertyVariables>`-Einträge total.

---

### Area C — ImportLockedPostRejectorIT Lock-Timeout (D-19 NEW)

| Option | Description | Selected |
|--------|-------------|----------|
| Wurzel-Analyse + Fix als Plan 89-01 Task 5 (per [[no-flaky-dismissal]]) | Root-cause-first, deadline-bump nur mit Justification | ✓ |
| Nur Deadline auf 20s bumpen, tech-debt dokumentieren | Schneller, aber deckt latenten Bug zu | |
| Test mit `@DisabledIfSystemProperty` unter forkCount>1 skippen | Verliert Coverage, Anti-Pattern | |

**Result → D-19:** Plan 89-01 bekommt eigenen Task 5 für ImportLock-Acquire-Pfad-Analyse + Static-Lock-Leak-Check unter `reuseForks=true`. Drei zulässige Outcomes: (a) source patch fix, (b) `@AfterEach` lock-release strengthening, (c) deadline-bump auf 20s NUR mit Justification + Follow-up-Issue.

---

### Area D — BackupStagingDirPerForkIT Test 2 (D-12 clarified)

| Option | Description | Selected |
|--------|-------------|----------|
| Test 2 bleibt unverändert, weil D-04R.2 die Property exposed | Test 2 wird non-vacuous unter Failsafe | ✓ |
| Test 2 droppen, Test 1 Regex auf `backup-staging-fork-[12]` einengen | Schneidet IDE-Runs ab | |
| Test 2 zu strikter Assertion: forkNum MUSS gesetzt sein | Brittle Run-Mode-Abhängigkeit | |

**Result → D-12 (clarified):** Test 2 (`if (forkNum != null && !forkNum.isBlank()) endsWith("-" + forkNum)`) bleibt im IT-Code, aber feuert jetzt scharf weil D-04R.2 die Property als JVM-Property injiziert. Vacuous-pass von Attempt 1 ist behoben.

---

### Plan 89-01 Task Count (D-01 revised)

Plan 89-01 wächst von 4 auf 5 Tasks:

1. pom.xml — 6 `<systemPropertyVariables>`-Einträge (3 properties × 2 plugins), Failsafe `default-it` forkCount=2+reuseForks=true, KEIN project-property fallback.
2. `BackupStagingDirPerForkIT` per D-12 (clarified).
3. `BackupStagingCleanupRaceIT` per D-17.
4. **NEW:** ImportLock-Timeout-Analyse + Fix per D-19.
5. 3-seed Failsafe verification per D-13 + Legacy `rm -rf` per D-06.

Plans 89-02 + 89-03 sind unberührt; ihre Dependency auf 89-01 bleibt bestehen.
