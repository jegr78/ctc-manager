# Phase 74: Backup Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate - Context

**Gathered:** 2026-05-12
**Status:** Ready for planning

<domain>
## Phase Boundary

A **write-free** import path that accepts a multipart ZIP upload, stages it under `data/${spring.profiles.active}/backup-staging/upload-{uuid}.zip` (override `app.backup.staging-dir`), reads `manifest.json` first, refuses ZIPs whose `schema_version != BackupSchema.SCHEMA_VERSION` before any DB write, hardens ZIP processing against ZIP-Slip and ZipBomb (per-entry 50 MB, total 500 MB, ≤50.000 entries), raises multipart limits to 100 MB on both Spring and Tomcat layers, maps `MaxUploadSizeExceededException` to a Flash-message-rendering admin error, and renders a stateless per-entity Preview screen plus a Confirm dialog with a mandatory checkbox. The Confirm-final-submit endpoint exists as a **stub** in Phase 74 — it re-parses the staged ZIP, re-validates, and Flashes a "Phase 75 will enable execution" message; no DB row, no file system mutation outside the staging dir.

**Out of scope** (Phase 75+): the actual wipe + restore transaction; `JdbcTemplate.batchUpdate` audit-listener bypass; `data_import_audit.success=true` rows; upload-tree restore + stage-and-rename; `BackupImportRollbackIT`; Live MariaDB UAT; the import lock + read-only banner (Phase 76); auto-export-before-import (Phase 76).

</domain>

<decisions>
## Implementation Decisions

### UI Language (resolves CLAUDE.md vs REQUIREMENTS.md conflict)

- **D-01:** **All UI text is English.** The German strings quoted verbatim in REQUIREMENTS.md SECU-04 / IMPORT-02 / IMPORT-04 are treated as **spec examples of the meaning**, not as locked wording. CLAUDE.md `feedback_ui_language.md` ("alle UI-Texte konsequent Englisch, keine Ausnahmen") wins. REQUIREMENTS.md is not rewritten — this override is documented here for traceability.
- **D-02:** **Final English strings (locked, terse style):**
  1. Multipart size exceeded (`MaxUploadSizeExceededException` → Flash via `GlobalExceptionHandler`):
     `Upload too large — maximum is 100 MB.`
  2. Schema-version mismatch (HTTP 400 + Flash, before any DB write):
     `Schema version mismatch: backup={actual}, expected={current}. Cannot import.`
  3. ZIP-Slip / per-entry-size / total-size / entry-count reject (Flash):
     `Backup archive failed safety checks (size or path) and was rejected.`
  4. Confirm-dialog mandatory checkbox label (IMPORT-04):
     `I am an admin and I understand all operational data will be deleted.`
  5. Stub execute redirect Flash (Phase 74 only, removed in Phase 75):
     `Validation succeeded. Import execution will be enabled in Phase 75.`

### Preview Page UX (IMPORT-03 layout)

- **D-03:** **Compact card grid, one card per entity.** Page renders a responsive grid of 24 cards (one per `EntityRef` in `BackupSchema.getExportOrder()`), each showing: entity name (kebab-case → human-readable label via planner-supplied helper), `current rows → imported rows` with a delta pill. Delta pill color: red when `imported < current` (potential data loss), green when `imported ≥ current`, neutral gray when both are zero. No diff-per-row, no estimated duration — counts only.
- **D-04:** **Schema-version-match banner at the top.** Above the card grid: a green "Schema version 1 matches" pill. (Mismatches never reach the preview page — they get HTTP 400 + Flash redirect to `/admin/backup` before the page renders, per D-09.)
- **D-05:** **Header block above the schema pill.** Renders: ZIP filename, ZIP size (KB/MB), uploads-file count, total imported-rows-across-all-entities sum. Helps the admin orient before scanning the 24 cards.
- **D-06:** **CTAs on preview page.** Two buttons: secondary `Cancel` (links to `/admin/backup`, also triggers staging-file cleanup via `?cleanup={uuid}` query — see D-15), and primary `Proceed to Confirm` (POSTs to `/admin/backup/import-confirm` with the staging UUID, lands on the confirm page).
- **D-07:** **Dedicated template `admin/backup-preview.html`** — not inline into `admin/backup.html`. Keeps the upload-landing-page lean and the preview-page focused. Confirm-page is also separate: `admin/backup-confirm.html`. Both follow the established `admin/layout.html` fragment pattern with `Backup` sidebar active.

