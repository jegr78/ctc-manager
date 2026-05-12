# Phase 74: Backup Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate — Research

**Researched:** 2026-05-12
**Domain:** Spring Boot 4.0.6 multipart import + hardened ZIP parsing + stateless preview/confirm flow
**Confidence:** HIGH
**Researcher:** Claude (gsd-researcher)

## Overview

Phase 74 implements a **write-free** import pipeline that mirrors Phase 73's export. All 25 decisions (D-01..D-25) are locked in `74-CONTEXT.md`. This research answers HOW each decision is implemented at the Java/Spring/Thymeleaf level. Findings are tagged with provenance — `[VERIFIED]` (probed in this repo), `[CITED]` (read in official docs), `[ASSUMED]` (training knowledge).

**Primary recommendation:** Use a small custom `LimitedInputStream` wrapper for ZipBomb defense (no new dependency), reuse the existing `card-grid` CSS class for the 24-card preview grid (already defined in `admin.css:1692-1696`), implement `MaxUploadSizeExceededException` as a **new ControllerAdvice class** that returns `String "redirect:/admin/backup"` with `RedirectAttributes` (does not touch the existing `ModelAndView`-based `GlobalExceptionHandler`), and generate all malicious fixtures programmatically in a shared `MaliciousZipFactory` test helper.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01:** All UI text is English. The German strings in REQUIREMENTS.md SECU-04 / IMPORT-02 / IMPORT-04 are treated as spec examples of meaning, not locked wording. CLAUDE.md `feedback_ui_language.md` wins.

**D-02:** Final English strings (locked, terse style):
1. Multipart size exceeded → Flash: `Upload too large — maximum is 100 MB.`
2. Schema-version mismatch (HTTP 400 + Flash): `Schema version mismatch: backup={actual}, expected={current}. Cannot import.`
3. ZIP-Slip / per-entry / total / count reject (Flash): `Backup archive failed safety checks (size or path) and was rejected.`
4. Confirm checkbox label: `I am an admin and I understand all operational data will be deleted.`
5. Stub execute redirect Flash: `Validation succeeded. Import execution will be enabled in Phase 75.`

**D-03:** Compact card grid, one card per entity (24 cards). Each card: entity name, `current rows → imported rows`, delta pill (red = data loss, green = ≥, gray = both zero).

**D-04:** Green schema-match banner above grid. Mismatches never reach the preview page (HTTP 400 + Flash redirect).

**D-05:** Header block above the pill: ZIP filename, size (KB/MB), uploads-file count, total imported rows.

**D-06:** Preview-page CTAs: secondary `Cancel` (links to `/admin/backup`, triggers cleanup), primary `Proceed to Confirm`.

**D-07:** Dedicated templates `admin/backup-preview.html` + `admin/backup-confirm.html`. Both use the `admin/layout` fragment.

**D-08:** `POST /admin/backup/import-execute` is a Phase 74 validation stub: re-reads staged ZIP, re-validates, redirects with Flash `Validation succeeded. Import execution will be enabled in Phase 75.` Staging file NOT deleted by the stub.

**D-09:** Schema-version mismatch rejected at preview-upload AND re-checked at execute.

**D-10:** Confirm-checkbox is server-side authoritative via `BackupImportConfirmForm.acknowledged: @NotNull @AssertTrue Boolean`. JS confirm is UX-only.

**D-11:** Reuse `FileStorageService` SECU-02 path-traversal pattern via shared helper or inline-comment cross-reference (`FileStorageService:153-158`). New: `Paths.get(stagingDir).resolve(entryName).normalize().startsWith(stagingDir.toRealPath())`.

**D-12:** Constants `MAX_ENTRY_BYTES = 50*1024*1024`, `MAX_TOTAL_BYTES = 500*1024*1024`, `MAX_ENTRIES = 50_000`. Enforce streaming via inflated-byte counter + entry counter. Don't trust `ZipEntry.getSize()`.

**D-13:** Multipart limits in `application.yml` only:
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB
server:
  tomcat:
    max-http-form-post-size: 104857600
    max-swallow-size: 104857600
```

**D-14:** `GlobalExceptionHandler` gets a new `@ExceptionHandler(MaxUploadSizeExceededException.class)` mapping — `RedirectAttributes` flash to `/admin/backup`, NOT `ModelAndView`.

**D-15:** Staging dir property `app.backup.staging-dir`, default `data/${spring.profiles.active}/backup-staging`.

**D-16:** Reject paths delete staging file synchronously in `try/finally`. Failed delete logged WARN.

**D-17:** `ApplicationReadyEvent` listener `BackupStagingCleanup` (`@Component`) clears staging directory at startup.

**D-18:** No `@SessionAttributes`, no in-memory cache. UUID round-trips through hidden form input.

**D-19:** Single `BackupImportService` (`org.ctc.backup.service`). Phase 74 public surface: `stage(MultipartFile)` → `BackupImportPreview`; `reparse(UUID)` → `BackupImportPreview`; `deleteStagingFile(UUID)`.

**D-20:** `BackupArchiveService` gains `readManifest(Path)`, `countDataEntries(Path)` (streaming JsonParser), `countUploadFiles(Path)`.

**D-21:** Preview DTO records:
```java
record BackupImportPreview(UUID stagingId, String originalFilename, long fileSizeBytes,
    int schemaVersion, int currentSchemaVersion, boolean schemaMatches,
    List<EntityRowCount> entityCounts, int uploadFileCount, long totalImportedRows) {}
