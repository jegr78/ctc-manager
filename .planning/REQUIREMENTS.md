# Requirements: CTC Manager — v1.10 Spring Boot Upgrade & Data Export/Import

**Defined:** 2026-05-09
**Core Value:** Architectural Consistency: All controllers delegate to services, exception handling is centralized, and the production environment is secured.

**Foundation:** `.planning/research/SUMMARY.md` — synthesized research from 4 parallel domain researchers (STACK / FEATURES / ARCHITECTURE / PITFALLS). Critical correction propagated: Spring Boot 4.0.6 ships **Thymeleaf 3.1.5.RELEASE** (CVE-2026-40478 SpEL canonicalization hardening), NOT Thymeleaf 3.2.

## v1.10 Requirements

Requirements for milestone v1.10. Each maps to roadmap phases.

### PLAT — Spring Boot Platform Upgrade

- [ ] **PLAT-01**: Maven `spring-boot-starter-parent` von 4.0.5 auf 4.0.6 gehoben (1 Zeile in `pom.xml`)
- [ ] **PLAT-02**: `<dependencyManagement>` pinnt `org.thymeleaf:thymeleaf` explicit auf 3.1.5 (Forward-Compat-Schutz gegen weitere transitive Bumps; verhindert dass eine künftige Spring-Boot-Patch-Version eine Thymeleaf-Version mit weiterer SpEL-Verschärfung ungewollt einzieht)
- [ ] **PLAT-03**: Die 3 bekannten Templates (`templates/admin/match-scoring-form.html`, `race-scoring-form.html`, `season-phase-form.html` — alle Zeile 3) erhalten Title-Computation im Controller (`pageTitle` Model-Attribut); Templates referenzieren `${pageTitle}` in `th:replace="~{admin/layout :: layout(${pageTitle}, ~{::section})}"`
- [ ] **PLAT-04**: Preventiver Audit aller ~80 Thymeleaf-Templates (~62 admin + ~16 site) auf Pattern `th:(replace|insert|include)=".*\(.*\$\{.*\}.*\)"`; alle Findings (über die 3 bekannten hinaus) werden analog zu PLAT-03 gefixt; gefundene Template-Liste wird im Phase-Plan dokumentiert
- [ ] **PLAT-05**: `./mvnw verify -Pe2e` BUILD SUCCESS auf 4.0.6 mit allen 1227 Unit + 31 Playwright E2E Tests; JaCoCo line coverage ≥ 82 % gehalten (entspricht der Phase 70 / v1.9 Baseline)
- [ ] **PLAT-06**: `TemplateRenderingSmokeIT` als Regression-Test im Failsafe-Profile: jeder `/admin/**`-Route wird mit dev-data-seed GETet und auf HTTP 200 + Abwesenheit von `org.thymeleaf.exceptions.TemplateProcessingException` geprüft (verhindert lazy template breakage in zukünftigen Spring-Boot-Bumps)
- [ ] **PLAT-07**: Lightweight Maven `exec-maven-plugin`-basierter Build-Guard, der bei neu eingeführten Fragment-Parameter-Ternaries fehlschlägt (Forward-Commitment aus Phase 67 D-06 / v1.9; Pattern-grep `th:(replace|insert|include)=".*\(.*\$\{.*\?.*:.*\}.*\)"` über `src/main/resources/templates/`)

### SCHEMA — Backup Wire Contract & Manifest

- [ ] **SCHEMA-01**: Statisches `BackupSchema.SCHEMA_VERSION`-Constant (Integer, Wert `1` für v1.10); bei späterer Schema-Drift manuell zu bumpen
- [ ] **SCHEMA-02**: `BackupManifest`-Record mit Feldern `schema_version` (int), `app_version` (String, aus `pom.xml` resolved), `export_date` (ISO-8601 Instant), `table_counts` (Map<String, Long>); seriaisiert als `manifest.json` und ist FIRST entry im ZIP (Read-Order-Determinismus)
- [ ] **SCHEMA-03**: Flyway-Migration `V7__data_import_audit.sql` erstellt Tabelle `data_import_audit` (id UUID PK, executed_at TIMESTAMP, executed_by VARCHAR, schema_version INT, table_counts_wiped JSON, table_counts_restored JSON, source_filename VARCHAR, success BOOLEAN); H2 + MariaDB kompatibel
- [ ] **SCHEMA-04**: `@Qualifier("backupObjectMapper")` `ObjectMapper`-Bean konfiguriert explizit mit `FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule`, allen Per-Entity-MixIns; pollutet NICHT den Default-Spring-`ObjectMapper` (Isolation gegen unbeabsichtigte Wirkung auf admin REST/AJAX-Pfade)

