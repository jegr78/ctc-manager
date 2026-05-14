# CTC Manager — Backup Import Operational Runbook

Audience: on-call operators of the single-admin CTC Manager liga. This runbook covers
recovery flows and operational guarantees around `POST /admin/backup/import-execute`,
the destructive replace-all import. The defenses are layered: Phase 75 wraps the entire
restore in a single JPA transaction so a mid-restore failure rolls back to pre-import
state; Phase 76 adds three concentric rings on top of that — a singleton import lock,
a read-only banner with HTTP 503 on non-whitelisted admin POSTs, and a synchronous
pre-import auto-backup ZIP. This document is the single source of truth for operator
recovery; the in-app strings here MUST match the runtime UI verbatim.

## 1. Recovery from auto-backup

A successful or failed import attempt leaves a recovery ZIP at:

```
data/.import-backups/<ts>/auto-backup-before-import.zip
```

`<ts>` is the ISO instant of the import attempt with colons replaced by hyphens, e.g.
`2026-05-14T17-30-42Z`. The ZIP is written BEFORE the wipe, so it captures the
pre-import database state regardless of which step the import later failed in.

**When the app is UP** (DB is mutated, but you want to revert to the pre-import state):

1. Identify the `<ts>` directory of the most recent import attempt — `ls -lt data/.import-backups/ | head`.
2. Navigate to `/admin/backup` in the browser.
3. Click `Import Backup`, upload `data/.import-backups/<ts>/auto-backup-before-import.zip`.
4. Confirm the preview screen, then click `Execute Import`. The replace-all flow runs
   again, this time restoring the snapshot you just uploaded.

**When the app is DOWN** (process crashed mid-import, you want a clean recovery before
restarting):

1. Restart the JVM (`./mvnw spring-boot:run …` or your systemd unit). The in-memory
   import lock dies with the process, so the next admin GET to `/admin/backup` will be
   unblocked.
2. Follow the "app UP" path above. The ZIP's `data/*.json` files are JSON, not raw SQL,
   so the round-trip via `/admin/backup/import-execute` is the supported recovery path —
   do not attempt `mariadb-import < anything.sql` against the live DB without a separate
   `mysqldump`. A future end-to-end disaster-recovery procedure is owned by Phase 77.

## 2. 24h retention semantics

Both Phase-75 and Phase-76 artifacts live under the same `<ts>` directory:

```
data/.import-backups/<ts>/
  auto-backup-before-import.zip    # Phase 76 — pre-wipe DB snapshot (recovery)
  uploads-old/                     # Phase 75 — previous uploads tree (recovery)
  uploads-new/                     # transient; cleaned up automatically (catch-block + AFTER_COMMIT)
```

Both are retained for ~24 hours by convention. The operator is responsible for cleanup
after that window; the application never deletes them automatically. Example commands:

```bash
# POSIX (macOS / Linux):
find data/.import-backups -mindepth 1 -maxdepth 1 -type d -mtime +1 -exec rm -rf {} +
```

```powershell
# Windows PowerShell (run from inside data/.import-backups/):
Get-ChildItem -Directory | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-1) } | Remove-Item -Recurse -Force
```

Notes:
- On Windows the partial-ZIP cleanup on auto-backup failure is best-effort; file-locking
  can prevent `Files.deleteIfExists` from removing a half-written ZIP. The 24h sweep
  above will catch those leftovers.
- The `uploads-new/` sibling is internal and is cleaned up by the import service itself
  (catch-block finally + AFTER_COMMIT listener). It should not be visible after a
  successful import; if it lingers, it is safe to delete alongside the rest of `<ts>`.

## 3. Audit-id query SQL

Every import attempt — successful, mid-restore-failed, or pre-import-auto-backup-failed —
records exactly one row in the `data_import_audit` table via a `REQUIRES_NEW`
propagation so the row survives the outer transaction rollback.

The audit UUID appears in the user-facing flash message after a failed import. To
investigate:

```sql
SELECT id, executed_at, executed_by, schema_version, success,
       source_filename,
       LEFT(table_counts_wiped, 100)    AS wiped_preview,
       LEFT(table_counts_restored, 100) AS restored_preview
FROM data_import_audit
WHERE id = '<uuid-from-flash-message>';
```

Result interpretation:

| `success` | `table_counts_wiped` | `table_counts_restored` | Meaning |
|-----------|----------------------|-------------------------|---------|
| `TRUE`    | non-empty JSON       | non-empty JSON          | Successful import. The numbers report rows wiped and rows restored per entity. |
| `FALSE`   | `{}`                 | `{}`                    | Phase 76 SECU-07 — pre-import auto-backup failed BEFORE any DB mutation. The DB is unchanged. The auto-backup ZIP may be partial or missing. |
| `FALSE`   | non-empty JSON       | (possibly non-empty)    | Phase 75 mid-restore-failure path. The outer `@Transactional` rolled back, so the live DB is back to pre-import state. The wiped/restored counts are the in-flight snapshot at the point of failure. |

Column shape notes:
- `table_counts_wiped` and `table_counts_restored` are `LONGTEXT` (Phase 72 schema
  decision) holding canonical JSON objects of the form `{"seasons": 12, "drivers": 240, …}`.
- `executed_at` is set by the audit service writer, not by `AuditingEntityListener`,
  because Phase 75's replace-all path runs with JPA auditing bypassed.
- `executed_by` is `unknown` in dev profile (no Spring Security); in prod/docker it
  carries the authenticated principal name.

## 4. Concurrent-import behavior

**Symptom:** Clicking `Execute Import` returns to `/admin/backup` with a red flash
error reading `Another import is already running — please wait.` Open browser DevTools →
Network and the `POST /admin/backup/import-execute` request shows HTTP **409 Conflict**
(not 302, not 503).

**Meaning:** Another browser tab, another operator, or a background re-import attempt
is already holding the in-memory `ImportLockService` mutex. Because the application
runs single-JVM (single-host deployment), the lock state is process-local: there is no
distributed coordination, no Redis, no DB row. The lock is held for the entire duration
of the import's outer `@Transactional` method, plus the synchronous `AFTER_COMMIT`
listener for the uploads-tree swap.

**Resolution:**

1. Wait. Most imports finish in under 30 seconds. Refresh `/admin/backup` and re-check
   the yellow banner (Section 5).
2. If the yellow banner is gone, retry the import.
3. If the banner has been visible for more than ~30 minutes with no log activity, the
   JVM is wedged or the lock is genuinely stuck. Restart the JVM — the in-memory lock
   dies with the process.

## 5. Read-only state during imports

**Symptom:** A yellow banner reading `Backup import in progress — write access is temporarily locked.` renders at the top of every page under `/admin/**`. Submitting any admin form (POST/PUT/PATCH/DELETE outside the whitelisted `/admin/backup/import-execute` endpoint) returns a minimal HTML page with the same English wording and HTTP **503 Service Unavailable**.

**Meaning:** An import is mid-execute. The Phase-76 Ring 2 `ImportLockedWriteRejector`
interceptor enforces the read-only state at the request boundary; the banner is the
visible signal. Read-only GETs to `/admin/**` are unaffected — operators can navigate
freely to check state, but they cannot mutate it.

Whitelist exceptions:
- `POST /admin/backup/import-execute` itself bypasses the interceptor (otherwise the
  in-flight import would block itself). The endpoint is already gated by the
  `ImportLockService.tryLock()` 409 response from Ring 1, so a second concurrent
  attempt still fails fast with 409 + the locked flash.
- Non-`/admin/**` paths (the public site, `/actuator/*`, static resources) are
  unaffected.

**Resolution:** Wait. The banner disappears automatically when the import finishes,
whether the path was success, mid-restore-failure (Phase 75 rollback), or
pre-import-auto-backup-failure (Phase 76 SECU-07). The banner is the trustworthy
signal — once it is gone, admin writes work again. If the page-render cache is stale,
hard-refresh (Ctrl-F5) to drop any cached banner HTML.

---

Phase 76 of v1.10 — see ROADMAP.md for milestone scope.
