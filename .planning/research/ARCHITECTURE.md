# Architecture Research — v1.10 Spring Boot 4.0.6 Upgrade & Backup Export/Import

**Domain:** Internal admin tool for sports league management (Spring Boot 4 / Thymeleaf MVC / MariaDB)
**Researched:** 2026-05-09
**Confidence:** HIGH (existing 3-tier architecture is well-documented; new components plug in via established patterns. Spring Boot 4.0.6 verified as patch-level release with no SPI/auto-config changes affecting MVC/JPA/Thymeleaf consumers.)

---

## Scope of This Research

Two related but architecturally orthogonal pieces of work:

1. **Spring Boot 4.0.5 → 4.0.6 upgrade** — POM-only change plus a Thymeleaf-3.2 template audit driven by the v1.9 abort lesson. Architecture impact: essentially zero (see §10).
2. **Backup Export/Import feature** — new feature module under `org.ctc.backup` mirroring `org.ctc.dataimport` package shape. Adds ~6 new files and modifies ~3 (admin layout nav entry, `WebConfig` if a download endpoint needs streaming tweaks, `application.yml` for backup settings).

The bulk of the document focuses on the new Backup feature because that is where architectural decisions live.

---

## 1. System Overview — Where Backup Plugs In

```
┌─────────────────────────────────────────────────────────────────────┐
│  Admin UI (Thymeleaf)                                               │
│                                                                     │
│  ┌─────────────────────┐                                            │
│  │ admin/backup.html   │  GET /admin/backup            (form)       │
│  │ admin/backup-       │  POST /admin/backup/export    (download)   │
│  │   preview.html      │  POST /admin/backup/import-preview         │
│  │                     │  POST /admin/backup/import-execute         │
│  └──────────┬──────────┘                                            │
└─────────────┼───────────────────────────────────────────────────────┘
              │
┌─────────────┼───────────────────────────────────────────────────────┐
│  Controller │ org.ctc.backup                                         │
│             ▼                                                        │
│  ┌─────────────────────────┐                                         │
│  │ BackupController         │  thin handlers, multipart upload,      │
│  │ (3 POST + 1 GET)         │  StreamingResponseBody for export      │
│  └──────┬─────────────┬─────┘                                        │
│         │             │                                              │
└─────────┼─────────────┼──────────────────────────────────────────────┘
          ▼             ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Service Layer (business logic)                                      │
│                                                                      │
│  ┌─────────────────────────┐   ┌─────────────────────────┐           │
│  │ BackupExportService      │   │ BackupImportService     │           │
│  │ - collect entities       │   │ - parse ZIP             │           │
│  │ - build manifest         │   │ - validate manifest     │           │
│  │ - serialize JSON         │   │ - build preview         │           │
│  │ - copy referenced files  │   │ - wipe + restore (TX)   │           │
│  │ - stream ZIP             │   │ - restore upload tree   │           │
│  └────┬─────────┬──────┬────┘   └──┬─────────┬─────────┬──┘           │
│       │         │      │           │         │         │              │
│       │         │      ▼           │         │         ▼              │
│       │         │  ┌──────────────────────────┐    ┌──────────────┐  │
│       │         │  │ BackupArchiveService     │    │ BackupSchema │  │
│       │         │  │ (ZIP read/write,         │    │ (constants:  │  │
│       │         │  │  StreamingResponseBody)  │    │ schema_ver,  │  │
│       │         │  └──────────────────────────┘    │ entity ord.) │  │
│       │         │                                  └──────────────┘  │
│       │         ▼                                                    │
│       │   ┌──────────────────────────┐                               │
│       │   │ FileStorageService       │  (existing — read upload tree)│
│       │   │ + new: walkUploads(),    │                               │
│       │   │   restoreUploads()       │                               │
│       │   └──────────────────────────┘                               │
│       ▼                                                              │
│  ┌──────────────────────────────────────────────────────┐            │
│  │ Existing repositories (findAll + bulk deleteAll)     │            │
│  │ Season / SeasonPhase / SeasonPhaseGroup / PhaseTeam  │            │
│  │ Team / SeasonTeam / Driver / SeasonDriver / PsnAlias │            │
│  │ Matchday / Match / Race / RaceLineup / RaceResult    │            │
│  │ Playoff / PlayoffRound / PlayoffMatchup / PlayoffSeed│            │
│  │ RaceScoring / MatchScoring / RaceSettings / RaceAtt. │            │
│  └──────────────────────────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Persistence + Filesystem                                             │
│  MariaDB / H2 (Flyway-managed)        data/{profile}/uploads/        │
└──────────────────────────────────────────────────────────────────────┘
```

### Layer-by-layer integration

