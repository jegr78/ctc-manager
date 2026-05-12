---
id: "04"
title: "BackupArchiveService reader + ZIP hardening integration"
phase: 74
plan: "04"
type: execute
wave: 2
depends_on: ["02"]
requirements: [IMPORT-01, SECU-01, SECU-02]
files_modified:
  - src/main/java/org/ctc/backup/service/BackupArchiveService.java
  - src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java
autonomous: true
tags: [backup, zip-hardening, streaming-json, jackson, path-traversal]

must_haves:
  truths:
    - "BackupArchiveService.readManifest opens a Phase-73 export ZIP and returns a BackupManifest whose schemaVersion equals BackupSchema.SCHEMA_VERSION."
    - "BackupArchiveService.countDataEntries returns a per-table row-count Map for every data/<entity>.json entry without buffering the full document (streaming JsonParser)."
    - "BackupArchiveService.countUploadFiles returns the number of entries under uploads/ in the ZIP."
    - "Every read method enforces ZIP-Slip defense per D-11 — entries whose normalized path escapes the staging root throw BackupArchiveException(Reason.PATH_TRAVERSAL)."
    - "Every read method enforces ZipBomb defense per D-12 — per-entry inflated size > 50 MB, total inflated size > 500 MB, and entry count > 50 000 all throw typed BackupArchiveException."
    - "readManifest throws BackupArchiveException(Reason.MANIFEST_MISSING) when entry 0 is not named manifest.json, and Reason.MANIFEST_INVALID on Jackson parse failure."
    - "Phase 73 writer methods (writeZip, writeJson) remain byte-identical — Plan 04 only adds reader methods."
  artifacts:
    - path: "src/main/java/org/ctc/backup/service/BackupArchiveService.java"
      provides: "readManifest(Path), countDataEntries(Path), countUploadFiles(Path), private openHardened(Path) helper"
      contains: "readManifest"
    - path: "src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java"
      provides: "Failsafe IT covering manifest-first contract, streaming counts, ZIP-Slip reject, deflate-bomb reject"
      exports: ["givenPhase73Export_whenReadManifest_thenSchemaVersionEqualsOne",
                "givenManifestNotFirst_whenReadManifest_thenThrowsManifestMissing",
                "givenManifestMalformedJson_whenReadManifest_thenThrowsManifestInvalid",
                "givenValidZip_whenCountDataEntries_thenReturnsPerTableRowCounts",
                "givenZipSlipEntry_whenCountDataEntries_thenThrowsPathTraversal",
                "givenEntryWithInflatedSizeExceedingLimit_whenCountDataEntries_thenThrowsEntryTooLarge"]
  key_links:
    - from: "BackupArchiveService.readManifest"
      to: "@Qualifier(\"backupObjectMapper\") ObjectMapper"
      via: "existing constructor-injected field (Phase 73)"
      pattern: "backupObjectMapper\\.readValue"
    - from: "BackupArchiveService.openHardened"
      to: "PathTraversalGuard.assertWithin"
      via: "per-entry name check before opening InputStream"
      pattern: "PathTraversalGuard\\.assertWithin"
    - from: "BackupArchiveService.openHardened"
      to: "LimitedInputStream"
      via: "wraps each entry's segment with MAX_ENTRY_BYTES limit + onLimitExceeded callback"
      pattern: "new LimitedInputStream"
    - from: "BackupArchiveServiceReadIT happy path"
      to: "BackupArchiveService.writeZip (Phase 73)"
      via: "runtime export-then-read round-trip — no committed binary fixtures (D-25)"
      pattern: "archiveService\\.writeZip"
---

## Objective

Extend the existing `BackupArchiveService` (Phase 73 writer-only) with three streaming reader methods — `readManifest`, `countDataEntries`, `countUploadFiles` — that satisfy the manifest-first wire contract (Phase 72 D-14, Phase 73 round-trip), parse data arrays via Jackson `JsonParser` token-loop (no full-document buffering), and route every `ZipEntry` through a single hardened iteration helper that enforces ZIP-Slip and ZipBomb defenses (D-11, D-12, D-20).

The reader is the I/O foundation Plan 05's `BackupImportService.stage(...)` and `BackupImportService.reparse(...)` build on. Plan 05 calls `readManifest` (for the schema-version gate, D-09), `countDataEntries` (for per-card "imported rows" values in `BackupImportPreview`, D-21), and `countUploadFiles` (for the upload-count field in the same DTO).

**Purpose:** make ZIP reading safe by construction. Every untrusted ZIP entry passes through `openHardened(...)` exactly once; the security guarantees are centralized, not scattered across three public methods.

