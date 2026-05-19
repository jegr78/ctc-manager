---
phase: 89-perf-instrumentation-lever-1-per-fork-backup-staging-dir
plan: 02
slug: perf-02-fingerprint-listener-aggregator
status: complete
completed: 2026-05-19
requirements:
  - PERF-02
---

# Plan 89-02 — PERF-02: cache-key fingerprint listener + aggregator + docs

## Objective recap

Add Spring TCF cache-key fingerprint instrumentation so Phase 90 (PERF-03) can pick targeted-consolidation candidates from real data instead of guessing. Extend the existing `ContextLoadCountListener` to a `total <N>` Line 1 format, introduce a new `ContextCacheKeyFingerprintListener` (TestExecutionListener auto-registered via `META-INF/spring.factories`) that records `<hex-hash>\t<mcc-display>` per `beforeTestClass` event into a PID-keyed sidecar marker, ship a shellcheck-clean aggregator script, document usage in `docs/test-performance.md`, and migrate the legacy aggregator loop to the new format.

## Goal-backward verification (must_haves)

- ✅ `ContextLoadCountListener` shutdown hook writes `total <N>` on Line 1 (D-08). Verified empirically: `head -1 target/test-perf/context-loads-*.txt` → `total 9`, `total 14`, `total 9`, `total 20` across 4 fork JVMs.
- ✅ `ContextCacheKeyFingerprintListener` is a `TestExecutionListener` registered via `META-INF/spring.factories` (D-07 hybrid surface, RESEARCH §RQ-3 Option A) — fires for ALL test classes with no per-class annotation.
- ✅ On `beforeTestClass`, the fingerprint listener records `<hex-hash>\t<mcc-display>` lines, where `<hex-hash>` = `Integer.toHexString(MergedContextConfiguration.hashCode())` (D-10) and `<mcc-display>` = `MergedContextConfiguration.toString()` truncated to 200 chars.
- ✅ Sidecar marker `target/test-perf/context-loads-{PID}-fingerprints.txt` carries the hash lines (RESEARCH §PATTERN sidecar approach — avoids JVM shutdown-hook ordering race).
- ✅ `scripts/test-perf/aggregate-fingerprints.sh` exists, executable, shellcheck-clean, emits Top-N clusters by `occurrence × cluster-size` (D-09).
- ✅ `docs/test-performance.md § PERF-02 Forensics` documents aggregator usage + sample Top-5 output + Phase 90 / Phase 86 cross-reference (D-16).
- ✅ Legacy aggregator loop in `docs/test-performance.md` migrated to `head -1 "$f" | awk '{print $2}'` extraction (D-08 backward-compat).
- ✅ Full `./mvnw clean verify --no-transfer-progress` exits 0: 1010 Surefire tests + 210 Failsafe tests = 1220 total, zero failures, zero errors, JaCoCo gate met, SpotBugs zero findings. Wallclock 08:11.

## Tasks completed

| Task | Commit | Outcome |
|------|--------|---------|
| 1 — `ContextLoadCountListener` `total <N>` format + Test | `13d0489a` | Single-line delta on the shutdown-hook write; new test asserts `"total " + count` matches `^total \d+$`. Existing counter test preserved. |
| 2 — `ContextCacheKeyFingerprintListener` + Test + `spring.factories` | `3a77a739` | New TestExecutionListener with cached reflection on `DefaultTestContext.mergedConfig`. Sidecar shutdown hook. Two unit tests: real-MCC shape match + 500-char truncation. `spring.factories` extended to 2 lines. |
| 3 — Combined listeners cross-check (`mvn verify -Dit.test='org.ctc.backup.**'`) | (no source change) | BUILD SUCCESS in 05:39. Primary markers all match `^total \d+$`; sidecars all match `^[0-9a-f]{1,8}\t.+$`; non-empty (47, 21, 26, 23 lines). Evidence in `/tmp/89-02-task-3-evidence.txt`. |
| 4 — `aggregate-fingerprints.sh` Top-N cluster report | `80d927e4` | Shellcheck-clean. awk-based hash bucketing, two-key sort, real-data smoke (29-occurrence top cluster) + synthetic smoke (3-occurrence `3fa2c1` → rank 1) both pass. |
| 5 — `docs/test-performance.md § PERF-02 Forensics` + aggregator migration | `b5951ea8` | New H2 section after `## Context Load Counts (PERF-02)`; aggregator loop migrated to `head -1 \| awk` extraction with sidecar exclusion; Phase 90 (PERF-03) + Phase 86 cross-reference present. |

Plan 89-02 final gate: `./mvnw clean verify --no-transfer-progress` BUILD SUCCESS (08:11 wallclock).

## Empirical instrumentation output (from final clean-verify run)

| Fork PID | Total contexts | Sidecar lines |
|----------|----------------|---------------|
| 94959 | 9 | 25 |
| 94960 | 14 | 76 |
| 98233 | 9 | 25 |
| 98234 | 20 | 61 |

