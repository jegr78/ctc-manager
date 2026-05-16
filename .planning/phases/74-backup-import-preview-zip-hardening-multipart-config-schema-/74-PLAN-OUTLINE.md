---
phase: 74
slug: backup-import-preview-zip-hardening-multipart-config-schema
mode: outline
generated: 2026-05-12
plan_count: 10
wave_count: 3
requirements_total: 8
requirements_covered: [IMPORT-01, IMPORT-02, IMPORT-03, IMPORT-04, SECU-01, SECU-02, SECU-03, SECU-04]
---

# Phase 74 — Plan Outline

> Backup Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate.
> Write-free import surface: stage → preview → confirm → stub-execute. Phase 75 inherits the staging file and adds the wipe + restore transaction.

## Plan Table

| Plan ID | Objective | Wave | Depends On | Requirements |
|---------|-----------|------|------------|--------------|
| 01 | Multipart + Tomcat limits (100 MB) + `app.backup.staging-dir` property in `application.yml` (single source per D-13); profile-aware default `data/${spring.profiles.active}/backup-staging` per D-15 | 1 | — | SECU-03 |
| 02 | ZIP hardening primitives — `BackupImportLimits` constants (50 MB / 500 MB / 50 000 entries per D-12), `LimitedInputStream` filter for inflated-byte counting, `PathTraversalGuard` static helper extracting `FileStorageService:153-158` predicate per D-11, `BackupArchiveException` + `Reason` enum | 1 | — | SECU-01, SECU-02 |
| 03 | DTO records — `BackupImportPreview` + `EntityRowCount` records (D-21) + `BackupImportConfirmForm` Lombok form DTO with `@NotNull @AssertTrue Boolean acknowledged` (D-10, D-21) | 1 | — | IMPORT-03, IMPORT-04 |
| 04 | `BackupArchiveService` reader extension — `readManifest` (manifest-first contract assertion + `backupObjectMapper` strict parse), `countDataEntries` (streaming `JsonParser` ARRAY-token loop), `countUploadFiles`, all wired through `LimitedInputStream` + `PathTraversalGuard` per D-20; covers `BackupArchiveServiceReadIT` | 2 | 02 | IMPORT-01, SECU-01, SECU-02 |
| 05 | `BackupImportService` — `stage(MultipartFile)`, `reparse(UUID)`, `deleteStagingFile(UUID)` (D-19); ZIP magic-byte sniff (`50 4B 03 04`); staging-file lifecycle with `try/finally` reject-delete per D-16; schema-version gate before any DB read (D-09); covers `BackupImportServiceIT` + `BackupImportSchemaVersionMismatchIT` + `BackupImportZipSlipIT` + `BackupImportZipBombIT` | 2 | 02, 03, 04 | IMPORT-01, IMPORT-02, SECU-01, SECU-02 |
| 06 | `BackupUploadExceptionHandler` — new sibling `@ControllerAdvice` (separate class, NOT mixed into `GlobalExceptionHandler` per D-14 + RESEARCH risk #2) mapping `MaxUploadSizeExceededException` → `RedirectAttributes` flash redirect to `/admin/backup` with locked D-02#1 string; covers `BackupImportMultipartLimitIT` | 2 | 01 | SECU-04 |
| 07 | `BackupStagingCleanup` — `@Component` `@EventListener(ApplicationReadyEvent.class)` startup-sweep over staging dir, deleting every `upload-*.zip` per D-17; covers `BackupStagingCleanupIT` | 2 | 01 | (operational; supports IMPORT-01) |
| 08 | `BackupController` extension — 4 new endpoints (`import-preview`, `import-confirm`, `import-execute` stub per D-08, `import-cancel`) per D-22, CSRF-protected, profile-conditional auth identical to Phase 73; covers `BackupImportControllerSecurityIT` + `BackupImportConfirmFormValidationIT` | 3 | 03, 05, 06 | IMPORT-01, IMPORT-02, IMPORT-04 |
| 09 | Templates — extend `admin/backup.html` with second `.card` Import form (D-23), new `admin/backup-preview.html` (header block + schema pill + 24-card grid + Proceed/Cancel CTAs per UI-SPEC), new `admin/backup-confirm.html` (warning callout + recap + `@AssertTrue` checkbox + `.btn-danger` Execute Import per D-10 + UI-SPEC); locked English D-02 copy verbatim; zero new CSS | 3 | 08 | IMPORT-03, IMPORT-04 |
| 10 | `BackupImportE2ETest` Playwright walkthrough — upload Phase-73 export → preview cards visible → click Proceed → tick acknowledgment checkbox → submit → land on `/admin/backup` with stub D-02#5 Flash; proves SC#1 (preview rendered) + SC#5 (stateless re-parse, no session) | 3 | 08, 09 | IMPORT-01, IMPORT-02, IMPORT-03, IMPORT-04 |

## Wave Structure

| Wave | Plans | Parallelism Rationale |
|------|-------|------------------------|
| 1 | 01, 02, 03 | All foundation — config / primitives / DTOs. Zero file-modification overlap; independently testable. |
| 2 | 04, 05, 06, 07 | Service-layer build. 04→05 chains via constructor injection; 06 and 07 only depend on Wave 1 (config), run parallel to 04/05. No template/controller touches. |
| 3 | 08, 09, 10 | Controller + UI + E2E. 08 wires endpoints onto the Wave-2 services; 09 authors the templates 08's view-name returns refer to; 10 exercises the full chain. 09 depends on 08 (controller view-names must exist first); 10 depends on both. |

## File Ownership (parallelism safety)

| Plan | Owns (no other plan touches in same wave) |
|------|--------------------------------------------|
| 01 | `src/main/resources/application.yml` |
| 02 | `org/ctc/backup/security/PathTraversalGuard.java`, `org/ctc/backup/service/BackupImportLimits.java`, `org/ctc/backup/io/LimitedInputStream.java`, `org/ctc/backup/exception/BackupArchiveException.java` |
| 03 | `org/ctc/backup/dto/BackupImportPreview.java`, `org/ctc/backup/dto/EntityRowCount.java`, `org/ctc/backup/dto/BackupImportConfirmForm.java` |
| 04 | `org/ctc/backup/service/BackupArchiveService.java` (extend with 3 reader methods — Phase 73 writer methods untouched) |
| 05 | `org/ctc/backup/service/BackupImportService.java` |
| 06 | `org/ctc/backup/exception/BackupUploadExceptionHandler.java` |
| 07 | `org/ctc/backup/service/BackupStagingCleanup.java` |
| 08 | `org/ctc/backup/BackupController.java` (extend — Phase 73 export endpoint untouched) |
| 09 | `src/main/resources/templates/admin/backup.html` (extend), `src/main/resources/templates/admin/backup-preview.html` (new), `src/main/resources/templates/admin/backup-confirm.html` (new) |
| 10 | `src/test/java/org/ctc/e2e/BackupImportE2ETest.java` |

## Requirements Coverage Audit

| Requirement | Plan(s) | Source |
|-------------|---------|--------|
| IMPORT-01 (import-preview endpoint + staging + manifest read) | 04, 05, 08, 10 | REQUIREMENTS.md L41 |
| IMPORT-02 (schema-version gate before any DB write) | 05, 08, 10 | REQUIREMENTS.md L42 |
| IMPORT-03 (preview UX — per-table counts, stateless) | 03, 09, 10 | REQUIREMENTS.md L43 |
| IMPORT-04 (confirm dialog + `@AssertTrue` acknowledgement) | 03, 08, 09, 10 | REQUIREMENTS.md L44 |
| SECU-01 (ZIP-Slip defense + `PathTraversalGuard` reuse) | 02, 04, 05 | REQUIREMENTS.md L52 |
| SECU-02 (ZipBomb limits — entry/total/count) | 02, 04, 05 | REQUIREMENTS.md L53 |
| SECU-03 (multipart limits in `application.yml`) | 01 | REQUIREMENTS.md L54 |
| SECU-04 (`MaxUploadSizeExceededException` → Flash mapping) | 06, 08 | REQUIREMENTS.md L55 |

**Audit:** every Phase-74 requirement ID appears in at least one plan. No gaps. No deferred items appear in the plan set (D-deferred items live in Phase 75/76/77 per ROADMAP).

## Notes for Plan Authors

- **Multipart limits live in `application.yml` ONLY (D-13).** Plan 01 owns this file; no other plan touches it. Phase-conditional overrides explicitly out of scope.
- **`@ControllerAdvice` separation (D-14 / RESEARCH risk #2).** Plan 06 spawns a NEW `BackupUploadExceptionHandler` class. Plan 06 does NOT modify `GlobalExceptionHandler` (mixed `ModelAndView` + `String "redirect:..."` return types in one advice cause Spring binding ambiguity per RESEARCH).
- **Stateless preview (D-18 / IMPORT-03).** No `@SessionAttributes`, no in-memory cache. Plan 05's `reparse(UUID)` is the single source of truth for execute-time re-validation; Plan 08's `import-execute` re-reads staging file via UUID. Plan 10 verifies via cookie-jar reset.
- **English UI copy is authoritative (D-01 / D-02).** Plan 09 copies the 5 locked D-02 strings verbatim. REQUIREMENTS.md German quotes are spec examples, not locked wording.
- **Zero Flyway migrations in Phase 74.** `data_import_audit` already shipped in Phase 72 V7. Phase 74 does NOT write to it — Phase 75 owns the audit-row write.
- **Fixtures programmatically generated (D-25).** Plans 04/05/10 produce malicious ZIPs via `ZipOutputStream` in `@BeforeAll`. No binary blobs under `src/test/resources/backup-fixtures/malicious/`.
- **Phase 74 ships zero new CSS** (UI-SPEC §"New CSS — none"). Plan 09 reuses `admin.css` classes only; planner verifies via grep before writing any new selector.

## OUTLINE COMPLETE