### Execute-Endpoint Stub (Phase 74↔75 seam)

- **D-08:** **`POST /admin/backup/import-execute` exists in Phase 74 as a validation-stub.** Flow: re-reads the staged ZIP from disk (stateless, no `@SessionAttributes` — uses the staging UUID submitted by the confirm form), re-runs the full validation chain (`startsWith(uploadDir.toRealPath())` ZIP-Slip check, per-entry/total/count limits, schema-version re-check), then redirects to `/admin/backup` with Flash `Validation succeeded. Import execution will be enabled in Phase 75.` The staging file is **not deleted** by the stub — Phase 75 inherits it and deletes after successful execute.
- **D-09:** **Schema-version mismatch is rejected at preview-upload time, AND re-checked at execute time.** Defense-in-depth: even if a stale staging file somehow survives a schema-version bump between upload and execute, the execute stub re-validates and refuses. Both rejection paths use the same Flash string (D-02#2).
- **D-10:** **Confirm-checkbox enforcement is server-side.** JS-`confirm` dialog is a UX-extra (per IMPORT-04 "JS-Confirm-Dialog ZUSÄTZLICH zur Server-Seite"), but the authoritative check is the form-bound `@NotNull @AssertTrue Boolean acknowledged` field on a `BackupImportConfirmForm` DTO. Missing/false → re-render confirm page with validation error.

### ZIP Hardening (SECU-01, SECU-02) and Multipart Limits (SECU-03, SECU-04)

- **D-11:** **Reuse `FileStorageService` SECU-02 path-traversal defense pattern, do NOT duplicate.** The new `BackupArchiveService.readEntries(...)` (or equivalent) validates every `ZipEntry.getName()` by `Paths.get(stagingDir).resolve(entryName).normalize().startsWith(stagingDir.toRealPath())`. Absolute paths are rejected. The check is extracted into a small package-private helper so `FileStorageService.validate()` and the new ZIP-reader share semantics (planner decides where it lives — `org.ctc.backup.security.PathTraversalGuard` or kept inline with a comment cross-referencing `FileStorageService:65`).
- **D-12:** **ZipBomb limits are constants on `BackupImportLimits` or similar.** `MAX_ENTRY_BYTES = 50L * 1024 * 1024`, `MAX_TOTAL_BYTES = 500L * 1024 * 1024`, `MAX_ENTRIES = 50_000`. Enforced **as the ZIP is streamed**: per-entry `ZipEntry.getSize()` check first (cheap, fails the obviously oversized), then a running byte counter on the inflated stream (defends against deflate bombs where header size is small but inflated size is huge). Entry-count enforced by a simple counter incremented on each `getNextEntry()`. Violation: throw `BackupArchiveException` with reason code; handler maps to D-02#3 Flash.
- **D-13:** **Multipart limits live in `application.yml` only (single source).** Adds:
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
  No per-profile override — backup limits are admin-side and identical across dev/local/docker/prod. Existing 10 MB Spring limit is replaced (no other multipart use case in the app needs more than 100 MB).
- **D-14:** **`GlobalExceptionHandler` gets a new `@ExceptionHandler(MaxUploadSizeExceededException.class)` mapping.** Returns `RedirectAttributes`-backed flash to `/admin/backup` (sees the user back on the backup landing) with D-02#1 string. Cannot use the existing `ModelAndView`-error-view pattern because Flash redirect is the IMPORT-04 contract; planner extends the handler with a redirect-aware variant for this single case.

### Staging-File Lifecycle (state management)

- **D-15:** **Profile-aware staging directory.** Default: `data/${spring.profiles.active}/backup-staging` (mirrors the existing `app.upload-dir: data/dev/uploads` profile convention). Override: `app.backup.staging-dir` property in `application.yml` (no default, falls back to the profile-aware path). Directory is created via `Files.createDirectories(stagingDir)` on first use; safe to re-run.
- **D-16:** **Reject paths delete the staging file synchronously.** Schema-version mismatch, ZIP-Slip, per-entry-size, total-size, entry-count, and `BackupArchiveException` of any kind: the staging file is deleted in a `try/finally` block in the service before the Flash is set. Failed delete is logged at WARN but does not block the rejection (the cleanup sweep below catches leaks).
- **D-17:** **Startup-sweep clears the entire staging directory.** An `ApplicationReadyEvent` listener (`org.ctc.backup.service.BackupStagingCleanup` — Spring-discovered `@Component`) walks the staging directory and deletes every `upload-*.zip` file at app startup. `info`-logs `Cleared {N} stale staging files`. No `@Scheduled` job (this is admin-only feature with 1-2 uploads/week — startup sweep + reject-delete is enough; if leaks ever become a problem, a scheduled sweep is a one-line v1.11 add).
- **D-18:** **No `@SessionAttributes`, no in-memory cache.** Preview page form-renders the staging UUID into a hidden input; confirm form re-submits the UUID; execute stub re-reads from disk by UUID. Pure stateless. Direct mirror of v1.8 D-15 (`CsvImportController` staging-path pattern).

### Service / Controller Architecture

- **D-19:** **Single `BackupImportService` (`org.ctc.backup.service`) in Phase 74, extended in Phase 75.** Phase 74 public surface:
  - `BackupImportPreview stage(MultipartFile file)` — saves staging file, parses manifest, runs all hardening checks, returns preview DTO. Throws typed exceptions for the four reject paths.
  - `BackupImportPreview reparse(UUID stagingId)` — used by execute stub (D-08) to re-run validation against the on-disk staging file. Same exceptions.
  - `void deleteStagingFile(UUID stagingId)` — used by Cancel (D-06) and reject-paths (D-16).
  - Phase 75 will add `void execute(UUID stagingId)` (the actual wipe + restore transaction).
- **D-20:** **`BackupArchiveService` (existing) gains a read counterpart to the existing `writeZip`.** New methods (planner names them):
  - `BackupManifest readManifest(Path zipPath)` — opens ZIP, reads first entry, asserts name = `manifest.json`, deserializes via `backupObjectMapper` (qualifier from Phase 72). Throws on missing/misnamed first entry.
  - `Map<String, Long> countDataEntries(Path zipPath)` — parses every `data/<entity>.json`, returns row counts per `tableName`. Streaming JsonParser (Jackson `JsonParser` ARRAY-token loop), no full-document buffering.
  - `int countUploadFiles(Path zipPath)` — counts entries under `uploads/`.
  - Hardening checks (D-11, D-12) are applied in every ZIP-read method via a shared internal helper.
- **D-21:** **Preview DTO is a record `BackupImportPreview`** in `org.ctc.backup.dto`:
  ```
  record BackupImportPreview(
      UUID stagingId,
      String originalFilename,
      long fileSizeBytes,
      int schemaVersion,
      int currentSchemaVersion,
      boolean schemaMatches,
      List<EntityRowCount> entityCounts,
      int uploadFileCount,
      long totalImportedRows
  ) {}
  record EntityRowCount(String tableName, String humanLabel, long currentRows, long importedRows) {}
  ```
  Templates bind to this DTO; no entity lookups in Thymeleaf.
- **D-22:** **`BackupController` is extended, not duplicated.** New endpoints: `POST /admin/backup/import-preview` (multipart), `POST /admin/backup/import-confirm` (preview → confirm page transition), `POST /admin/backup/import-execute` (stub, D-08), `POST /admin/backup/import-cancel` (delete staging + redirect). All CSRF-protected; profile-conditional auth identical to existing export endpoint.
- **D-23:** **Upload form lives on the existing `admin/backup.html`** (the Phase 73 landing). Below the Export Backup button: a second `<form enctype="multipart/form-data">` with `<input type="file" accept=".zip">` and an `Import Backup` primary button. Keeps the entry point single (`/admin/backup`).

### Test Plan Anchors (planner expands)

- **D-24:** **At minimum these tests must exist** (planner can split/merge):
  - `BackupImportServiceIT` — happy path: stage a Phase 73-exported ZIP, get preview with non-zero counts.
  - `BackupImportSchemaVersionMismatchIT` — forged manifest with `schema_version=999`, asserts HTTP 400 + Flash + DB unchanged (row count snapshot before/after).
  - `BackupImportZipSlipIT` — fixture ZIP with `../../etc/passwd`, asserts reject + staging file deleted.
  - `BackupImportZipBombIT` — fixture with one entry whose inflated size exceeds 50 MB, asserts reject. Plus a fixture exceeding total-byte and entry-count limits.
  - `BackupImportMultipartLimitIT` — `MockMultipartFile` of 101 MB asserts `MaxUploadSizeExceededException` → Flash.
  - `BackupImportE2ETest` (Playwright, `-Pe2e`) — full UI click-through: upload Phase 73 export → preview cards visible → click Proceed → confirm checkbox → submit → land on `/admin/backup` with Phase-74-stub Flash.
  - `BackupStagingCleanupIT` — write 3 stale `upload-*.zip` files, fire `ApplicationReadyEvent`, assert all deleted + 1 info-log line.
- **D-25:** **Malicious fixtures live in `src/test/resources/backup-fixtures/malicious/`.** Programmatically generated in test `@BeforeAll` (`ZipOutputStream` + `ZipEntry` with `setSize(Long.MAX_VALUE)` for the bomb case) — not committed as binary blobs. Keeps repo lean and makes the fixture self-documenting in test code.

### Claude's Discretion

- Exact human-label mapping in `EntityRowCount.humanLabel` ("season_phases" → "Season Phases"). A planner-supplied helper or a static map.
- Card grid CSS — reuse `admin.css` classes (`card`, `grid-3`, etc.); no inline styles. Planner verifies via grep.
- Whether the Schema-version-mismatch path can ever render the preview page in a "blocked" state (per D-09 it cannot — rejection happens server-side before redirect). Planner may revisit if they find a UX reason.
- `BackupArchiveException` reason-code enum design (planner decides; not user-facing).
- Whether `PathTraversalGuard` is its own class or stays inline with a comment (D-11 trade-off).
- `RedirectAttributes` flash mechanics — `success-message` vs `error-message` keys — should follow the existing CTC convention (`successMessage` / `errorMessage` per CLAUDE.md "Flash attributes").

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### v1.10 milestone foundation
- `.planning/ROADMAP.md` §"Phase 74" — Goal, depends-on, success criteria (5 SCs), requirements list (IMPORT-01..04, SECU-01..04)
- `.planning/REQUIREMENTS.md` §IMPORT-01..04 + §SECU-01..04 — Acceptance criteria. **NOTE:** German UI strings in SECU-04 / IMPORT-02 / IMPORT-04 are overridden by D-01/D-02 of this CONTEXT — final English strings are in D-02. REQUIREMENTS.md is not rewritten; this CONTEXT is the language authority.
- `.planning/PROJECT.md` §"Backup Wire Contract (v1.10)" — Invariants 1-4: integer SCHEMA_VERSION, manifest-first ZIP layout, 24-entity scope, ObjectMapper isolation. Phase 74 reads the same `BackupSchema` + `BackupManifest` + `backupObjectMapper` qualifier.

### Prior-phase context (mandatory carry-forward)
- `.planning/phases/72-backup-wire-contract-schema-manifest-objectmapper-audit-log-/72-CONTEXT.md` — `BackupSchema.SCHEMA_VERSION = 1` (D-01 GAP-2), `EXPORT_ORDER` topo-sort (D-04), `backupObjectMapper` strict mapper with `FAIL_ON_UNKNOWN_PROPERTIES=true` (D-11). Phase 74 reads only — does not modify.
- `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-CONTEXT.md` — `BackupArchiveService` shape, `BackupExportService` `@Transactional(readOnly=true)` pattern, controller package layout (`org.ctc.backup` outside `org.ctc.admin.controller`).
- `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-VERIFICATION.md` — Final write-order discipline confirmed (`manifest.json` is entry 0); export-side `enumerateReferencedUploads()` + ZIP-Slip pattern in `BackupArchiveService.java:121-132` is the reference Phase 74 mirrors on the read side.

### Existing code Phase 74 references but does NOT modify
- `src/main/java/org/ctc/domain/service/FileStorageService.java:65` — `validate()` SECU-02 path-traversal defense (`startsWith(uploadDir.toRealPath())`). Phase 74 reuses the same pattern in the ZIP reader per D-11; planner extracts a shared helper or inline-comments the cross-reference.
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Phase 74 adds ONE new `@ExceptionHandler(MaxUploadSizeExceededException.class)` mapping with redirect-flash variant (D-14). Existing handlers untouched.
- `src/main/java/org/ctc/dataimport/CsvImportController.java` — v1.8 D-15 staging-path-pattern reference (`/admin/import/preview` → staging-file → `/admin/import/execute` re-parse). Phase 74 mirrors the same stateless approach for `/admin/backup/import-preview` → `/admin/backup/import-execute`.
- `src/main/resources/templates/admin/import-preview.html` — Reference for Thymeleaf preview-page shape (NOT layout copy — Phase 74 uses card grid per D-03, not the CSV table).
- `src/main/resources/templates/admin/backup.html` — Phase 73 landing page; Phase 74 extends it with a second `<form>` per D-23.
- `src/main/resources/templates/admin/layout.html:75-76` — Sidebar Data group with Backup link; Phase 74 does not modify (the Backup link already covers both export and import).
- `src/main/java/org/ctc/backup/service/BackupArchiveService.java` — Phase 74 adds read-counterpart methods per D-20; the existing `writeZip` is untouched.
- `src/main/java/org/ctc/backup/BackupController.java` — Phase 74 extends with import endpoints per D-22; existing export endpoint untouched.
- `src/main/resources/application.yml` — Phase 74 raises multipart limits per D-13; existing `app.upload-dir`, `spring.servlet.multipart.max-file-size: 10MB`, `max-request-size: 10MB` replaced with 100 MB values + Tomcat keys.

### Project conventions (mandatory reading)
- `CLAUDE.md` §"Architectural Principles" — Controllers thin, DTOs in controllers (`BackupImportConfirmForm`), no fallback calculations, no inline styles on buttons.
- `CLAUDE.md` §"Constraints" — Coverage ≥ 82 %, OSIV active (preview reads use existing pattern), no new Flyway changes (Phase 74 has zero migrations).
- `CLAUDE.md` `feedback_ui_language.md` — All UI text English (the authority that resolved the REQUIREMENTS-quoted-German-strings conflict per D-01).
- `CLAUDE.md` `feedback_no_inline_styles.md` — Card grid uses `admin.css` classes only; planner verifies.
- `.planning/codebase/TESTING.md` — Surefire (Unit) vs Failsafe (IT) split; `@SpringBootTest` profile for Failsafe; Playwright E2E under `-Pe2e`.
- `.planning/codebase/CONVENTIONS.md` — `@RequiredArgsConstructor` + `@Slf4j` on services; `successMessage` / `errorMessage` flash keys.

### External APIs (consulted, not on-disk)
- Spring Boot 4.0.6 `MaxUploadSizeExceededException` — thrown by Tomcat connector when post-size exceeds limit; mapped via `@ExceptionHandler` per D-14.
- Java `java.util.zip.ZipInputStream` / `ZipEntry` — primary API for hardened reading. `ZipEntry.getSize()` reads the central-directory size (can be spoofed by malicious ZIPs — defense-in-depth requires inflated-byte counting per D-12).
- Jackson `JsonParser` streaming API — for `countDataEntries` (D-20) to count array elements without buffering the full document.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`BackupSchema` (Phase 72)** — `getExportOrder()` is the iteration source for the 24 preview cards. Same instance is `@Autowired` into `BackupImportService`.
- **`BackupManifest` (Phase 72)** — record with `schemaVersion`, `appVersion`, `exportDate`, `tableCounts`. Deserialized by `backupObjectMapper`. Phase 74's preview reads `schemaVersion` for the gate and `tableCounts` for the per-card "imported rows" values **without** re-parsing every `data/<entity>.json` (manifest is authoritative — verified by Phase 73's `BackupRoundTripIT`). Re-parsing `data/<entity>.json` for counts is a defense-in-depth fallback; planner decides if it's worth the cost.
- **`backupObjectMapper` qualifier (Phase 72 D-11)** — `FAIL_ON_UNKNOWN_PROPERTIES=true` is exactly what Phase 74 needs for strict manifest reading. Malformed/extra fields → exception → reject path.
- **`BackupArchiveService.writeZip` (Phase 73)** — its inverse is the new `readManifest` / `countDataEntries` / `countUploadFiles` per D-20. Both writer and reader share the same `@Qualifier("backupObjectMapper")` injection and same `org.ctc.backup.service` package.
- **`FileStorageService.validate` (v1.1 SECU-02)** — `Paths.get(uploadDir).resolve(filename).normalize().startsWith(...)` is the proven path-traversal defense; Phase 74's `BackupArchiveService` reader reuses the exact same idiom per D-11.
- **`BackupController` (Phase 73)** — `@Controller @RequestMapping("/admin/backup")` already exists; Phase 74 adds 4 new endpoints (D-22) under the same prefix.
- **`admin/backup.html` (Phase 73)** — landing page; Phase 74 adds an Import-Backup form below the existing Export-Backup form (D-23).
- **`GlobalExceptionHandler`** — `Environment`-injected, profile-aware error rendering. Phase 74 adds one `MaxUploadSizeExceededException` mapping (D-14) that uses `RedirectAttributes` instead of the existing `ModelAndView` pattern.
- **v1.8 `CsvImportController` staging-path pattern** — proven stateless approach: file lands in staging dir, UUID in form, execute re-reads. Phase 74 mirrors the architecture.

