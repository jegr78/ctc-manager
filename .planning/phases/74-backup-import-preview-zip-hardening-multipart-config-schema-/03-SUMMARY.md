---
phase: "74"
plan: "03"
subsystem: "backup-import"
tags: [dto, records, validation, jakarta-bean-validation]
dependency_graph:
  requires: []
  provides:
    - org.ctc.backup.dto.BackupImportPreview
    - org.ctc.backup.dto.EntityRowCount
    - org.ctc.backup.dto.BackupImportConfirmForm
  affects:
    - Plan 05 (BackupImportService — builds BackupImportPreview/EntityRowCount instances)
    - Plan 06 (BackupController — binds BackupImportConfirmForm via @Valid @ModelAttribute)
    - Plan 07 (Thymeleaf templates — renders BackupImportPreview fields)
    - Plan 08 (BackupImportConfirmFormValidationIT — full Spring-binding IT)
tech_stack:
  added: []
  patterns:
    - Java 25 records for immutable view-model DTOs
    - Lombok @Getter @Setter @NoArgsConstructor triplet for Spring-bindable form DTOs
    - Jakarta Bean Validation @NotNull + @AssertTrue on Boolean wrapper (null/false/true distinction)
key_files:
  created:
    - src/main/java/org/ctc/backup/dto/BackupImportPreview.java
    - src/main/java/org/ctc/backup/dto/EntityRowCount.java
    - src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java
    - src/test/java/org/ctc/backup/dto/BackupImportPreviewTest.java
    - src/test/java/org/ctc/backup/dto/EntityRowCountTest.java
    - src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java
  modified: []
decisions:
  - "BackupImportPreview.schemaMatches is a stored field (not derived from schemaVersion==currentSchemaVersion) — service computes once and passes in, keeping record as pure data carrier with clean equals/hashCode"
  - "BackupImportConfirmForm uses Boolean wrapper (not primitive boolean) so @NotNull fires for absent-checkbox vs @AssertTrue fires for false, per Jakarta §6.1.1 + RESEARCH §Pitfall 3"
  - "@AssertTrue message uses locked UI-SPEC inline string, not a bundle key — i18n deferred per CONTEXT Deferred Ideas"
  - "@Data not used on BackupImportConfirmForm — narrower @Getter @Setter @NoArgsConstructor triplet matches project form-DTO pattern (MatchdayForm, SeasonPhaseForm)"
metrics:
  duration: "~8 minutes"
  completed: "2026-05-12T15:53:57Z"
  tasks_completed: 2
  files_created: 6
---

# Phase 74 Plan 03: Import preview + confirm form DTOs Summary

DTO contracts for Phase 74 backup-import flow: two immutable records (`BackupImportPreview`, `EntityRowCount`) and one Spring-bindable Lombok form class (`BackupImportConfirmForm`) with Jakarta Bean Validation enforcing the null/false/true distinction on the mandatory confirm checkbox.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create BackupImportPreview + EntityRowCount records | 8f12239 | BackupImportPreview.java, EntityRowCount.java, BackupImportPreviewTest.java, EntityRowCountTest.java |
| 2 | Create BackupImportConfirmForm + Validator unit test | eb7b108 | BackupImportConfirmForm.java, BackupImportConfirmFormValidationTest.java |

## What Was Built

Three new types in `org.ctc.backup.dto`:

**`BackupImportPreview` (record)** — 9-field canonical constructor per CONTEXT D-21:
`UUID stagingId, String originalFilename, long fileSizeBytes, int schemaVersion, int currentSchemaVersion, boolean schemaMatches, List<EntityRowCount> entityCounts, int uploadFileCount, long totalImportedRows`. Pure data carrier; `schemaMatches` is stored (not derived). Thymeleaf consumer: `admin/backup-preview.html` (Plan 07).

**`EntityRowCount` (record)** — 4-field record per CONTEXT D-21: `String tableName, String humanLabel, long currentRows, long importedRows`. One card in the 24-card preview grid. `humanLabel` is precomputed by `BackupImportService.toHumanLabel(String)` (Plan 05) — no derivation logic on the record.

**`BackupImportConfirmForm` (Lombok class)** — `@Getter @Setter @NoArgsConstructor` per project pattern. `@NotNull UUID stagingId` + `@NotNull @AssertTrue(message="You must acknowledge the deletion warning to continue.") Boolean acknowledged`. Boolean wrapper (not primitive) preserves the null-vs-false distinction per Jakarta Bean Validation §6.1.1.

**Unit tests (10 methods across 3 classes, all green):**
- `BackupImportPreviewTest`: 3 methods (all-fields constructor, stored schemaMatches, record equality)
- `EntityRowCountTest`: 3 methods (all-fields constructor, zero counts, record equality)
- `BackupImportConfirmFormValidationTest`: 4 methods (null→@NotNull, false→@AssertTrue, true→no violations, null stagingId→@NotNull). Uses bare `jakarta.validation.Validator` — no Spring context boot.

## Deviations from Plan

None — plan executed exactly as written. All CONTEXT D-21 field signatures used verbatim. All acceptance criteria verified.

## Known Stubs

None. These are pure DTO types with no data-flow stubs.

## Threat Flags

None. These are plain DTO/record types with no network endpoints, auth paths, file access, or schema changes.

## Self-Check

### Files Exist
- [x] src/main/java/org/ctc/backup/dto/BackupImportPreview.java
- [x] src/main/java/org/ctc/backup/dto/EntityRowCount.java
- [x] src/main/java/org/ctc/backup/dto/BackupImportConfirmForm.java
- [x] src/test/java/org/ctc/backup/dto/BackupImportPreviewTest.java
- [x] src/test/java/org/ctc/backup/dto/EntityRowCountTest.java
- [x] src/test/java/org/ctc/backup/dto/BackupImportConfirmFormValidationTest.java

### Commits Exist
- [x] 8f12239 — feat(74-03): add BackupImportPreview + EntityRowCount records
- [x] eb7b108 — feat(74-03): add BackupImportConfirmForm Lombok form DTO + validation tests

### Tests Pass
- [x] `./mvnw -Dtest='BackupImportPreviewTest,EntityRowCountTest,BackupImportConfirmFormValidationTest' -DfailIfNoTests=true test` — 10 methods green

## Self-Check: PASSED