**Output:** one extended production class (`BackupArchiveService`), one new Failsafe IT (`BackupArchiveServiceReadIT`). Phase 73 writer methods remain byte-identical. The reader inherits the same `@Slf4j` + `@Service` + `@Transactional(readOnly = true)` class header — the `readOnly` annotation is harmless here (no JPA access) and preserves a single-class invariant.

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-RESEARCH.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md
@.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md
@src/main/java/org/ctc/backup/service/BackupArchiveService.java
@src/main/java/org/ctc/backup/schema/BackupSchema.java
@src/main/java/org/ctc/backup/schema/BackupManifest.java
@src/main/java/org/ctc/backup/schema/EntityRef.java
@src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java

<interfaces>
<!-- Contracts the executor needs. Already present on the classpath BEFORE Plan 04 runs. -->
<!-- Plan 02 ships the four hardening primitives; Plan 04 wires them. -->

From `org.ctc.backup.schema.BackupSchema` (Phase 72):
  public static final int SCHEMA_VERSION = 1;
  public List<EntityRef> getExportOrder();   // 24 entries, FK-topo-sorted

From `org.ctc.backup.schema.BackupManifest` (Phase 72):
  public record BackupManifest(
      @JsonProperty("schema_version") int schemaVersion,
      @JsonProperty("app_version")    String appVersion,
      @JsonProperty("export_date")    Instant exportDate,
      @JsonProperty("table_counts")   Map<String, Long> tableCounts) {}

From `org.ctc.backup.schema.EntityRef` (Phase 72):
  public record EntityRef(Class<?> entityClass, String tableName, String fileName) {}
  // fileName format: "data/<table-with-underscores-as-dashes>.json"

From `org.ctc.backup.service.BackupArchiveService` (Phase 73 — EXISTING fields to REUSE, do not duplicate):
  private final BackupExportService backupExportService;
  private final BackupSchema        backupSchema;
  private final ObjectMapper        backupObjectMapper;   // @Qualifier("backupObjectMapper"), FAIL_ON_UNKNOWN_PROPERTIES=true
  private final String              appVersion;
  public  void writeZip(OutputStream out, Instant exportDate) throws IOException;

From Plan 02 (`org.ctc.backup.security.PathTraversalGuard`):
  public static void assertWithin(Path baseDir, String entryName);
  // throws BackupArchiveException(Reason.PATH_TRAVERSAL, ...) when normalized resolve escapes baseDir,
  // or when entryName is absolute. baseDir is resolved via toAbsolutePath().normalize() inside the helper.

From Plan 02 (`org.ctc.backup.io.LimitedInputStream`):
  public LimitedInputStream(InputStream delegate, long limit, Runnable onLimitExceeded);
  // FilterInputStream — counts inflated bytes pulled via read()/read(byte[],int,int).
  // Throws BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...) when running total > limit.
  // The Runnable `onLimitExceeded` is invoked ONCE — immediately BEFORE the exception is thrown
  // (so callers can log the offending entry name from the catch-context). It is NOT a per-byte
  // tick and it is NOT invoked at end-of-stream success. May be null.

From Plan 02 (`org.ctc.backup.service.BackupImportLimits`):
  public static final long MAX_ENTRY_BYTES = 50L*1024*1024;     // 50 MB  (52_428_800)
  public static final long MAX_TOTAL_BYTES = 500L*1024*1024;    // 500 MB (524_288_000)
  public static final int  MAX_ENTRIES     = 50_000;

From Plan 02 (`org.ctc.backup.exception.BackupArchiveException`):
  public class BackupArchiveException extends RuntimeException {
      public enum Reason { PATH_TRAVERSAL, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, TOO_MANY_ENTRIES,
                           MANIFEST_MISSING, MANIFEST_INVALID,
                           SCHEMA_MISMATCH, NOT_A_ZIP }
      // SCHEMA_MISMATCH and NOT_A_ZIP are consumed by Plan 05, not Plan 04.
      public BackupArchiveException(Reason reason, String message);
      public BackupArchiveException(Reason reason, String message, Throwable cause);
      public Reason reason();
  }
</interfaces>
</context>