### Established Patterns
- **`@RequiredArgsConstructor` + `final` fields** — `BackupImportService` follows; no setter injection.
- **`@Slf4j`** — all services log via parameterized `log.info("{}", ...)`.
- **DTOs in controllers (CLAUDE.md)** — `BackupImportConfirmForm` is the form-bound DTO with `@AssertTrue Boolean acknowledged`. The preview-DTO `BackupImportPreview` is a record per D-21.
- **Flash attributes `successMessage` / `errorMessage`** — Phase 74 reject paths use `errorMessage`; the stub success uses `successMessage`.
- **`@Transactional(readOnly=true)` on read-side services** — `BackupImportService.stage` and `reparse` are `readOnly=true` (counts use `repo.count()` which is a SELECT). No write-side methods in Phase 74.
- **Given-When-Then test naming** — all D-24 tests follow `givenContext_whenAction_thenExpectedResult()`.
- **`@Nested` profile classes for Security ITs** — Phase 73's `BackupControllerSecurityIT` pattern is mirrored for the new import endpoints (anonymous + CSRF matrix on prod, anonymous-allowed on dev).

### Integration Points
- **New package contents (no new packages — extends `org.ctc.backup`):**
  - `org.ctc.backup.service.BackupImportService` (new)
  - `org.ctc.backup.service.BackupStagingCleanup` (new, `ApplicationReadyEvent` listener)
  - `org.ctc.backup.service.BackupImportLimits` (new, constants holder) or constants inline — planner decides
  - `org.ctc.backup.dto.BackupImportPreview` (new record)
  - `org.ctc.backup.dto.EntityRowCount` (new record)
  - `org.ctc.backup.dto.BackupImportConfirmForm` (new form DTO)
  - `org.ctc.backup.exception.BackupArchiveException` (new — or reuse `BusinessRuleException` if reason-codes via constants are enough)
