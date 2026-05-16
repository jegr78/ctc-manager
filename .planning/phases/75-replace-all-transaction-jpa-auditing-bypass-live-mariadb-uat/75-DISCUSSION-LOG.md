# Phase 75: Replace-All Transaction + JPA Auditing Bypass + Live MariaDB UAT - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-14
**Phase:** 75-replace-all-transaction-jpa-auditing-bypass-live-mariadb-uat
**Areas discussed:** Audit-Row TX-Scope, Restore-SQL-Strategie, Uploads-Restore Failure-Recovery, HUMAN-UAT Scope-Tiefe

---

## Audit-Row TX-Scope

| Option | Description | Selected |
|--------|-------------|----------|
| REQUIRES_NEW @Transactional | Separate `DataImportAuditService.recordResult(...)` method with `@Transactional(propagation=REQUIRES_NEW)`, Spring-AOP-standard. | ✓ |
| Manual TransactionTemplate | `TransactionTemplate` with `PROPAGATION_REQUIRES_NEW` instantiated in service code, `execute(...)` call. | |
| Try-with-resources JdbcTemplate ohne JPA | Audit komplett über `JdbcTemplate` schreiben (eigene Connection aus DataSource). | |

**User's choice:** REQUIRES_NEW @Transactional
**Notes:** ROADMAP-Goal-3 explizit verlangt `success=false`-Audit-Row beim Mid-Restore-Failure → erzwingt separate TX. Spring-AOP-Pattern wurde dem manuellen TransactionTemplate vorgezogen (kleinerer Test-Footprint, deklarativer). DataImportAuditService landet bei `org.ctc.backup.audit.DataImportAuditService` (Companion zur existierenden `DataImportAuditRepository` aus Phase 72). Captured in CONTEXT D-01..D-03.

---

## Restore-SQL-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Reflection aus JPA-Metamodel + Jackson-Tree | DRY, schema-drift-resistent. Komplexer JsonNode→JDBC-Typ-Coercion-Layer für LocalDateTime/UUID/BigDecimal/enums. | |
| 24 hand-geschriebene SQL-Templates | Pro Entity eine `EntityRestorer`-Klasse mit fixem `INSERT ... VALUES (?,?,...)` + typisierter Setter. Lesbar, debuggable. | ✓ |
| Hybrid: Generic + Entity-Override-Hook | Reflection-default + `EntityRestorer`-Hook für Sonderfälle (z.B. Team-2-Pass). 95% Reuse, 5% Spezialisierung. | |

**User's choice:** 24 hand-geschriebene SQL-Templates
**Notes:** Reflection-Variante hätte den `Team.parentTeam`-2-Pass-NULL-then-UPDATE schwierig modelliert, plus Jackson-Tree→JDBC-Coercion-Layer wäre ein eigenes Subsystem. Hand-Templates sind in Code-Review explizit sichtbar, Schema-Drift-Kosten (24 File-Edits bei neuer Spalte) sind akzeptabel für ein Feature das einmal pro Milestone angefasst wird. `EntityRestorer` Interface mit 24 Implementierungen, Batch-Size 500, JsonNode→JDBC-Coercion inline in jedem Setter. Captured in CONTEXT D-04..D-08.

---

## Uploads-Restore Failure-Recovery

| Option | Description | Selected |
|--------|-------------|----------|
| Best-Effort Auto-Revert + Loud Flash | Bei Step-2-Fail: Step-1 rückgängig + loud `errorMessage`. Audit success=true (DB-Sicht). | |
| Preflight + Fail-Before-Commit | Vor `tx.commit()`: Permission/Disk-Space-Check, Race-Condition-Restrisiko bleibt. | |
| Loud Failure ohne Auto-Revert | DB ge-changed, Filesystem-Zwischenzustand. Manueller Recovery-Workflow. | |
| Atomic-Move-Triple mit Manual-Cleanup | Strikte 3-Schritt-Sequenz (move uploads→uploads-old; move staged→uploads; audit-row), Best-Effort-Revert nur auf Step-2-Fail, kein Preflight. | ✓ |

**User's choice:** Atomic-Move-Triple mit Manual-Cleanup
**Notes:** 3-Schritt-Sequenz mit step-by-step `info`-Logging. Bei Step-2-Fail (staged → uploads): Best-Effort-Move-Revert von Step-1 (uploads-old → uploads). Bei Step-3-Fail (Audit): Files sind safe, nur Loud-Log + Soft-Fail-Flash. `<ts>` ist `Instant.now().truncatedTo(SECONDS).toString().replace(":","-")` für Windows-Pathability. Captured in CONTEXT D-09..D-12.