record EntityRowCount(String tableName, String humanLabel, long currentRows, long importedRows) {}
```

**D-22:** `BackupController` extended with 4 endpoints under `/admin/backup`: `import-preview`, `import-confirm`, `import-execute`, `import-cancel`. CSRF + profile-conditional auth identical to existing export.

**D-23:** Upload form lives in existing `admin/backup.html` below the Export form.

**D-24:** Minimum tests: `BackupImportServiceIT`, `BackupImportSchemaVersionMismatchIT`, `BackupImportZipSlipIT`, `BackupImportZipBombIT`, `BackupImportMultipartLimitIT`, `BackupImportE2ETest`, `BackupStagingCleanupIT`.

**D-25:** Malicious fixtures generated programmatically in `src/test/resources/backup-fixtures/malicious/` via `ZipOutputStream` snippets in `@BeforeAll`. Not committed as binaries.

### Claude's Discretion

- `EntityRowCount.humanLabel` mapping (helper or static map)
- Card-grid CSS — reuse `admin.css` `card-grid` (verified to exist at L1692) vs add `backup-preview-grid`
- Whether `PathTraversalGuard` is its own class or inline with comment
- `BackupArchiveException` reason-code enum design
- Flash key convention follows CTC `successMessage` / `errorMessage`

### Deferred Ideas (OUT OF SCOPE)

- Per-Saison import selectivity (v1.11+ `IMPORT-FUT-01`)
- SHA-256 checksum verify (v1.11+ `IMPORT-FUT-02`)
- Admin staging-files browser page
- Scheduled cleanup job
- i18n via `messages.properties`
- `BackupImportRollbackIT` (Phase 75)
- Live MariaDB UAT round-trip (Phase 75 QUAL-03)
- Import lock + read-only banner + auto-export (Phase 76)
- Replace-All transaction, JPA-auditing bypass, upload-tree restore (Phase 75)
- Audit-log viewer UI

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IMPORT-01 | Multipart upload to `data/{profile}/backup-staging/upload-{uuid}.zip`, configurable via `app.backup.staging-dir`; renders BackupPreview page with per-table wipe+restore counts | §1.4 Staging-file lifecycle, §1.7 Profile-aware path interpolation, §1.10 Card grid CSS, §2.1 `BackupImportPreview` DTO, §2.4 Streaming `countDataEntries` |
| IMPORT-02 | Schema-version check BEFORE any DB write: mismatch → HTTP 400 + Flash; DB unchanged | §3.1 Defense-in-depth re-validation, §4.SC#2 row-count snapshot assertion |
| IMPORT-03 | Preview screen: current vs imported, uploads count, schema match indicator; STATELESS — re-parse on execute (D-15 v1.8 pattern, no `@SessionAttributes`) | §2.1 DTO, §2.5 hidden UUID input, §3.2 reparse |
| IMPORT-04 | Confirm dialog with mandatory checkbox + Pflicht-Bestätigung; JS confirm zusätzlich | §1.5 `@AssertTrue` validation, §2.3 Confirm form + dialog wiring |
| SECU-01 | ZIP-Slip defense: `startsWith(uploadDir.toRealPath())` reusing v1.1 SECU-02 pattern | §1.1 path-traversal pattern from `FileStorageService:153-158` |
| SECU-02 | ZipBomb: 50 MB per-entry, 500 MB total, 50.000 entries; HTTP 400 + Flash | §1.2 ZipBomb implementation via `LimitedInputStream` |
| SECU-03 | Multipart limits raised in `application.yml`: 100 MB + Tomcat keys | §1.6 application.yml layering |
| SECU-04 | `MaxUploadSizeExceededException` mapped in `GlobalExceptionHandler` with friendly Flash | §1.4 New `@ControllerAdvice` redirect handler |

</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Multipart upload reception | API/Backend (Spring MVC + Tomcat) | — | Server-side; multipart parsing is Tomcat's job, Spring MVC binds `MultipartFile` |
| ZIP hardening (Slip/Bomb) | API/Backend | — | Pure server-side defense — never trust the client; check `ZipEntry.getName()` and inflated bytes |
| Staging file persistence | Database/Storage (file system) | — | Local filesystem under `data/{profile}/backup-staging/` — not a remote object store |
| Schema-version gate | API/Backend | — | Read `manifest.json` and compare to `BackupSchema.SCHEMA_VERSION` before any DB write |
| Manifest parsing | API/Backend | — | Jackson `backupObjectMapper` (`@Qualifier`) — strict (`FAIL_ON_UNKNOWN_PROPERTIES=true`) |
| Per-entity row count (current) | API/Backend (JPA) | Database | `repo.count()` × 24 = 24 lightweight SELECTs |
| Per-entity row count (imported) | API/Backend (Jackson streaming) | — | Manifest `table_counts` is authoritative; defense-in-depth: streaming `JsonParser` per data file (optional) |
| Preview rendering | Frontend Server (SSR/Thymeleaf) | — | Server-rendered cards via `admin/backup-preview.html` template + `admin/layout` fragment |
| Confirm checkbox enforcement | API/Backend (Jakarta Validation) | Browser (JS UX-only) | Authoritative `@AssertTrue` server-side; `confirm()` JS is UX sugar, not a security boundary |
| Stale staging cleanup | API/Backend (Spring) | — | `ApplicationReadyEvent` listener — runs once at startup, no scheduled tier needed |
| Flash redirect on size limit | API/Backend (`@ControllerAdvice`) | Browser (cookie) | Spring `FlashMap` ⇄ session cookie → next request re-hydrates Flash attributes |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Multipart parsing, `@ControllerAdvice`, `ApplicationReadyEvent`, MVC, security | [VERIFIED: pom.xml:8] Already pinned; no version bump |
| Jakarta Validation | (managed by Spring Boot 4.0.6) | `@NotNull`, `@AssertTrue` on `BackupImportConfirmForm` | [VERIFIED: pom.xml:49] `spring-boot-starter-validation` pulled in already |
| Jackson 2.21.x (`com.fasterxml.jackson.databind`) | (managed by SB 4.0.6) | Manifest deserialize, streaming `JsonParser` for `countDataEntries` | [VERIFIED: pom.xml:75 comment "Jackson 2.21.x"] — backup module uses this 2.x mapper (NOT Spring Boot 4's `tools.jackson.*` primary mapper) |
| `java.util.zip.ZipInputStream` | JDK 25 | Streaming ZIP reads with mid-stream limit enforcement | [VERIFIED: pom.xml:17 `java.version=25`] Java's built-in stream API; pairs with custom `LimitedInputStream` |
| `org.springframework.mock.web.MockMultipartFile` | (test scope, Spring Boot 4.0.6) | Multipart fixtures in tests | [VERIFIED: src/test/java/.../CsvImportControllerTest.java:52] CTC already uses this idiom 14 times |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Playwright | 1.59.0 | E2E click-through (`BackupImportE2ETest`) | [VERIFIED: pom.xml:18] Existing `-Pe2e` profile; mirror `BackupExportE2ETest` shape |
| AssertJ | (managed) | All `assertThat(...)` in tests | [VERIFIED: TESTING.md L13] CTC convention; `BackupRoundTripIT` style |
| Lombok `@RequiredArgsConstructor` + `@Slf4j` | 1.18.46 | Service-class boilerplate | [VERIFIED: pom.xml:19, CONVENTIONS.md L57-67] Mirror `BackupArchiveService` constructor |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Custom `LimitedInputStream` (~25 LOC) | Apache Commons IO `BoundedInputStream` | [VERIFIED: pom.xml grep] Commons IO is NOT a current dependency; adding it for one wrapper class violates the v1.10 ROADMAP "no new Maven dependencies" decision (STATE.md L77). 25 LOC of custom code is cheaper than a new POM dependency. |
| `@SessionAttributes` for preview state | Stateless re-parse via UUID | [VERIFIED: 74-CONTEXT.md D-18 + v1.8 `CsvImportController`] D-18 locks stateless approach; this matches the v1.8 D-15 staging pattern proven to work in `CsvImportController.java`. |
| `ModelAndView` for `MaxUploadSizeExceededException` | `String "redirect:/..."` + `RedirectAttributes` | [VERIFIED: 74-CONTEXT.md D-14] D-14 locks Flash-redirect. Existing handlers in `GlobalExceptionHandler.java:28-77` return `ModelAndView`; mixing return types in one handler class is supported by Spring MVC (per-method return-type dispatch), so a new `@ExceptionHandler` method can return `String` while siblings return `ModelAndView`. |
| Apache `commons-compress` for ZIP | JDK `java.util.zip` | [VERIFIED: pom.xml] Phase 73 export uses JDK `java.util.zip` exclusively (`BackupArchiveService.java:20-22`). Phase 74 stays consistent. JDK API is sufficient for streaming reads with per-entry limits. |
| `@Scheduled(fixedDelay=...)` cleanup | `ApplicationReadyEvent` listener | [VERIFIED: 74-CONTEXT.md D-17] D-17 locks startup-sweep — admin uploads are 1-2/week, scheduled job is over-engineered. |
| Maven `commons-io` dependency for size constants | Hand-rolled `BackupImportLimits` constants class | [VERIFIED: 74-CONTEXT.md D-12] Tiny, project-local, no new dep. |

**Installation:** No new Maven dependencies. All required libraries are transitively available.

**Version verification:**
- `spring-boot-starter-parent` 4.0.6 → [VERIFIED: pom.xml:8] published 2026-Q1, Thymeleaf 3.1.5 pinned at L23-27
- `jackson-datatype-jsr310` → [VERIFIED: pom.xml:78-81] managed by Spring Boot parent (2.21.x per L75 comment)
- `playwright` 1.59.0 → [VERIFIED: pom.xml:18]
- `lombok` 1.18.46 → [VERIFIED: pom.xml:19]

## Architecture Patterns

### System Architecture Diagram

```
              Browser
                |
                | (1) POST /admin/backup/import-preview (multipart/form-data, file=*.zip)
                v
   +---------------------------------+
   |  Tomcat (max-request-size       |
   |  104857600 — 100 MB)            |
   |  ┌── 101 MB ──> MaxUploadSize-  |
   |  └──────────────  ExceededExc.  |---> [F] @ExceptionHandler (new ControllerAdvice)
   +-------------+-------------------+      → redirect:/admin/backup + Flash D-02#1
                 |
                 v
   +---------------------------------+
   |  BackupController.importPreview |
   |    @PostMapping("/import-       |
   |     preview")                   |
   |    MultipartFile file           |
   +-------------+-------------------+
                 |
                 v
   +---------------------------------+
   |  BackupImportService.stage(file)|
   |                                 |
   |  1. ZIP magic sniff (50 4B 03 04)|
   |  2. Save to staging dir         |
   |     data/${profile}/backup-     |
   |     staging/upload-{uuid}.zip   |
   |  3. readManifest(path) -------- | --> [A] BackupArchiveService.readManifest
   |  4. schemaVersion == ?          |     (ZipInputStream → first entry must be
   |     no → BackupArchiveException |      "manifest.json" → backupObjectMapper)
   |     yes → continue              |
   |  5. countDataEntries(path) ---- | --> [B] BackupArchiveService.countDataEntries
   |     (per-entry hardening)       |     (streaming JsonParser ARRAY-token loop)
   |  6. countUploadFiles(path) ---- | --> [C] BackupArchiveService.countUploadFiles
   |  7. Build BackupImportPreview   |
   |     DTO from manifest + counts  |
   |  8. Throw → reject paths        |
   |     → delete staging in finally |
   +-------------+-------------------+
                 |
                 v
   +---------------------------------+
   |  Model.addAttribute("preview")  |
   |  return "admin/backup-preview"  |
   +-------------+-------------------+
                 |
                 | (2) GET render — Thymeleaf
                 v
   +---------------------------------+
   |  admin/backup-preview.html      |
   |  - Header block (D-05)          |
   |  - Schema match pill (D-04)     |
   |  - 24-card grid (D-03)          |
   |    [card-grid CSS reused]       |
   |  - Hidden input stagingId       |
   |  - "Proceed" button → POST      |
   |    /admin/backup/import-confirm |
   |  - "Cancel" → POST              |
   |    /admin/backup/import-cancel  |
   +-------------+-------------------+
                 |
                 v
   +---------------------------------+
   |  BackupController.importConfirm |
   |  re-reads stagingId from form   |
   |  renders admin/backup-confirm   |
   |  with BackupImportConfirmForm   |
   |  (acknowledged=false)           |
   +-------------+-------------------+
                 |
                 v
   +---------------------------------+
   |  admin/backup-confirm.html      |
   |  - Mandatory checkbox (D-02#4)  |
   |  - JS onSubmit confirm dialog   |
   |  - Submit → POST /import-execute|
   +-------------+-------------------+
                 |
                 v
   +---------------------------------+
   |  BackupController.importExecute |
   |  @Valid BackupImportConfirmForm |
   |  acknowledged=false →           |
   |    re-render confirm page       |
   |    with BindingResult errors    |
   |  acknowledged=true →            |
   |    backupImportService          |
   |      .reparse(stagingId)        | --> re-runs steps 3-7 (defense in depth)
   |    flash D-02#5 + redirect      |
   +---------------------------------+

   Startup:
   +---------------------------------+
   |  BackupStagingCleanup           |
   |  @EventListener(Application-    |
   |    ReadyEvent.class)            |
   |  Files.walk(stagingDir)         |
   |    .filter("upload-*.zip")      |
   |    .forEach(Files::delete)      |
   +---------------------------------+
```

### Recommended Project Structure

```
src/main/java/org/ctc/backup/
├── BackupController.java                   # [extended — adds 4 endpoints]
├── service/
│   ├── BackupArchiveService.java           # [extended — adds 3 read methods]
│   ├── BackupImportService.java            # [NEW]
│   ├── BackupStagingCleanup.java           # [NEW — ApplicationReadyEvent listener]
│   └── BackupImportLimits.java             # [NEW — constants holder, optional]
├── dto/
│   ├── BackupImportPreview.java            # [NEW record]
│   ├── EntityRowCount.java                 # [NEW record]
│   └── BackupImportConfirmForm.java        # [NEW Lombok form DTO]
├── exception/
│   └── BackupArchiveException.java         # [NEW — reason-code enum]
└── security/
    └── (planner's choice: PathTraversalGuard or inline)

src/main/java/org/ctc/admin/controller/
└── BackupUploadExceptionHandler.java       # [NEW @ControllerAdvice — RedirectAttributes-based]
                                            # Sibling to existing GlobalExceptionHandler.

src/main/resources/
├── application.yml                         # [edited — multipart 100 MB + Tomcat + staging-dir]
└── templates/admin/
    ├── backup.html                         # [edited — adds Import form below Export]
    ├── backup-preview.html                 # [NEW]
    └── backup-confirm.html                 # [NEW]

src/test/java/org/ctc/backup/
├── service/
│   ├── BackupImportServiceIT.java
│   ├── BackupImportSchemaVersionMismatchIT.java
│   ├── BackupImportZipSlipIT.java
│   ├── BackupImportZipBombIT.java
│   ├── BackupStagingCleanupIT.java
│   └── BackupArchiveReadIT.java             # [readManifest + count methods]
├── BackupImportMultipartLimitIT.java
└── BackupControllerImportSecurityIT.java    # [@Nested prod+dev — mirror Phase 73]

src/test/java/org/ctc/e2e/
└── BackupImportE2ETest.java                 # [-Pe2e profile]

src/test/resources/backup-fixtures/
└── (programmatic — no committed binaries per D-25;
    MaliciousZipFactory helper generates fixtures at test runtime)
```

### Pattern 1: Hardened ZIP Reading via LimitedInputStream Wrapper

**What:** A tiny per-entry wrapper around `ZipInputStream` that throws `BackupArchiveException` if more than `MAX_ENTRY_BYTES` (50 MB) are read. Defends against deflate bombs where `ZipEntry.getSize()` claims 1 KB but inflates to 5 GB. [CITED: Snyk "Zip Slip" + OWASP "Zip Bomb" hardening guidance]

**When to use:** Every ZIP-read method on `BackupArchiveService` (readManifest, countDataEntries, countUploadFiles).

**Example:**
```java
// Source: hand-rolled, ~25 LOC; equivalent to commons-io BoundedInputStream
// minus the dependency. [ASSUMED] no upstream changes since SB 4.0.6 ship.
public final class LimitedInputStream extends FilterInputStream {
    private final long maxBytes;
    private long bytesRead = 0;

    public LimitedInputStream(InputStream in, long maxBytes) {
        super(in);
        this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1 && ++bytesRead > maxBytes) {
            throw new BackupArchiveException(Reason.ENTRY_TOO_LARGE,
                "Entry exceeds " + maxBytes + " bytes");
        }
        return b;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        int n = super.read(buf, off, len);
        if (n > 0 && (bytesRead += n) > maxBytes) {
            throw new BackupArchiveException(Reason.ENTRY_TOO_LARGE,
                "Entry exceeds " + maxBytes + " bytes");
        }
        return n;
    }
}
```

Usage in the streaming reader (per-entry):
```java
try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipPath))) {
    ZipEntry entry;
    int entryCount = 0;
    long totalBytes = 0;
    while ((entry = zin.getNextEntry()) != null) {
        if (++entryCount > MAX_ENTRIES) {
            throw new BackupArchiveException(Reason.TOO_MANY_ENTRIES, ...);
        }
        guardPath(entry.getName());  // ZIP-Slip (D-11)
        // Don't trust entry.getSize() — wrap with LimitedInputStream
        InputStream entryStream = new LimitedInputStream(zin, MAX_ENTRY_BYTES);
        long entrySize = consume(entryStream);  // reads + counts via the limit wrapper
        totalBytes += entrySize;
        if (totalBytes > MAX_TOTAL_BYTES) {
            throw new BackupArchiveException(Reason.TOTAL_TOO_LARGE, ...);
        }
    }
}
```

**Confidence:** HIGH. The pattern is straightforward; the only subtlety is that `ZipInputStream.getNextEntry()` resets the inflated-byte counter via the wrapper recreation per entry — wrap **inside** the loop, not outside.

### Pattern 2: Path-Traversal Guard (Reuse from `FileStorageService:153-158`)

**What:** Resolve every `ZipEntry.getName()` against the staging directory and assert the normalized result still starts with the staging root. Reject absolute paths and `..` sequences.

**When to use:** Inside every `getNextEntry()` loop in `BackupArchiveService`. Also applies if Phase 74 ever extracts uploads (it doesn't — Phase 74 only counts; Phase 75 extracts).

**Example:**
```java
// Source: mirrors src/main/java/org/ctc/domain/service/FileStorageService.java:153-158
// (validatePathWithinUploadDir + validateNoPathTraversal). [VERIFIED] in this repo.
private void guardPath(String entryName, Path stagingRoot) {
    if (entryName == null || entryName.contains("..") || entryName.startsWith("/")) {
        throw new BackupArchiveException(Reason.PATH_TRAVERSAL,
            "Suspicious entry name: " + entryName);
    }
    Path resolved = stagingRoot.resolve(entryName).normalize();
    if (!resolved.startsWith(stagingRoot.toRealPath())) {
        throw new BackupArchiveException(Reason.PATH_TRAVERSAL,
            "Path escapes staging root: " + entryName);
    }
}
```

**Confidence:** HIGH. Identical idiom to `FileStorageService.validatePathWithinUploadDir` (verified in code). The export side `BackupArchiveService.java:122` (`entry.relativePath().contains("..")`) already uses the lightweight check — Phase 74's read side is the symmetric defense.

### Pattern 3: Manifest-First Streaming JsonParser

**What:** Read the FIRST entry, assert it's `manifest.json`, deserialize into `BackupManifest`. The wire contract enforces this (Phase 73 `BackupArchiveService.java:104` writes it first; `BackupRoundTripIT` proves it).

**When to use:** `BackupArchiveService.readManifest(Path zipPath)`.

**Example:**
```java
// Source: pairs with Phase 73 writer (BackupArchiveService.java:104).
// [VERIFIED] backupObjectMapper qualifier already exists from Phase 72 D-11.
public BackupManifest readManifest(Path zipPath) throws IOException {
    try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipPath))) {
        ZipEntry first = zin.getNextEntry();
        if (first == null || !"manifest.json".equals(first.getName())) {
            throw new BackupArchiveException(Reason.MANIFEST_MISSING,
                "First ZIP entry must be 'manifest.json', got: " +
                (first == null ? "<empty zip>" : first.getName()));
        }
        InputStream limited = new LimitedInputStream(zin, MAX_ENTRY_BYTES);
        // backupObjectMapper has FAIL_ON_UNKNOWN_PROPERTIES=true — tampered fields fail here.
        return backupObjectMapper.readValue(limited, BackupManifest.class);
    }
}
```

**Confidence:** HIGH. The wire contract is locked by Phase 73 verification (`BackupArchiveServiceIT.givenDevFixture_whenWriteZip_thenManifestIsFirstEntry`). The `FAIL_ON_UNKNOWN_PROPERTIES=true` (Phase 72 D-11) catches manifest tampering automatically.

### Pattern 4: Streaming `countDataEntries` via JsonParser ARRAY-Token Loop

**What:** For every `data/<entity>.json` entry, open a Jackson `JsonParser`, expect `START_ARRAY`, then count `START_OBJECT` tokens via `nextToken()` loop without buffering the whole array.

**When to use:** `BackupArchiveService.countDataEntries(Path)` — defense-in-depth for the manifest's `table_counts` (the manifest is authoritative per Phase 73 verification, but re-counting from data files is a check against forged manifests).

**Example:**
```java
// Source: Jackson 2.21.x streaming API. [CITED: jackson-databind JsonParser javadoc]
public Map<String, Long> countDataEntries(Path zipPath) throws IOException {
    Map<String, Long> counts = new LinkedHashMap<>();
    try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipPath))) {
        ZipEntry entry;
        int entryCount = 0;
        long totalBytes = 0;
        while ((entry = zin.getNextEntry()) != null) {
            if (++entryCount > MAX_ENTRIES) throw new BackupArchiveException(...);
            guardPath(entry.getName(), stagingRoot);
            String name = entry.getName();
            if (!name.startsWith("data/") || !name.endsWith(".json")) continue;
            // Strip "data/" prefix and ".json" suffix → kebab-case slug → snake_case tableName.
            String tableName = name.substring("data/".length(),
                                              name.length() - ".json".length())
                                   .replace('-', '_');
            InputStream limited = new LimitedInputStream(zin, MAX_ENTRY_BYTES);
            long rowCount = 0;
            try (JsonParser parser = backupObjectMapper.getFactory().createParser(limited)) {
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    throw new BackupArchiveException(Reason.MALFORMED_DATA,
                        "Expected array in " + name);
                }
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    parser.skipChildren();   // O(1) memory — never materializes the row
                    rowCount++;
                }
            }
            counts.put(tableName, rowCount);
            totalBytes += limited.bytesRead();  // see LimitedInputStream extension
            if (totalBytes > MAX_TOTAL_BYTES) throw new BackupArchiveException(...);
        }
    }
    return counts;
}
```

**Confidence:** HIGH. `parser.skipChildren()` is the canonical Jackson pattern for "count without materialize." The slug-to-table inversion mirrors Phase 72 `EntityRef.fromEntityType` (`table.replace('_', '-')` reversed).

### Pattern 5: ZIP Magic-Number Sniff Without Stream Consumption

**What:** Read the first 4 bytes of `MultipartFile.getInputStream()` and assert they equal `50 4B 03 04`. Then re-read the file by calling `getInputStream()` again — Spring's `StandardMultipartFile` opens a fresh stream each call because the multipart body is buffered to disk by Tomcat before reaching the handler.

**When to use:** `BackupImportService.stage(MultipartFile)` — first check before saving to staging.

**Example:**
```java
// Source: Spring MVC StandardMultipartHttpServletRequest docs.
// [CITED: Spring Framework reference: MultipartFile contract states each
//  getInputStream() call returns a fresh stream — backed by a temp file or memory.]
// [ASSUMED] Spring Boot 4.0.6 preserves this contract; verified empirically via
// existing CSV import (CsvImportController.java:47 reads file.getInputStream() then
// the same file is consumed again in /execute after redirect — same pattern).
private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};