- **Extended classes:**
  - `org.ctc.backup.service.BackupArchiveService` — adds `readManifest`, `countDataEntries`, `countUploadFiles`
  - `org.ctc.backup.BackupController` — adds 4 endpoints
  - `org.ctc.admin.controller.GlobalExceptionHandler` — adds `MaxUploadSizeExceededException` handler
  - `src/main/resources/application.yml` — raises multipart + Tomcat limits
  - `src/main/resources/templates/admin/backup.html` — adds import form below export form
- **New templates:**
  - `src/main/resources/templates/admin/backup-preview.html` (card grid)
  - `src/main/resources/templates/admin/backup-confirm.html` (confirm + checkbox)
- **CSS:** all card-grid styling via `admin.css` classes; planner checks if existing `card` / `grid-3` classes suffice or adds backup-specific classes (named `backup-preview-card`, `backup-preview-grid` to avoid collision).
- **Security:** profile-conditional auth identical to Phase 73 export. CSRF required on prod/docker, disabled on dev/local. `BackupControllerSecurityIT` extended with import-endpoint `@Nested` tests.
- **Profile-aware staging dir:** new property `app.backup.staging-dir` in `application.yml` with `data/${spring.profiles.active}/backup-staging` default. Profile YAMLs do not need overrides (interpolation handles it).