### EXPORT — Data Export

- [ ] **EXPORT-01**: Admin-Sidebar-Button `Backup` (auf `/admin`) führt zu `GET /admin/backup` Form-Page mit Export- und Import-Aktionen
- [ ] **EXPORT-02**: `POST /admin/backup/export` streamt ZIP via `StreamingResponseBody` mit Header `Content-Disposition: attachment; filename=ctc-backup-{ISO-instant}.zip`; keine Memory-Buffering ganzer Datasets
- [ ] **EXPORT-03**: ZIP enthält `manifest.json` (FIRST entry, fest geforced beim Schreiben) + per-entity-JSON-Files unter `data/` (z. B. `data/seasons.json`, `data/season-phases.json`, `data/matchdays.json`, `data/race-results.json`) + `uploads/`-Ordner-Mirror (nur Files, die von Entities referenziert werden — Logos, CTC-Grafiken, Race-Attachments)
- [ ] **EXPORT-04**: ~22 Per-Entity Jackson MixIns unter `org.ctc.backup.serialization` wenden `@JsonIdentityInfo(generator=PropertyGenerator.class, property="id")` extern an; alle Domain-Entities (Season, SeasonPhase, SeasonPhaseGroup, PhaseTeam, Team, SeasonTeam, Driver, SeasonDriver, PsnAlias, Matchday, Match, Race, RaceLineup, RaceResult, Playoff, PlayoffMatchup, PlayoffSeed, RaceScoring, MatchScoring, RaceSettings, RaceAttachment, FeatureSettings) bleiben unverändert (kein Annotation-Eingriff in `org.ctc.domain.model`)
- [ ] **EXPORT-05**: `BackupExportService` ist `@Transactional(readOnly=true)`, benutzt explizite `@EntityGraph`-Eager-Fetches für alle Collection-Felder die in der Export-Aggregate vorkommen — keine `LazyInitializationException` während Streaming nach Request-Ende
- [ ] **EXPORT-06**: Export-Endpoint ist CSRF-geschützt (Token-Header bei AJAX, Form-Token bei Form-Submission, da Spring Security 7 default für `prod`/`docker`-Profiles)

### IMPORT — Data Import

