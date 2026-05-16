# Phase 74: Backup Import Preview + ZIP Hardening + Multipart Config + Schema-Version Gate - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-12
**Phase:** 74-backup-import-preview-zip-hardening-multipart-config-schema-
**Areas discussed:** UI Language, Preview Layout + CTAs, Confirm-Dialog → Execute-Seam (Phase 74↔75), Staging-File Lebenszyklus + Cleanup

---

## UI Language

| Option | Description | Selected |
|--------|-------------|----------|
| Englisch, REQUIREMENTS-Strings als Spec-Beispiele | All UI text English. German strings in REQUIREMENTS treated as "spec examples of the meaning". CLAUDE.md `feedback_ui_language` wins. Override documented in CONTEXT.md. | ✓ |
| Deutsche Texte 1:1 übernehmen (Ausnahme für Backup) | Keep REQUIREMENTS German strings verbatim. Exception to English-only rule. | |
| Englisch + i18n-Hook vorbereiten | English strings via `messages.properties`. Overkill — app has no i18n yet. | |

**User's choice:** English with REQUIREMENTS treated as examples.

### Follow-up: Final string wording

| Option | Description | Selected |
|--------|-------------|----------|
| Diese drei Strings locken (formell) | "Backup file exceeds the maximum size of 100 MB…" / "Backup was created with schema version…" / "I understand that this action will delete all operational data…" | |
| Knapper, direkter Ton | "Upload too large — maximum is 100 MB." / "Schema version mismatch: backup={actual}, expected={current}. Cannot import." / "I am an admin and I understand all operational data will be deleted." / "Backup archive failed safety checks (size or path) and was rejected." | ✓ |
| Spec-Style, ausführlicher | Longer, formal explanations of each error. | |

**User's choice:** Terse, direct tone. Locked in CONTEXT.md D-02.

**Notes:** This resolves a genuine conflict between REQUIREMENTS.md (quotes German verbatim) and CLAUDE.md `feedback_ui_language.md` (English-only, no exceptions). The CLAUDE.md feedback is the authority because it post-dates REQUIREMENTS.md and was explicitly added as a user preference.

---

## Preview Layout + CTAs

| Option | Description | Selected |
|--------|-------------|----------|
| Klassische Diff-Tabelle + Schema-Banner | 4-column table (Table / Current / Imported / Delta) for 24 entities. Green schema-match banner top. | |
| Kompakte Cards pro Entität | 24 small cards in a grid, each showing entity name + `X → Y` + colored delta pill (red = data loss, green = grow). Schema-match top header pill. | ✓ |
| Liste mit Group-Headers (logische Gruppen) | Table grouped by domain (Masterdata, Seasons, Teams, Drivers, Races, Matchups, Playoffs). | |

**User's choice:** Compact cards per entity.

**Notes:** Card grid with red/green/neutral delta pills. Color semantics matter — red = imported < current = data loss visible at a glance. Dedicated `admin/backup-preview.html` template; not inline into the landing page.

---

## Confirm-Dialog → Execute-Seam (Phase 74↔75)

| Option | Description | Selected |
|--------|-------------|----------|
| Stub-Endpoint mit 501-Flash | `POST /admin/backup/import-execute` exists in Phase 74. Re-parses staging, re-validates (schema + path + size), redirects with Flash "Validation succeeded. Import execution will be enabled in Phase 75." Staging file persists for Phase 75. | ✓ |
| Confirm-Button disabled mit Phase-75-Hinweis | Confirm page renders, but final submit is disabled with tooltip. No execute endpoint in Phase 74. Cleaner cut but validation chain not testable end-to-end. | |
| Execute mit no-op + Audit-Vorbereitung | Writes `data_import_audit` row with `success=false`, redirects. Mixes Phase boundary. | |

**User's choice:** Stub endpoint with 501-style Flash, full validation chain runs.

**Notes:** Validation chain (path traversal + size limits + schema gate) is exercised end-to-end in Phase 74 — Phase 75 inherits a battle-tested validator and only adds the wipe + restore transaction. Defense-in-depth: schema re-check at execute time even though preview already validated (D-09).

---

## Staging-File Lebenszyklus + Cleanup

| Option | Description | Selected |
|--------|-------------|----------|
| Reject sofort löschen + Scheduled 24h-Sweep | Reject path deletes immediately. `@Scheduled(fixedDelayString="PT1H")` cleanup job deletes files older than 24h. Profile-aware staging dir. | |
| Startup-Cleanup + Reject sofort löschen | Reject deletes immediately. App startup clears the entire `backup-staging/` directory via `ApplicationReadyEvent` listener. No scheduled jobs. | ✓ |
| Keep-on-Reject für Forensik + Manual-Cleanup-Admin-Page | Reject moves to `rejected/<uuid>-<reason>.zip`. New admin page for manual cleanup. Out of scope for Phase 74. | |

**User's choice:** Startup-cleanup + immediate reject-delete.

**Notes:** Simple and sufficient for an admin-only feature with 1-2 uploads/week. If leaks ever appear in practice, a scheduled sweep is a one-line v1.11 add. Profile-aware path: `data/${spring.profiles.active}/backup-staging`, overridable via `app.backup.staging-dir`.

---

## Claude's Discretion

- Exact human-label mapping for `EntityRowCount.humanLabel` ("season_phases" → "Season Phases").
- Card grid CSS — reuse `admin.css` classes or add `backup-preview-*` classes.
- Whether `PathTraversalGuard` is its own helper class or inlined with a comment cross-referencing `FileStorageService:65`.
- `BackupArchiveException` reason-code enum design.
- Whether `BackupImportLimits` is a dedicated constants holder or constants live inline.
- Exact flash-key conventions (`successMessage` / `errorMessage` per CLAUDE.md).

## Deferred Ideas

- Per-Saison Import-Selectivity → v1.11 (`IMPORT-FUT-01`).
- SHA-256 checksum verification (`manifest.sha256` + verify-only mode) → v1.11 (`IMPORT-FUT-02`).
- Admin staging-files browser page (`/admin/backup/staging`) → considered, deferred.
- Scheduled `@Scheduled` cleanup job → considered, deferred (startup-sweep is enough).
- i18n via `messages.properties` → considered for the language conflict, deferred (app has no i18n).
- `BackupImportRollbackIT`, Live MariaDB UAT → Phase 75 deliverables.
- Import lock + read-only banner + auto-export-before-import → Phase 76 (SECU-05..07).
- README + WIKI documentation → Phase 77 (QUAL-01..05).
- Audit-log viewer UI (`/admin/backup/history`) → v1.11 nice-to-have.