private void sniffZipMagic(MultipartFile file) throws IOException {
    try (InputStream in = file.getInputStream()) {
        byte[] header = in.readNBytes(4);
        if (header.length < 4 || !Arrays.equals(header, ZIP_MAGIC)) {
            throw new BackupArchiveException(Reason.NOT_A_ZIP,
                "File does not look like a ZIP archive (bad magic bytes)");
        }
    }
    // Second getInputStream() — Spring opens a fresh stream from the buffered body.
    // No mark/reset needed.
}
```

**Confidence:** MEDIUM. The "fresh stream per call" contract is documented and CTC already relies on it implicitly (CSV import re-reads via `getInputStream()` on the `/execute` re-parse path). [ASSUMED for Spring Boot 4.0.6 specifically] — confirm by running an existing multipart test under SB 4.0.6 (already green per Phase 73 E2E run). The alternative (`PushbackInputStream` or `BufferedInputStream` with `mark`/`reset`) is not needed.

### Pattern 6: `@ExceptionHandler(MaxUploadSizeExceededException.class)` with `RedirectAttributes`

**What:** A new `@ControllerAdvice` class with a single `@ExceptionHandler` method that returns `String "redirect:/admin/backup"` after calling `redirectAttributes.addFlashAttribute("errorMessage", "...")`.

**When to use:** Handles Tomcat-thrown `MaxUploadSizeExceededException` for any multipart endpoint. Placing it in a new `@ControllerAdvice` (rather than amending the existing `GlobalExceptionHandler`) keeps the two return-type styles separated and makes the import-flash redirect behavior locally readable.

**Example:**
```java
// Source: [VERIFIED] Spring MVC `@ControllerAdvice` per-method return-type dispatch.
// Sibling to existing GlobalExceptionHandler (which returns ModelAndView).
// Both classes coexist; Spring picks the most specific @ExceptionHandler match.
@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class BackupUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        log.warn("Upload exceeded multipart limit: uri={}, maxBytes={}",
                request.getRequestURI(), ex.getMaxUploadSize(), ex);
        redirectAttributes.addFlashAttribute("errorMessage",
                "Upload too large — maximum is 100 MB.");
        return "redirect:/admin/backup";
    }
}
```

**Confidence:** HIGH for the pattern itself. The mixed-return-type concern is addressed by **separating** the new handler into its own `@ControllerAdvice` class — Spring MVC supports multiple `@ControllerAdvice` beans and dispatches per-exception-type to the most specific match. [CITED: Spring Framework reference `@ControllerAdvice` semantics.]

### Pattern 7: `@AssertTrue` + `@NotNull Boolean` on Confirm Form

**What:** Jakarta Validation `@AssertTrue` validates that a boolean property evaluates to `true`. On a `Boolean` wrapper (not primitive), a `null` value SHORT-CIRCUITS the `@AssertTrue` check (treated as "no value" — passes), so `@NotNull` is required alongside it for proper "must be checked" semantics.

**When to use:** `BackupImportConfirmForm.acknowledged`.

**Example:**
```java
// Source: [CITED: Jakarta Bean Validation 3.0 spec §6.1.1] —
// "constraints on null values are considered valid unless explicitly stated"
// (Validation API javadoc on @AssertTrue: "supports null").
// Therefore @NotNull is needed to reject null + false; @AssertTrue alone passes null.
@Getter @Setter @NoArgsConstructor
public class BackupImportConfirmForm {