---

## HUMAN-UAT Scope-Tiefe

| Option | Description | Selected |
|--------|-------------|----------|
| Screenshot-Vergleich Checklist | `75-HUMAN-UAT.md` mit 5-7 vor-/nach-Screenshot-Pairs auf Public-Site. Manuelle Sicht-Prüfung. | |
| Sicht-Prüfung-Only | Kurzcheckliste ohne Screenshots als Artefakte. | |
| Auto-UAT via playwright-cli | `/gsd-auto-uat` Skill, automatische Screenshots + Byte-Compare. | |
| Screenshot-Checklist + 1 IT-Smoke | Option 1 + `BackupImportMariaDbSmokeIT` im `mariadb-migration-smoke.yml`-CI-Workflow (Per-Table-Row-Count-Match, kein Hash-Vergleich). | ✓ |

**User's choice:** Screenshot-Checklist + 1 IT-Smoke
**Notes:** Belt-and-suspenders. Human-Layer: 6 Screenshot-Pairs (Standings Group-A/B, Driver-Ranking, Playoff-Bracket, Team-Phase-Breakdown, Driver-Phase-Breakdown) auf Saison 2023, Artefakte in `.screenshots/75/before/` und `.screenshots/75/after/`, PASS/FAIL in `75-HUMAN-UAT.md`. CI-Layer: `BackupImportMariaDbSmokeIT` (Failsafe IT, `@ActiveProfiles("local")` + Testcontainers-MariaDB) verifiziert Row-Count-Match. Hash-Vergleich bleibt explizit OUT-OF-SCOPE für Phase 75 — das macht QUAL-02 `BackupRoundTripIT` in Phase 77. Captured in CONTEXT D-16.

---

## Claude's Discretion

(Bereiche in denen der Planner Implementation-Wahl trifft — keine User-Konfrontation nötig):

- `BackupSchema.getWipeOrder()` — neue public Methode neben `getExportOrder()` ODER inline `Lists.reverse(...)` im Service. Kein observable Unterschied.
- `uploads-new/`-Cleanup auf Rollback: Delete in `finally{}` (Default-Empfehlung) ODER für Forensik behalten.
- Spring-6.x-Idiom für Post-Commit-Move-Triple: `@TransactionalEventListener(AFTER_COMMIT)` vs `TransactionSynchronizationManager.registerSynchronization(...)` vs manuell.
- IT-Class-Location: `org.ctc.backup.it` vs `org.ctc.backup` (orientiert sich an `.planning/codebase/TESTING.md`).
- `EntityRestorer`-Interface-Modifier: plain `interface` (Default) vs `sealed interface` mit 24 Permits (over-engineering).
- `BatchPreparedStatementSetter` (single) vs `ParameterizedPreparedStatementSetter` + Auto-Chunking — D-07's Batch-Size 500 mapped direkt auf Auto-Chunking-Flavor.
- `UuidPacker`-Helper: existiert evtl. schon in `org.ctc.domain.model` (Planner grept) — wenn nicht, neue Klasse `org.ctc.backup.restore.UuidPacker`.
- `UploadsRestoreException`-GlobalExceptionHandler-Mapping: nur nötig falls Service-internes Catch+Flash nicht greift (defensive Default).

## Deferred Ideas

- `/admin/backup/history` Audit-Viewer-Page — v1.11+ (Phase 74 hat schon deferred; Phase 75 surfaced Audit-ID im Error-Flash für SQL-Drill-Down).
- `SET FOREIGN_KEY_CHECKS = 0` während Wipe — Vereinfachung erwogen, abgelehnt: MariaDB-only, würde FK-Bugs in Export-Daten maskieren.
- `@Scheduled`-Cleanup von `data/.import-backups/<ts>/uploads-old/` jenseits 24h — operator-driven (`rm -rf` in cron); v1.11 falls operational nötig.
- SHA-256-Hash-Byte-Equality auf Sample-Entities — Phase 77 QUAL-02 `BackupRoundTripIT`. Phase 75 stoppt bei Row-Count-Parität.
- README + WIKI "Backup & Restore" Sektion — Phase 77 QUAL-05.
- `ImportLockService` + Read-Only-Banner + Auto-Backup-Before-Import — Phase 76 SECU-05..07.
- Reflection-basierter `EntityRestorer`-Generator — deferred indefinitely; bei künftiger Entity-Explosion v2.0-Refactor.
