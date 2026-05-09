---
gsd_state_version: 1.0
milestone: v1.10
milestone_name: Spring Boot Upgrade & Data Export/Import
status: planning
last_updated: "2026-05-09T19:08:25.587Z"
last_activity: 2026-05-09
progress:
  total_phases: 7
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-09)

**Core value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Current focus:** v1.10 milestone roadmap drafted (7 phases, 71-77). Awaiting Phase 71 planning via `/gsd-plan-phase 71`.

## Current Position

Phase: 71 — Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard
Plan: —
Status: Roadmap approved, planning Phase 71
Last activity: 2026-05-09 — v1.10 ROADMAP.md written, 37/37 requirements mapped to Phases 71-77

## Completed Milestones

- v1.0 Technical Debt Cleanup (5 phases, 12 plans) — shipped 2026-04-04
- v1.1 Codebase Concerns Cleanup (10 phases, 20 plans) — shipped 2026-04-07
- v1.2 Driver Merge (4 phases, 5 plans) — shipped 2026-04-07
- v1.3 English Test Data (8 phases) — shipped 2026-04-10
- v1.5 Code Review Fixes (9 phases, 14 plans) — shipped 2026-04-15
- v1.6 Static Site Quality (17 phases, 56 requirements) — shipped 2026-04-18
- v1.8 Bulk Driver Import from Google Sheets (2 phases, 4 plans, +52 tests) — shipped 2026-04-25
- v1.9 Season Phases & Groups (15 phases, ~70 plans, 38/38 requirements, +88.4k LOC) — shipped 2026-05-09

## Deferred Items

Items acknowledged and deferred at v1.9 milestone close on 2026-05-09:

| Category | Item | Status |
| -------- | ---- | ------ |
| debug | group-warnings-for-non-groups-seasons | diagnosed (hypothesis confirmed, see Resolution) |
| debug | shortname-resolver-picks-parent-without-phaseteam | diagnosed (hypothesis confirmed, see Resolution; superseded by Phase 70) |
| quick_task | 260404-jh8-fix-release-workflow-use-release-token-s | missing (predates v1.9) |
| uat | Phase 57 (57-HUMAN-UAT.md) | partial — 3 pending scenarios; MariaDB UAT covered transitively by Phase 61 UAT-03 + CI smoke gate (deferred) |
| uat | Phase 61 (61-HUMAN-UAT.md) | unknown (UAT-02 legacy season visual smoke deferred to next deploy) |
| uat | Phase 62 (62-HUMAN-UAT.md) | resolved |
| uat | Phase 66 (66-UAT.md) | diagnosed (superseded by Phase 70) |
| uat | Phase 70 (70-HUMAN-UAT.md) | diagnosed (UAT D-22 PASS — 287/357/0 errors on MariaDB Saison 2023) |

## Accumulated Context

### Decisions

All decisions logged in PROJECT.md Key Decisions table.

- [v1.10 start]: v1.10 milestone scope is platform hygiene (Spring Boot 4.0.6) + new admin Backup Export/Import feature. 37 requirements across 6 categories (PLAT × 7, SCHEMA × 4, EXPORT × 6, IMPORT × 8, SECU × 7, QUAL × 5).
- [v1.10 start]: All four research agents independently corrected the milestone wording — Spring Boot 4.0.6 ships **Thymeleaf 3.1.5.RELEASE** (CVE-2026-40478 SpEL canonicalization hardening), NOT Thymeleaf 3.2. Maintainer-recommended fix: controller-side `pageTitle` model attribute.
- [v1.10 roadmap]: Seven-phase structure (71-77) chosen. Cluster A (PLAT) is Phase 71 alone; Cluster B (SCHEMA + EXPORT + IMPORT + SECU + QUAL) breaks into Phases 72-77 along the natural wire-contract → export → import-preview → replace-all → ops-hardening → final-uat fault line. Rationale: PLAT first eliminates v1.9 platform debt before any feature code; Phase 72 (wire contract) before any export/import code; Phase 73 (export) before Phase 74 (import) so round-trip is the natural integration test; Phase 75 (replace-all) last among implementation phases so a Phase-75 failure clearly points at the riskiest path.
- [v1.10 roadmap]: GAP-1 (ZIP layout) resolved: per-entity files under `data/<entity>.json` + `manifest.json` first entry. Documented in Phase 72 success criteria.
- [v1.10 roadmap]: GAP-2 (schema version) resolved: integer constant `BackupSchema.SCHEMA_VERSION = 1`. Documented in Phase 72 success criteria.
- [v1.10 roadmap]: GAP-5 (canonical 22-entity FK ordering) resolved: generated from live entity classes in Phase 72, NOT hand-written.
- [v1.10 roadmap]: Audit-log table `data_import_audit` is permanently OUT of export scope (IMPORT-08). Decision baked into Phase 72 PROJECT.md decisions row.
- [v1.10 roadmap]: Replace-All implementation strategy locked to native SQL DELETE in FK-reverse order via `EntityManager.createNativeQuery()` + `JdbcTemplate.batchUpdate` for restore (bypasses `AuditingEntityListener`). TRUNCATE is forbidden because it auto-commits on MariaDB. Documented in Phase 75 goal.
- [v1.10 roadmap]: File-system mutations are NOT inside the JPA transaction. Upload-tree restore is post-commit via stage-and-rename, with the previous tree retained at `data/.import-backups/<ts>/uploads-old/` for 24 h manual recovery. Documented in Phase 75 goal.
- [v1.10 roadmap]: No new Maven dependencies. ZIP I/O via JDK `java.util.zip`, JSON via existing Jackson, multipart via existing Spring MVC. Only POM change is `spring-boot-starter-parent` 4.0.5 → 4.0.6 + `<dependencyManagement>` pin on Thymeleaf 3.1.5.

