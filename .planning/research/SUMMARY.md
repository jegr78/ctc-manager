# Project Research Summary

**Project:** CTC Manager v1.10 — Spring Boot 4.0.6 Upgrade + Admin Data Export/Import
**Domain:** Brownfield Spring Boot 4 / Thymeleaf / MariaDB admin tool — platform hygiene + new ZIP-based backup/restore feature
**Researched:** 2026-05-09
**Confidence:** HIGH (overall)

---

## Executive Summary

v1.10 is a brownfield SUBSEQUENT milestone immediately after the v1.9 close. It groups two architecturally orthogonal work streams that share **zero code paths** and can be implemented in parallel:

**Cluster A — Platform hygiene.** Bump `spring-boot-starter-parent` 4.0.5 → 4.0.6 (one POM line, 25–65 bug fixes, 8 CVE patches transitively, none in CTC's stack), then perform a preventive audit of all ~80 Thymeleaf templates against the **Thymeleaf 3.1.5** restricted-expression hardening (CVE-2026-40478 SpEL canonicalization fix). Critically: all four researchers independently corrected the milestone wording — Spring Boot 4.0.6 ships **Thymeleaf 3.1.5.RELEASE, NOT 3.2** (3.2 has no GA release as of 2026-05-09). The v1.9 template breakage was caused by `ExpressionUtils.normalize()` + the new `containsExternalAccess()` filter being far stricter on fragment-parameter expressions in restricted context. Maintainer-recommended fix (Daniel Fernandez, thymeleaf/thymeleaf#1082): compute conditional values in the controller as a `pageTitle` model attribute and reference plain `${pageTitle}` in `th:replace`. This aligns with CTC's existing "Keep Templates Lean" convention (CLAUDE.md L70).

**Cluster B — Admin Data Export/Import.** New `org.ctc.backup` module mirroring the `org.ctc.dataimport` package shape (validated v1.5/v1.8 pattern). Single-admin downloads a ZIP containing `data.json` + `uploads/` (or per-entity JSON files — see ARCHITECTURE for refinement) with SemVer schema header; upload runs through preview screen (reuse `CsvImportController`/`DriverSheetImportController` D-15 stateless re-parse pattern) → confirm → atomic Replace-All wipe + restore in one `@Transactional` boundary. Scope: ~20 operative entities (Seasons → SeasonPhases → SeasonPhaseGroups → PhaseTeams → Teams → SeasonTeams → Drivers → SeasonDrivers → PsnAlias → Matchdays → Matches → Races → RaceLineups → RaceResults → Playoffs → PlayoffRounds → PlayoffMatchups → PlayoffSeeds → RaceScoring → MatchScoring + RaceSettings/RaceAttachments). **Audit log table is OUT of scope** (operational metadata, never wiped).

**Net dependency cost: ZERO new Maven artifacts.** Everything (ZIP I/O, Jackson, multipart, JPA bulk delete) is already on the classpath via existing Spring Boot starters. The risk surface is concentrated in three places: (1) the Thymeleaf audit catching latent template breakage that only renders under specific data conditions, (2) the Replace-All transaction's MariaDB-vs-H2 quirks (TRUNCATE auto-commits on MariaDB → must use DELETE; `SET FOREIGN_KEY_CHECKS` syntax diverges → use FK-ordered DELETE instead), and (3) the entity↔JSON serialization strategy (Jackson MixIns with `@JsonIdentityInfo` is preferred — DTOs were considered but deemed over-engineered for an internal backup format; consensus is MEDIUM confidence and demands a round-trip integration test).

---

## Key Findings

### Recommended Stack

**Net new dependencies in `pom.xml`: zero.** The upgrade is a single-line `<version>` bump; the new feature reuses what is already on the classpath.

**Core technologies (all already present):**

- **Spring Boot 4.0.6** (target) — patch release, 25–65 bug fixes, no breaking changes, 8 CVE patches (none affecting CTC's stack since we don't use Elasticsearch/RabbitMQ/Cassandra/DevTools/PID files)
- **Thymeleaf 3.1.5** (transitive) — the actual version SB 4.0.6 ships; CVE-2026-40478 SpEL hardening tightened restricted-context evaluation; **3.2 is NOT released**
- **`java.util.zip.{ZipOutputStream,ZipInputStream}` (JDK)** — ZIP packaging; archives ≪4 GB, no Zip64 needed → `commons-compress` rejected (~700 KB jar bloat for zero benefit at our scale)
- **Jackson 3.1.2 (transitive)** + `ObjectMapper` + Jackson MixIns + `@JsonIdentityInfo` for cycle handling — already auto-configured by Spring Boot, no dependency change
- **Spring MVC `MultipartFile`** + `StreamingResponseBody` — standard Spring patterns
- **`jakarta.persistence.EntityManager`** for bulk DELETE in FK-reverse order + L1 cache discipline (`flush()` + `clear()`); native SQL preferred over JPA cascades for explicit per-table control
- **Flyway migration V7** — new `data_import_audit` table (audit log scope is OPERATIONAL, lives outside Export/Import scope per Pitfall 9)

**Configuration changes (yaml only):**

- `spring.servlet.multipart.max-file-size`: 1 MB → **100 MB** (some researchers suggested 200–500 MB; pick 100 MB MVP, document as bumpable)
- `spring.servlet.multipart.max-request-size`: 10 MB → **100 MB**
- `server.tomcat.max-http-form-post-size` + `max-swallow-size`: 100 MB
- `app.backup.staging-dir` (configurable; default `data/{profile}/backup-staging/`)
- `app.backup.max-upload-size` (read separately from generic multipart cap)

**Critical version correction (all four researchers flagged independently):** PROJECT.md L30 has been corrected to read **"Thymeleaf 3.1.5 (CVE-2026-40478 SpEL hardening)"**. Roadmapper, requirements writer, and all phase planners must use the same wording — never "Thymeleaf 3.2".

See `.planning/research/STACK.md` for full version matrix and per-decision rationale.

### Expected Features

See `.planning/research/FEATURES.md` for full table-stakes / differentiator / anti-feature analysis.

**Must have (table stakes for v1.10):**

Cluster A (Platform):

- `pom.xml` parent bump 4.0.5 → 4.0.6
- Fix the 3 known fragment-parameter ternaries (`match-scoring-form.html`, `race-scoring-form.html`, `season-phase-form.html`, all line 3) via controller-side `pageTitle` model attribute
- Preventive audit of all ~80 templates (62 admin + 16 site) for the same pattern, fix any new findings
- `./mvnw verify -Pe2e` green on 4.0.6
- JaCoCo ≥ 82 % held (currently 87.02 % — comfortable buffer)

Cluster B (Backup feature):

- `GET/POST /admin/backup/export` → streaming ZIP (`Content-Disposition: attachment; filename=ctc-backup-{ISO-instant}.zip`)
- ZIP packs `data.json` (or per-entity files — see ARCHITECTURE) + `uploads/` mirror
- JSON header: `schema_version` (SemVer or integer — see GAP-2), `app_version`, `export_date`, `table_counts`
- `POST /admin/backup/import-preview` (multipart) → preview screen with per-table wipe+restore counts
- `POST /admin/backup/import-execute` → atomic Replace-All in single `@Transactional` boundary
- Schema-version refusal **before** wipe (manifest read first; rejection leaves DB untouched)
- Confirm dialog with explicit "ALL operative data will be deleted" wording (mirror `DriverMergeController`)
- Audit log entry per import (who/when/wiped+restored row counts) → new `data_import_audit` table (Flyway V7), AUDIT TABLE ITSELF IS OUT OF SCOPE OF EXPORT
- Multipart limits raised + documented
- Round-trip integration test (export → import → assert no diff) on **both** H2 and MariaDB
- Build-time regex guard (lightweight Maven exec/grep gate) against re-introduction of fragment-parameter ternaries

**Should have (defer to v1.11 unless trivially in-scope):**

- Verify-only "validate this ZIP" mode
- SHA-256 checksum file inside ZIP
- Diff preview row-count comparison

**Defer to v1.11+:**

- **Per-season selective export** (explicitly user-deferred)
- `?includeUploads=false` query parameter
- Filesystem atomicity hardening via stage+rename pattern

**Anti-features (rejected — would harm v1.10 scope):**

- Cloud destinations (S3/GCS), scheduled/cron'd backups, multi-format support, merge-import, encryption at rest, async with progress polling, Maven Enforcer custom rule

### Architecture Approach

See `.planning/research/ARCHITECTURE.md` for full diagrams, file lists, and integration points.

The new `org.ctc.backup` package mirrors the proven `org.ctc.dataimport` shape (controller + 1–3 services + DTO records). Reuses every existing repository (`findAll`, `saveAll`, `deleteAllInBatch`), the existing exception handling, flash messaging, OSIV, and `FileStorageService` (additively extended).

**Major components (new):**

1. **`BackupController`** — thin: 4 handlers (GET form, POST export streams, POST import-preview, POST import-execute), multipart upload, redirect-flash, no business logic
2. **`BackupExportService`** — `@Transactional(readOnly = true)`, walks DB in FK-respecting order via `BackupSchema.EXPORT_ORDER`, builds bundle, streams to `OutputStream`
3. **`BackupImportService`** — `@Transactional` (write); preview re-parses ZIP (D-15 stateless pattern, NO `@SessionAttributes`); execute does FK-reverse-order DELETE + `em.flush()` + `em.clear()` + insert in EXPORT order
4. **`BackupArchiveService`** — pure ZIP I/O mechanics (read/write); no domain knowledge; supports `StreamingResponseBody`
5. **`BackupSchema`** — static utility class holding `SCHEMA_VERSION` constant, `EXPORT_ORDER` and `DELETE_ORDER` lists (single ordered list, NO Strategy pattern across 22 entity types)
6. **`BackupManifest` / `BackupBundle` / `BackupPreview`** — DTO records
7. **`BackupObjectMapperConfig`** — `@Qualifier("backupObjectMapper")` — explicit `FAIL_ON_UNKNOWN_PROPERTIES = true`, `WRITE_DATES_AS_TIMESTAMPS = false`, `JavaTimeModule`, MixIns registered → does NOT pollute the default Spring `ObjectMapper`
8. **Per-entity Jackson MixIn classes** in `org.ctc.backup.serialization` — applies `@JsonIdentityInfo`, `@JsonIgnore createdAt/updatedAt/version` externally → entities stay clean

**Templates (new):** `templates/admin/backup.html` (form), `templates/admin/backup-preview.html` (per-bucket-table pattern from `driver-import-preview.html`)

**Modified:** `pom.xml` (1 line), `templates/admin/layout.html` (sidebar), `application.yml` (multipart + `app.backup.*`), `FileStorageService.java` (additive helper methods), 3+ Thymeleaf templates (line-3 ternary fix).

**Architecturally NOT modified:** any operative entity (MixIns), any repository, any Flyway migration except the new V7, any existing controller, any auto-config bean. **No SPI/auto-config changes from SB 4.0.6** affect CTC's MVC/JPA/Thymeleaf consumers.

**One ARCHITECTURE-vs-STACK divergence to resolve in roadmap:** STACK proposed a single `data.json` document; ARCHITECTURE proposed per-entity files for streaming-friendly read + diagnosability + future selective import. Both are valid; ARCHITECTURE's per-entity choice is slightly more future-proof.

### Critical Pitfalls

See `.planning/research/PITFALLS.md` for all 13 pitfalls with full warning signs, recovery costs, and phase mappings. The five highest-stakes:

1. **Thymeleaf 3.1.5 fragment-parameter restricted-mode breakage beyond the 3 known templates** — coverage is partial because templates compile lazily. **Prevention:** mechanical grep audit of all `th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"` (not just ternaries); CI `TemplateRenderingSmokeIT` GETs every `/admin/**` URL with `DEV_DATA_SEEDED=true`; pin `org.thymeleaf:thymeleaf` in `<dependencyManagement>`.

2. **Replace-All transaction divergence on MariaDB vs H2** — TRUNCATE auto-commits on MariaDB; `SET FOREIGN_KEY_CHECKS=0` is MariaDB-only. **Prevention:** `DELETE FROM <table>` in FK-reverse order via `EntityManager.createNativeQuery().executeUpdate()`; single `@Transactional`; `em.flush() + em.clear()` between phases (mandatory); two-pass `UPDATE … SET parent_team_id = NULL` for `Team.parentTeam` self-FK; `innodb_lock_wait_timeout = 600`. Test on **both** profiles.

3. **JPA Auditing overwriting imported timestamps** — `AuditingEntityListener` silently overwrites `created_at` with `LocalDateTime.now()`. **Prevention:** restore via native SQL `JdbcTemplate.batchUpdate` (bypasses JPA listeners); IT `givenExportWithCreatedAt2024_whenImport_thenCreatedAtIsStill2024()`.

4. **ZIP Slip + ZipBomb on import** — **Prevention:** reuse `FileStorageService.store()` SECU-02 defense; validate `startsWith(uploadDir.toRealPath())`; reject absolute paths and `..`; per-entry size cap (50 MB), total cap (500 MB), entry-count cap (50,000); reject duplicate names.

5. **Schema-version drift = catastrophic data loss** — naive flow wipes before discovering mismatch. **Prevention:** read `manifest.json` (must be first ZIP entry — enforce in writer) and validate `schema_version == SCHEMA_VERSION` BEFORE entering wipe transaction; HTTP 400 + admin-readable message; counter-test asserts row count unchanged after rejection.

**Honorable mentions:** concurrent-import lock (Pitfall 8); `StreamingResponseBody` LazyInit (Pitfall 3); audit-log scope decision baked into Phase 2 (Pitfall 9); explicit `ObjectMapper` bean (Pitfall 13).

---

## Implications for Roadmap

### Phase 1: SB 4.0.6 Upgrade + Thymeleaf 3.1.5 Template Audit + Build Guard

**Rationale:** Eliminates v1.9 platform debt; produces green test baseline before any feature code.
**Delivers:** `pom.xml` 4.0.5 → 4.0.6; 3 known + N audit-discovered template fixes via controller-side `pageTitle`; CI `TemplateRenderingSmokeIT`; lightweight regex grep gate via `exec-maven-plugin`; `<dependencyManagement>` pin on Thymeleaf.
**Avoids:** Pitfalls 1, 11, 12.

### Phase 2: Audit-Log Scope Decision + Schema Versioning + ObjectMapper Bean + Flyway V7

**Rationale:** Defines wire contract before any export/import code; bakes audit-log-out-of-scope decision into PROJECT.md Decisions row.
**Delivers:** `BackupSchema.SCHEMA_VERSION`; `BackupManifest`; `BackupObjectMapperConfig` (`@Qualifier("backupObjectMapper")`); Flyway V7 `data_import_audit` table; PROJECT.md Decisions row.
**Avoids:** Pitfalls 7, 9, 13.

### Phase 3: Export Service + Jackson MixIns + Streaming Endpoint

**Rationale:** Read-only path is safer half; produces artifact for round-trip.
**Delivers:** Per-entity MixIn classes (~22 files); `BackupExportService` with `@EntityGraph` eager-fetch; `BackupArchiveService.writeZip()`; `BackupController.export()` with `StreamingResponseBody`; `templates/admin/backup.html`; `FileStorageService` additive helpers; serialization unit tests.
**Avoids:** Pitfalls 3, 10.

### Phase 4: Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate

**Rationale:** No-write path; schema-version refusal BEFORE wipe.
**Delivers:** `BackupImportService.preview()`; `BackupArchiveService.readManifest()/readBundle()`; ZIP-Slip + ZipBomb defenses; multipart YAML config; staging-dir lifecycle; `templates/admin/backup-preview.html`; `MaxUploadSizeExceededException` mapped; `X-CSRF-TOKEN` header from JS.
**Avoids:** Pitfalls 2, 6, 7, 13.

### Phase 5: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT

**Rationale:** Riskiest phase. MariaDB-vs-H2 quirks make this a "passes on H2, breaks on MariaDB" trap.
**Delivers:** `BackupImportService.execute()` with self-FK two-pass pre-step → FK-reverse DELETE via native SQL → `em.flush() + em.clear()` → restore via `JdbcTemplate.batchUpdate`; `data_import_audit` row written on success; post-commit upload-tree restore; `innodb_lock_wait_timeout` bump; H2 + MariaDB ITs; mid-restore-failure rollback test; live UAT against Saison-2023.
**Avoids:** Pitfalls 4, 5, 10.

### Phase 6: Operational Hardening — Import Lock + Read-Only Banner + Auto-Backup + Runbook

**Rationale:** Safety net for single-admin-but-not-enforced model.
**Delivers:** `ImportLockService` `ReentrantLock` singleton; `@ControllerAdvice` filter rejecting `/admin/.*POST.*` with 503; persistent yellow banner; auto-export-before-import to `data/.import-backups/<timestamp>/`; operational runbook.
**Avoids:** Pitfall 8.

### Phase 7: Final UAT + JaCoCo Gate Hold + Docs Update

**Delivers:** `./mvnw verify -Pe2e` green; round-trip IT on H2 AND MariaDB; JaCoCo ≥ 82%; README backup section; WIKI page; final UAT checklist.

### Phase Ordering Rationale

- Cluster A (Phase 1) first is lower-risk default — green baseline before feature code.
- Phase 2 before any export/import code because manifest format + audit-log-scope + Jackson defaults are the wire contract.
- Phase 3 (export) before Phase 4 (import) because round-trip is the natural integration test.
- Phase 5 last among implementation phases — failing Phase 5 is then *clearly* a Replace-All issue.
- Phase 6 drop-in-late-friendly except for `@ControllerAdvice` design hook — flag early.
- Audit-log-scope decision baked into Phase 2 (PROJECT.md Decisions row).

### Research Flags

**Phases needing deeper research during planning:**

- **Phase 1:** Smoke-IT seeded-data preconditions for ALL admin routes including phase-specific ones.
- **Phase 5:** `repository.save()` `isNew()` on pre-set UUIDs (H2 + MariaDB); `AuditingHandler.setDateTimeProvider()` thread-safety; self-ref entity completeness audit.
- **Phase 6:** `@ControllerAdvice` read-only-mode filter bypass for import-execute without CSRF hole.

**Phases with standard patterns (skip research-phase):** Phase 2, 3, 4, 7.

---

## Confidence Assessment

| Area | Confidence | Notes |
| ---- | ---------- | ----- |
| Stack | **HIGH** | SB 4.0.6 + Thymeleaf 3.1.5 verified via official sources; one MEDIUM call-out on Hibernate proxy via eager-fetch (flag for monitoring during Phase 3) |
| Features | **HIGH** | Cross-validated against GitLab/Discourse/GitHub Migrations; UI patterns have 1:1 in-codebase precedents |
| Architecture | **HIGH** | Mirrors proven `org.ctc.dataimport` pattern; SB 4.0.6 has zero SPI impact; MEDIUM call-out on `@JsonIdentityInfo` collection edge case (round-trip IT mandatory) |
| Pitfalls | **HIGH** | All 13 cross-checked against official sources; MEDIUM on Hibernate 7.2 specifics (verify in Phase 1 smoke) |

**Overall confidence: HIGH.**

### Gaps to Address

- **GAP-1** ZIP layout (single `data.json` vs per-entity files) — Phase 2; per-entity slightly preferred
- **GAP-2** Schema version representation (integer vs SemVer) — Phase 2; integer simpler
- **GAP-3** Multipart cap size (100 vs 200 vs 500 MB) — MVP 100 MB profile-overridable
- **GAP-4** JPA auditing bypass strategy (native SQL vs `AuditingHandler` scope-disable) — Phase 5 research
- **GAP-5** Canonical 22-entity FK ordering — generate from live entity classes, do NOT trust hand-written research lists
- **GAP-6** Hibernate 7.2 entity equality / OSIV deprecation chatter — verify in Phase 1 upgrade smoke

---

## Sources

### Primary (HIGH confidence)

- Spring Boot v4.0.6 Release Notes (GitHub) — patch-only, no breaking changes
- Spring Boot 4.0.6 announcement (spring.io blog) — confirms Thymeleaf 3.1.5
- Spring Boot Dependency Versions Documentation
- thymeleaf/thymeleaf#1082 — maintainer recommends controller `pageTitle`
- Endor Labs — CVE-2026-40478 root-cause analysis
- GitLab Advisory — CVE-2026-40478
- SEI CERT IDS04-J — ZIP Slip defense
- Snyk Zip Slip vulnerability database
- MariaDB Foreign Key Constraints documentation — TRUNCATE-auto-commit semantics
- Spring Security CSRF reference — Multipart section

### Secondary (MEDIUM confidence)

- Baeldung — Jackson Bidirectional Relationships, JPA Auditing, TRUNCATE TABLE in Spring Data JPA, MaxUploadSizeExceededException
- FasterXML/jackson-datatype-hibernate
- Mastering Hibernate 7: Proxies and LazyInitializationException
- Java Code Geeks — Spring Boot 4 Migration: Breaking Changes
- Wim Deblauwe — Migrating away from Thymeleaf Layout Dialect (anti-feature reference)
- Hibernate Forum — disable first-level cache
- GitLab Backup/Restore documentation, Discourse backup admin UI thread

### Tertiary (LOW confidence — needs validation in implementation)

- Spring Boot 4.0.6 GitHub milestone #422
- Hibernate 7.2 entity-equality / OSIV-deprecation specifics
- Jackson 3.1.2 `use-jackson2-defaults` flip

### Project-internal

- `.planning/PROJECT.md`, `.planning/research/{STACK,FEATURES,ARCHITECTURE,PITFALLS}.md`, `.planning/codebase/{ARCHITECTURE,CONVENTIONS,STACK,STRUCTURE,TESTING}.md`, `CLAUDE.md`
- In-codebase precedents: `DriverSheetImportController/Service`, `CsvImportController`, `driver-import-preview.html`, `gt7-sync-preview.html`, `driver-merge.html`, `FileStorageService` (SECU-02), `match-scoring-form.html`/`race-scoring-form.html`/`season-phase-form.html` (line 3 — known broken)

---

*Research completed: 2026-05-09*
*Ready for roadmap: yes*