    @NotNull
    private UUID stagingId;

    @NotNull(message = "{backup.confirm.required}")
    @AssertTrue(message = "You must confirm the data-loss acknowledgement.")
    private Boolean acknowledged;
}
```

**Error codes in `BindingResult`:**
- `@NotNull` fails → field error code `NotNull` (Spring) + message-key `NotNull.backupImportConfirmForm.acknowledged`
- `@AssertTrue` fails → field error code `AssertTrue` + message-key `AssertTrue.backupImportConfirmForm.acknowledged`

Template renders via:
```html
<input type="checkbox" id="acknowledged" name="acknowledged" value="true"
       th:errorclass="input-error">
<small th:if="${#fields.hasErrors('acknowledged')}"
       th:errors="*{acknowledged}" class="text-error"></small>
```

**Confidence:** HIGH. Jakarta Validation behavior is spec-defined and stable across Spring Boot 4.x.

### Pattern 8: `ApplicationReadyEvent` Listener for Startup Cleanup

**What:** `@Component` class with a single method annotated `@EventListener(ApplicationReadyEvent.class)`. Fires after `@PostConstruct`, after `ApplicationContextEvent`, after `ContextRefreshedEvent`, AND after the embedded web server is fully started — guaranteeing safe filesystem access.

**When to use:** `BackupStagingCleanup` — wipes `data/{profile}/backup-staging/upload-*.zip` once at app startup.

**Example:**
```java
// Source: [CITED: Spring Boot reference §1.4 "Spring Application: Application Events
// and Listeners" — ApplicationReadyEvent fires LAST among startup events.]
// [VERIFIED] no existing usage in CTC (grep -rn "ApplicationReadyEvent" returned empty),
// so this is a greenfield pattern in the codebase.
@Component
@RequiredArgsConstructor
@Slf4j
public class BackupStagingCleanup {

    @Value("${app.backup.staging-dir:data/${spring.profiles.active:dev}/backup-staging}")
    private String stagingDirRaw;

    @EventListener(ApplicationReadyEvent.class)
    public void clearStaleStagingFiles() {
        Path stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        if (!Files.exists(stagingDir)) {
            log.debug("Staging dir does not exist yet, nothing to clean: {}", stagingDir);
            return;
        }
        try (var stream = Files.list(stagingDir)) {
            long deleted = stream
                .filter(p -> p.getFileName().toString().matches("upload-.*\\.zip"))
                .peek(p -> {
                    try { Files.delete(p); } catch (IOException e) {
                        log.warn("Failed to delete stale staging file: {}", p, e);
                    }
                })
                .count();
            log.info("Cleared {} stale staging files from {}", deleted, stagingDir);
        } catch (IOException e) {
            log.warn("Failed to scan staging dir for cleanup: {}", stagingDir, e);
        }
    }
}
```

**Confidence:** HIGH. `ApplicationReadyEvent` is the canonical Spring Boot signal for "everything is initialized and the web server is up." Fires once per `JVM` startup.

### Pattern 9: Profile-Aware `application.yml` Path Interpolation

**What:** `data/${spring.profiles.active}/backup-staging` interpolates whatever profiles are active. When multiple profiles (e.g., `dev,demo`) are active, `${spring.profiles.active}` resolves to the comma-joined string `dev,demo` — which produces an INVALID path containing a comma. The safe pattern is to supply a default-profile fallback.

**When to use:** `app.backup.staging-dir` default.

**Example:**
```yaml
# application.yml
app:
  backup:
    staging-dir: data/${spring.profiles.active:dev}/backup-staging