### Phase Numbering

Continuing from v1.9 (last phase: 70). v1.10 phases start at **Phase 71**.

- Phase 71: Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard (PLAT-01..07)
- Phase 72: Backup Wire Contract — Schema, Manifest, ObjectMapper, Audit-Log Scope (SCHEMA-01..04, IMPORT-08)
- Phase 73: Backup Export — Jackson MixIns + Streaming ZIP Endpoint (EXPORT-01..06)
- Phase 74: Backup Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate (IMPORT-01..04, SECU-01..04)
- Phase 75: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT (IMPORT-05..07, QUAL-03)
- Phase 76: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup-Before-Import (SECU-05..07)
- Phase 77: Final UAT + JaCoCo Hold + Round-Trip Test + Documentation (QUAL-01, QUAL-02, QUAL-04, QUAL-05)

### Roadmap Evolution

- 2026-05-09: v1.10 ROADMAP.md drafted with 7 phases (71-77). All 37 requirements mapped, no orphans, no duplicates. Coverage validated.

### Key Technical Context

- Foundation document: `.planning/research/SUMMARY.md` (synthesized from 4 parallel domain researchers — STACK / FEATURES / ARCHITECTURE / PITFALLS).
- New package: `org.ctc.backup` mirroring proven `org.ctc.dataimport` shape (controller + 1-3 services + DTO records).
- New files (~30): `BackupController`, `BackupExportService`, `BackupImportService`, `BackupArchiveService`, `BackupSchema`, `BackupManifest`, `BackupBundle`, `BackupPreview`, `BackupObjectMapperConfig`, ~22 per-entity Jackson MixIns under `org.ctc.backup.serialization`, 2 Thymeleaf templates (`backup.html`, `backup-preview.html`), Flyway `V7__data_import_audit.sql`, `ImportLockService`, read-only-mode `@ControllerAdvice` filter.
- Modified files: `pom.xml` (1 line + dependencyManagement pin), `templates/admin/layout.html` (sidebar + read-only banner), `application.yml` (multipart limits + `app.backup.*`), `FileStorageService.java` (additive helpers), 3+ Thymeleaf templates (line-3 fragment-parameter ternary fix), `GlobalExceptionHandler` (MaxUploadSizeExceededException mapping).
- Files NOT modified: All 22 operative entities (Jackson MixIns leave entities clean), all 22 repositories (stock `JpaRepository.findAll/saveAll/deleteAllInBatch`), V1-V6 Flyway migrations (only V7 added).
- Critical risks (from PITFALLS.md): (1) Thymeleaf 3.1.5 fragment-parameter restricted-mode breakage beyond 3 known templates; (2) MariaDB-vs-H2 transaction divergence (TRUNCATE auto-commits, FK syntax diverges); (3) JPA Auditing overwriting imported timestamps (mitigated via `JdbcTemplate.batchUpdate` bypass); (4) ZIP-Slip + ZipBomb on import; (5) schema-version drift = catastrophic data loss (mitigated via manifest-first read + reject-before-wipe).

### Blockers/Concerns

None. Roadmap approved, ready for Phase 71 planning.

## Session Continuity

Last session: 2026-05-09 — v1.10 milestone opened.

**v1.10 startup commits (anticipated, not yet made):**

- ROADMAP.md update with 7-phase v1.10 structure (Phases 71-77)
- STATE.md update pointing at Phase 71 as current
- REQUIREMENTS.md traceability table populated for all 37 REQ-IDs

**Stopped at:** v1.10 ROADMAP.md drafted by `gsd-roadmapper`. 37/37 requirements mapped to Phases 71-77. Ready for `/gsd-plan-phase 71` to begin Phase 71 planning.

**Next action:** Run `/gsd-plan-phase 71` to break Phase 71 (Spring Boot 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard) into executable plans.

**Branch:** `gsd/v1.10-spring-boot-upgrade-and-export-import` (TBD — to be created at Phase 71 planning kickoff per `gsd/{milestone}-{slug}` template).