## Tasks

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add reader methods to BackupArchiveService + hardened iteration helper</name>
  <files>src/main/java/org/ctc/backup/service/BackupArchiveService.java</files>
  <read_first>
    @src/main/java/org/ctc/backup/service/BackupArchiveService.java
    @src/main/java/org/ctc/backup/schema/BackupSchema.java
    @src/main/java/org/ctc/backup/schema/BackupManifest.java
    @src/main/java/org/ctc/backup/schema/EntityRef.java
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md (§"`org.ctc.backup.service.BackupArchiveService` — extension")
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-11, D-12, D-20)
  </read_first>
  <behavior>
    - Manifest-first contract: opening a Phase-73 export ZIP and calling `readManifest(path)` returns a `BackupManifest` whose `schemaVersion == BackupSchema.SCHEMA_VERSION` (round-trip via Phase 73 `writeZip`).
    - Manifest-missing rejection: a ZIP whose first entry is not literally named `manifest.json` throws `BackupArchiveException(Reason.MANIFEST_MISSING, ...)`.
    - Manifest-invalid rejection: a ZIP whose `manifest.json` entry contains malformed JSON (e.g., `{`) throws `BackupArchiveException(Reason.MANIFEST_INVALID, ...)` with the Jackson exception as `cause`.
    - Data-count behavior: `countDataEntries(path)` walks every entry whose name starts with `data/` and ends with `.json`, opens a Jackson `JsonParser` over its inflated stream, asserts the first token is `START_ARRAY`, counts `START_OBJECT` tokens (each top-level object = one row), and skips nested children via `parser.skipChildren()`. Returns `Map<tableName, rowCount>` keyed by the table-name slug derived from the entry name (`data/season-phases.json` → `season_phases`). Map iteration order is insertion-order (use `LinkedHashMap`) so callers can iterate in ZIP order.
    - Upload-count behavior: `countUploadFiles(path)` counts entries whose name starts with `uploads/` AND `!entry.isDirectory()`. Returns the count as an `int`.
    - ZIP-Slip rejection: any entry whose resolved path escapes the staging-root throws `BackupArchiveException(Reason.PATH_TRAVERSAL, "entry: " + entryName)` from any of the three public methods.
    - Per-entry bomb rejection: an entry whose inflated byte stream exceeds `MAX_ENTRY_BYTES` throws `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)`. This works even when `ZipEntry.getSize()` lies (header says 1 KB, inflated is 5 GB) because the `LimitedInputStream` counts actual bytes pulled from `InflaterInputStream`.
    - Total-size rejection: running tally across all entries exceeds `MAX_TOTAL_BYTES` → `BackupArchiveException(Reason.TOTAL_TOO_LARGE, ...)`. The tally is a STACK-LOCAL `long[] inflatedAcc = new long[]{0L}` array; each public method declares its own. After draining/parsing each entry, the post-loop `assertEntrySafe(...)` call uses `inflatedAcc[0]` to enforce `MAX_TOTAL_BYTES`. The per-entry true inflated count is computed by counting the bytes returned by `LimitedInputStream.read(...)` calls inside the public method's drain/parse loop (NOT by reading `ZipEntry.getSize()` — that value is spoofable).
    - Entry-count rejection: monotonic counter incremented on each successful `getNextEntry()`; on exceeding `MAX_ENTRIES` → `BackupArchiveException(Reason.TOO_MANY_ENTRIES, ...)`.
    - Phase 73 invariance: `writeZip` and `writeJson` produce identical bytes for the same `(exportDate, fixture)` pair as they did before Plan 04. The existing three `BackupArchiveServiceIT` tests still pass.
  </behavior>
  <action>
    Extend the existing `org.ctc.backup.service.BackupArchiveService` class (do NOT create a sibling `BackupArchiveReadService` — D-20 explicitly requires single class). Phase 73 writer state — fields `backupExportService`, `backupSchema`, `backupObjectMapper`, `appVersion`, the explicit `@Qualifier`-bearing constructor, and the methods `writeZip(OutputStream, Instant)` and `writeJson(OutputStream, Object, boolean)` — is byte-identical after this task. The reader is purely additive.

    Imports to add (alphabetic): `com.fasterxml.jackson.core.JsonParser`, `com.fasterxml.jackson.core.JsonToken`, `org.ctc.backup.exception.BackupArchiveException`, `org.ctc.backup.exception.BackupArchiveException.Reason`, `org.ctc.backup.io.LimitedInputStream`, `org.ctc.backup.security.PathTraversalGuard`, `org.ctc.backup.service.BackupImportLimits.MAX_ENTRY_BYTES`, `MAX_TOTAL_BYTES`, `MAX_ENTRIES` (static-import the three constants), `java.io.InputStream`, `java.nio.file.Path`, `java.util.LinkedHashMap`, `java.util.zip.ZipEntry`, `java.util.zip.ZipInputStream`.

    Public method `readManifest(Path zipPath) throws BackupArchiveException` — opens the ZIP through `openHardened(zipPath)`, calls `zis.getNextEntry()` once, throws `BackupArchiveException(Reason.MANIFEST_MISSING, ...)` when the result is `null` or when `entry.getName()` is not literally the string `"manifest.json"` (no slash, no nested path). Declares a stack-local `long[] inflatedAcc = new long[]{0L}` and an `entryName` variable for the limit-exceeded log callback (see lambda below). Wraps the segment with `new LimitedInputStream(zis, MAX_ENTRY_BYTES, () -> log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes", entryName, MAX_ENTRY_BYTES))` — the third constructor argument is a `Runnable` per Plan 02's locked signature; the lambda CAPTURES the `entryName` local so the warn log carries the offending entry's name (the `LimitedInputStream` itself does not know the entry name). Deserializes via `backupObjectMapper.readValue(limited, BackupManifest.class)`. Catches `com.fasterxml.jackson.core.JsonProcessingException` and re-throws as `BackupArchiveException(Reason.MANIFEST_INVALID, "manifest.json parse failed", cause)`. Logs `log.info("Backup manifest read: schemaVersion={}, appVersion={}", manifest.schemaVersion(), manifest.appVersion())` on success and `log.warn("Backup manifest rejected: reason={}, msg={}", reason, message)` on every reject branch — same parameterized-`{}` style as the writer.

    Public method `countDataEntries(Path zipPath) throws BackupArchiveException` — opens the ZIP via `openHardened(zipPath)`, declares stack-local `int entryCount = 0;` and `long[] inflatedAcc = new long[]{0L};`, iterates EVERY entry (including the manifest — skipped via the predicate below). For each entry whose name `startsWith("data/")` AND `endsWith(".json")` AND not `isDirectory()`: capture the entry name into a final local `String entryName = entry.getName();` so the lambda below can reference it. Wrap the segment in a `LimitedInputStream` constructed as `new LimitedInputStream(zis, MAX_ENTRY_BYTES, () -> log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes", entryName, MAX_ENTRY_BYTES))` — the third argument is the locked `Runnable` parameter (Plan 02). Create a `JsonParser` via `backupObjectMapper.getFactory().createParser(limited)`, assert the first `nextToken()` is `JsonToken.START_ARRAY` (else throw `BackupArchiveException(Reason.MANIFEST_INVALID, "data file is not a JSON array: " + name)` — reuse `MANIFEST_INVALID` for malformed data arrays since they are also "wire-shape violations"; do not introduce a new Reason here), then loops via `while ((tok = parser.nextToken()) != null && tok != JsonToken.END_ARRAY)`; when `tok == JsonToken.START_OBJECT`, increment per-table counter then call `parser.skipChildren()` to advance past the row's nested content. Always close the parser in a `try { ... } finally { parser.close(); }` (the parser's close MUST NOT close the underlying `ZipInputStream` — pass `JsonParser.Feature.AUTO_CLOSE_SOURCE=false` via `parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)` immediately after creation, mirroring the writer-side `JsonGenerator.Feature.AUTO_CLOSE_TARGET=false` discipline at `BackupArchiveService.java:152`).

    To track the per-entry inflated-byte count for the `MAX_TOTAL_BYTES` gate (Plan 02's `LimitedInputStream` does NOT call back with a final byte count — its `Runnable` only fires on the OVERFLOW path), maintain a per-entry counter by READING the value-stream byte-count through a thin wrapper: do NOT introduce a new wrapper class. Instead, BEFORE creating the `JsonParser`, drain a probe is impossible (the parser needs the bytes). Use the existing `LimitedInputStream` and after the parser closes, read its `count` indirectly by snapshotting `zis.getRawBytesRead()` — but `ZipInputStream` does not expose that API. Therefore use the SIMPLEST correct approach: add ONE byte-counting `java.io.FilterInputStream` ADAPTER as a private static inner class `ByteCountingInputStream` inside `BackupArchiveService` (≤ 20 LOC) that wraps `LimitedInputStream` and exposes `getBytesRead()`. After parser close, accumulate `inflatedAcc[0] += counter.getBytesRead();`. The inner class is private — no public-surface impact. After the data-entry parse + close, call `assertEntrySafe(entry, stagingRoot, ++entryCount, inflatedAcc[0])` to refresh the running tally with the just-completed entry's actual inflated byte count.

    After the loop, derive `tableName` from the entry name via the inverse of `EntityRef.fromEntityType` slug rule — substring between `"data/"` and `".json"`, then `replace('-', '_')`. Put `(tableName → count)` into a `LinkedHashMap<String, Long>`. Returns the map. Log `log.info("Backup data counts read: entries={}, totalRows={}", map.size(), map.values().stream().mapToLong(Long::longValue).sum())`.

    Public method `countUploadFiles(Path zipPath) throws BackupArchiveException` — opens the ZIP via `openHardened(zipPath)`, declares stack-local `int entryCount = 0;` and `long[] inflatedAcc = new long[]{0L};`, iterates EVERY entry, increments a counter when `entry.getName().startsWith("uploads/") && !entry.isDirectory()`. Capture `final String entryName = entry.getName();` and wrap each upload entry in a `LimitedInputStream` constructed via `new LimitedInputStream(zis, MAX_ENTRY_BYTES, () -> log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes", entryName, MAX_ENTRY_BYTES))`, wrap the `LimitedInputStream` in the same `ByteCountingInputStream` adapter, drain it via a discard-buffer (`byte[] buf = new byte[8192]; while (counter.read(buf) != -1) { /* discard */ }`) so the bomb defense fires on upload entries too — counting alone reads only the central-directory size, which is spoofable. After draining, accumulate `inflatedAcc[0] += counter.getBytesRead();` and call `assertEntrySafe(entry, stagingRoot, ++entryCount, inflatedAcc[0])`. Returns the upload-entry counter. Log `log.info("Backup upload entries counted: count={}", count)`.

    Private helper `private ZipInputStream openHardened(Path zipPath) throws IOException` — opens the ZIP from the given Path via `Files.newInputStream(zipPath)` wrapped in `new ZipInputStream(...)`. Does NOT itself resolve the staging root or per-entry guarantees; that work lives in `assertEntrySafe` so each public method retains explicit, inline visibility into the per-entry safety checks. The staging root is computed by each public method as `Path stagingRoot = zipPath.toAbsolutePath().getParent();` (or `zipPath.toAbsolutePath()` if the parent is null, which is a pathological case — fall back to `Paths.get(".").toAbsolutePath().normalize()` in that branch). Pass `stagingRoot` explicitly into `assertEntrySafe(...)` calls.

    Private static helper `private static void assertEntrySafe(ZipEntry entry, Path stagingRoot, int currentEntryCount, long currentInflatedBytes)`:
      - throws `BackupArchiveException(Reason.TOO_MANY_ENTRIES, "exceeded " + MAX_ENTRIES)` when `currentEntryCount > MAX_ENTRIES`,
      - throws `BackupArchiveException(Reason.TOTAL_TOO_LARGE, "exceeded " + MAX_TOTAL_BYTES + " bytes")` when `currentInflatedBytes > MAX_TOTAL_BYTES`,
      - and (for non-directory entries) delegates to `PathTraversalGuard.assertWithin(stagingRoot, entry.getName())`. Directories are skipped from the path-traversal check (their name ends with `/` and that resolves cleanly).

    `ByteCountingInputStream` (private static inner class inside `BackupArchiveService`): extends `java.io.FilterInputStream`, exposes `long getBytesRead()`. Overrides both `read()` and `read(byte[], int, int)`; on each successful read increments a `private long bytesRead` counter by the byte count returned by `super.read(...)`. EOF (`-1`) returns are not counted. Not `final` is fine — it is `private static`. Has a single constructor `ByteCountingInputStream(InputStream delegate)` that calls `super(delegate)`. No Spring annotations. The class exists solely to bridge Plan 02's overflow-only `Runnable` callback to Plan 04's end-of-stream final-count requirement.

    Method-level `throws` clause: all three public reader methods declare `throws BackupArchiveException` (unchecked; declared for clarity). They do NOT declare `IOException` — the helper wraps any I/O failure (e.g., truncated ZIP central directory) as `BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", cause)`. Implementation choice rationale: callers (`BackupImportService` in Plan 05) catch `BackupArchiveException` exclusively — adding `IOException` to the signature would force a second catch branch and dilute the single-exception contract per D-20.

    Logging style: same as Phase 73 writer — `log.info(...)` for state changes with parameterized `{}` placeholders, `log.warn(...)` for rejections with `reason` and `message` keys. No string concatenation in log calls (CLAUDE.md §"Logging").

    Class-level annotations: leave `@Slf4j @Service @Transactional(readOnly = true)` as-is. The reader does no JPA access, but the writer needs the open session; keeping the annotation on the class is correct.

    Javadoc: add a class-level paragraph above `readManifest` describing the reader extension (Phase 74 D-20). Each public reader method gets a Javadoc block stating: the contract (manifest-first for `readManifest`, streaming-token-loop for `countDataEntries`, drain-and-count for `countUploadFiles`), the hardening invariants (path-traversal, per-entry limit, total limit, entry-count limit), and the throws contract.
  </action>
  <verify>
    <automated>./mvnw -q -Dtest='BackupArchiveService*Test' -Dit.test='BackupArchiveServiceReadIT,BackupArchiveServiceIT' verify</automated>
  </verify>
  <done>
    `src/main/java/org/ctc/backup/service/BackupArchiveService.java` exports public methods `readManifest(Path)`, `countDataEntries(Path)`, `countUploadFiles(Path)` plus the private `assertEntrySafe` helper and the private static `ByteCountingInputStream` inner class. `./mvnw -q compile` succeeds. The three pre-existing `BackupArchiveServiceIT` tests (manifest-first / per-entity-data-entry / schema-version-match) are green — Phase 73 writer untouched. No new `IOException` declarations on public surface; only `BackupArchiveException`.
  </done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: BackupArchiveServiceReadIT — manifest-first, streaming counts, ZIP-Slip + bomb rejection</name>
  <files>src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java</files>
  <read_first>
    @src/test/java/org/ctc/backup/service/BackupArchiveServiceIT.java
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-VALIDATION.md (§"Per-Task Verification Map" — BackupArchiveServiceReadIT row)
    @.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md (D-24, D-25 fixture discipline)
  </read_first>
  <behavior>
    - Six `@Test` methods, all named per `givenContext_whenAction_thenExpectedResult` BDD convention with inline `// given` / `// when` / `// then` comments.
    - `givenPhase73Export_whenReadManifest_thenSchemaVersionEqualsOne()` — invokes `archiveService.writeZip(out, Instant.now())` at runtime to produce a real export, writes the bytes to a `@TempDir` Path, then calls `readManifest(path)` and asserts `manifest.schemaVersion() == BackupSchema.SCHEMA_VERSION` (== 1).
    - `givenManifestNotFirst_whenReadManifest_thenThrowsManifestMissing()` — programmatic `ZipOutputStream` produces a ZIP whose entry 0 is `data/foo.json` and entry 1 is `manifest.json`; `readManifest(...)` throws `BackupArchiveException` with `reason() == Reason.MANIFEST_MISSING`.
    - `givenManifestMalformedJson_whenReadManifest_thenThrowsManifestInvalid()` — programmatic ZIP with `manifest.json` entry containing only the byte `{` (incomplete JSON); throws `Reason.MANIFEST_INVALID`. Assert `getCause()` is a Jackson exception.
    - `givenValidZip_whenCountDataEntries_thenReturnsPerTableRowCounts()` — uses the same Phase 73 export from test 1; calls `countDataEntries(path)`; asserts the map has 24 keys (matches `backupSchema.getExportOrder().size()`), every value is `>= 0L`, every key matches an `EntityRef.tableName()` in `backupSchema.getExportOrder()`. At least one entry has a non-zero count (the dev fixture seeds data).
    - `givenZipSlipEntry_whenCountDataEntries_thenThrowsPathTraversal()` — programmatic ZIP with first entry `manifest.json` (well-formed minimal manifest) and second entry literally named `../../etc/passwd` containing `[]`; `countDataEntries(path)` throws `BackupArchiveException` with `reason() == Reason.PATH_TRAVERSAL`.
    - `givenEntryWithInflatedSizeExceedingLimit_whenCountDataEntries_thenThrowsEntryTooLarge()` — programmatic ZIP whose `data/big.json` entry inflates to `> MAX_ENTRY_BYTES` (50 MB). Generation strategy: write a synthetic JSON array of repeated padding strings until inflated size crosses the limit; use a small payload-per-row but enough rows to exceed 50 MB inflated. To keep the test under 2 seconds, set the entry's deflate level to `Deflater.BEST_SPEED` and use a constant string row pattern. Asserts `BackupArchiveException` with `reason() == Reason.ENTRY_TOO_LARGE`.
    - Class header mirrors `BackupArchiveServiceIT` exactly: `@SpringBootTest @ActiveProfiles("dev")` class declaration; autowire `BackupArchiveService archiveService`, `BackupSchema backupSchema`, `@Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper`. No `@Transactional` on the test class (mirrors Phase 73 IT — production `@Transactional` on the service supplies the session).
    - Helper methods (private, file-local): `writeProgrammaticZip(Path target, ZipFixture... entries)` builds a hand-rolled ZIP via `ZipOutputStream`. `ZipFixture` is a private static record `(String name, byte[] body, Integer levelOverride)`. No fixture binaries committed (D-25).
    - JUnit 5 `@TempDir Path tempDir` provides per-test scratch space; staging path = `tempDir.resolve("import.zip")`.
  </behavior>
  <action>
    Create `src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java` in the existing `org.ctc.backup.service` test package — sibling of `BackupArchiveServiceIT`. Use the SAME `@SpringBootTest @ActiveProfiles("dev")` class header pattern (no `@Transactional`). Wire `archiveService`, `backupSchema`, and `backupObjectMapper` via the same `@Autowired` + `@Qualifier("backupObjectMapper")` triple that `BackupArchiveServiceIT` uses.

    Author the six tests above. For the round-trip happy paths (tests 1 and 4), call `archiveService.writeZip(out, Instant.now())` against a `ByteArrayOutputStream`, then `Files.write(tempDir.resolve("export.zip"), out.toByteArray())`, then pass that Path to the reader under test. For the malicious programmatic ZIPs (tests 2, 3, 5, 6), drive `ZipOutputStream(Files.newOutputStream(target))` directly with `putNextEntry(new ZipEntry(name))` + `out.write(body)` + `closeEntry()` calls, choosing the entry order deliberately (e.g., test 2 writes `data/foo.json` first to force manifest into entry 1).

    For test 6 (deflate bomb), generate the synthetic data array as repeated small JSON objects of the form `{"k":"<padding>"}` where padding is a constant 256-byte ASCII string. Loop until inflated size > 60 MB to ensure the limit fires inside the array parse, not just after. Set `ZipEntry.setMethod(ZipEntry.DEFLATED)` and rely on the default deflater. Test runtime should stay below 2 s on a M-series Mac (the inflated size is ~60 MB but the compressed payload of the repeated pattern is ~200 KB).

    Use AssertJ `assertThat(...).as("rationale — D-XX / VALIDATION.md SC#X")` for every assertion, mirroring the rationale-annotation pattern at `BackupArchiveServiceIT.java:74-76` and `:92-95`. For the typed-exception assertions use `assertThatThrownBy(() -> archiveService.readManifest(path)).isInstanceOf(BackupArchiveException.class).extracting("reason").isEqualTo(Reason.MANIFEST_MISSING)` — single-line, no nested try/catch. For the path-traversal test specifically, the `extracting("reason").isEqualTo(...)` argument is `Reason.PATH_TRAVERSAL` (NOT `Reason.ZIP_SLIP` — the Plan 02 canonical enum uses `PATH_TRAVERSAL`).

    Do NOT add `@DirtiesContext` — these tests do not mutate Spring-managed state. Do NOT add `@MockBean` — the production wiring is exactly what is under test. Tests are pure I/O against `@TempDir`.

    Naming + package: `package org.ctc.backup.service;` — must match `BackupArchiveServiceIT` so Failsafe discovers both. Class name `BackupArchiveServiceReadIT` ends with `IT` (Failsafe convention) — Surefire skips it.
  </action>
  <verify>
    <automated>./mvnw -q -Dit.test=BackupArchiveServiceReadIT verify</automated>
  </verify>
  <done>
    `src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java` exists, declares the six `@Test` methods above, and runs green via Failsafe. All six pass on first run. No new test fixtures live in `src/test/resources/` (D-25 — programmatic generation only).
  </done>
</task>

</tasks>

## Verification

`must_haves` block (frontmatter) is the contract this plan must satisfy at execution time. Concretely, at the end of Plan 04 the following all hold:

1. `./mvnw -q compile` succeeds — `BackupArchiveService` references the four Plan-02 primitives (`PathTraversalGuard`, `LimitedInputStream`, `BackupImportLimits`, `BackupArchiveException`) and the imports resolve.
2. `./mvnw -q -Dit.test='BackupArchiveServiceReadIT,BackupArchiveServiceIT' verify` is green — Plan 04's new IT passes AND Phase 73's pre-existing IT remains untouched.
3. `grep -nE 'public (BackupManifest readManifest|Map<String, Long> countDataEntries|int countUploadFiles)' src/main/java/org/ctc/backup/service/BackupArchiveService.java | grep -v '^[[:space:]]*//' | wc -l` returns `3` (exactly three new public reader methods).
4. `grep -nE 'writeZip|writeJson' src/main/java/org/ctc/backup/service/BackupArchiveService.java | grep -v '^[[:space:]]*//' | wc -l` returns `≥ 2` (Phase 73 writer methods still present).
5. `grep -n 'new BackupArchiveReadService' src/main/java/org/ctc/backup/ -r` returns nothing (Plan 04 did NOT split the class — D-20 single-class invariant).
6. `grep -n '@Qualifier("backupObjectMapper")' src/main/java/org/ctc/backup/service/BackupArchiveService.java | wc -l` returns `1` (constructor qualifier present exactly once — no duplicate field).
7. `grep -nE 'Reason\.(ZIP_SLIP|SCHEMA_VERSION_MISMATCH|MANIFEST_PARSE_FAILED)' src/main/java/org/ctc/backup/service/BackupArchiveService.java src/test/java/org/ctc/backup/service/BackupArchiveServiceReadIT.java | grep -v '^[[:space:]]*//' | wc -l` returns `0` (no stale enum names; the canonical Plan 02 names `PATH_TRAVERSAL` / `SCHEMA_MISMATCH` / `MANIFEST_INVALID` are the only ones referenced).
8. `grep -nE 'this::trackInflatedBytes|trackInflatedBytes\(' src/main/java/org/ctc/backup/service/BackupArchiveService.java | wc -l` returns `0` (no reference to a non-existent helper method — byte accumulation is stack-local via `long[] inflatedAcc` and the `ByteCountingInputStream` inner class).

## Notes

- **Single-class invariant (D-20):** the reader lives on `BackupArchiveService`, not a sibling `BackupArchiveReadService`. The class is `@Transactional(readOnly = true)`; the reader does not need a DB session but inheriting the annotation is harmless and preserves the writer's lazy-init behavior.
- **`@Qualifier("backupObjectMapper")` reuse:** the writer's strict ObjectMapper field is reused — `FAIL_ON_UNKNOWN_PROPERTIES=true` is exactly what the manifest-invalid gate needs (a forged manifest with an extra `bypassSchemaCheck: true` field fails to deserialize and routes to `Reason.MANIFEST_INVALID`).
- **Reason enum names — canonical (Plan 02):** the eight `BackupArchiveException.Reason` values fixed by Plan 02 are `PATH_TRAVERSAL`, `ENTRY_TOO_LARGE`, `TOTAL_TOO_LARGE`, `TOO_MANY_ENTRIES`, `MANIFEST_MISSING`, `MANIFEST_INVALID`, `SCHEMA_MISMATCH`, `NOT_A_ZIP`. Plan 04 references ONLY these names. Earlier draft names — `ZIP_SLIP`, `SCHEMA_VERSION_MISMATCH`, `MANIFEST_PARSE_FAILED` — are stale and MUST NOT appear in any code or comment authored by Plan 04. `MANIFEST_INVALID` covers BOTH manifest.json parse/structural failures AND `data/*.json` structural failures (e.g., top-level token not `START_ARRAY`) — confirmed by Plan 02's revised note. `Reason.SCHEMA_MISMATCH` and `Reason.NOT_A_ZIP` exist on the enum but are consumed by Plan 05, not Plan 04.
- **`LimitedInputStream` callback contract (Plan 02 — locked signature):** the third constructor argument is `Runnable onLimitExceeded`. The `Runnable` is invoked ONCE — immediately before the `BackupArchiveException(Reason.ENTRY_TOO_LARGE, ...)` is thrown — and ONLY on the overflow path. It is NOT a per-byte tick and it is NOT invoked at end-of-stream success. Plan 04 uses the callback as a `() -> log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes", entryName, MAX_ENTRY_BYTES)` lambda that captures the just-set `entryName` local so the warn log carries the offending entry's name. The end-of-stream per-entry inflated count needed by the `MAX_TOTAL_BYTES` gate is supplied by a private static inner `ByteCountingInputStream` (FilterInputStream) wrapped AROUND the `LimitedInputStream` inside each public reader method; the wrapper exposes `getBytesRead()` after the entry is drained/parsed. No reference to a `this::trackInflatedBytes` method exists — byte accumulation is stack-local via `long[] inflatedAcc = new long[]{0L}` updated from `counter.getBytesRead()`.
- **Streaming parser hygiene:** the `JsonParser` must be opened with `disable(JsonParser.Feature.AUTO_CLOSE_SOURCE)` immediately after creation. This mirrors the writer-side `JsonGenerator.Feature.AUTO_CLOSE_TARGET=false` discipline at `BackupArchiveService.java:152`. Without it, closing the parser cascades to the `ZipInputStream` and prevents the next `getNextEntry()` call.
- **Path-traversal scope (Phase 74 only — read-only):** `PathTraversalGuard.assertWithin(stagingRoot, entryName)` resolves `entryName` against `stagingRoot` and asserts `startsWith(stagingRoot)`. Plan 04 passes the ZIP's PARENT DIRECTORY as the staging root, which is the correct READ-TIME interpretation for Phase 74 — Phase 74 NEVER EXTRACTS entries to disk (the reader is purely a counting/parsing pass on inflated streams), so the loose parent-directory base is adequate to reject `../../etc/passwd`-shaped attacks. **Phase 75 will EXTRACT `uploads/*` entries to the filesystem** as part of the wipe+restore flow; at that point the traversal-root MUST tighten to a per-import EXTRACTION SUBDIRECTORY (e.g. `data/<env>/backup-staging/<importId>/uploads/`) to defend against intra-staging-dir traversal between concurrent imports. The Plan 02 helper is parameterized for this exact reason — Phase 75 passes a tighter root without modifying the predicate. Phase 74 does not extract, so the current loose check is adequate. (Tracked separately for the Phase 75 plan-set — no Plan 04 action item.)
- **`IOException` is wrapped, not declared:** the three reader methods do not declare `throws IOException`. Any underlying I/O failure (truncated central directory, corrupted entry header) is caught and re-thrown as `BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", cause)`. This keeps the public contract single-exception and aligns with Plan 05's expected catch pattern (`catch (BackupArchiveException ex)` — no `IOException` branch needed in the controller).
- **No new Flyway migrations.** Plan 04 is pure read-side service work. Confirmed against Phase 74 CONTEXT D-?? — zero migrations in Phase 74.
- **Test discipline (D-25):** all malicious fixtures are generated programmatically in the test method body via `ZipOutputStream`. No binary blobs committed under `src/test/resources/backup-fixtures/malicious/`. The Phase-73 happy-path fixture is produced at runtime by calling `archiveService.writeZip(...)` — no committed binary export either.
- **Coverage:** the six new tests exercise every branch of the three new public methods AND every Reason-throwing path in the new private helper. JaCoCo should land near 100 % line coverage on the reader extension; the ≥ 82 % project minimum (CLAUDE.md "Constraints") is not at risk.

## PLAN COMPLETE 04
