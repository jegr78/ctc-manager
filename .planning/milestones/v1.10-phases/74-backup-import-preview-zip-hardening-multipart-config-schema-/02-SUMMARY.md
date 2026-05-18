---
phase: 74
plan: "02"
subsystem: backup-security
tags: [zip-hardening, security, secu-01, secu-02, primitives, unit-tests]
dependency_graph:
  requires: []
  provides:
    - BackupArchiveException (Reason enum — 8 canonical values)
    - BackupImportLimits (3 constants)
    - LimitedInputStream (FilterInputStream, LongConsumer callback)
    - PathTraversalGuard (assertWithin static helper)
  affects:
    - Plan 04 (BackupArchiveService.read* consumes all four primitives)
    - Plan 05 (BackupImportService.stage consumes NOT_A_ZIP + PathTraversalGuard)
    - Plan 06/08 (BackupController branches on Reason enum values)
tech_stack:
  added: []
  patterns:
    - FilterInputStream subclass for inflated-byte counting
    - toAbsolutePath().normalize().startsWith() path-traversal predicate (mirrors FileStorageService:153-158)
key_files:
  created:
    - src/main/java/org/ctc/backup/exception/BackupArchiveException.java
    - src/main/java/org/ctc/backup/service/BackupImportLimits.java
    - src/main/java/org/ctc/backup/io/LimitedInputStream.java
    - src/main/java/org/ctc/backup/security/PathTraversalGuard.java
    - src/test/java/org/ctc/backup/exception/BackupArchiveExceptionTest.java
    - src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java
    - src/test/java/org/ctc/backup/io/LimitedInputStreamTest.java
    - src/test/java/org/ctc/backup/security/PathTraversalGuardTest.java
  modified: []
decisions:
  - "Reason.PATH_TRAVERSAL instead of PATTERNS' ZIP_SLIP (planning context is authoritative; name avoids confusion in non-ZIP call sites)"
  - "Reason.SCHEMA_MISMATCH instead of SCHEMA_VERSION_MISMATCH (shorter form for cleaner call-site code)"
  - "MANIFEST_INVALID covers both manifest.json parse/shape failures AND data/*.json non-array failures (no separate DATA_NOT_ARRAY — UX is identical; Plan 04 reuses this Reason)"
  - "LongConsumer onClose replaces Runnable onLimitExceeded (Plan 04 needs final byte count for MAX_TOTAL_BYTES accumulator)"
  - "toAbsolutePath().normalize() not toRealPath() — matches FileStorageService:30 idiom; no IOException; symlink-TOCTOU accepted (T-74-02-05)"
metrics:
  duration_seconds: ~45
  completed_date: "2026-05-12"
  test_methods: 19
  test_runtime_seconds: "<5 (no Spring context)"
---

# Phase 74 Plan 02: ZIP Hardening Primitives Summary

Four pure-Java security primitives for ZIP-bomb and ZIP-Slip defense: `BackupArchiveException` (8-value `Reason` enum), `BackupImportLimits` (3 threshold constants), `LimitedInputStream` (inflated-byte `FilterInputStream` with `LongConsumer` callback), and `PathTraversalGuard` (path-traversal static helper).

## What Was Built

### Task 1 — `BackupArchiveException` + `BackupImportLimits` (commit `4f11169`)

**`BackupArchiveException`** — `RuntimeException` with public `Reason` enum.

Eight `Reason` values (canonical order, stable across all plans):