- [ ] **IMPORT-01**: `POST /admin/backup/import-preview` (multipart) speichert ZIP unter `data/{profile}/backup-staging/upload-{uuid}.zip` (konfigurierbar via `app.backup.staging-dir`), liest Manifest und ZIP-Inhalt, rendert `BackupPreview`-Page mit Per-Tabelle-Wipe+Restore-Counts
- [ ] **IMPORT-02**: Schema-Version-Check VOR jeglichem DB-Schreibzugriff: bei `manifest.schema_version != BackupSchema.SCHEMA_VERSION` HTTP 400 mit klar admin-lesbarer Fehlermeldung („Backup wurde mit Schema-Version X erstellt, aktuelle Version Y, kein Import möglich") über Flash-Message; DB unverändert
- [ ] **IMPORT-03**: Preview-Screen zeigt: Per-Tabelle Anzahl current rows vs imported rows, Anzahl Files in `uploads/`, schema_version match indicator. Preview-State ist STATELESS — Re-Parse beim Execute aus dem staging-Path; kein `@SessionAttributes` (D-15-Pattern aus v1.8)
- [ ] **IMPORT-04**: `POST /admin/backup/import-execute` rendert Confirm-Dialog (vor tatsächlicher Ausführung) mit explizitem Wording „⚠ Diese Aktion löscht ALLE operativen Daten und ersetzt sie durch den Backup-Inhalt. Diese Aktion kann nicht rückgängig gemacht werden."; Pflicht-Bestätigungs-Checkbox („Ich verstehe und bin Admin dieser Liga") + Submit-Button — JS-Confirm-Dialog ZUSÄTZLICH zur Server-Seite
- [ ] **IMPORT-05**: Replace-All-Wipe + Restore in einer einzigen `@Transactional`-Transaktion: (a) `Team.parentTeam = NULL`-Pre-Step (UPDATE statement) entkoppelt Self-FK; (b) FK-Reverse-Order DELETE via native SQL `EntityManager.createNativeQuery("DELETE FROM ...").executeUpdate()` über alle ~22 operativen Tabellen; (c) `em.flush() + em.clear()`; (d) Restore via `JdbcTemplate.batchUpdate` (bypassed `AuditingEntityListener` → `created_at`/`updated_at` aus Export bleiben erhalten); (e) `data_import_audit`-Row geschrieben
- [ ] **IMPORT-06**: Post-Commit (also AUSSERHALB der DB-Transaktion, NACH `tx.commit()`): `uploads/`-Ordner-Restore via stage-and-rename — der bestehende `data/{profile}/uploads/`-Tree wird umbenannt nach `data/.import-backups/<ts>/uploads-old/`, der Import-Tree wird hineinkopiert, dann atomic-rename. Alter Tree verbleibt 24 h als manuelles Recovery-Net
- [ ] **IMPORT-07**: Audit-Log-Eintrag in `data_import_audit` bei Import-Erfolg (timestamp, user via Spring Security `Authentication.getName()`, schema_version, per-table wipe + restore counts als JSON, source_filename aus Multipart, success=true). Bei Fehler success=false + Stack-Trace im SLF4J-Log
- [ ] **IMPORT-08**: Tabelle `data_import_audit` ist EXPLIZIT AUSSERHALB des Export-Scope — ihre Rows überleben jeden Import (operative Metadata über die Liga-Migration, nicht Liga-Daten selbst). Documented in PROJECT.md Decisions

### SECU — Security & Operations