| Layer | New | Modified | Reused as-is |
|-------|-----|----------|--------------|
| Templates | `templates/admin/backup.html`, `templates/admin/backup-preview.html` | `templates/admin/layout.html` (sidebar entry) | layout fragment |
| Controller | `org.ctc.backup.BackupController` | none | `GlobalExceptionHandler` catches typed exceptions |
| Service (orchestration) | `BackupExportService`, `BackupImportService`, `BackupArchiveService` | none | `FileStorageService` extended (additive methods) |
| DTO | `BackupManifest`, `BackupBundle`, `BackupPreview`, `BackupSchema` | none | n/a |
| Repository | none | none | all 22 existing repositories — `findAll()` + `deleteAllInBatch()` |
| Entity | none | none | every operative entity used as-is via Jackson MixIns (§3) |
| Config | `application.yml`: `app.backup.*` block | none | profile YAMLs unchanged |

This is the same shape as `org.ctc.dataimport` (controller + 1-3 services + DTO records), which is the project's blessed feature-module pattern (validated in v1.5/v1.8).

---

## 2. Service Layer Decomposition

**Decision:** **Two orchestrators (Export + Import) + one shared archive helper + one schema constant — NO Strategy pattern.**

### Why no per-entity Exporter/Importer

A Strategy pattern (one `EntityExporter<T>` per entity) would buy nothing for this scope:

- 22 operative entity types — small enough that a single ordered list is readable.
- All entities share the same serialization path (Jackson + MixIns); per-entity logic does not exist.
- Order matters for deletes/inserts — putting that order in a strategy registry hides what is fundamentally a sequential process.
- Test surface explodes (22 strategy beans × 2 directions = 44 test classes vs. 2 services).

The entity ordering lives in `BackupSchema` as a single ordered list (see §5). The two services iterate that list.

### Why two services not one

`BackupExportService` and `BackupImportService` have different transactional shapes:

- Export: `@Transactional(readOnly = true)`, streams output, never mutates DB.
- Import: `@Transactional` (write), wipes + restores, file-system mutation, must roll back atomically.

Mixing read-only orchestration with write-orchestration in one bean creates exactly the kind of God-Service that Phase 3 of v1.0 split apart (`RaceManagementService`).

### Repository reuse vs. EntityManager streaming

**Decision: reuse existing `Repository.findAll()` for v1.10 MVP.**

Rationale and trade-off:

- Largest realistic table is `RaceResult` — at ~10 drivers × 40 races/season × 5 seasons = ~2 000 rows. Trivial.
- `findAll()` returns entities with managed lazy associations — Jackson + `@JsonIdentityInfo` (or MixIn equivalent) follows them and uses OSIV to materialize.
- EntityManager streaming (`Stream<T>` + `setHint("org.hibernate.fetchSize", 50)`) is the correct answer at 10⁵+ rows; CTC operative data is at most low thousands. YAGNI.
- If FUTURE scale demands streaming, the change is local to `BackupExportService` — it's an internal refactor, not an architecture change.

### Where ZIP packaging lives

**Decision: separate `BackupArchiveService` bean.**

- Export and Import both need ZIP read + write — without a shared bean we duplicate `ZipOutputStream` / `ZipInputStream` management in two services.
- Streaming concerns (Spring `StreamingResponseBody` for download, temp file or memory bound for upload) are I/O concerns that don't belong next to JSON serialization or transaction management.
- `BackupArchiveService` exposes: `writeZip(BackupBundle, OutputStream)`, `readZip(InputStream) → BackupBundle`. Pure mechanics, no domain knowledge.

### Component responsibilities

| Component | Responsibility | Implementation |
|-----------|---------------|----------------|
| `BackupController` | HTTP I/O, multipart, redirect-flash | thin: 4 handlers, no business logic |
| `BackupExportService` | Walk DB in FK order → build `BackupBundle` (manifest + JSON + file refs) | `@Transactional(readOnly = true)`; iterates `BackupSchema.EXPORT_ORDER` |
| `BackupImportService` | Validate manifest → preview → wipe → restore | `@Transactional` wrapping wipe+restore; preview uses re-parse on execute (D-15 from v1.8) |
| `BackupArchiveService` | ZIP read/write, file tree mirror | uses `ZipOutputStream` / `ZipInputStream`, supports `StreamingResponseBody` |
| `BackupSchema` | Constants: `SCHEMA_VERSION`, `EXPORT_ORDER`, `DELETE_ORDER` | static-only utility class with a record `EntityRef(Class<?>, String name)` list |
| `BackupManifest` | DTO record: schema_version, app_version, export_date, table_counts | Jackson-serializable record |
| `BackupBundle` | DTO: manifest + entity-data Map + file-path list | in-memory representation passed between services |
| `BackupPreview` | DTO: parsed manifest + per-table delta (rowsToWipe vs rowsToRestore) for preview screen | record |

---

## 3. JSON Serialization Strategy