</code_context>

<specifics>
## Specific Ideas

- **Defense-in-depth at execute time (D-09).** Even though preview-time validation already caught everything, the Phase 74 execute stub re-runs the full validation chain. Phase 75 inherits this discipline — write-time re-check is the last line of defense against stale staging files surviving a `SCHEMA_VERSION` bump.
- **Card grid color semantics (D-03).** Red delta = data loss visible at a glance. The admin should see "imported < current" with maximum prominence — this is the most consequential signal on the entire page.
- **`backupObjectMapper`'s `FAIL_ON_UNKNOWN_PROPERTIES=true` is doing double duty.** Phase 72 chose it for export-side strictness, but it pays off here too: a tampered manifest with extra fields (e.g., injected `bypassSchemaCheck: true`) deserializes into an exception, not into a permissive `BackupManifest`. The reject path catches it generically as "manifest parse failed".
- **The `ZipEntry.getSize()` trust problem (D-12).** Malicious ZIPs can lie in the central directory — claim 1 KB but inflate to 5 GB. Defense is to count actual bytes read from `InflaterInputStream` against `MAX_ENTRY_BYTES`. Planner: a `LimitedInputStream` wrapper that throws after N bytes is the cleanest implementation.
- **`accept=".zip"` is a UX hint, not enforcement.** The server-side check is on `Content-Type: application/zip` AND the first 4 bytes of the file body (`50 4B 03 04` ZIP magic) — defense against renamed `.txt` uploads. Planner adds a header-byte sniff before passing to `BackupArchiveService.readManifest`.
- **Phase 74 has zero Flyway migrations.** `data_import_audit` table already exists from Phase 72 V7. Phase 74 does not write to it (Phase 75 does). Confirmed.