Aggregate context-load count across all forks: **52** (down from the pre-Phase-89 baseline of 79–81 reported in `docs/test-performance.md § Context Load Counts (PERF-02)`). The reduction is a side-effect of Plan 89-01's per-fork isolation enabling `forkCount=2 reuseForks=true` cleanly across the whole suite (not a PERF-02 goal per se — the instrumentation is the deliverable here).

### Top-5 cache-key clusters from the clean-verify run

```text
1. ac9a4e12 — 29 occurrences across 29 classes (score=841)
   [WebMergedContextConfiguration@... testClass = db.migration.V5MigrationTest
2. 499c01dd — 18 occurrences across 18 classes (score=324)
   [WebMergedContextConfiguration@... testClass = db.migration.V4MigrationSmok
3. 3c6228fd — 9 occurrences across 9 classes (score=81)
   [WebMergedContextConfiguration@... testClass = org.ctc.admin.controller.Sea
4. 2cb78737 — 9 occurrences across 9 classes (score=81)
   [WebMergedContextConfiguration@... testClass = org.ctc.dataimport.CsvImport
5. d963dcc8 — 4 occurrences across 4 classes (score=16)
   [WebMergedContextConfiguration@... testClass = org.ctc.backup.AdminLayoutIT
```

**Implication for Phase 90 (PERF-03):** the `V5MigrationTest` cluster (29 classes sharing one cache key) is the single largest concentrate of reused context state in the suite — a natural first consolidation candidate. The `db.migration` family (V5 + V4 = 47 occurrences combined) dominates the fingerprint distribution.

## Decisions honored

- **D-07 (hybrid surface):** `ContextLoadCountListener` stays an `ApplicationContextInitializer` (count-only, exact pre-Phase-89 behavior except for the marker-file Line 1 format change); new `ContextCacheKeyFingerprintListener` is a separate `TestExecutionListener`. Both auto-register via `META-INF/spring.factories`.
- **D-08 (marker format migration):** primary marker Line 1 = `total <N>`; sidecar file carries the fingerprint lines. Aggregator loop in docs migrated to head-extraction with sidecar exclusion. 200-char display truncation honored.
- **D-09 (script-based aggregator):** `scripts/test-perf/aggregate-fingerprints.sh` is a real executable script (not an inline-doc snippet); follows `scripts/app.sh` conventions; shellcheck-clean.
- **D-10 (hash + truncation):** `Integer.toHexString(mcc.hashCode())` produces 1–8 hex chars (no leading zeros); display truncated to 200 chars.
- **D-14 (production unchanged):** all listeners live under `src/test/java/` and `src/test/resources/`; no production `application*.yml` touched; no production source touched.
- **D-16 (doc scope):** `docs/test-performance.md` updated; `CLAUDE.md` untouched; `README.md` updated by Plan 89-03 instead.
- **Planner-discretion sidecar approach** (RESEARCH §PATTERN): two listeners write to two distinct files at JVM shutdown — eliminates ordering race that would have plagued an "appended" single-file approach.

## Files modified

- `src/test/java/org/ctc/testsupport/ContextLoadCountListener.java` — single-line format change.
- `src/test/java/org/ctc/testsupport/ContextLoadCountListenerTest.java` — new format-contract assertion.
- `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListener.java` — NEW.
- `src/test/java/org/ctc/testsupport/ContextCacheKeyFingerprintListenerTest.java` — NEW.
- `src/test/resources/META-INF/spring.factories` — extended to 2 lines.
- `scripts/test-perf/aggregate-fingerprints.sh` — NEW (executable, shellcheck-clean).
- `docs/test-performance.md` — aggregator-loop migration + new `## PERF-02 Forensics` section.

Untouched (D-14 / D-16 invariants): `src/main/**`, `application*.yml`, `CLAUDE.md`, `README.md`.

## Cross-references

- **Plan 89-03 (Wave 3)** — consumes PERF-02 instrumentation in the Wave-4 idle-protocol measurements (3-run wallclock + context-load + Top-5 fingerprint).
- **Phase 90 (PERF-03)** — consumes the Top-N cluster output to choose the highest-fragmentation cluster for targeted consolidation. Initial signal: db.migration family (47 combined occurrences) is the standout candidate.
- **Phase 86 Lesson** — `[[86-02-SUMMARY]]` records the reverse failure mode (per-class `@DynamicPropertySource` split one shared cache key into seven). PERF-03's consolidation work is the corrective inverse.

## Reference

- [[89-CONTEXT]] — D-07..D-10, D-16
- [[89-RESEARCH]] — §RQ-2 (reflection), §RQ-3 (spring.factories), §RQ-7 (hash bucketing), §RQ-8 (aggregator skeleton), §RQ-9 (doc structure)
- [[89-PATTERNS]] — listener pattern lines 242-365, aggregator pattern 431-485, doc pattern 492-530
- [[89-VALIDATION]] — row 89-02-03 (combined-listeners validation)
- [[89-01-SUMMARY]] — predecessor plan; this plan rides on Plan 89-01's per-fork isolation