**Decision: Jackson MixIn classes + `@JsonIdentityInfo` on the operative-entity MixIn — NOT a parallel DTO layer, NOT entity-direct annotations.**

### Why MixIns over option A (entity annotations)

Adding `@JsonIdentityInfo`, `@JsonIgnore`, `@JsonManagedReference` directly to entities pollutes the domain model with serialization concerns that exist for one feature (backup). The v1.5 cleanup (Phase 32 — RaceGraphicService relocated) explicitly fought this kind of layering violation.

### Why MixIns over option B (parallel DTO layer)

A `BackupSeasonDto` + `BackupTeamDto` + … layer for ~22 entities means:

- ~22 DTO records + ~22 mapper methods (`toBackupDto` / `fromBackupDto`).
- Every entity field add forces a parallel DTO change — schema drift is a maintenance tax that the schema-version field is supposed to obviate.
- DTOs lose JPA's `@Version` and `@CreatedDate` semantics on round-trip unless explicitly carried.

### Why MixIns are the right middle ground

Mixin annotations are applied externally — domain entities stay clean, all serialization config sits in one package (`org.ctc.backup.serialization`). This is the textbook Jackson recommendation for entity serialization without modifying entities.

```java
// org.ctc.backup.serialization.SeasonMixIn
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
abstract class SeasonMixIn {
    @JsonIgnore abstract LocalDateTime getCreatedAt(); // skip audit noise
    @JsonIgnore abstract LocalDateTime getUpdatedAt();
    @JsonIgnore abstract Integer getVersion();
}
```

`@JsonIdentityInfo` breaks bidirectional cycles (Season ↔ Matchday, Team ↔ subTeams self-ref) by emitting a full object on first encounter and an ID-reference thereafter. Reading back, Jackson rebuilds the object graph from the IDs. **This is essential** for the CTC entity graph because Season → Matchday → Match → Race → RaceResult has back-pointers throughout.

### Mixin registration

Single `BackupObjectMapperConfig` `@Configuration` bean produces an `ObjectMapper` qualified `@Qualifier("backupObjectMapper")`. The default Spring `ObjectMapper` is untouched — backup serialization rules don't bleed into anything else.

### Concrete confidence call-out

Jackson `@JsonIdentityInfo` with collections has a documented edge case: an item fully serialized in a previous iteration is emitted as ID-reference for the rest of that array. For backup this is a feature, not a bug — we want the second occurrence to be a reference, otherwise we duplicate. **MEDIUM confidence — needs an integration test that round-trips a Season with shared SeasonTeam references** (Plan-level acceptance criterion).

---

## 4. ZIP Layout

**Decision: the proposed layout is sensible; one minor refinement.**

```
backup-2026-05-09T143012Z.zip
├── manifest.json                     # schema_version, app_version, export_date, table_counts
├── data/
│   ├── seasons.json                  # one file per entity type
│   ├── season_phases.json
│   ├── season_phase_groups.json
│   ├── phase_teams.json
│   ├── teams.json
│   ├── season_teams.json
│   ├── drivers.json
│   ├── season_drivers.json
│   ├── psn_aliases.json
│   ├── matchdays.json
│   ├── matches.json
│   ├── races.json
│   ├── race_lineups.json
│   ├── race_results.json
│   ├── playoffs.json
│   ├── playoff_rounds.json
│   ├── playoff_matchups.json
│   ├── playoff_seeds.json
│   ├── race_scorings.json
│   ├── match_scorings.json
│   ├── race_settings.json
│   └── race_attachments.json
└── uploads/                          # mirror of data/{profile}/uploads/
    ├── teams/{logo files}
    ├── tracks/{image files}
    ├── cars/{image files}
    ├── race-attachments/{files}
    └── team-cards/{generated PNGs}
```

### Why per-entity files instead of one `data.json`

The PROJECT.md Goal section proposed `data.json` (single document). Recommendation: **split per-entity** for three reasons:

1. **Streaming-friendly read** — `BackupArchiveService.readZip()` can stream entry-by-entity, parse, dispatch. With one giant `data.json` we either load it all into memory or use a streaming Jackson parser, which is harder to reason about and harder to test.
2. **Diagnosability** — when a backup is malformed or partially incompatible, "race_results.json failed at row 1432" is actionable; "data.json failed somewhere" is not.
3. **Selective import (future)** — opens the door to per-entity-class restoration without forcing v1.10 to ship that feature now.

The schema-version + manifest still gates the whole archive — there is no per-file versioning.

### Manifest content

```json
{
  "schema_version": 1,
  "app_version": "1.10.0",
  "export_date": "2026-05-09T14:30:12Z",
  "table_counts": {
    "seasons": 12, "season_phases": 14, "matches": 543, "race_results": 4216, ...
  },
  "uploads_count": 87,
  "uploads_total_bytes": 12546321
}
```

### Anti-feature: no SQL dump