| # | Value | Consumer |
|---|-------|----------|
| 1 | `PATH_TRAVERSAL` | `PathTraversalGuard.assertWithin` |
| 2 | `ENTRY_TOO_LARGE` | `LimitedInputStream` |
| 3 | `TOTAL_TOO_LARGE` | Plan 04 `BackupArchiveService.read*` |
| 4 | `TOO_MANY_ENTRIES` | Plan 04 `BackupArchiveService.read*` |
| 5 | `MANIFEST_MISSING` | Plan 04 |
| 6 | `MANIFEST_INVALID` | Plan 04 (manifest.json + data/*.json non-array — dual scope) |
| 7 | `SCHEMA_MISMATCH` | Plan 04 |
| 8 | `NOT_A_ZIP` | Plan 05 magic-byte sniff |

Two constructors: `(Reason, String)` and `(Reason, String, Throwable)`. Accessor: `public Reason reason()`.

**`BackupImportLimits`** — utility constants holder (D-12 / SECU-02):

| Constant | Value | Literal expression |
|----------|-------|--------------------|
| `MAX_ENTRY_BYTES` | 52 428 800 | `50L * 1024 * 1024` |
| `MAX_TOTAL_BYTES` | 524 288 000 | `500L * 1024 * 1024` |
| `MAX_ENTRIES` | 50 000 | `50_000` |

Tests: `BackupArchiveExceptionTest` (2 methods), `BackupImportLimitsTest` (3 methods).

### Task 2 — `LimitedInputStream` (commit `3760e10`)

**Constructor signature (Plan 04 call site uses this verbatim):**
```
public LimitedInputStream(InputStream delegate, long limit, java.util.function.LongConsumer onClose)
```

**`onClose` contract — exactly once, callback-before-throw:**
- Success path: fires when `close()` is called (delivers final byte count).
- Limit-exceeded path: fires BEFORE `BackupArchiveException(ENTRY_TOO_LARGE)` is thrown.
- Idempotent: multiple `close()` calls fire at most once (`onCloseFired` guard).
- `null` is silently skipped.

Plan 04 canonical call site:
```java
new LimitedInputStream(zipInputStream, BackupImportLimits.MAX_ENTRY_BYTES,
    finalBytes -> totalInflatedAcc[0] += finalBytes)
```

Tests: `LimitedInputStreamTest` (6 methods — under-limit, success-close, single-byte limit trip, bulk-read crossover, double-close idempotency, null-callback guard).

### Task 3 — `PathTraversalGuard` (commit `35c69f8`)

**Method signature:**
```java
public static void assertWithin(Path baseDir, String candidateEntryName)
```

**Predicate (bit-identical to `FileStorageService:153-158`):**
```java
Path absoluteBase = baseDir.toAbsolutePath().normalize();
Path resolved = absoluteBase.resolve(candidateEntryName).normalize();
resolved.startsWith(absoluteBase)
```

Rejects:
- Absolute entry names (e.g. `/etc/passwd`) → `BackupArchiveException(PATH_TRAVERSAL, "Absolute path rejected: ...")`.
- Traversal names (e.g. `../../etc/passwd`) → `BackupArchiveException(PATH_TRAVERSAL, "Path traversal detected: ...")`.
- `null`/empty inputs → `IllegalArgumentException` (programmer-error guard, not security event).

Tests: `PathTraversalGuardTest` (8 methods — safe relative, dot-dot, absolute, nested safe, null base, null candidate, empty candidate, "." edge case).

## All Tests Green

```
./mvnw -q -Dtest='BackupArchiveExceptionTest,BackupImportLimitsTest,LimitedInputStreamTest,PathTraversalGuardTest' test
```

BUILD SUCCESS — 19 test methods total (2 + 3 + 6 + 8). Runtime < 5 s (no Spring context loaded).

## Deviations from Plan

None — plan executed exactly as written. All contracts, constructor signatures, enum values, and predicate semantics match the plan specifications verbatim.

## Threat Surface Scan

No new network endpoints, auth paths, or schema changes introduced. All four classes are pure POJO utilities with no Spring annotations. Threat mitigations T-74-02-01 through T-74-02-05 are delivered per the plan's threat register.

## Known Stubs

None — these are pure primitives with no UI and no data sources. No stub patterns present.

## Self-Check: PASSED

- `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` — FOUND
- `src/main/java/org/ctc/backup/service/BackupImportLimits.java` — FOUND
- `src/main/java/org/ctc/backup/io/LimitedInputStream.java` — FOUND
- `src/main/java/org/ctc/backup/security/PathTraversalGuard.java` — FOUND
- `src/test/java/org/ctc/backup/exception/BackupArchiveExceptionTest.java` — FOUND
- `src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java` — FOUND
- `src/test/java/org/ctc/backup/io/LimitedInputStreamTest.java` — FOUND
- `src/test/java/org/ctc/backup/security/PathTraversalGuardTest.java` — FOUND
- Commit `4f11169` — Task 1
- Commit `3760e10` — Task 2
- Commit `35c69f8` — Task 3
