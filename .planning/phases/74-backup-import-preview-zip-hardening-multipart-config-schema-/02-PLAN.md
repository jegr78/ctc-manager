---
id: "02"
title: "ZIP hardening primitives"
phase: 74
plan: "02"
type: execute
wave: 1
depends_on: []
requirements: [SECU-01, SECU-02]
files_modified:
  - src/main/java/org/ctc/backup/service/BackupImportLimits.java
  - src/main/java/org/ctc/backup/io/LimitedInputStream.java
  - src/main/java/org/ctc/backup/security/PathTraversalGuard.java
  - src/main/java/org/ctc/backup/exception/BackupArchiveException.java
  - src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java
  - src/test/java/org/ctc/backup/io/LimitedInputStreamTest.java
  - src/test/java/org/ctc/backup/security/PathTraversalGuardTest.java
  - src/test/java/org/ctc/backup/exception/BackupArchiveExceptionTest.java
autonomous: true

must_haves:
  truths:
    - "Per-entry inflated-byte counting throws when actual bytes read exceed `BackupImportLimits.MAX_ENTRY_BYTES` (52 428 800)."
    - "Total-archive byte counting throws when running total exceeds `BackupImportLimits.MAX_TOTAL_BYTES` (524 288 000)."
    - "Entry-count cap is `BackupImportLimits.MAX_ENTRIES = 50_000` (enforced by callers in plans 04/05; constant is fixed here)."
    - "Path-traversal helper rejects entry names that escape the base directory after normalization, AND rejects absolute paths."
    - "All four primitives are pure-Java POJOs (no `@Component`, no `@Service`, no Spring context) so they can be unit-tested with plain JUnit 5 / Surefire."
  artifacts:
    - path: "src/main/java/org/ctc/backup/service/BackupImportLimits.java"
      provides: "Constants holder for ZIP-bomb defense thresholds (D-12)."
      contains: "public static final long MAX_ENTRY_BYTES"
    - path: "src/main/java/org/ctc/backup/io/LimitedInputStream.java"
      provides: "FilterInputStream that throws `BackupArchiveException(Reason.ENTRY_TOO_LARGE)` once the running byte count exceeds a configurable limit."
      contains: "extends java.io.FilterInputStream"
    - path: "src/main/java/org/ctc/backup/security/PathTraversalGuard.java"
      provides: "Static helper that validates a ZIP-entry name resolves inside a base directory (D-11 extract of `FileStorageService:153-158`)."
      contains: "public static void assertWithin"
    - path: "src/main/java/org/ctc/backup/exception/BackupArchiveException.java"
      provides: "Runtime exception with `Reason` enum used by every hardening primitive and caller (plans 04, 05)."
      contains: "public enum Reason"
  key_links:
    - from: "src/main/java/org/ctc/backup/io/LimitedInputStream.java"
      to: "src/main/java/org/ctc/backup/exception/BackupArchiveException.java"
      via: "throw new BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)"
      pattern: "throw new BackupArchiveException\\(Reason\\.ENTRY_TOO_LARGE"
    - from: "src/main/java/org/ctc/backup/security/PathTraversalGuard.java"
      to: "src/main/java/org/ctc/backup/exception/BackupArchiveException.java"
      via: "throw new BackupArchiveException(Reason.PATH_TRAVERSAL, ...)"
      pattern: "throw new BackupArchiveException\\(Reason\\.PATH_TRAVERSAL"
---

# 74-02 — ZIP Hardening Primitives

<objective>
Provide the four pure-Java primitives that every later plan (04 = `BackupArchiveService.read*`, 05 = `BackupImportService.stage(...)`, hardening ITs) consumes:

1. `BackupImportLimits` — the three numeric constants from D-12 (50 MB / 500 MB / 50 000) in one well-named place.
2. `LimitedInputStream` — a `FilterInputStream` that counts inflated bytes and throws after a configurable limit. This is the only defense against the "header says 1 KB but inflates to 5 GB" attack — `ZipEntry.getSize()` returns the spoofable central-directory value, not the truth (CONTEXT §specifics, D-12).
3. `PathTraversalGuard.assertWithin(Path, String)` — extracted form of the `FileStorageService:153-158` idiom (D-11). Lives in its own class instead of inline so the `BackupArchiveService` reader can call it without pulling `org.ctc.domain.service` into `org.ctc.backup.service` (planner's D-11 discretion — OWN CLASS for reuse + unit-test isolation).
4. `BackupArchiveException` — a `RuntimeException` with a public `Reason` enum. Every reject path in this milestone routes through it; `BackupController` will branch on `reason()` to pick the locked D-02 Flash strings.

Purpose: isolate the security-critical primitives in tightly-scoped, unit-tested units so plans 04/05 can wire them in without re-validating the math or the path-traversal semantics. SECU-01 (ZIP-Slip) and SECU-02 (ZIP-bomb) gain their structural defense layer here.

Output: 4 new production classes (no Spring annotations, no transitive runtime dependencies beyond JDK) + 4 new Surefire unit tests covering the three constants, the limit-trip math (including the boundary case where a bulk read crosses the limit mid-chunk), the path-traversal accept/reject matrix, and the exception-`Reason` enum sanity check.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md
@.planning/REQUIREMENTS.md

<interfaces>
<!-- Existing code the executor will read but NOT modify. These are the anchor points the new primitives mirror. -->

From `src/main/java/org/ctc/domain/service/FileStorageService.java:25` — the constants idiom this plan extends:
- `private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;` (single-file uploads, unrelated to backups; cited only as the literal-multiplication style for the new `BackupImportLimits`).

From `src/main/java/org/ctc/domain/service/FileStorageService.java:153-158` — the canonical SECU-02 path-traversal idiom (D-11 says this plan extracts it, NOT duplicates it):
- `private void validatePathWithinUploadDir(Path target) { Path normalized = target.toAbsolutePath().normalize(); if (!normalized.startsWith(uploadDir)) { log.warn("Attempted path traversal: {}", target); throw new IllegalArgumentException("Path traversal detected: " + target); } }`
- New `PathTraversalGuard.assertWithin` reproduces the same `toAbsolutePath().normalize().startsWith(baseDir)` predicate, but throws `BackupArchiveException(Reason.PATH_TRAVERSAL, ...)` instead of `IllegalArgumentException` so the controller can route by `Reason` to the locked D-02#3 Flash string.

From `src/main/java/org/ctc/backup/service/BackupArchiveService.java:121-132` — the export-side ZIP-Slip defense (skip-with-WARN; the import side will fail-loud through `PathTraversalGuard`):
- `if (entry.relativePath().contains("..")) { log.warn("Skipping suspicious upload path during export: {}", entry.relativePath()); skippedTraversal++; continue; }`
- Phase 74 import-side mirror lives in `PathTraversalGuard.assertWithin` — NOT in this plan's callers. The import path throws instead of skipping because malicious input on the read side must fail loud (cited as design intent in PATTERNS §"BackupArchiveService extension").

From `src/main/java/org/ctc/domain/exception/BusinessRuleException.java` — the existing `RuntimeException` template (PATTERNS §"BackupArchiveException"):
- `public class BusinessRuleException extends RuntimeException { public BusinessRuleException(String message) { super(message); } }`
- The new exception copies the structure verbatim and adds a `Reason` enum field exposed via `reason()`.

REQUIREMENTS.md acceptance criteria (verbatim from §SECU):
- **SECU-01**: "ZIP-Slip-Defense: jeder ZipEntry-Path wird gegen `uploadDir.toRealPath()` validiert (`startsWith`-Check); absolute Paths und `..` werden abgelehnt; Nutzung der bestehenden `FileStorageService.store()` SECU-02-Defense aus v1.1 (Wiederverwendung statt Duplikat)" — this plan delivers the **reuse** half (extract a shared predicate); the call sites that **apply** it land in plan 04 (`BackupArchiveService.read*`) and plan 05 (`BackupImportService.stage`).
- **SECU-02**: "ZipBomb-Defense: per-Entry max 50 MB (uncompressed), total max 500 MB, max 50.000 Entries; bei Überschreitung HTTP 400 + Flash-Message" — this plan delivers the three CONSTANTS and the per-entry COUNTING MECHANISM (`LimitedInputStream`); the total-byte + entry-count counters live in plan 04 (`BackupArchiveService.read*`); the HTTP 400 + Flash wiring lives in plan 05/06.
</interfaces>
</context>

<tasks>

<task type="auto" id="74-02-01">
  <name>Task 1: Add `BackupArchiveException` with `Reason` enum + `BackupImportLimits` constants holder</name>
  <files>
    src/main/java/org/ctc/backup/exception/BackupArchiveException.java
    src/main/java/org/ctc/backup/service/BackupImportLimits.java
    src/test/java/org/ctc/backup/exception/BackupArchiveExceptionTest.java
    src/test/java/org/ctc/backup/service/BackupImportLimitsTest.java
  </files>
  <read_first>
    - src/main/java/org/ctc/domain/exception/BusinessRuleException.java — exact structural template (extends `RuntimeException`, one `String` constructor, no extra state).
    - src/main/java/org/ctc/domain/service/FileStorageService.java:25 — literal-multiplication style for byte-size constants (`10 * 1024 * 1024`).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"`org.ctc.backup.exception.BackupArchiveException`" — the eight-value `Reason` enum draft (`SCHEMA_VERSION_MISMATCH`, `ZIP_SLIP`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_PARSE_FAILED`, `MANIFEST_MISSING`, plus this plan adds `SCHEMA_MISMATCH` (alias-friendly) / `NOT_A_ZIP` per planning_context). NOTE: PATTERNS used `ZIP_SLIP`; planning_context specifies `PATH_TRAVERSAL`. **Use `PATH_TRAVERSAL`** (the name in the planning context is canonical for this plan; PATTERNS pre-dated the rename).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §D-12 — locked numeric constants (50 MB / 500 MB / 50 000).
  </read_first>
  <action>
    Create `org.ctc.backup.exception.BackupArchiveException` as a `public class BackupArchiveException extends RuntimeException`. Add a public enum `Reason` with exactly these values, in this order: `PATH_TRAVERSAL`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_MISSING`, `MANIFEST_INVALID`, `SCHEMA_MISMATCH`, `NOT_A_ZIP`. Add a `private final Reason reason` field. Provide two constructors:
    1. `public BackupArchiveException(Reason reason, String message) { super(message); this.reason = reason; }`
    2. `public BackupArchiveException(Reason reason, String message, Throwable cause) { super(message, cause); this.reason = reason; }`
    Expose the reason via `public Reason reason() { return reason; }` (Lombok-free accessor — keep this class POJO).

    **`MANIFEST_INVALID` semantic scope (revision per checker Issue 6):** `Reason.MANIFEST_INVALID` covers ANY JSON structural rejection inside the backup archive — the `manifest.json` file itself (parse failure, missing required field, wrong shape) AND any `data/*.json` entry whose top-level token is not a `START_ARRAY`. Plan 04's `countDataEntries` reuses this `Reason` when it asserts the first JSON token of a `data/<entity>.json` entry is `START_ARRAY` and the assertion fails. There is intentionally no separate `DATA_NOT_ARRAY` enum value — the user-facing Flash routing (Plan 08 `mapReason`) folds `MANIFEST_INVALID` into the generic safety-checks message D-02#3, so the additional fan-out would not change the UX. Document this dual scope in the enum's Javadoc on the `MANIFEST_INVALID` constant so the next maintainer does not "narrow" the meaning when they see only manifest-targeted call sites.

    **`NOT_A_ZIP` ownership (revision per checker side-edit):** the enum value `NOT_A_ZIP` is canonical here in Plan 02 — it is the eighth value listed in the action above (position 8 of 8). Plan 05's magic-byte sniff in `BackupImportService.stage(...)` throws `BackupArchiveException(Reason.NOT_A_ZIP, "...")` when the first four bytes are not `0x50 0x4B 0x03 0x04`. Plan 02 owns the enum constant; Plan 05 owns the call site. Plan 05's own notes acknowledged the value might need to be added as a side-edit if Plan 02 omitted it — Plan 02 does NOT omit it.

    Create `org.ctc.backup.service.BackupImportLimits` as a `public final class BackupImportLimits` with a `private BackupImportLimits() { /* utility */ }` constructor. Declare three `public static final` constants with the EXACT literal expressions below (so a code-review can read the multiplication and verify the math without consulting comments):
    - `public static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;` (resolves to 52 428 800)
    - `public static final long MAX_TOTAL_BYTES = 500L * 1024 * 1024;` (resolves to 524 288 000)
    - `public static final int MAX_ENTRIES = 50_000;`
    Add a class-level Javadoc citing CONTEXT §D-12 and SECU-02 as the source of these literal values; cite that `LimitedInputStream` consumes `MAX_ENTRY_BYTES` and that `BackupArchiveService.read*` (plan 04) consumes `MAX_TOTAL_BYTES` + `MAX_ENTRIES`.

    Create `org.ctc.backup.exception.BackupArchiveExceptionTest` with one test method `givenEachReason_whenConstruct_thenReasonAndMessagePropagate()` that loops over `BackupArchiveException.Reason.values()`, constructs an exception with a synthetic message `"reason=" + reason.name()`, asserts `ex.reason() == reason` AND `ex.getMessage().equals("reason=" + reason.name())` for every enum value. This implicitly enforces the eight-value enum stays at eight values — if a future change adds or removes a `Reason`, the test still passes structurally, but explicit count assertion `assertThat(Reason.values()).hasSize(8)` MUST also be present so accidental drift fails loudly. Add second test `givenReasonAndMessageAndCause_whenConstruct_thenCausePreserved()` covering the two-arg + three-arg constructors (cause non-null, `ex.getCause()` returns the same instance).

    Create `org.ctc.backup.service.BackupImportLimitsTest` with three test methods (all `static final` constant reads, no `@SpringBootTest`):
    - `givenMaxEntryBytes_whenRead_thenEquals52428800()` — `assertThat(BackupImportLimits.MAX_ENTRY_BYTES).isEqualTo(52_428_800L);`
    - `givenMaxTotalBytes_whenRead_thenEquals524288000()` — `assertThat(BackupImportLimits.MAX_TOTAL_BYTES).isEqualTo(524_288_000L);`
    - `givenMaxEntries_whenRead_thenEquals50000()` — `assertThat(BackupImportLimits.MAX_ENTRIES).isEqualTo(50_000);`
    Tests live under `src/test/java/...`; package mirrors source package. Use AssertJ (already on the test classpath) and JUnit 5 `@Test`. No Spring context; pure-Surefire.

    Add `@Slf4j` to NEITHER class — both are pure data carriers, no logging belongs here. Do not place any fenced code blocks in caller comments; the JDK reads the field, not commentary.
  </action>
  <verify>
    <automated>./mvnw -q -Dtest='BackupArchiveExceptionTest,BackupImportLimitsTest' test</automated>
  </verify>
  <acceptance_criteria>
    - File `src/main/java/org/ctc/backup/exception/BackupArchiveException.java` compiles and contains exactly the eight `Reason` values listed in the action (in the order listed). Verify with `grep -c '^[[:space:]]*\(PATH_TRAVERSAL\|ENTRY_TOO_LARGE\|TOTAL_TOO_LARGE\|TOO_MANY_ENTRIES\|MANIFEST_MISSING\|MANIFEST_INVALID\|SCHEMA_MISMATCH\|NOT_A_ZIP\)' src/main/java/org/ctc/backup/exception/BackupArchiveException.java` returns `8`.
    - `NOT_A_ZIP` is present in the enum body (`grep -v '^[[:space:]]*//' src/main/java/org/ctc/backup/exception/BackupArchiveException.java | grep -v '^[[:space:]]*\*' | grep -c 'NOT_A_ZIP'` returns at least `1`) so Plan 05's magic-byte sniff compiles against the canonical constant.
    - `BackupArchiveException.Reason.values().length == 8` (enforced by `BackupArchiveExceptionTest`).
    - `BackupImportLimits.MAX_ENTRY_BYTES == 52_428_800L`, `MAX_TOTAL_BYTES == 524_288_000L`, `MAX_ENTRIES == 50_000` (enforced by `BackupImportLimitsTest`).
    - No `@Component`, `@Service`, or `@Configuration` on either production class (`grep -nE '@(Component|Service|Configuration)' src/main/java/org/ctc/backup/exception/BackupArchiveException.java src/main/java/org/ctc/backup/service/BackupImportLimits.java` returns zero matches; run after stripping comment lines via `grep -v '^[[:space:]]*//' | grep -v '^[[:space:]]*\*'`).
    - All four test methods green (1 `Reason` propagation test + 1 cause test = 2 in `BackupArchiveExceptionTest`; 3 constant tests in `BackupImportLimitsTest`).
  </acceptance_criteria>
  <done>
    Both production classes compile, both test classes pass with `./mvnw -Dtest='BackupArchiveExceptionTest,BackupImportLimitsTest' test`, and no Spring annotations leak into either class.
  </done>
</task>

<task type="auto" id="74-02-02">
  <name>Task 2: Add `LimitedInputStream` (FilterInputStream that throws after a configurable inflated-byte limit) + unit tests</name>
  <files>
    src/main/java/org/ctc/backup/io/LimitedInputStream.java
    src/test/java/org/ctc/backup/io/LimitedInputStreamTest.java
  </files>
  <read_first>
    - src/main/java/org/ctc/backup/exception/BackupArchiveException.java (just authored in task 1) — for the `Reason.ENTRY_TOO_LARGE` reference.
    - src/main/java/org/ctc/backup/service/BackupImportLimits.java (just authored in task 1) — for the `MAX_ENTRY_BYTES` default reference (callers will inject this; the class itself stays parameter-driven).
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §specifics — the `ZipEntry.getSize()` trust problem: "Malicious ZIPs can lie in the central directory — claim 1 KB but inflate to 5 GB. Defense is to count actual bytes read from `InflaterInputStream` against `MAX_ENTRY_BYTES`."
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"No Analog Found" — confirms no `FilterInputStream` exists in the repo; the ~25-LOC hand-rolled implementation pattern is the canonical reference.
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/04-PLAN.md §"Task 1 / behavior" — Plan 04 builds the `MAX_TOTAL_BYTES` running tally by capturing the final inflated-byte count of EVERY entry (success and limit-exceeded) via the constructor callback. This dictates the callback signature: `LongConsumer onClose` that fires exactly once per stream with the final byte count.
  </read_first>
  <action>
    Create package `org.ctc.backup.io` (new — no existing files in this package). Create `LimitedInputStream` as `public final class LimitedInputStream extends java.io.FilterInputStream`.

    **Constructor signature (revision per checker Issue 3 — REPLACES the previous `Runnable onLimitExceeded` form):**

    `public LimitedInputStream(InputStream delegate, long limit, java.util.function.LongConsumer onClose)`

    Field setup:
    - `private final long limit;` — the maximum number of bytes that may pass through `read()`/`read(byte[],int,int)` before the stream throws. Caller passes `BackupImportLimits.MAX_ENTRY_BYTES` (or any value for test isolation).
    - `private final java.util.function.LongConsumer onClose;` — callback invoked EXACTLY ONCE with the final inflated byte count. Fires when either (a) `close()` is invoked normally after a successful drain, OR (b) `read()` / `read(byte[],int,int)` is about to throw `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)` because `count > limit`. May be `null` — `null` is silently skipped (defensive, simplifies tests).
    - `private long count;` — running byte count, monotonically increasing.
    - `private boolean onCloseFired;` — guard that ensures `onClose` is invoked at most once across the success path AND the limit-exceeded path, regardless of how many times `close()` is called afterwards.

    **Semantics (the contract `LimitedInputStreamTest` enforces — Plan 04 depends on these guarantees verbatim):**

    - `onClose` ALWAYS fires once with the final `count` value. The value is the EXACT number of inflated bytes the stream produced, including the byte(s) that pushed `count` past `limit` on the throw path. Plan 04 uses this number to update the `MAX_TOTAL_BYTES` accumulator AFTER every entry — including entries that tripped the per-entry cap — so the total-archive defense remains accurate even after a per-entry failure (the controller can log the cumulative inflated bytes seen so far).

    - On the limit-exceeded path, the callback fires FIRST, the throw fires SECOND. This ordering is observable from the caller's perspective: a caller can install a callback that records `lastCount = bytes` and then catch the exception — `lastCount` will be populated. Plan 04 exploits this.

    - `close()` invokes `onClose` (if non-null and not yet fired) with the current `count`, then delegates to `super.close()`. Multiple `close()` calls (e.g. via try-with-resources nesting) MUST NOT fire the callback more than once — guarded by `onCloseFired`.

    Required behaviour:

    - `read()` (single-byte read): delegate to `super.read()`; if the result is `-1` (EOF), return `-1`. Otherwise increment `count` by `1`. If `count > limit`: fire `onClose` with `count` (set `onCloseFired = true` first), then throw `new BackupArchiveException(Reason.ENTRY_TOO_LARGE, "Entry exceeds limit: limit=" + limit + " bytes")`. Otherwise return the byte (as `int 0..255`).

    - `read(byte[] b, int off, int len)` (bulk read): delegate to `super.read(b, off, len)`; if the result is `-1`, return `-1`. Otherwise increment `count` by the result. If `count > limit`: fire `onClose` with `count` (set `onCloseFired = true` first), then throw `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)`. Otherwise return the actual byte count. Note: the exception is thrown ON THE CHUNK THAT CROSSES THE LIMIT — bytes that were already read into `b[]` before the limit was crossed are NOT rolled back. The exception's message MUST cite `limit` (not `count`) so leaked information stays minimal.

    - `read(byte[] b)`: do NOT override (inherits `FilterInputStream`'s default which delegates to the three-arg variant on the same instance — meaning the override above catches it correctly).

    - `close()`: OVERRIDE. Body: `if (!onCloseFired && onClose != null) { onCloseFired = true; onClose.accept(count); } super.close();`. Even when `onClose == null`, set `onCloseFired = true` to keep semantics uniform for tests.

    - `skip(long n)` / `mark` / `reset` / `available()`: do NOT override — `FilterInputStream` defaults are correct.

    - Mark the class `final` so subclasses cannot bypass the counter by overriding `read`.

    - Add a class-level Javadoc citing CONTEXT §D-12 and §specifics ("The `ZipEntry.getSize()` trust problem") as the rationale; cite that the standard call site is `new LimitedInputStream(zipInputStream, BackupImportLimits.MAX_ENTRY_BYTES, finalBytes -> totalInflatedAcc[0] += finalBytes)` (plan 04). Explicitly document the "exactly once" guarantee for `onClose` and the "callback first, throw second" ordering on the limit-exceeded path.

    Create `org.ctc.backup.io.LimitedInputStreamTest` (Surefire, no Spring context) with the following tests:

    1. `givenStreamUnderLimit_whenRead_thenAllBytesReturned()` — wrap a 1 024-byte `ByteArrayInputStream` with `new LimitedInputStream(in, 2_048L, null)`; read fully via a 256-byte buffer in a loop; assert exactly 1 024 bytes consumed AND `read()` returns `-1` at EOF AND no exception thrown.

    2. `givenStreamUnderLimit_whenClose_thenOnCloseFiresWithFinalByteCount()` — wrap a 1 024-byte `ByteArrayInputStream` with a `LongConsumer` that records into `long[] captured = new long[]{-1L}`; loop-read fully (count = 1024); then call `limited.close()`; assert `captured[0] == 1024L`. (Success-path contract: `onClose` delivers the final byte count.)

    3. `givenStreamExceedingLimit_whenRead_thenOnCloseFiresWithLimitPlusOne_thenThrows()` — wrap a 1 024-byte `ByteArrayInputStream` with limit `512L` and a `LongConsumer` that records into `long[] captured = new long[]{-1L}`. Loop-read single bytes via `read()`. Use AssertJ's `assertThatThrownBy(() -> { while (limited.read() != -1) {} })`. Assert: type is `BackupArchiveException`, `ex.reason() == Reason.ENTRY_TOO_LARGE`, `ex.getMessage()` contains the literal string `"limit=512"`, AND `captured[0] == 513L` (limit + 1 — the single byte that crossed the limit). The callback MUST have fired BEFORE the exception was thrown — `captured[0]` being non-`-1L` at the catch site proves this ordering.

    4. `givenBulkReadCrossesLimit_whenRead_thenThrowsOnTheChunkThatCrossesLimit_andOnCloseFires()` — wrap a 2 048-byte `ByteArrayInputStream` with `new LimitedInputStream(in, 1_500L, captured::accept)` where `captured` is the same `long[]` accumulator pattern. Read with a 1 024-byte buffer. First call returns 1 024 bytes (cumulative = 1 024, under limit). Second call: assert it throws `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)`. Assert `captured[0] == 2048L` (1024 from chunk 1 + 1024 from chunk 2 — the bulk-read accounting credits the FULL second chunk because the byte count is tallied against the bytes that `super.read` actually placed into the buffer). Cite in a `// then` comment that bytes already in the buffer when the limit is crossed are intentionally not rolled back (defense-in-depth lives one level up — the caller discards the partial entry).

    5. `givenOnCloseCallback_whenCloseCalledTwice_thenCallbackFiresOnlyOnce()` — install a `LongConsumer` that increments `AtomicInteger fireCount`; close the stream twice (idempotency guard); assert `fireCount.get() == 1`. Edge case for try-with-resources nesting where an outer block calls `close()` after the inner block already did.

    6. `givenNullOnClose_whenLimitExceeded_thenThrowsWithoutNullPointerException()` — wrap a 1 024-byte stream with limit `512L` and `null` for `onClose`. Loop-read single bytes. Assert `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)` is thrown (NOT `NullPointerException`). Defensive-null contract enforced.

    All tests use Given-When-Then naming and `// given` / `// when` / `// then` comments (CLAUDE.md "Test Naming" + PATTERNS §"Pattern E").
  </action>
  <verify>
    <automated>./mvnw -q -Dtest='LimitedInputStreamTest' test</automated>
  </verify>
  <acceptance_criteria>
    - `src/main/java/org/ctc/backup/io/LimitedInputStream.java` compiles, is `final`, and extends `java.io.FilterInputStream`.
    - Constructor signature is exactly `public LimitedInputStream(InputStream delegate, long limit, java.util.function.LongConsumer onClose)`. Verify with `grep -c 'LimitedInputStream(InputStream[^,]*,[[:space:]]*long[^,]*,[[:space:]]*\(java\.util\.function\.\)\{0,1\}LongConsumer' src/main/java/org/ctc/backup/io/LimitedInputStream.java` returns at least `1`.
    - `java.util.function.LongConsumer` import (or fully qualified usage) is present. Verify with `grep -c 'LongConsumer' src/main/java/org/ctc/backup/io/LimitedInputStream.java` returns at least `2` (one for the import or FQN at the field, one at the constructor — comments stripped via `grep -v '^[[:space:]]*\(//\|\*\)'`).
    - No `Runnable` reference appears in the production class. Verify with `grep -v '^[[:space:]]*\(//\|\*\)' src/main/java/org/ctc/backup/io/LimitedInputStream.java | grep -c 'Runnable'` returns `0`.
    - Both `read()` and `read(byte[], int, int)` increment the byte counter, fire the `LongConsumer` (when non-null) with the post-increment `count`, and throw `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)` when the running total exceeds `limit`. The callback fires BEFORE the throw on the limit-exceeded path.
    - `close()` is overridden and guards the callback with a `onCloseFired` flag so multiple `close()` invocations fire `onClose` at most once.
    - No call to `super.read(byte[])` (the two-arg variant is not overridden — `grep -c 'public int read(byte\[\] b)' src/main/java/org/ctc/backup/io/LimitedInputStream.java` returns `0`).
    - All six test methods in `LimitedInputStreamTest` green.
  </acceptance_criteria>
  <done>
    `./mvnw -Dtest='LimitedInputStreamTest' test` passes; all six behaviour tests green; class is `final`, has no Spring annotations, and exposes the `LongConsumer onClose` contract Plan 04 depends on.
  </done>
</task>

<task type="auto" id="74-02-03">
  <name>Task 3: Add `PathTraversalGuard.assertWithin(Path, String)` static helper + path-traversal accept/reject matrix tests</name>
  <files>
    src/main/java/org/ctc/backup/security/PathTraversalGuard.java
    src/test/java/org/ctc/backup/security/PathTraversalGuardTest.java
  </files>
  <read_first>
    - src/main/java/org/ctc/domain/service/FileStorageService.java:153-158 — the canonical idiom this class extracts. Predicate: `target.toAbsolutePath().normalize().startsWith(baseDir)` where `baseDir` is itself already absolute-normalized.
    - src/main/java/org/ctc/backup/exception/BackupArchiveException.java (from task 1) — for `Reason.PATH_TRAVERSAL`.
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md §D-11 — locks the planner's discretion choice ("OWN CLASS for reuse + unit-test isolation"). Predicate must match `FileStorageService:153-158` semantics exactly so SECU-01 is provably reused (REQUIREMENTS verbatim: "Wiederverwendung statt Duplikat").
    - .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md §"`org.ctc.backup.security.PathTraversalGuard`" — pattern guidance.
  </read_first>
  <action>
    Create package `org.ctc.backup.security` (new). Create `PathTraversalGuard` as `public final class PathTraversalGuard` with a `private PathTraversalGuard() { /* utility */ }` constructor (utility class — no instances).

    Single public static method:

    `public static void assertWithin(Path baseDir, String candidateEntryName)`

    Behaviour (the test class enforces every branch):

    1. If `baseDir == null` OR `candidateEntryName == null` OR `candidateEntryName.isEmpty()` → throw `IllegalArgumentException("baseDir and candidateEntryName must be non-null/non-empty")`. (Programmer-error guard — fires before any path math; not a `BackupArchiveException` because it's not a security event.)

    2. Compute `Path absoluteBase = baseDir.toAbsolutePath().normalize();` — caller may pass a relative `baseDir` (e.g. `Paths.get("data/dev/backup-staging")`); we resolve once here so the predicate is deterministic across callers. Do NOT call `toRealPath()` — `toRealPath()` requires the directory to exist on disk AND throws `IOException` for non-existent paths; the unit tests run against `@TempDir` paths that DO exist, but the production path `FileStorageService:30` uses `toAbsolutePath().normalize()` (NOT `toRealPath()`) — this class follows the same pattern verbatim so the semantics are bit-identical with the existing SECU-02 defense.

    3. Reject ABSOLUTE entry names: if `Paths.get(candidateEntryName).isAbsolute()` → throw `BackupArchiveException(Reason.PATH_TRAVERSAL, "Absolute path rejected: " + candidateEntryName)`. (Defends against entries like `/etc/passwd` even if they happen to normalize to a path inside `baseDir` — RESEARCH calls this an explicit reject.)

    4. Compute `Path resolved = absoluteBase.resolve(candidateEntryName).normalize();` — `resolve` honours relative path segments (including `..`), `normalize` collapses them. After normalization, a malicious `../../etc/passwd` lands outside `absoluteBase`.

    5. If `!resolved.startsWith(absoluteBase)` → throw `BackupArchiveException(Reason.PATH_TRAVERSAL, "Path traversal detected: candidate=" + candidateEntryName + " baseDir=" + absoluteBase)`. Otherwise return silently.

    6. The class is `@Slf4j`-free — no logging from a utility (callers log via their own logger when they catch the exception; matches `FileStorageService:155` pattern only loosely — `FileStorageService` logs because it owns the I/O context; the guard does not).

    Create `org.ctc.backup.security.PathTraversalGuardTest` with the following test methods (use JUnit 5 `@TempDir Path tempDir` for the base directory):

    1. `givenSafeRelativeName_whenAssertWithin_thenPasses()` — `assertWithin(tempDir, "manifest.json")` and `assertWithin(tempDir, "data/seasons.json")` both return without exception.

    2. `givenDotDotEntry_whenAssertWithin_thenThrowsPathTraversal()` — `assertThatThrownBy(() -> assertWithin(tempDir, "../../etc/passwd")).isInstanceOf(BackupArchiveException.class)` AND assert `ex.reason() == Reason.PATH_TRAVERSAL` AND message contains `"Path traversal detected"`.

    3. `givenAbsoluteEntry_whenAssertWithin_thenThrowsPathTraversal()` — on Unix: `assertWithin(tempDir, "/etc/passwd")`. Use `assertThatThrownBy(...)`; assert `Reason.PATH_TRAVERSAL` AND message contains `"Absolute path rejected"`. (On Windows, the predicate `Paths.get("/etc/passwd").isAbsolute()` returns `false`; the CI runs on `ubuntu-latest` (verified in `.github/workflows/ci.yml`) so the Unix-only assertion is safe. Add a `// then — Unix-shaped absolute path; CI runs on Ubuntu so this is reliable` comment.)

    4. `givenNestedSafePath_whenAssertWithin_thenPasses()` — `assertWithin(tempDir, "uploads/races/abc/photo.png")` returns without exception (the import side uses this path shape for `uploads/`-mirror entries per BackupArchiveService:127).

    5. `givenNullBaseDir_whenAssertWithin_thenThrowsIllegalArgument()` — `assertThatThrownBy(() -> assertWithin(null, "x")).isInstanceOf(IllegalArgumentException.class)`.

    6. `givenNullCandidate_whenAssertWithin_thenThrowsIllegalArgument()` — symmetric: `assertThatThrownBy(() -> assertWithin(tempDir, null)).isInstanceOf(IllegalArgumentException.class)`.

    7. `givenEmptyCandidate_whenAssertWithin_thenThrowsIllegalArgument()` — `assertWithin(tempDir, "")` throws `IllegalArgumentException`.

    8. `givenEntryNormalizingExactlyToBase_whenAssertWithin_thenPasses()` — `assertWithin(tempDir, ".")` resolves to `tempDir`, which `startsWith(tempDir)` returns `true`. (Edge-case sanity — the predicate is `startsWith`, not `startsWith` + length-greater.) NOTE: an entry name of `.` is harmless (it points at the base directory itself, not at a child); it cannot extract a file because `ZipEntry.getName() == "."` would mean the entry IS the directory. This is a sanity check, not a security gate.

    All tests follow Given-When-Then naming + comments. Use AssertJ exclusively.
  </action>
  <verify>
    <automated>./mvnw -q -Dtest='PathTraversalGuardTest' test</automated>
  </verify>
  <acceptance_criteria>
    - `src/main/java/org/ctc/backup/security/PathTraversalGuard.java` compiles and exposes exactly one public static method `assertWithin(Path, String)`.
    - Predicate uses `toAbsolutePath().normalize()` (NOT `toRealPath()`) — verify by `grep -c 'toRealPath' src/main/java/org/ctc/backup/security/PathTraversalGuard.java` returns `0`.
    - Predicate uses `startsWith(absoluteBase)` — `grep -c 'startsWith' src/main/java/org/ctc/backup/security/PathTraversalGuard.java` returns at least `1`.
    - Absolute-path rejection is its own branch — `grep -c 'isAbsolute' src/main/java/org/ctc/backup/security/PathTraversalGuard.java` returns at least `1`.
    - All eight tests in `PathTraversalGuardTest` green.
  </acceptance_criteria>
  <done>
    `./mvnw -Dtest='PathTraversalGuardTest' test` passes; the predicate matches `FileStorageService:153-158` semantics (asserted by a `grep -A2 'toAbsolutePath' src/main/java/org/ctc/backup/security/PathTraversalGuard.java` showing the same `.normalize().startsWith(...)` chain); no `toRealPath` call.
  </done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| ZIP-entry name → filesystem path | Untrusted attacker-controlled string crosses into `Paths.resolve` — defended by `PathTraversalGuard.assertWithin`. |
| ZIP-entry bytes → in-memory buffer | Untrusted bytes (potentially inflating to gigabytes) cross into `read()` calls — defended by `LimitedInputStream` per-entry counting. |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-74-02-01 | Tampering | `LimitedInputStream` | mitigate | Inflated-byte counter enforces `BackupImportLimits.MAX_ENTRY_BYTES` (52 428 800) regardless of the `ZipEntry.getSize()` value the attacker placed in the central directory. CONTEXT §D-12 + §specifics. |
| T-74-02-02 | Tampering | `PathTraversalGuard.assertWithin` | mitigate | `toAbsolutePath().normalize().startsWith(baseDir)` predicate; absolute-path branch rejects names that would normalize back inside `baseDir`. Idiom identical to `FileStorageService:153-158` (REQUIREMENTS SECU-01: "Wiederverwendung statt Duplikat"). |
| T-74-02-03 | Information Disclosure | `BackupArchiveException` message | accept | Exception messages cite the `limit` (not the running `count`) and the offending entry name. Entry name is attacker-controlled — disclosing it back in the message is information they already possess. No internal paths leak via `getMessage()`; `getStackTrace()` is silenced by the controller's redirect-flash path (plan 06). |
| T-74-02-04 | Denial of Service | `LimitedInputStream` bulk read | mitigate | Exception fires on the chunk that crosses the limit (NOT after a full read). Worst case: attacker forces the JVM to allocate one chunk (e.g. 8 KB read buffer) past the limit before the throw — bounded. Tested by `givenBulkReadCrossesLimit_whenRead_thenThrowsOnTheChunkThatCrossesLimit_andOnCloseFires()`. |
| T-74-02-05 | Elevation of Privilege | `PathTraversalGuard` use of `toAbsolutePath()` vs `toRealPath()` | accept | `toAbsolutePath().normalize()` does NOT follow symlinks; an attacker who can plant a symlink in `baseDir` could circumvent the check by naming a target inside `baseDir` that symlinks elsewhere. Risk accepted because (a) `baseDir` is created and owned by the application user (no third-party write access), (b) `FileStorageService:30` uses the same idiom and has shipped since v1.1 without incident, (c) deviating from `FileStorageService` would break the REQUIREMENTS SECU-01 reuse mandate. If this changes, plan 04/05 can introduce `toRealPath` at the caller level. |
</threat_model>

<verification>
After all three tasks: run the focused unit-test set as a single Surefire call:

`./mvnw -q -Dtest='BackupArchiveExceptionTest,BackupImportLimitsTest,LimitedInputStreamTest,PathTraversalGuardTest' test`

Expected: all four test classes green, ≥ 18 test methods total (2 + 3 + 6 + 8). No Spring context loaded — runtime should be < 5 s.

Spot-check the package layout: `find src/main/java/org/ctc/backup -type f -newer .planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — exactly four new files (one per task target).

Cross-reference REQUIREMENTS.md SECU-01 + SECU-02 are partially delivered: the **mechanism** (counters, predicate, exception, constants) ships here; the **wiring** ships in plans 04 (BackupArchiveService.read*) and 05 (BackupImportService.stage). This plan's success criterion is "the primitives compile, are unit-tested, and have no Spring dependency" — plan 04 will fail if any of these primitives is missing or has the wrong semantics.
</verification>

<success_criteria>
- Four new production files exist under `src/main/java/org/ctc/backup/{service,io,security,exception}/`.
- Four new test files exist under `src/test/java/org/ctc/backup/{service,io,security,exception}/`.
- `./mvnw -q -Dtest='BackupArchiveExceptionTest,BackupImportLimitsTest,LimitedInputStreamTest,PathTraversalGuardTest' test` → BUILD SUCCESS, 0 failures, 0 errors.
- Zero Spring annotations (`@Component`, `@Service`, `@Configuration`, `@Bean`, `@Autowired`) on any of the four production files.
- `BackupImportLimits.MAX_ENTRY_BYTES == 52_428_800L`, `MAX_TOTAL_BYTES == 524_288_000L`, `MAX_ENTRIES == 50_000`.
- `BackupArchiveException.Reason` enum has exactly 8 values in the order listed in task 1's action (including `NOT_A_ZIP` at position 8 for Plan 05's magic-byte sniff).
- `LimitedInputStream` is `final`, extends `FilterInputStream`, has constructor signature `(InputStream, long, java.util.function.LongConsumer)`, and fires the `LongConsumer` exactly once with the final inflated byte count on BOTH the success-close path AND the limit-exceeded throw path (callback before throw).
- `PathTraversalGuard.assertWithin(Path, String)` rejects absolute paths AND rejects `..`-normalized paths that escape `baseDir`, throwing `BackupArchiveException(Reason.PATH_TRAVERSAL, ...)` in both cases.
- Test coverage on the four new production files ≥ 90 % line coverage (JaCoCo) — verified by `./mvnw verify` after plan 03 or via spot-grep of `target/site/jacoco/org.ctc.backup.{io,security,exception,service}/index.html`. Phase-wide ≥ 82 % JaCoCo gate (`pom.xml`) is the binding constraint at the end of wave 1; this plan contributes high-coverage primitives.
</success_criteria>

<output>
After completion, create `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-02-SUMMARY.md` per `templates/summary.md`. Capture:

- Files created (4 production + 4 test).
- The eight `Reason` values (so plan 04/05 can grep this SUMMARY to know the routing keys) — including `NOT_A_ZIP` at position 8 (consumed by Plan 05's magic-byte sniff) and the dual scope of `MANIFEST_INVALID` (covers manifest.json AND data/*.json structural failures, consumed by Plan 04's `countDataEntries`).
- The three `BackupImportLimits` constants with literal values.
- The `LimitedInputStream` constructor signature (`InputStream, long, java.util.function.LongConsumer`) — Plan 04 needs it verbatim. Note the "exactly once" `onClose` contract: fires on both success-close and limit-exceeded paths; callback fires BEFORE the throw on the limit-exceeded path so callers can read the final inflated byte count even after a per-entry rejection.
- The `PathTraversalGuard.assertWithin(Path, String)` signature + the exact predicate (`toAbsolutePath().normalize().startsWith(absoluteBase)`).
- Test runtime in seconds (sanity check for "no Spring context loaded").
- Any deviation from PATTERNS or CONTEXT (none expected — the planning_context is explicit).
</output>

## Notes

- **Rename `ZIP_SLIP` → `PATH_TRAVERSAL`:** PATTERNS.md drafted `Reason.ZIP_SLIP`; the planning_context for this plan specifies `PATH_TRAVERSAL`. The planning_context is authoritative for this plan and downstream consumers. The change is cosmetic (no semantic difference for SECU-01) and avoids confusion when the same predicate is reused outside ZIP contexts (e.g. plan 04 may call `assertWithin` on `uploads/<rel>` paths sourced from the filesystem, not from a ZIP).
- **`SCHEMA_MISMATCH` vs PATTERNS' `SCHEMA_VERSION_MISMATCH`:** planning_context shortened the name. Same semantic; chose the shorter form so call-site code reads cleaner (`throw new BackupArchiveException(Reason.SCHEMA_MISMATCH, ...)`).
- **`MANIFEST_INVALID` dual scope (revision):** broader name (covers parse failure AND structural-but-non-parse failures — including manifest.json's own shape AND `data/*.json` entries whose top-level token is not `START_ARRAY`). Plan 04's `countDataEntries` reuses this `Reason` for the data-array structural assertion. Plan 08's `mapReason` folds it into the D-02#3 generic safety-checks Flash message — no UX-visible difference between manifest-shape failure and data-shape failure. Option B from checker (cheapest path — no new enum value).
- **`NOT_A_ZIP` is canonical here (revision):** Plan 02 owns this enum constant (position 8 of 8 in the `Reason` enum). Plan 05's earlier notes contemplated adding it as a side-edit if Plan 02 had omitted it — Plan 02 does NOT omit it, so Plan 05 needs zero side-edits to the exception class. The value covers the header-byte-sniff case from CONTEXT §specifics ("the server-side check is on `Content-Type: application/zip` AND the first 4 bytes of the file body (`50 4B 03 04` ZIP magic)").
- **`onClose` `LongConsumer` parameter (revision per checker Issue 3):** REPLACES the earlier `Runnable onLimitExceeded` design. Use case (Plan 04): the callback fires `finalBytes -> totalInflatedAcc[0] += finalBytes` after EVERY entry (success path AND limit-exceeded path). Plan 04 then runs the `MAX_TOTAL_BYTES` total-archive check using this accurate per-entry tally. Without the `LongConsumer` form, Plan 04 could not distinguish a 50 MB entry that succeeded from a 50 MB entry that tripped the cap — both look identical to a parameterless `Runnable`. The exact byte count delivered by the callback is the truth Plan 04's accumulator needs.
- **No `@Slf4j` on any of the four new classes** — they are pure primitives; logging belongs to their callers (plans 04 / 05 / 06).

## PLAN COMPLETE 02