Tempting to ship a `mysqldump`-style SQL file as belt-and-suspenders. Don't — H2 vs MariaDB SQL diverges, the whole point of going through JPA is database-portability. A SQL dump would tie the backup to MariaDB and break the dev-prod migration use case.

---

## 5. Import Flow Integration with Existing Patterns

### Reuse the v1.8/CsvImportController preview-state pattern (D-15 lesson)

The CTC project has a hard-won lesson from v1.8 (`CsvImportController` + `DriverSheetImportController`): **stateless preview controllers re-parse on execute.** Session-scoped state was rejected as an anti-pattern because:

- Predictable transactional boundary (each POST is a transaction; no half-committed session state).
- No `@SessionAttributes` cleanup logic.
- Survives admin-tab refresh / re-upload.

For backup import, this means:

- `POST /admin/backup/import-preview` — multipart upload, parses ZIP into a temp file (or memory if small), validates manifest, builds `BackupPreview`, returns preview template. **Temp file path is stashed in a hidden form field** in the preview template.
- `POST /admin/backup/import-execute` — re-reads the temp file from the path in the form (path validated against a whitelisted backup-staging dir to prevent traversal), executes wipe + restore in one transaction, deletes temp file in `finally`.
- If the temp file is gone (admin took 30 minutes, server restarted, dev-mode reloaded) — execute fails fast with a clear "preview expired, please re-upload" error.

### Why temp file, not full re-upload on execute

The ZIP can be 100 MB+ when uploads are included — forcing a second upload on Confirm is poor UX. The temp-file path is the minimum state we can carry forward; it's the same trade-off `CsvImportController` makes by carrying parsed metadata via hidden form fields.

### Temp-file handling

```
data/{profile}/backup-staging/
└── upload-2026-05-09T143012Z-{uuid}.zip   # one per upload, UUID-suffixed to prevent collision
```

Configurable via `app.backup.staging-dir`. On startup, `BackupImportService` deletes files older than 1 hour (admin abandoned a preview). Path-traversal hardening: the path-from-form must `startsWith()` the canonical staging-dir path, otherwise `ValidationException`.

### Controller endpoints

```
GET  /admin/backup                       → admin/backup.html (form: Export button + Import upload)
POST /admin/backup/export                → StreamingResponseBody download (Content-Disposition: attachment)
POST /admin/backup/import-preview        → admin/backup-preview.html (table_counts + diff + Confirm form)
POST /admin/backup/import-execute        → redirect:/admin/backup with flash success/error
```

All under `/admin/*` — same auth profile as everything else (Basic Auth in prod/docker, open in dev/local).

---

## 6. Replace-All Transaction — FK Ordering

**Decision: order-based delete + insert. Do NOT use `SET FOREIGN_KEY_CHECKS=0`.**

### Delete order (leaf-to-root)

```
RaceResult
RaceLineup
RaceAttachment
RaceSettings
Race
PlayoffSeed
PlayoffMatchup
PlayoffRound
Playoff
Match
Matchday
PsnAlias
SeasonDriver
PhaseTeam
SeasonPhaseGroup
SeasonPhase
SeasonTeam
Season
Driver
Team           # parent/sub-team self-ref: see Team-specific note below
RaceScoring
MatchScoring
```

### Insert order (root-to-leaf, exact reverse)

Same list reversed. `BackupSchema.EXPORT_ORDER` is the canonical list; `DELETE_ORDER = reverse(EXPORT_ORDER)`.

### Why not `SET FOREIGN_KEY_CHECKS=0`

1. **MariaDB-only** — fails on H2 (test profile). Dev parity matters; test profile must exercise the same code path.
2. **Hides bugs** — if FK-ordering has a defect, `FOREIGN_KEY_CHECKS=0` lets the import succeed with corrupt referential integrity that surfaces only when a downstream service tries to resolve a dangling FK.
3. **Database-coupled** — moves us from "Spring Data JPA" toward raw native SQL. The existing codebase has zero `SET` statements; staying ORM-pure preserves the H2/MariaDB portability promise.
4. **Audit-log friendly** — JPA `deleteAll()` calls go through the entity lifecycle (audit listeners fire if needed). Native truncate bypasses those.

### Self-reference: Team parent/sub-team

`Team.parentTeam` is a self-FK. Simple delete-order doesn't help — a parent can't be deleted before its children. Two approaches:

- **A — Two-pass delete:** first `UPDATE teams SET parent_team_id = NULL`, then `DELETE FROM teams`. JPA equivalent: `teamRepository.findAll().forEach(t -> t.setParentTeam(null))` + `flush()` + `deleteAllInBatch()`. Adds one SQL statement.
- **B — Bulk JPQL:** `@Modifying @Query("UPDATE Team t SET t.parentTeam = NULL")` then `deleteAllInBatch()`.