```

**Multi-profile gotcha:** When user runs `--spring.profiles.active=dev,demo`, Spring resolves `${spring.profiles.active}` to literal `"dev,demo"`. The path becomes `data/dev,demo/backup-staging` which fails on Windows (comma in path) and creates a literally-named directory on POSIX.

**Mitigation options:**
1. Document that multi-profile resolution uses the first profile only (Spring 4.x: `spring.profiles.active[0]` SpEL — verbose).
2. Inject the resolved value programmatically in `BackupImportService` via `Environment.getActiveProfiles()[0]`.
3. (Recommended) Override per profile: in `application-dev.yml` and `application-local.yml` and `application-docker.yml`, hardcode `app.backup.staging-dir: data/dev/backup-staging` (etc.). Spring's profile-specific YAML wins over the default.

**Confidence:** HIGH for the gotcha; MEDIUM for the recommended mitigation — but the gotcha contradicts D-15's "no per-profile override" intent. **Planner decision needed**: either accept the multi-profile risk (rare — only `dev,demo` matters in CTC) or revisit D-15.

[ASSUMED]: D-15's "no per-profile override" was written assuming single-profile activation. The `dev,demo` profile pairing (used for GT7 demo data per CLAUDE.md) breaks the assumption. **Recommend: planner adds a brief safeguard in `BackupImportService` that splits `${spring.profiles.active}` on comma and uses the first segment — `data/${active.split(',')[0]}/backup-staging` semantically, implemented in Java via `@Value` + `Environment` or a `@PostConstruct` resolver. This preserves D-15's single-source intent.**

### Anti-Patterns to Avoid

- **Trusting `ZipEntry.getSize()`:** Header-declared size can be spoofed. ALWAYS count inflated bytes via `LimitedInputStream`. The export side already learned this — Phase 73 `BackupArchiveService.java:122` skips suspicious paths but does not trust sizes; Phase 74 is the read side where this matters most.
- **Storing `BackupImportPreview` in `@SessionAttributes` or `HttpSession`:** Locked out by D-18. Phase 75 expects pure stateless re-parse on every step. Storing state would couple Phase 74 ↔ 75 in a way that breaks `import-execute` re-validation.
- **Catching `MaxUploadSizeExceededException` in the controller method:** Tomcat throws this BEFORE the controller method is invoked (the multipart parser fails during request resolution). The controller never sees it — only `@ControllerAdvice` can catch it.
- **Calling `ObjectMapper.readTree(...)` for `countDataEntries`:** Buffers the whole JSON document into memory. For a 50 MB entity file this is 5x worse than the streaming `JsonParser` pattern. Always use `parser.nextToken()` + `parser.skipChildren()`.
- **Returning `ResponseEntity` from `@ExceptionHandler(MaxUploadSizeExceededException)`:** Doesn't trigger Flash. Must return a `String` view name starting with `redirect:` so Spring's `RedirectView` handles the Flash propagation.
- **Deleting the staging file in the controller `finally` block:** That couples controller to file lifecycle. Service owns the staging file — controller delegates `deleteStagingFile(uuid)` on Cancel.
- **Using `Files.deleteIfExists(staging)` without try/finally around the throw paths:** D-16 requires synchronous delete on reject paths. Without `try/finally` the staging file leaks on every rejection.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Multipart parsing | Custom request-body parser | Spring MVC `@RequestParam MultipartFile file` | Tomcat handles disk-buffered uploads transparently; the auto-configured `MultipartResolver` is fully sufficient. |
| ZIP-archive reading | Read central directory manually | `java.util.zip.ZipInputStream` | JDK 25 provides streaming ZIP reads; pairs with `LimitedInputStream` for hardening. |
| JSON streaming | Custom array-element tokenizer | Jackson `JsonParser` + `skipChildren()` | Battle-tested; `backupObjectMapper` already configured (Phase 72 D-11). |
| Manifest deserialization | Custom map-walking parser | `objectMapper.readValue(in, BackupManifest.class)` | `FAIL_ON_UNKNOWN_PROPERTIES=true` catches manifest tampering for free. |
| Form-bound validation | Manual `if (!form.isAcknowledged()) ...` | `@Valid` + `@AssertTrue` + `BindingResult` | Spring auto-renders the field-level errors via Thymeleaf `*{...}`. |
| Flash redirect on exception | Session-attribute hack or thread-local | `@ControllerAdvice` + `RedirectAttributes` parameter | Spring MVC injects `RedirectAttributes` into `@ExceptionHandler` methods natively. |
| Startup hook | `@Scheduled(fixedDelay=...)` | `@EventListener(ApplicationReadyEvent.class)` | One-shot startup is the semantic; scheduling adds noise. |
| Profile-aware path | Custom `Environment.getActiveProfiles()` parser | `@Value` placeholder interpolation | (with multi-profile caveat per Pattern 9) |
| Path traversal defense | Inline `if (name.contains(".."))` everywhere | Centralized `Paths.resolve(...).normalize().startsWith(root)` | Defense-in-depth + matches v1.1 SECU-02 idiom (FileStorageService:153-158). |
| Stateless multi-step flow | Server-side cache map | Hidden UUID input + re-parse | Mirrors v1.8 D-15 CsvImportController pattern; survives server restarts. |

**Key insight:** Every problem in Phase 74 has a stock Spring/JDK/Jackson solution. The only hand-rolled code worth writing is the **~25-line `LimitedInputStream` wrapper** — and that's because adding a new Maven dep (Commons IO) for one class violates the v1.10 "no new deps" rule.

## Runtime State Inventory

> Phase 74 is **not** a rename/refactor/migration phase. This section is informational only — to identify any pre-existing runtime state Phase 74 must respect.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — Phase 74 has zero DB writes. `data_import_audit` table exists from Phase 72 V7 migration; Phase 74 does NOT write to it (Phase 75 does). | None |
| Live service config | None — Phase 74 has no external service config dependencies | None |
| OS-registered state | None | None |
| Secrets/env vars | `app.backup.staging-dir` is a new property; no secret. Profile activation (`SPRING_PROFILES_ACTIVE`) influences staging path resolution — already handled in Pattern 9 | None |
| Build artifacts | None — Phase 74 has zero Flyway migrations and zero new dependencies | None |

**The staging directory itself** (`data/{profile}/backup-staging/`) needs to exist at runtime; Phase 74's `BackupImportService` creates it via `Files.createDirectories(stagingDir)` on first use (mirrors `FileStorageService.store():37` pattern).

## Common Pitfalls

### Pitfall 1: `MaxUploadSizeExceededException` Throws Before Controller Invocation

**What goes wrong:** Developers try to `try/catch` the exception in the import-preview controller method. It never fires there.

**Why it happens:** Tomcat parses the multipart body BEFORE the request is dispatched to `BackupController.importPreview()`. When the body exceeds the limit, Tomcat throws during request resolution; Spring then propagates the exception to `@ControllerAdvice` handlers.

**How to avoid:** Implement the catch as a `@ControllerAdvice`-level `@ExceptionHandler(MaxUploadSizeExceededException.class)`. NEVER put it inside the controller.

**Warning signs:** The handler is in the controller and tests show 500 instead of 302 + Flash.

### Pitfall 2: Multi-Profile `${spring.profiles.active}` Interpolation

**What goes wrong:** When user runs `--spring.profiles.active=dev,demo`, the staging path resolves to `data/dev,demo/backup-staging` (literal comma). On Windows this fails immediately; on POSIX it silently creates a comma-named directory.

**Why it happens:** Spring's `${spring.profiles.active}` joins all active profiles with commas — it's a `List<String>` rendered via `toString()`.

**How to avoid:** Per Pattern 9: either override per profile in profile-specific YAML, or resolve programmatically in `@PostConstruct` by splitting on comma and taking the first profile.

**Warning signs:** `dev,demo` mode used by CTC for GT7 demo data (per CLAUDE.md Profiles section) — actively triggers this.

### Pitfall 3: `@AssertTrue` Passes on `null`

**What goes wrong:** Confirm form's `Boolean acknowledged` is declared with `@AssertTrue` only (no `@NotNull`). When the checkbox is unchecked, the browser sends NO `acknowledged` parameter, Spring binds `null`, and `@AssertTrue` silently passes (Bean Validation treats null as valid by default).

**Why it happens:** Jakarta Bean Validation §6.1.1 — null values pass all constraints unless `@NotNull` is also present.

**How to avoid:** Always pair `@NotNull` + `@AssertTrue` on Boolean wrapper fields. Don't use primitive `boolean` — `boolean acknowledged` would default to `false` and lose the null-vs-false distinction.

**Warning signs:** Unit test with `acknowledged=null` returns `BindingResult.hasErrors() == false`.

### Pitfall 4: Trusting `ZipEntry.getSize()`

**What goes wrong:** Code reads `entry.getSize()` from the ZIP central directory and decides "1 KB, allow it" — then inflates to 5 GB.

**Why it happens:** The ZIP central directory size field can be set to any value by the archiver. It's a hint, not a contract.

**How to avoid:** Always wrap the entry stream in `LimitedInputStream(MAX_ENTRY_BYTES)` and count actual inflated bytes.

**Warning signs:** ZipBomb test passes when it shouldn't.

### Pitfall 5: Manifest-First Order Already Wrong in Imported ZIP

**What goes wrong:** A ZIP not produced by Phase 73 might have entries in a different order (e.g., the OS `zip` CLI sometimes orders alphabetically). `readManifest` looks at entry 0, sees `data/cars.json`, throws.

**Why it happens:** The wire contract is "manifest first" but Phase 74 must defend against non-CTC-generated ZIPs.

**How to avoid:** This is the correct behavior — non-Phase-73 ZIPs must be rejected. The Flash message D-02#3 ("failed safety checks") covers it. Document this explicitly in `BackupArchiveException(Reason.MANIFEST_MISSING)` so logs are clear.

**Warning signs:** Admin uploads a Phase-73-produced ZIP and gets rejected — investigate ZIP-tool roundtrip if it occurs.

### Pitfall 6: Staging File Leaks on Reject

**What goes wrong:** Reject path throws `BackupArchiveException`, controller returns 400 with Flash, but staging file is never deleted. Disk fills with rejected ZIPs.

**Why it happens:** Without explicit `try/finally`, exceptions bypass cleanup.

**How to avoid:** Service-level `try { validate... } catch (BackupArchiveException e) { deleteStaging(uuid); throw; }`. Logged-but-not-rethrown delete failure (per D-16). The startup-sweep `BackupStagingCleanup` is the safety net.

**Warning signs:** Listing `data/dev/backup-staging/` after running schema-mismatch test shows leftover files.

### Pitfall 7: `MultipartFile.getInputStream()` Stream Consumption

**What goes wrong:** Code calls `file.getInputStream()` for the magic sniff, then tries to save the file via `file.transferTo(path)` — but the stream was already consumed.

**Why it happens:** Misconception. `transferTo` operates on the buffered multipart content, NOT the same `InputStream` instance. So this is actually safe — each `getInputStream()` returns a fresh stream over the buffered body.

**How to avoid:** Trust the contract: `MultipartFile` supports multiple `getInputStream()` calls and `transferTo`. The Phase 74 sniff-then-save pattern is correct.

**Warning signs:** Pre-existing CTC code already uses both methods in sequence (CSV import). Verify symptom doesn't appear; otherwise no action needed.

### Pitfall 8: `Files.copy(in, target)` for Staging Save

**What goes wrong:** Naive code uses `Files.copy(file.getInputStream(), stagingPath)` without `StandardCopyOption.REPLACE_EXISTING`. On UUID collision (extraordinarily unlikely but possible), the call throws `FileAlreadyExistsException`.

**Why it happens:** Default `Files.copy` semantics.

**How to avoid:** Use `file.transferTo(stagingPath)` (idiomatic in CTC — `FileStorageService.store():43`) — it handles the platform-specific atomic move/copy. Or `Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING)`. UUID collision is negligible but defensive coding is cheap.

**Warning signs:** None expected; mention to planner for completeness.

## Code Examples

### Common Operation 1: Save Multipart to Staging + Magic-Sniff

```java
// Source: synthesized from FileStorageService.store():33-47 + Pattern 5 above.
// [VERIFIED: src/main/java/org/ctc/domain/service/FileStorageService.java]
@Transactional(readOnly = true)
public BackupImportPreview stage(MultipartFile file) throws IOException {
    sniffZipMagic(file);                                // Pattern 5
    Path stagingDir = ensureStagingDir();
    UUID uuid = UUID.randomUUID();
    Path stagingFile = stagingDir.resolve("upload-" + uuid + ".zip");
    file.transferTo(stagingFile);                       // Atomic; multipart-aware
    log.info("Staged backup upload: uuid={}, size={} bytes, original={}",
            uuid, file.getSize(), file.getOriginalFilename());
    try {
        return buildPreview(uuid, stagingFile, file.getOriginalFilename(), file.getSize());
    } catch (BackupArchiveException | IOException e) {
        // D-16 — delete staging synchronously on reject before the exception escapes.
        deleteStagingFileQuietly(stagingFile);
        throw e;
    }
}

private Path ensureStagingDir() throws IOException {
    Path stagingDir = Paths.get(resolvedStagingDirRaw).toAbsolutePath().normalize();
    Files.createDirectories(stagingDir);
    return stagingDir;
}
```

### Common Operation 2: Schema-Version Gate

```java
// Source: spec from D-09 + D-02#2 + Phase 72 BackupSchema.SCHEMA_VERSION.
// [VERIFIED: src/main/java/org/ctc/backup/schema/BackupSchema.java:33]
private BackupImportPreview buildPreview(UUID uuid, Path stagingFile,
                                          String originalFilename, long fileSize)
        throws IOException {
    BackupManifest manifest = backupArchiveService.readManifest(stagingFile);
    int currentVersion = BackupSchema.SCHEMA_VERSION;
    int backupVersion = manifest.schemaVersion();
    if (backupVersion != currentVersion) {
        throw new BackupArchiveException(Reason.SCHEMA_MISMATCH,
                String.format("Schema version mismatch: backup=%d, expected=%d. Cannot import.",
                        backupVersion, currentVersion));
    }
    // Counts:
    Map<String, Long> currentRows = countCurrentRowsPerTable();         // 24 × repo.count()
    Map<String, Long> importedRows = manifest.tableCounts();            // authoritative
    int uploadFileCount = backupArchiveService.countUploadFiles(stagingFile);
    List<EntityRowCount> cards = backupSchema.getExportOrder().stream()
            .map(ref -> new EntityRowCount(
                    ref.tableName(),
                    humanLabel(ref.tableName()),
                    currentRows.getOrDefault(ref.tableName(), 0L),
                    importedRows.getOrDefault(ref.tableName(), 0L)))
            .toList();
    long totalImported = importedRows.values().stream().mapToLong(Long::longValue).sum();
    return new BackupImportPreview(uuid, originalFilename, fileSize,
            backupVersion, currentVersion, true,
            cards, uploadFileCount, totalImported);
}

private static String humanLabel(String tableName) {
    // season_phases -> Season Phases
    return Arrays.stream(tableName.split("_"))
            .map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
            .collect(Collectors.joining(" "));
}
```

### Common Operation 3: Programmatic Malicious-Fixture Factory

```java
// Source: D-25 — fixtures generated in @BeforeAll, not committed binaries.
// [CITED: java.util.zip.ZipOutputStream javadoc for setSize/CRC fields]
public final class MaliciousZipFactory {