- [ ] **SECU-01**: ZIP-Slip-Defense: jeder ZipEntry-Path wird gegen `uploadDir.toRealPath()` validiert (`startsWith`-Check); absolute Paths und `..` werden abgelehnt; Nutzung der bestehenden `FileStorageService.store()` SECU-02-Defense aus v1.1 (Wiederverwendung statt Duplikat)
- [ ] **SECU-02**: ZipBomb-Defense: per-Entry max 50 MB (uncompressed), total max 500 MB, max 50.000 Entries; bei Überschreitung HTTP 400 + Flash-Message „ZIP exceeds size limits"
- [ ] **SECU-03**: Multipart-Limits in `application.yml` angehoben: `spring.servlet.multipart.max-file-size=100MB`, `spring.servlet.multipart.max-request-size=100MB`, `server.tomcat.max-http-form-post-size=104857600`, `server.tomcat.max-swallow-size=104857600` (Tomcat-Limits überschreiben sonst Spring-Limits stillschweigend)
- [ ] **SECU-04**: `MaxUploadSizeExceededException` in `GlobalExceptionHandler` mit lesbarer Flash-Message gemappt („Backup-Datei überschreitet die Maximalgröße von 100 MB. Bitte komprimieren oder kontaktieren Sie den Admin.")
- [ ] **SECU-05**: Concurrent-Import-Lock: `ImportLockService` mit `ReentrantLock`-Singleton (`@Service` `@Scope("singleton")`); zweiter parallel ausgeführter Import wird mit HTTP 409 + Flash-Message „Ein anderer Import läuft bereits — bitte warten" abgelehnt
- [ ] **SECU-06**: Read-Only-Banner während Import: persistent yellow Banner auf allen Admin-Pages „Import läuft — Schreibzugriff temporär gesperrt"; `@ControllerAdvice`-Filter rejected POSTs auf andere `/admin/**`-Routes mit HTTP 503 während aktivem Import-Lock; Import-Execute-Route selbst ist whitelisted
- [ ] **SECU-07**: Auto-Backup-Before-Import: vor `IMPORT-05`-Wipe wird automatisch ein Export nach `data/.import-backups/<ts>/auto-backup-before-import.zip` geschrieben (synchron, blockend — wenn Export fehlschlägt, wird Import abgebrochen). Recovery-Net falls Import schiefgeht trotz Transaktion

### QUAL — Quality, Testing & Documentation

- [ ] **QUAL-01**: JaCoCo line coverage ≥ 82 % gehalten (entspricht v1.9-Baseline 87.02 %; Komfort-Buffer für neue Code-Pfade vorhanden)
- [ ] **QUAL-02**: Round-Trip-IT (`BackupRoundTripIT`) auf H2 UND MariaDB Profilen: export → wipe → import → assert per-table row counts equal + SHA-256 hash ≥ 3 Sample-Entities byte-equal nach Re-Serialisierung. MariaDB-Profile via `mariadb-migration-smoke.yml`-CI-Workflow analog zu v1.9
- [ ] **QUAL-03**: Live UAT auf lokaler MariaDB mit Saison-2023-Fixture (siehe v1.9 D-22 Pattern): export → DB-Wipe → import → manuelle Verifikation dass Standings, Driver-Ranking, Phase-Breakdowns identisch sind. Documented in `<phase>-HUMAN-UAT.md`
- [ ] **QUAL-04**: Mid-Restore-Failure-Rollback-IT (`BackupImportRollbackIT`): inject Exception nach 50 % der Restore-Schleife → assert dass DB-State exakt der Pre-Import-Zustand ist (alle Original-Rows da, keine Import-Row, audit-log row mit success=false)
- [ ] **QUAL-05**: README-Sektion + WIKI-Page „Backup & Restore" mit Step-by-Step-Anleitung (Export-Workflow, Import-Workflow, Schema-Version-Erklärung, Recovery aus `data/.import-backups/<ts>/`); Screenshots optional. Documented in `docs/site/` und `wiki/`

## Future Requirements

Deferred to v1.11+ milestones. Tracked but explicit out-of-scope für v1.10.

### Per-Saison Selektivität

- **EXPORT-FUT-01**: Per-Saison-Export (User wählt im Admin-UI eine oder mehrere Saisons aus, ZIP enthält nur diese + abhängige Entities)
- **IMPORT-FUT-01**: Per-Saison-Import (Preview erlaubt Auswahl einzelner Saisons aus dem ZIP)

### Verify-Only-Mode

- **EXPORT-FUT-02**: SHA-256 Checksum-File `manifest.sha256` zusätzlich zum Manifest (Integrity-Check vor Import)
- **IMPORT-FUT-02**: Verify-Only-Mode (`POST /admin/backup/import-verify`) — parsed ZIP, prüft Schema-Version + Checksum + Konsistenz, ohne DB zu wipen

### Operational Polish

- **SECU-FUT-01**: Encryption at rest für Export-Files (passwort-geschütztes ZIP mit AES-256)
- **SECU-FUT-02**: Async Export mit Progress-Polling für sehr große Datasets (StreamingResponseBody reicht für ≤ 100 MB)
- **OPS-FUT-01**: Cron-Backup zu konfigurierbarem File-Path

## Out of Scope

Explicitly excluded for v1.10. Documented to prevent scope creep.

| Feature | Reason |
| ------- | ------ |
| Cloud-Backup-Destinations (S3/GCS/Azure) | Single-Admin-Liga, lokales File-System ausreichend; Cloud würde Auth + Provider-Setup nötig machen |
| Scheduled / Cron'd Backups | Manuell-Button-only ist explizite User-Wahl; einfacher zu reasoning, kein Background-Job-Scheduling |
| Multi-Format-Support (CSV/XML/SQL) | Für Round-Trip-Use-Case ist JSON ausreichend; CSV/XML wären nur für Selektive Export-Slices interessant (Future) |
| Merge-Import-Conflict-Policy | Replace-All ist sicherer (User-Choice 2026-05-09); Merge bringt Schema-Drift-Inkonsistenzen |
| Encryption at Rest für Export-Files | Aktuelle Liga-Daten enthalten keine Compliance-relevanten Personendaten (PSN-IDs sind öffentliche IDs); Encryption deferred |
| Async Export mit Progress-Polling | StreamingResponseBody mit ≤100 MB-Cap genügt; UX-Komplexität von Polling-UIs nicht gerechtfertigt |
| Maven Enforcer Custom Rule | `exec-maven-plugin`-grep-gate (PLAT-07) ist niedriger Aufwand für gleiches Ergebnis; Custom Rule wäre eigenes Maven-Modul |
| Forward-/Backward-Compat-Migration zwischen Schema-Versionen | Schema-Version-Strict-Equality reicht für MVP; bei späterer Schema-Drift kann ein Migration-Helper als eigenes Future-Feature kommen |

## Traceability

Which phases cover which requirements. Filled during roadmap creation by `gsd-roadmapper`.

| Requirement | Phase | Status |
| ----------- | ----- | ------ |
| PLAT-01 | Phase 71 | Not started |
| PLAT-02 | Phase 71 | Not started |
| PLAT-03 | Phase 71 | Not started |
| PLAT-04 | Phase 71 | Not started |
| PLAT-05 | Phase 71 | Not started |
| PLAT-06 | Phase 71 | Not started |
| PLAT-07 | Phase 71 | Not started |
| SCHEMA-01 | Phase 72 | Not started |
| SCHEMA-02 | Phase 72 | Not started |
| SCHEMA-03 | Phase 72 | Not started |
| SCHEMA-04 | Phase 72 | Not started |
| EXPORT-01 | Phase 73 | Not started |
| EXPORT-02 | Phase 73 | Not started |
| EXPORT-03 | Phase 73 | Not started |
| EXPORT-04 | Phase 73 | Not started |
| EXPORT-05 | Phase 73 | Not started |
| EXPORT-06 | Phase 73 | Not started |
| IMPORT-01 | Phase 74 | Not started |
| IMPORT-02 | Phase 74 | Not started |
| IMPORT-03 | Phase 74 | Not started |
| IMPORT-04 | Phase 74 | Not started |
| IMPORT-05 | Phase 75 | Not started |
| IMPORT-06 | Phase 75 | Not started |
| IMPORT-07 | Phase 75 | Not started |
| IMPORT-08 | Phase 72 | Not started |
| SECU-01 | Phase 74 | Not started |
| SECU-02 | Phase 74 | Not started |
| SECU-03 | Phase 74 | Not started |
| SECU-04 | Phase 74 | Not started |
| SECU-05 | Phase 76 | Not started |
| SECU-06 | Phase 76 | Not started |
| SECU-07 | Phase 76 | Not started |
| QUAL-01 | Phase 77 | Not started |
| QUAL-02 | Phase 77 | Not started |
| QUAL-03 | Phase 75 | Not started |
| QUAL-04 | Phase 77 | Not started |
| QUAL-05 | Phase 77 | Not started |

**Total: 37 REQ-IDs** (PLAT × 7, SCHEMA × 4, EXPORT × 6, IMPORT × 8, SECU × 7, QUAL × 5)

### Coverage by Phase

| Phase | Requirements | Count |
| ----- | ------------ | ----- |
| Phase 71 | PLAT-01, PLAT-02, PLAT-03, PLAT-04, PLAT-05, PLAT-06, PLAT-07 | 7 |
| Phase 72 | SCHEMA-01, SCHEMA-02, SCHEMA-03, SCHEMA-04, IMPORT-08 | 5 |
| Phase 73 | EXPORT-01, EXPORT-02, EXPORT-03, EXPORT-04, EXPORT-05, EXPORT-06 | 6 |
| Phase 74 | IMPORT-01, IMPORT-02, IMPORT-03, IMPORT-04, SECU-01, SECU-02, SECU-03, SECU-04 | 8 |
| Phase 75 | IMPORT-05, IMPORT-06, IMPORT-07, QUAL-03 | 4 |
| Phase 76 | SECU-05, SECU-06, SECU-07 | 3 |
| Phase 77 | QUAL-01, QUAL-02, QUAL-04, QUAL-05 | 4 |
| **Total** | | **37** |