Recommendation: **B** — single JPQL update is cheaper than 30 entity loads + setter loop. No `@PreRemove` cascade concerns because `Team` has no cascade=REMOVE on `parentTeam`.

### L1 cache after wipeAll

After `deleteAllInBatch()` the persistence-context still holds detached entity references to deleted rows. **`entityManager.clear()` is mandatory** before the insert phase, otherwise a re-saved entity with the same ID can throw `EntityExistsException` or trigger a stale-update.

```java
@Transactional
public void executeImport(BackupBundle bundle) {
    deleteAllInOrder();           // bulk deletes in BackupSchema.DELETE_ORDER
    entityManager.flush();        // force SQL emission
    entityManager.clear();        // detach all from L1
    insertAllInOrder(bundle);     // saveAll in BackupSchema.EXPORT_ORDER
}
```

### Hibernate ID-generation gotcha

Backup carries existing UUIDs. JPA's `GenerationType.UUID` only generates IDs for entities **with null IDs** at persist time. Restoring an entity with its original UUID via `repository.save()` works because Hibernate sees the ID is already set and treats it as a `merge()` candidate — but a `merge()` on a non-existent row issues an UPDATE that affects 0 rows and silently fails.

**Fix:** after `entityManager.clear()`, the persistence context is empty; calling `repository.saveAll()` with pre-set UUIDs goes through `EntityManager.persist()` because `repository.save()` checks `isNew()` (Spring Data's strategy), and `BaseEntity` IDs that are non-null but with null `@Version` are considered new. **MEDIUM confidence — verify in an IT** with H2 + MariaDB; if it misbehaves, fall back to explicit `entityManager.persist(entity)` instead of `repository.save()`.

---

## 7. Schema Versioning

**Decision: simple integer constant in `BackupSchema`. NOT derived from Flyway, NOT a per-class annotation.**

```java
public final class BackupSchema {
    public static final int SCHEMA_VERSION = 1;   // bump when the JSON shape changes
    public static final List<EntityRef> EXPORT_ORDER = List.of(...);
    private BackupSchema() {}
}
```

### Why not derived from Flyway

- Flyway version reflects DB-schema migrations. Backup format depends on **JSON structure** which can change without a new migration (e.g., we add a new field on `Season` — schema unchanged, but old backups have a missing field).
- Tying the two creates false signal: every Flyway migration would force a new backup version, even those that are pure indexes or constraint-only.

### Why not per-class annotation

- 22 entities × 1 annotation each = 22 lines of bookkeeping nobody will keep accurate.
- Single integer in `BackupSchema` is one line. Code review immediately catches a forgotten bump because the constant change is visually obvious in a PR diff.

### Compatibility check on import

```java
if (manifest.schemaVersion() != BackupSchema.SCHEMA_VERSION) {
    throw new ValidationException(
        "Backup schema_version=%d incompatible with current=%d. No implicit upgrade."
        .formatted(manifest.schemaVersion(), BackupSchema.SCHEMA_VERSION));
}
```

`app_version` from manifest is informational (logged + shown in preview), NOT enforced. Different app versions with same `schema_version` are import-compatible.

---

## 8. File Handling — Uploads Mirror

### Export side

- Walk `data/{profile}/uploads/` recursively.
- For each file, check whether any entity field references it (Team.logoUrl, Track.imageUrl, Car.imageUrl, RaceAttachment.path, etc.). Use a simple `Set<String> referencedPaths` built once before walking.
- Include only referenced files. Orphans (files in the upload tree with no entity reference) are **skipped** — they're either failed-upload garbage or deleted-entity remnants; neither belongs in a clean backup.
- Path traversal: every path is validated to be inside the canonical `app.upload-dir` before inclusion in the ZIP.

### Import side

- After successful DB restore, write each `uploads/...` ZIP entry to `data/{profile}/uploads/...` (same relative path).
- **Replace existing**: if the file is already there, overwrite. The DB just got wiped — old files cannot belong to extant entities.
- Existing files in `data/uploads/` not present in the ZIP are **left alone** (not deleted). Rationale: avoid catastrophic deletion if the backup is incomplete or the admin has unrelated files in the dir; orphan cleanup is a separate concern.
- Path traversal hardening: every ZIP entry's resolved target path must start with the canonical upload dir. Reject any `..` traversal — same defense as `FileStorageService.store()` from v1.5 Phase 28.

### Why not delete-then-restore the uploads dir

Considered and rejected:
- Atomicity is hard (mid-delete crash leaves no files at all).
- The DB wipe + restore is already atomic via `@Transactional`; aligning the file-system to that atomicity would require a transactional file-system (out of scope).
- If a backup is missing one logo file by accident, leaving the existing one is graceful degradation.

### Filesystem operations are NOT inside the JPA transaction

Important: `Files.copy()` calls cannot participate in a JPA `@Transactional` boundary. The order is:
1. Begin TX → wipe DB → restore DB rows → commit TX.
2. After commit succeeds: write uploaded files.
3. If file-write fails AFTER DB commit, the system has a "DB-restored, files-stale" inconsistency that admin must repair manually. Log error + flash an explicit warning.

This is the same trade-off `FileStorageService` makes today (DB write happens before file write in some flows). Document the trade-off in the plan; do not invent a 2PC.

---

## 9. Data Flow — Step by Step

### Export flow

```
[Admin clicks Export Backup]
    ↓
POST /admin/backup/export
    ↓
BackupController.export(HttpServletResponse)
    ↓ sets Content-Disposition: attachment; filename=backup-{ts}.zip
    ↓ returns StreamingResponseBody
    ↓
BackupExportService.streamBackup(OutputStream)
    ├── readOnly TX begin
    ├── for each EntityRef in BackupSchema.EXPORT_ORDER:
    │     repo.findAll() → List<entity>
    │     write JSON file entry to ZIP via BackupArchiveService
    ├── build manifest (counts, version, date)
    ├── write manifest.json entry
    ├── walk data/{profile}/uploads/, filter to referenced paths
    ├── stream each file as ZIP entry
    └── readOnly TX end
    ↓
[Browser receives ZIP stream]
```

### Import preview flow

```
[Admin uploads ZIP via form]
    ↓
POST /admin/backup/import-preview (multipart)
    ↓
BackupController.importPreview(MultipartFile)
    ↓ writes file to data/{profile}/backup-staging/upload-{uuid}.zip
    ↓
BackupImportService.preview(stagingPath)
    ├── BackupArchiveService.readManifest(stagingPath) → BackupManifest
    ├── validate schema_version == SCHEMA_VERSION (else throw ValidationException)
    ├── BackupArchiveService.readBundle(stagingPath) → BackupBundle (in-memory)
    ├── for each EntityRef: count current rows (repo.count()) vs bundle rows
    └── return BackupPreview(manifest, perTableDelta, stagingPath)
    ↓
admin/backup-preview.html (table of "wipe N rows / restore M rows" + Confirm form with hidden stagingPath)
```

### Import execute flow

```
[Admin confirms preview]
    ↓
POST /admin/backup/import-execute (hidden field: stagingPath)
    ↓
BackupController.importExecute(stagingPath)
    ↓ validate stagingPath ∈ canonical staging dir
    ↓
BackupImportService.execute(stagingPath)
    ├── @Transactional begin
    ├── BackupArchiveService.readBundle(stagingPath)  // re-parse, do not trust preview state
    ├── re-validate manifest
    ├── deleteAllInOrder():
    │     UPDATE teams SET parent_team_id = NULL  (self-ref pre-step)
    │     for each EntityRef in DELETE_ORDER: repo.deleteAllInBatch()
    │     entityManager.flush() + clear()
    ├── insertAllInOrder():
    │     for each EntityRef in EXPORT_ORDER: repo.saveAll(bundle.entities(ref))
    ├── @Transactional commit
    ├── (post-commit) restore upload files from ZIP
    ├── (post-commit) audit-log: rows wiped, rows restored, files restored
    └── delete stagingPath
    ↓
redirect:/admin/backup with flash success or specific error
```

---

## 10. Spring Boot 4.0.6 Architecture Impact

**Verdict: zero architectural impact for CTC. POM-only change.**

### What 4.0.5 → 4.0.6 actually contains (verified via Spring blog)

- 65 bug fixes, doc improvements, dependency upgrades.
- 8 CVEs, **none affecting CTC's stack**:
  - Elasticsearch / RabbitMQ / Cassandra TLS hostname-verification — CTC uses none of these.
  - DevTools timing-attack — CTC has no DevTools dependency.
  - Random PRNG used as secret — CTC's only secret is the BCrypt-hashed admin password in env vars.
  - Authorization gap with Actuator — CTC exposes only `/actuator/health` and SecurityConfig already authenticates everything outside that path.
  - Symlink in PID file — CTC doesn't write PID files.
  - Temp directory ownership — applies; mitigated because CTC's container runs non-root user `ctc` with isolated `/tmp` (verified in Dockerfile).
- Transitive Thymeleaf bumps to 3.2.x — **this is the actual risk**, addressed by the v1.10 template-audit goal (not by this research; that's a Phase plan concern).

### Things that are NOT changing

- Spring MVC dispatcher / `@Controller` / `@RequestMapping` — unchanged
- Spring Data JPA repository SPI — unchanged
- `JpaRepository.findAll() / saveAll() / deleteAllInBatch()` semantics — unchanged
- OSIV interceptor — unchanged
- Flyway integration — unchanged
- Auto-configuration order — unchanged for the modules CTC uses
- Jackson `ObjectMapper` defaults — unchanged

### Action items the upgrade DOES require

- POM bump `spring-boot-starter-parent` 4.0.5 → 4.0.6 (one line).
- Re-verify with `./mvnw verify -Pe2e`.
- Audit ~80 Thymeleaf templates for the 3.2 ternary-in-fragment pattern (the v1.9-abort root cause). This is template work, NOT architectural.
- Optional: re-confirm `/actuator/health` is the only exposed actuator endpoint, given the Actuator-related CVE (already true in `application.yml`).

**No new beans, no new auto-configuration to opt into, no removed APIs to migrate from.**

---

## 11. Build Order (FK-Dependency-Aware)

The roadmapper should sequence implementation in phases that respect entity dependencies and let each phase be independently testable. Suggested order:

1. **Phase A — Foundations (no DB I/O):**
   - `BackupSchema` (constants, ordering lists)
   - `BackupManifest`, `BackupBundle`, `BackupPreview` records
   - `BackupArchiveService` (pure ZIP I/O — testable with in-memory streams)
   - Jackson MixIn classes + `BackupObjectMapperConfig`
   - Unit tests for serialization round-trip on each entity-type-by-type (start with leaf entities: `Team`, `Driver` — fewest associations).

2. **Phase B — Export:**
   - `BackupExportService` (read-only)
   - File-walk + reference-filter logic on `FileStorageService`
   - `BackupController.export()` endpoint + StreamingResponseBody
   - `templates/admin/backup.html` minimal form
   - IT: full export of dev fixture (Season 2023 multi-phase + Season 2024-3 empty-phase) → assert ZIP entry count matches `BackupSchema.EXPORT_ORDER` × `BackupSchema` table_counts + uploads.

3. **Phase C — Import preview:**
   - `BackupImportService.preview()` (no writes)
   - `BackupController.importPreview()` endpoint + multipart staging
   - `templates/admin/backup-preview.html`
   - Schema-version mismatch handling
   - IT: round-trip — export ZIP, parse it back, assert manifest + per-table counts match.

4. **Phase D — Import execute (the risky one):**
   - `BackupImportService.execute()` with full wipe + restore
   - Self-ref pre-step (Team.parentTeam = null)
   - `entityManager.clear()` between wipe and restore
   - Post-commit upload-tree restore
   - Audit log
   - IT: H2 + MariaDB profile — export, wipe DB manually, import, assert all entities + relationships restored byte-identically (compare via `repository.findAll()` lists with deep equals).

5. **Phase E — Spring Boot 4.0.6 upgrade + Thymeleaf 3.2 audit:**
   - POM bump.
   - Template audit & fix (3 known: match-scoring-form, race-scoring-form, season-phase-form Line 3) plus any new findings.
   - Full `./mvnw verify -Pe2e`.
   - This phase is **independent** of A-D and can interleave or run in parallel — gating it on a dedicated branch prevents the v1.9-abort scenario from blocking backup work.

Phase E can be sequenced first if the team prefers to clear the platform debt before adding the feature; both orderings are safe because there are no shared files between the two work-streams.

---

## 12. Anti-Patterns to Avoid

### Anti-Pattern 1: Storing the BackupBundle in `@SessionAttributes`

**What:** "Just put the parsed bundle in the session, simpler than re-parsing."
**Why wrong:** A 100 MB bundle in HTTP session bloats memory, breaks horizontal scaling, and re-introduces the exact session-state-management debt that v1.8 D-15 explicitly removed. Session expiry + admin-tab restart = silent data loss.
**Instead:** Stage the ZIP file on disk; carry only the staging path through hidden form fields.

### Anti-Pattern 2: Single mega-method `BackupService.exportAndStream()`

**What:** One service with a 200-line `exportAndStream()` that does DB walk + JSON serialization + ZIP write + file-tree mirror inline.
**Why wrong:** Tests need to mock `OutputStream` AND `ZipOutputStream` AND every repository AND `FileStorageService`. The `RaceManagementService` 673-line nightmare from v1.0 Phase 3 was exactly this anti-pattern.
**Instead:** Composition — `BackupExportService` orchestrates, delegates ZIP mechanics to `BackupArchiveService`, file-tree walking to `FileStorageService`.

### Anti-Pattern 3: `SET FOREIGN_KEY_CHECKS=0` "for safety"

**What:** Disable FK checks during import to avoid ordering bugs.
**Why wrong:** Database-coupled (breaks H2 tests), masks ordering bugs that would otherwise surface as test failures, hides corrupted-FK states from observability.
**Instead:** Trust the ordered-delete + ordered-insert pattern; if a constraint fires, it's a bug to fix, not noise to silence.

### Anti-Pattern 4: Per-entity Strategy beans

**What:** `interface EntityExporter<T>` + 22 implementations + a registry.
**Why wrong:** Premature abstraction — entities don't have per-type logic. The "obvious" Strategy explodes the test surface and scatters ordering across 22 files.
**Instead:** Single ordered list in `BackupSchema`, two services iterating it.

### Anti-Pattern 5: Mixing file-system operations into the JPA transaction

**What:** `@Transactional` method that does `repository.save()` AND `Files.copy()` interleaved.
**Why wrong:** Transactions roll back DB, not files. Mid-method failure leaves orphan files. JPA cannot guarantee filesystem semantics.
**Instead:** Two-phase — DB wipe + restore inside `@Transactional`; file restore strictly after commit, in a `try/catch` that logs failures explicitly.

---

## 13. Integration Points Summary

| Integration Point | Existing Component | Modification |
|-------------------|-------------------|--------------|
| Admin sidebar nav | `templates/admin/layout.html` | add "Backup" entry |
| Auth filter chain | `SecurityConfig` (prod/docker) | none — all `/admin/*` already authenticated |
| Exception handling | `GlobalExceptionHandler` | none — `ValidationException` and `BusinessRuleException` already mapped |
| Flash messaging | RedirectAttributes pattern | reuse |
| Static asset serving | `WebConfig` | none — backup downloads are dynamic, not static |
| Multipart upload | already configured `max-file-size: 10MB` in `application.yml` | **MODIFY**: bump `app.backup.max-upload-size` to 200 MB or read from a profile-specific override; keep general 10 MB as default for non-backup endpoints |
| Audit logging | none today (no audit table) | **DECIDE per Phase plan**: log to SLF4J at INFO + a future audit table (out of scope unless explicit) |
| File storage | `FileStorageService` | **MODIFY**: add `walkUploads()`, `restoreUpload(path, bytes)`, `referencedPaths(entities)` methods (additive only) |

### Files added (new)

- `src/main/java/org/ctc/backup/BackupController.java`
- `src/main/java/org/ctc/backup/BackupExportService.java`
- `src/main/java/org/ctc/backup/BackupImportService.java`
- `src/main/java/org/ctc/backup/BackupArchiveService.java`
- `src/main/java/org/ctc/backup/BackupSchema.java`
- `src/main/java/org/ctc/backup/BackupManifest.java` (record)
- `src/main/java/org/ctc/backup/BackupBundle.java` (record)
- `src/main/java/org/ctc/backup/BackupPreview.java` (record)
- `src/main/java/org/ctc/backup/serialization/BackupObjectMapperConfig.java`
- `src/main/java/org/ctc/backup/serialization/{Entity}MixIn.java` (~22 files — one per operative entity)
- `src/main/resources/templates/admin/backup.html`
- `src/main/resources/templates/admin/backup-preview.html`

### Files modified

- `pom.xml` — `<version>4.0.5</version>` → `<version>4.0.6</version>` for parent (single line).
- `src/main/resources/templates/admin/layout.html` — sidebar nav entry for Backup.
- `src/main/resources/application.yml` — `app.backup.staging-dir` + `app.backup.max-upload-size` block.
- `src/main/java/org/ctc/domain/service/FileStorageService.java` — additive helper methods.
- 3 (or more, per audit) Thymeleaf templates — fix Line-3 fragment-parameter ternaries.

### Files NOT modified

- All 22 operative entity classes — Jackson MixIns leave entities clean.
- All 22 repositories — `findAll`, `saveAll`, `deleteAllInBatch` are stock `JpaRepository` methods.
- `Flyway` migrations — no schema changes.
- All existing controllers — no shared endpoints.

---

## Sources

- [Spring Boot 4.0.6 release announcement](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/) — verified patch-only, no SPI changes
- [Jackson @JsonIdentityInfo for circular references](https://www.logicbig.com/tutorials/misc/jackson/json-identity-info-annotation.html) — handles bidirectional JPA cycles
- [Baeldung — Jackson Bidirectional Relationships](https://www.baeldung.com/jackson-bidirectional-relationships-and-infinite-recursion) — MixIn pattern for keeping entity classes clean
- [Vlad Mihalcea — Cascade DELETE patterns](https://vladmihalcea.com/cascade-delete-unidirectional-associations-spring/) — entity ordering for bulk delete
- [Medium — FK constraint errors in Spring Data JPA](https://medium.com/tuanhdotnet/why-foreign-key-constraint-errors-occur-in-spring-data-jpa-and-how-to-resolve-them-e9d59ee836c0) — order-based delete vs. SET FOREIGN_KEY_CHECKS
- Project files: `.planning/codebase/ARCHITECTURE.md`, `.planning/codebase/STRUCTURE.md`, `.planning/PROJECT.md`, `pom.xml`, `BaseEntity.java`, `application.yml`, `CsvImportController.java` (preview pattern reference)

---

*Architecture research for: CTC Manager v1.10 — Spring Boot 4.0.6 platform upgrade + ZIP-based admin Backup Export/Import*
*Researched: 2026-05-09*