    public static byte[] zipSlip(String maliciousEntryName, byte[] payload) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // (a) Manifest first so readers reach the data step.
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"schema_version\":1,\"app_version\":\"x\",\"export_date\":\"2026-01-01T00:00:00Z\",\"table_counts\":{}}".getBytes());
            zos.closeEntry();
            // (b) Path-traversal entry.
            zos.putNextEntry(new ZipEntry(maliciousEntryName));   // e.g. "../../etc/passwd"
            zos.write(payload);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] zipBomb_oversizedHeaderClaim() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"schema_version\":1,\"app_version\":\"x\",\"export_date\":\"2026-01-01T00:00:00Z\",\"table_counts\":{}}".getBytes());
            zos.closeEntry();
            // Use STORED method + explicit size to fool size-trust readers.
            ZipEntry entry = new ZipEntry("data/seasons.json");
            entry.setMethod(ZipEntry.STORED);
            byte[] big = new byte[60 * 1024 * 1024];           // 60 MB > 50 MB limit
            entry.setSize(big.length);
            entry.setCompressedSize(big.length);
            CRC32 crc = new CRC32(); crc.update(big);
            entry.setCrc(crc.getValue());
            zos.putNextEntry(entry);
            zos.write(big);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] zipBomb_inflatedDeflate() throws IOException {
        // 100 KB of repeating zeros deflates to ~100 bytes — easy to make 50 MB+ on read.
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"schema_version\":1,\"app_version\":\"x\",\"export_date\":\"2026-01-01T00:00:00Z\",\"table_counts\":{}}".getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("data/seasons.json"));
            byte[] zeros = new byte[51 * 1024 * 1024];  // 51 MB of zeros → tiny deflated, huge inflated
            zos.write(zeros);                            // deflates extremely well
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    public static byte[] entryCountOverflow() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"schema_version\":1,\"app_version\":\"x\",\"export_date\":\"2026-01-01T00:00:00Z\",\"table_counts\":{}}".getBytes());
            zos.closeEntry();
            for (int i = 0; i < 50_001; i++) {
                zos.putNextEntry(new ZipEntry("data/junk-" + i + ".json"));
                zos.write("[]".getBytes());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
```

### Common Operation 4: Confirm-Page Template Skeleton

```html
<!-- Source: D-07 + Pattern 7. Mirrors admin/backup.html shape. -->
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Backup — Confirm Import', ~{::section})}">
<body>
<section>
    <h1>Confirm Backup Import</h1>
    <div class="alert alert-warning mb-md">
        <strong>Warning:</strong> This action will delete ALL operational data
        and replace it with the contents of the uploaded backup. This action
        cannot be undone.
    </div>
    <form th:action="@{/admin/backup/import-execute}" method="post"
          th:object="${confirmForm}"
          onsubmit="return confirm('Replace all operational data? This cannot be undone.');">
        <input type="hidden" th:field="*{stagingId}">
        <div class="form-group mb-md">
            <label>
                <input type="checkbox" th:field="*{acknowledged}" value="true">
                I am an admin and I understand all operational data will be deleted.
            </label>
            <small th:if="${#fields.hasErrors('acknowledged')}"
                   th:errors="*{acknowledged}" class="text-error"></small>
        </div>
        <div class="actions">
            <a th:href="@{/admin/backup}" class="btn btn-secondary">Cancel</a>
            <button type="submit" class="btn btn-danger">Execute Import</button>
        </div>
    </form>
</section>
</body>
</html>
```

### Common Operation 5: Card-Grid Preview Template Skeleton

```html
<!-- Source: D-03 + verified admin.css:1692 card-grid class. -->
<section>
    <h1>Backup Import Preview</h1>

    <!-- Header block (D-05) -->
    <div class="card mb-md">
        <p><strong>File:</strong> <span th:text="${preview.originalFilename}"></span></p>
        <p><strong>Size:</strong> <span th:text="${#numbers.formatDecimal(preview.fileSizeBytes / 1024.0 / 1024.0, 1, 2)} + ' MB'"></span></p>
        <p><strong>Uploads:</strong> <span th:text="${preview.uploadFileCount}"></span> files</p>
        <p><strong>Total imported rows:</strong> <span th:text="${preview.totalImportedRows}"></span></p>
    </div>

    <!-- Schema match pill (D-04) — note: mismatches never reach this template -->
    <div class="alert alert-success mb-md" th:if="${preview.schemaMatches}">
        Schema version <span th:text="${preview.schemaVersion}"></span> matches.
    </div>

    <!-- 24-card grid (D-03) -->
    <div class="card-grid mb-md">
        <div class="card card--compact" th:each="card : ${preview.entityCounts}">
            <h3 th:text="${card.humanLabel}"></h3>
            <p>
                <span th:text="${card.currentRows}"></span>
                →
                <span th:text="${card.importedRows}"></span>
            </p>
            <!-- Delta pill: red if loss, green if gain, gray if both zero -->
            <span th:classappend="${card.importedRows < card.currentRows} ? 'alert-error' :
                                  (${card.currentRows == 0 and card.importedRows == 0} ? '' : 'alert-success')"
                  th:text="${(card.importedRows - card.currentRows)}"
                  class="badge"></span>
        </div>
    </div>

    <!-- CTAs (D-06) -->
    <form th:action="@{/admin/backup/import-confirm}" method="post" class="inline-form">
        <input type="hidden" name="stagingId" th:value="${preview.stagingId}">
        <div class="actions">
            <form th:action="@{/admin/backup/import-cancel}" method="post" style="display:inline">
                <input type="hidden" name="stagingId" th:value="${preview.stagingId}">
                <button type="submit" class="btn btn-secondary">Cancel</button>
            </form>
            <button type="submit" class="btn btn-primary">Proceed to Confirm</button>
        </div>
    </form>
</section>
```

**Note:** The skeleton above shows the structure; planner refines per CLAUDE.md "no inline styles on buttons" (the `style="display:inline"` on the Cancel form is illustrative — replace with a CSS class). The `delta pill` color scheme should use existing `.alert-success` / `.alert-error` color tokens or a new `.delta-loss` / `.delta-gain` class — planner's call (within Discretion §10 of CONTEXT).

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Apache Commons FileUpload | Spring MVC `MultipartFile` | Spring Boot 2.x+ | Modern auto-config, no separate dep |
| `@SessionAttributes` for multi-step forms | Stateless re-parse with hidden form fields | v1.8 CTC D-15 (2026-04-25) | Survives server restarts, simpler reasoning |
| `BoundedInputStream` from Commons IO | JDK 25 + small custom wrapper | This phase (v1.10) | Zero new dependencies; same defense |
| `ApplicationListener<ApplicationReadyEvent>` interface | `@EventListener(ApplicationReadyEvent.class)` annotation | Spring 4+ | Less boilerplate; same semantics |
| `Jackson2ObjectMapperBuilder.json().build()` (cloning default) | Explicit `new ObjectMapper()` for `@Qualifier("backupObjectMapper")` | Phase 72 D-11 (2026-05-11) | Isolated from default mapper — no admin REST drift |

**Deprecated/outdated:**
- **`spring.servlet.multipart.max-file-size` without `server.tomcat.max-http-form-post-size`** — Tomcat's limit silently overrides Spring's. Both must be raised. [CITED: D-13 + Spring Boot 4.0.6 reference.]
- **Trusting `MultipartFile.getInputStream()` to be `markSupported()`** — depends on the multipart resolver (`StandardServletMultipartResolver` buffers to temp file; mark/reset unreliable). Pattern 5's "read twice, fresh stream each call" is the safe idiom.

## Project Constraints (from CLAUDE.md)

| Constraint | Phase 74 Compliance |
|------------|---------------------|
| Communication: German | Acknowledged (Plan files and discussion in German). |
| Code/UI/Docs: English | Locked by D-01/D-02. All new file content English. |
| ≥82% JaCoCo line coverage | Phase 74 adds ~6 services/DTOs + 2 templates; tests in D-24 cover all paths. Coverage delta tracked by CI. |
| Do NOT modify V1 (or any existing) Flyway migration | Phase 74 has ZERO migrations. Confirmed. |
| Auth only for prod/docker | `BackupController` new endpoints follow existing pattern. `BackupControllerImportSecurityIT` mirrors Phase 73's `@Nested prod+dev` matrix. |
| OSIV remains enabled — only `@EntityGraph` optimizations | `BackupImportService` is `@Transactional(readOnly=true)`; `repo.count()` calls do not need eager fetches. |
| Backward compatibility on URLs | New endpoints under `/admin/backup/import-*` — no existing URL touched. |
| Playwright stays compile-scope | Mirrors `BackupExportE2ETest`; no scope change. |
| Thin controllers — services own business logic | `BackupController` delegates ALL logic to `BackupImportService`. |
| DTOs not entities in POST | `BackupImportConfirmForm` (Lombok) for `@AssertTrue` checkbox; preview DTO is a record. No entity in any POST. |
| No inline styles on buttons | Card grid + confirm page reuse `admin.css` classes (`.btn-primary`, `.btn-secondary`, `.btn-danger`, `.card-grid`, `.card`, `.alert-success`, `.alert-error`, `.alert-warning`). Planner verifies via grep. |
| Test data isolation | Tests use `MockMultipartFile` and programmatic ZIP factories — no shared fixture pollution. |
| Flash attributes `successMessage` / `errorMessage` | All Flash redirects use these exact keys (already wired in `admin/layout.html:82-83`). |
| Given-When-Then test naming | All D-24 tests use `givenContext_whenAction_thenExpectedResult()`. |
| `@RequiredArgsConstructor` + `@Slf4j` | All new services use these. |
| `feedback_ui_language.md` (English UI) | D-01/D-02 locked. |
| `feedback_no_inline_styles.md` | Templates reuse `admin.css` classes only — planner greps. |
| `feedback_grep_all_usages.md` | Planner greps for `MaxUploadSizeExceededException` (none), `ApplicationReadyEvent` (none), `card-grid` CSS class (verified at L1692). |
| `feedback_e2e_verification.md` | `BackupImportE2ETest` runs in `-Pe2e`. |
| `feedback_test_data_isolation.md` | Programmatic ZIP fixtures isolated per test method. |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 | Build + runtime | ✓ | 25 (Eclipse Temurin) | — |
| Spring Boot 4.0.6 | Multipart, MVC, `@ControllerAdvice` | ✓ | 4.0.6 | — |
| Jakarta Validation 3.x | `@NotNull`, `@AssertTrue` | ✓ | (managed) | — |
| Jackson 2.21.x | `JsonParser`, `ObjectMapper` | ✓ | 2.21.x (transitive via flyway-core) | — |
| `java.util.zip` (JDK) | ZIP reads | ✓ | JDK 25 | — |
| Playwright 1.59.0 | E2E test | ✓ | 1.59.0 | — |
| H2 in-memory | dev/test profile | ✓ | (managed) | — |
| MariaDB | local/docker/prod | ✓ | (separate container) | — |
| `./mvnw` Maven wrapper | Build | ✓ | (in repo) | — |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None — no new external dependencies introduced.

## Validation Architecture

Phase 74 success-criteria mapped to observable test assertions. **All success criteria are programmatically verifiable** — no human-only items.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + Mockito + AssertJ + Playwright 1.59.0 |
| Config file | `pom.xml` (Surefire L184-194; Failsafe L256-278) — no new framework config |
| Quick run command | `./mvnw test -Dtest='BackupImport*Test'` (Surefire pattern; ~10 s) |
| Full suite command | `./mvnw verify -Pe2e` |
| Phase-74 focused IT run | `./mvnw failsafe:integration-test failsafe:verify -Dit.test='BackupImport*IT,BackupStagingCleanupIT,BackupArchiveReadIT,BackupControllerImportSecurityIT'` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| IMPORT-01 | Multipart upload stages to disk, builds preview | integration | `./mvnw failsafe:integration-test -Dit.test=BackupImportServiceIT` | Wave 0 |
| IMPORT-02 | Schema-version mismatch → 400 + Flash + DB unchanged | integration | `./mvnw failsafe:integration-test -Dit.test=BackupImportSchemaVersionMismatchIT` | Wave 0 |
| IMPORT-03 | Stateless flow — preview→confirm→execute re-reads from disk | integration | `./mvnw failsafe:integration-test -Dit.test=BackupImportServiceIT#givenStagedFile_whenReparseByUuid_thenSameCounts` | Wave 0 |
| IMPORT-04 | Confirm checkbox enforced server-side | unit + integration | `./mvnw test -Dtest=BackupImportConfirmFormTest` + IT | Wave 0 |
| SECU-01 | ZIP-Slip rejected | integration | `./mvnw failsafe:integration-test -Dit.test=BackupImportZipSlipIT` | Wave 0 |
| SECU-02 | ZipBomb (per-entry, total, count) rejected | integration | `./mvnw failsafe:integration-test -Dit.test=BackupImportZipBombIT` | Wave 0 |
| SECU-03 | Multipart limits raised in application.yml | property assert | `./mvnw failsafe:integration-test -Dit.test=BackupImportMultipartLimitIT` + `MultipartConfigPropertyIT` | Wave 0 |
| SECU-04 | `MaxUploadSizeExceededException` → Flash + redirect | integration | `./mvnw failsafe:integration-test -Dit.test=BackupImportMultipartLimitIT#given101MbUpload_whenPostImportPreview_thenRedirectWithFlash` | Wave 0 |
| (D-17) | Startup-sweep clears staging | integration | `./mvnw failsafe:integration-test -Dit.test=BackupStagingCleanupIT` | Wave 0 |
| (D-22 security) | Anonymous on prod rejected, dev permitted | integration | `./mvnw failsafe:integration-test -Dit.test=BackupControllerImportSecurityIT` | Wave 0 |
| (end-to-end UX) | Browser click-through upload→preview→confirm→stub | e2e | `./mvnw verify -Pe2e -Dit.test=BackupImportE2ETest -Dtest=void` | Wave 0 |

### Roadmap Success Criteria → Observable Assertion → Test Class

| SC# | Truth | Observable Assertion | Test Class |
|-----|-------|----------------------|------------|
| SC#1 | Admin uploads ZIP, lands on preview screen with per-table current vs imported rows + uploads count + schema match indicator | (a) `mockMvc.perform(multipart("/admin/backup/import-preview").file(zipBytes)).andExpect(view().name("admin/backup-preview"))`; (b) `andExpect(model().attribute("preview", allOf(hasProperty("entityCounts", hasSize(24)), hasProperty("schemaMatches", is(true)))))`; (c) `andExpect(model().attribute("preview", hasProperty("uploadFileCount", greaterThanOrEqualTo(0))))` | `BackupImportServiceIT` (uses Phase 73 fresh export ZIP as fixture — see Pattern below) |
| SC#2 | Schema-version mismatch ZIP → HTTP 400 + Flash + DB byte-identically unchanged | Row-count snapshot **before** + **after**: (a) `long[] before = countAllTables();` → upload forged-manifest ZIP → assert `redirectedUrl("/admin/backup") + flash("errorMessage", containsString("Schema version mismatch"))`; (b) `assertThat(countAllTables()).isEqualTo(before)` (across all 24 entities + `data_import_audit`); (c) `assertThat(stagingDir).matches(empty())` after reject | `BackupImportSchemaVersionMismatchIT` |
| SC#3 | Path-traversal (`../../etc/passwd`) / absolute path / per-entry >50 MB rejected | `assertThatThrownBy(() -> service.stage(maliciousFile)).isInstanceOf(BackupArchiveException.class).hasFieldOrPropertyWithValue("reason", PATH_TRAVERSAL)` (and ENTRY_TOO_LARGE / TOTAL_TOO_LARGE / TOO_MANY_ENTRIES variants); `assertThat(stagingFile).doesNotExist()` per D-16 | `BackupImportZipSlipIT` + `BackupImportZipBombIT` |
| SC#4 | Upload > 100 MB triggers `MaxUploadSizeExceededException` and renders Flash | `mockMvc.perform(multipart(...).file("file", 105MB-blob)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/backup")).andExpect(flash().attribute("errorMessage", "Upload too large — maximum is 100 MB."))`; the test uses `MockMvc` `.file()` and the Tomcat resolver path | `BackupImportMultipartLimitIT` |
| SC#5 | Preview state STATELESS — re-parse on execute via staging-path; no `@SessionAttributes`, mirroring v1.8 D-15 | (a) `mockMvc.perform(post("/admin/backup/import-execute").param("stagingId", uuid).param("acknowledged", "true"))` succeeds even when invoked from a fresh session cookie; (b) `BackupImportServiceIT#givenStagedFile_whenReparseByUuid_thenSameCounts` — calls `stage` then `reparse(uuid)` on a new service instance and asserts identical `entityCounts`; (c) grep `@SessionAttributes` over `BackupController.java` returns empty (regression gate); (d) staging file still on disk after the stub-execute redirect (Phase 75 inherits it, per D-08) | `BackupImportServiceIT` + `BackupControllerImportStatelessIT` |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest='BackupImport*Test'` (Surefire only; ~10 s)
- **Per wave merge:** `./mvnw verify -DskipE2E` (Surefire + Failsafe; ~3 min on CTC's existing baseline)
- **Phase gate:** `./mvnw verify -Pe2e` (FULL suite incl. Playwright; ~5 min) — must be green before `/gsd-verify-work`

### Wave 0 Gaps

All test infrastructure already exists from Phase 73 — Phase 74 only adds new test files. No framework install, no new conftest equivalent.

- [ ] `src/test/java/org/ctc/backup/service/BackupImportServiceIT.java` — covers SC#1 + parts of SC#3, SC#5
- [ ] `src/test/java/org/ctc/backup/service/BackupImportSchemaVersionMismatchIT.java` — covers SC#2
- [ ] `src/test/java/org/ctc/backup/service/BackupImportZipSlipIT.java` — covers SC#3 (path-traversal subset)
- [ ] `src/test/java/org/ctc/backup/service/BackupImportZipBombIT.java` — covers SC#3 (size/count subset)
- [ ] `src/test/java/org/ctc/backup/service/BackupStagingCleanupIT.java` — covers D-17
- [ ] `src/test/java/org/ctc/backup/service/BackupArchiveReadIT.java` — covers `readManifest` + `countDataEntries` + `countUploadFiles` shape
- [ ] `src/test/java/org/ctc/backup/BackupImportMultipartLimitIT.java` — covers SC#4
- [ ] `src/test/java/org/ctc/backup/BackupControllerImportSecurityIT.java` — `@Nested prod+dev` matrix mirror of Phase 73
- [ ] `src/test/java/org/ctc/backup/BackupControllerImportStatelessIT.java` — covers SC#5 grep regression
- [ ] `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` — full UI click-through under `-Pe2e`
- [ ] `src/test/java/org/ctc/backup/dto/BackupImportConfirmFormTest.java` — Surefire unit, `@AssertTrue` + `@NotNull` matrix
- [ ] `src/test/java/org/ctc/backup/service/MaliciousZipFactory.java` — shared helper, no `@Test` methods (per D-25)

**Test fixture pattern (re-usable from Phase 73):**

```java
// Helper to generate a real Phase-73-shaped export ZIP for happy-path tests.
// [VERIFIED: BackupRoundTripIT pattern, BackupArchiveServiceIT pattern]
@Autowired private BackupArchiveService archiveService;

private byte[] producePhase73ExportZip() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    archiveService.writeZip(out, Instant.now());
    return out.toByteArray();
}

// Then in the test:
MockMultipartFile mp = new MockMultipartFile("file", "ctc-backup-test.zip",
    "application/zip", producePhase73ExportZip());
mockMvc.perform(multipart("/admin/backup/import-preview").file(mp)).andExpect(...);
```

This pattern reuses Phase 73's already-verified export to produce a valid backup ZIP at test time. No committed fixture binary needed; no test-data drift between phases. (D-25-compatible: malicious fixtures use `MaliciousZipFactory`, happy-path uses the producer above.)

## Security Domain

> Required because `security_enforcement` is enabled (default).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|------------------|
| V2 Authentication | yes | Spring Security 7 `anyRequest().authenticated()` on prod/docker (inherited from Phase 73 wiring); profile-conditional permitAll on dev/local. |
| V3 Session Management | no (mostly) | Phase 74 deliberately stateless — D-18. Spring CSRF cookie + Flash cookie persist across the multi-step flow, but no server-side session attributes are added. |
| V4 Access Control | yes | Admin-only routes; `BackupControllerImportSecurityIT` proves anonymous-on-prod → 401. |
| V5 Input Validation | yes | `BackupImportConfirmForm` (`@AssertTrue` + `@NotNull`); `MultipartFile` magic-byte sniff; manifest parsed with `FAIL_ON_UNKNOWN_PROPERTIES=true`. |
| V6 Cryptography | no | Phase 74 does not handle secrets, signatures, or encryption. (`manifest.sha256` is deferred to v1.11 per `IMPORT-FUT-02`.) |
| V11 Logging & Monitoring | yes | All reject paths log at WARN (path-traversal, size limit, schema mismatch); successful staging logs at INFO; SLF4J `@Slf4j` parameterized `{}` format throughout. |
| V12 File-System Operations | yes | This is the **core** of Phase 74. ZIP-Slip + ZipBomb + per-entry-size + total-byte + entry-count caps + staging-file cleanup all live here. |
| V14 Configuration | yes | Multipart limits in `application.yml` only; profile-aware staging path. |

### Known Threat Patterns for {Spring Boot 4.0.6 + Tomcat + Jakarta multipart}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| ZIP-Slip (path-traversal in ZIP entries) | Tampering | `resolve(...).normalize().startsWith(root)` (mirror SECU-02 from `FileStorageService:153-158`) |
| ZipBomb (deflate ratio attack) | DoS | `LimitedInputStream(MAX_ENTRY_BYTES=50MB)` wrapping the inflated stream — never trust `ZipEntry.getSize()` |
| ZIP entry-count overflow (memory exhaustion via many small entries) | DoS | Counter incremented per `getNextEntry()`, capped at `MAX_ENTRIES=50_000` |
| Multipart bomb (101+ MB upload) | DoS | Tomcat `max-http-form-post-size=104857600` + Spring `max-request-size=100MB` + `MaxUploadSizeExceededException` handler |
| Mass assignment via form binding | Tampering | `BackupImportConfirmForm` (form DTO), NEVER bind an entity. Per CLAUDE.md "DTOs in controllers". |
| CSRF (cross-site request forgery) | Tampering | Spring Security 7 default CSRF token; profile-conditional (prod/docker enforced, dev disabled) — identical to Phase 73 |
| Forged manifest (extra fields, bypass flags) | Tampering | `backupObjectMapper` has `FAIL_ON_UNKNOWN_PROPERTIES=true` (Phase 72 D-11) — extra fields fail manifest parse → reject path |
| Renamed non-ZIP upload (`.zip` extension on a `.txt`) | Tampering | Magic-byte sniff (`50 4B 03 04`) before any further parsing (Pattern 5) |
| Schema-version drift (catastrophic data loss) | Tampering / Repudiation | Schema-version gate BEFORE any DB write; re-validated at execute time (D-09 defense-in-depth) |
| Concurrent confirm-form submission with stale UUID | Tampering | Phase 75 owns the import lock (SECU-05); Phase 74 stateless re-parse means a stale UUID merely 404s |
| Logged sensitive data | Information Disclosure | Filenames are admin-provided and OK to log; no PII (PSN IDs are public per OUT_OF_SCOPE table); manifest contents not dumped to log |
| Staging-file collision | Tampering | UUID-named files; UUID collision negligible; `file.transferTo` is atomic |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Boot 4.0.6 `MultipartFile.getInputStream()` returns a fresh stream on every call (StandardServletMultipartResolver buffers to disk) | Pattern 5 | Magic-sniff path consumes the stream; subsequent `transferTo` fails. **Mitigation:** add a `BackupImportServiceIT` test that calls magic-sniff followed by save and asserts the stored file size matches `file.getSize()`. |
| A2 | `MaxUploadSizeExceededException` is thrown DURING request resolution, BEFORE any controller method runs | Pattern 6 + Pitfall 1 | If thrown later, the `@ControllerAdvice` placement still works (Spring dispatches the exception either way), but the controller-method-level `try/catch` could mask it. **Mitigation:** explicit `BackupImportMultipartLimitIT` verifies the redirect flow. |
| A3 | Jackson 2.21.x `JsonParser.skipChildren()` runs in O(1) memory for an array of objects | Pattern 4 | False would mean `countDataEntries` materializes the row data internally. **Mitigation:** spot-check via `Runtime.getRuntime().freeMemory()` snapshot in a test against a synthetic 100 MB JSON. |
| A4 | The `card-grid` CSS class at `admin.css:1692-1696` with `minmax(220px, 1fr)` is visually appropriate for 24 entity cards | Pattern 5 (template) + Pattern Card Grid | If too narrow/wide, planner adds `backup-preview-grid` override. **Mitigation:** Discretion item 2 — planner verifies visually via playwright-cli per CLAUDE.md `feedback_playwright_cli.md`. |
| A5 | `${spring.profiles.active:dev}` interpolation when profiles=`dev,demo` produces literal `dev,demo` (with comma) | Pattern 9 + Pitfall 2 | Comma in path is broken on Windows. **Mitigation:** recommend profile-specific YAML override OR Java-side `@PostConstruct` resolver — planner picks one. This is the **highest-risk assumption** in this research. |
| A6 | Empty `BackupSchema.getExportOrder()` list at startup is impossible because Phase 72 IT `BackupSchemaTopologyIT` asserts 24 entities | §1.5 | If empty, preview renders zero cards — degraded UX but not a security issue. **Mitigation:** `BackupImportServiceIT` asserts `entityCounts.size() == 24`. |
| A7 | A successful `@ExceptionHandler` method returning `String "redirect:/..."` triggers a 302 with Flash via Spring's `RedirectView` even when the exception was thrown in multipart resolution (pre-controller) | Pattern 6 | If Spring's exception-resolver chain doesn't honor `RedirectAttributes` for pre-controller exceptions, Flash is dropped — admin sees a redirect with no message. **Mitigation:** explicit `MockMvc` test in `BackupImportMultipartLimitIT` asserting `flash().attribute("errorMessage", ...)`. |

## Open Questions

1. **Multi-profile staging path interpolation (A5).**
   - What we know: D-15 locks `data/${spring.profiles.active}/backup-staging` as default; `dev,demo` profile activation is documented in CLAUDE.md.
   - What's unclear: D-15 does not anticipate the comma-join issue.
   - Recommendation: planner picks ONE — (a) Java-side resolver in `BackupImportService.@PostConstruct` that takes the first active profile, or (b) override `app.backup.staging-dir` explicitly in `application-dev.yml`. Option (a) preserves D-15's "single source" intent; option (b) is simpler but violates "no per-profile override."

2. **`BackupArchiveException` reason-code enum placement.**
   - What we know: D-20 says hardening checks live in `BackupArchiveService`; the reason code is needed to drive the Flash-message branching.
   - What's unclear: Should the enum live inside the exception class, in a sibling `BackupArchiveReason` class, or be replaced with distinct exception subclasses (`SchemaMismatchException extends BackupArchiveException`, etc.)?
   - Recommendation: single enum inside the exception class — lightweight, sufficient for Phase 74's 6-7 reason codes; subclass-per-reason is over-engineered for this scope.

3. **`humanLabel` mapping source (Discretion item 1).**
   - What we know: D-21 includes `humanLabel` as a record field; mapping is planner's discretion.
   - What's unclear: static `Map<String,String>` (24 entries) vs algorithmic (`split("_")` → titlecase).
   - Recommendation: algorithmic for v1.10 (handles 22 of 24 cases cleanly: `season_phases` → "Season Phases", `match_scorings` → "Match Scorings"); special-cases for `psn_aliases` ("PSN Aliases") and `data_import_audit` (excluded from cards anyway per IMPORT-08) via a tiny override map.

4. **Defense-in-depth: re-count from data files OR trust manifest only?**
   - What we know: Manifest is authoritative per Phase 73 verification. `countDataEntries` is "defense-in-depth" only.
   - What's unclear: Phase 74 spec says implement both `readManifest` AND `countDataEntries` (D-20). Should the preview card render manifest counts or re-counted values? The two should match.
   - Recommendation: render manifest counts on the card (authoritative + cheap); call `countDataEntries` only as a validation step (assert manifest matches actual at upload time; log discrepancy at WARN). This makes the "imported rows" column trustworthy AND catches manifest drift.

5. **Spring Boot 4.x multipart resolver: `StandardServletMultipartResolver` vs deprecated `CommonsMultipartResolver`?**
   - What we know: Tomcat embedded; SB auto-configures `StandardServletMultipartResolver` since 2.x.
   - What's unclear: nothing concrete — assumed standard behavior.
   - Recommendation: no action; verify via existing Phase 73 multipart-free path doesn't break.

## Sources

### Primary (HIGH confidence)

- [VERIFIED] `src/main/java/org/ctc/backup/service/BackupArchiveService.java:1-166` — Phase 73 export-side reference for read methods, `@Transactional(readOnly=true)`, `backupObjectMapper` injection.
- [VERIFIED] `src/main/java/org/ctc/backup/BackupController.java:1-110` — controller shape, ISO filename, `@RequestMapping("/admin/backup")`.
- [VERIFIED] `src/main/java/org/ctc/backup/schema/BackupSchema.java:33` — `SCHEMA_VERSION=1`.
- [VERIFIED] `src/main/java/org/ctc/backup/schema/BackupManifest.java:36-41` — JsonProperty snake_case keys.
- [VERIFIED] `src/main/java/org/ctc/backup/schema/EntityRef.java:24-37` — snake_case → kebab-case filename derivation (inverse used for slug→tableName).
- [VERIFIED] `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java:1-78` — existing exception handler returns ModelAndView; Phase 74 adds NEW @ControllerAdvice for redirect.
- [VERIFIED] `src/main/java/org/ctc/domain/service/FileStorageService.java:33-47, 153-166` — SECU-02 path-traversal idiom Phase 74 mirrors.
- [VERIFIED] `src/main/java/org/ctc/dataimport/CsvImportController.java:38-211` — v1.8 D-15 stateless multi-step pattern.
- [VERIFIED] `src/main/resources/templates/admin/backup.html:1-21` — Phase 73 landing; Phase 74 extends with Import form.
- [VERIFIED] `src/main/resources/templates/admin/import-preview.html` — reference shape (table, not grid).
- [VERIFIED] `src/main/resources/templates/admin/layout.html:75-76` — sidebar Backup link unchanged.
- [VERIFIED] `src/main/resources/static/admin/css/admin.css:165-171, 211-249, 1692-1696` — `card`, `btn-*`, `card-grid` classes.
- [VERIFIED] `src/main/resources/application.yml:5-11` — current multipart limits at 10 MB.
- [VERIFIED] `pom.xml:1-120` — SB 4.0.6, Java 25, Jackson 2.21.x, Playwright 1.59.0.
- [VERIFIED] `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` — D-01..D-25 authoritative.
- [VERIFIED] `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-VERIFICATION.md` — manifest-first invariant, ZIP-Slip-on-export pattern at line 121-132.
- [VERIFIED] `.planning/phases/72-backup-wire-contract-schema-manifest-objectmapper-audit-log-/72-CONTEXT.md` — D-11 (FAIL_ON_UNKNOWN_PROPERTIES), D-15 (canonical wire contract docs).
- [VERIFIED] `CLAUDE.md` — language, constraints, conventions, sub-agent rules.
- [VERIFIED] `.planning/codebase/CONVENTIONS.md` — Lombok, flash keys, controller patterns.
- [VERIFIED] `.planning/codebase/TESTING.md` — Surefire/Failsafe split, Given-When-Then, MockMultipartFile usage.

### Secondary (MEDIUM confidence)

- [CITED] Spring Framework reference — `@ControllerAdvice` per-method return-type dispatch.
- [CITED] Spring Framework reference — `MultipartFile.getInputStream()` contract.
- [CITED] Spring Boot reference §1.4 — `ApplicationReadyEvent` listener semantics.
- [CITED] Jakarta Bean Validation 3.0 §6.1.1 — null values pass `@AssertTrue`; need `@NotNull`.
- [CITED] OWASP "Zip Slip Vulnerability" + Snyk Zip Slip catalog — `Paths.resolve(...).normalize().startsWith(root)` pattern.
- [CITED] OWASP "Zip Bomb" + OWASP File Upload cheat sheet — inflated-byte counter required; size header untrustworthy.
- [CITED] Jackson 2.21.x JsonParser javadoc — `skipChildren()` O(1) memory streaming.

### Tertiary (LOW confidence)

- [ASSUMED] Multi-profile `${spring.profiles.active}` literal comma-join behavior — verified empirically via training knowledge; tested in Pattern 9 mitigation. **Highest-risk assumption.**
- [ASSUMED] `MaxUploadSizeExceededException` Flash works when thrown pre-controller (A7) — strongly indicated by Spring docs, but Phase 74 IT explicitly verifies.

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all libraries verified in `pom.xml`; zero new dependencies.
- Architecture: HIGH — every pattern has a concrete CTC precedent (Phase 72/73 + v1.8 CsvImportController) or a stock Spring/JDK idiom.
- Pitfalls: HIGH — 8 enumerated pitfalls, all traceable to either spec text or prior CTC bugs.
- Security: HIGH — V12 file-system controls are well-understood (ZIP-Slip + ZipBomb canonical OWASP patterns).
- One MEDIUM-risk gap: multi-profile staging-path interpolation (A5 / OQ #1).

**Research date:** 2026-05-12

**Valid until:** 2026-06-11 (30 days — stack and decisions are stable; only Open Question #1 may force a tweak).

## RESEARCH COMPLETE