</specifics>

<deferred>
## Deferred Ideas

- **Per-Saison Import-Selectivity** — pick a subset of seasons/phases to import. v1.11+ (REQUIREMENTS `IMPORT-FUT-01`).
- **SHA-256 checksum verification (`manifest.sha256` + verify-only mode)** — v1.11+ (REQUIREMENTS `IMPORT-FUT-02`).
- **Admin staging-files browser page** — `/admin/backup/staging` with manual delete + forensic-keep on reject. Considered for Phase 74, deferred; current cleanup strategy (startup-sweep + reject-delete) is sufficient at 1-2 uploads/week.
- **Scheduled cleanup job (`@Scheduled(fixedDelayString="PT1H")`)** — considered for stale-file management; deferred in favor of startup-sweep + reject-delete (D-17). One-line v1.11 add if leaks ever appear.
- **i18n via `messages.properties`** — considered for the English/German UI-text conflict resolution; deferred because the app has no i18n infrastructure yet. Adding it for one feature would be overkill (D-01).
- **`BackupImportRollbackIT`** — injects exception mid-restore, asserts DB pre-import state. Phase 75 deliverable; Phase 74's rollback story is trivial (no DB writes at all).
- **Live MariaDB UAT (Saison 2023 fixture round-trip)** — Phase 75 QUAL-03 deliverable; Phase 74 ships only the import-preview half.
- **Import lock + read-only banner + auto-export-before-import** — Phase 76 (SECU-05..07).
- **Replace-All transaction, JPA-auditing bypass, `Team.parentTeam=NULL` pre-step, post-commit upload-tree restore** — Phase 75 (IMPORT-05..07).
- **README + WIKI documentation, final UAT** — Phase 77 (QUAL-01..05). CLAUDE.md `feedback_docs_update.md` triggers on milestone completion, not phase-by-phase.
- **Audit-log viewer UI** — `/admin/backup/history` would render `data_import_audit` rows. Not in v1.10 scope; could be a quick win in v1.11.

</deferred>

---

*Phase: 74-backup-import-preview-zip-hardening-multipart-config-schema-*
*Context gathered: 2026-05-12*
