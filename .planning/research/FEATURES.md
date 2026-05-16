# Feature Research

**Domain:** Spring Boot Admin Backup/Restore + Platform Upgrade (CTC Manager v1.10)
**Researched:** 2026-05-09
**Confidence:** HIGH (product features, table-stakes patterns, anti-features) / MEDIUM (Spring Boot 4.0.6 release contents, since the official announcement enumerates only CVEs and totals, not individual changes) / HIGH (Thymeleaf 3.2 fragment-parameter incompatibility — the project itself reproduced and documented the failure mode in v1.9)

---

## Scope Note

Two distinct feature clusters in this milestone, treated separately because they share no code paths:

1. **Cluster A — Spring Boot 4.0.6 Platform Upgrade** (incl. preventive Thymeleaf-3.2 template audit)
2. **Cluster B — Admin Data Export/Import** (ZIP backup, replace-all restore)

Cluster A is technical hygiene — its "features" are dependency bumps and a build-time guard. Cluster B is a real new admin function with UX surface area. The table-stakes / differentiator analysis applies primarily to Cluster B; Cluster A is presented in a leaner format at the bottom.

---

## Cluster B — Admin Data Export/Import

### Table Stakes (Users Expect These)

Features the league operator (single admin, technically savvy enough to use Spring profiles and `mysqldump`) will assume exist on day one. Missing any of these makes the feature feel half-built and unsafe to use against production data.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Single-button export to ZIP | "Backup" means one click in every admin tool the user has touched (GitLab, Discourse, Immich, phpMyAdmin "Export") | LOW | `GET /admin/backup/export` streams `application/zip` via `ZipOutputStream` directly into the response — no temp file on disk |
| ZIP packs `data.json` + `uploads/` directory | League logos, CTC graphic templates, race attachments are referenced by filename in DB rows. JSON without uploads = orphan FK paths after restore | LOW-MEDIUM | Walk `app.upload-dir`, add each file to the ZIP under a stable prefix (`uploads/...`); JSON references stay relative |
| `schema_version` + `app_version` + `export_date` header in `data.json` | Every backup tool the user has met (Discourse, GitLab, Postgres `pg_dump`) writes a header. Without it, the ZIP is opaque and unsafe to ingest | LOW | Top-level object before the entity arrays: `{"schemaVersion": 7, "appVersion": "1.10.0", "exportDate": "2026-05-15T12:34:56Z", "entities": {...}}` |
| Schema-version refusal on import | The whole point of the header. Importing a v1.8-era ZIP into a post-V6 schema would either silently lose data or crash mid-restore | LOW | Compare ZIP `schemaVersion` against current Flyway version (`MAX(installed_rank)` or hardcoded constant matching the latest `V*__*.sql`). Mismatch → 400 with explicit error page, never a silent upgrade |
| Preview screen before destructive import | This is the project's own playbook (`CsvImportController`, `DriverSheetImportController`, `gt7-sync-preview.html` all use POST → preview → confirm). Skipping it would be inconsistent with the rest of the admin UI | MEDIUM | After upload, parse the ZIP into memory, render a table of "rows that will be wiped per table" + "rows that will be restored per table". Confirm button POSTs the parsed bundle (or a server-side staged path) to the executor |
| Explicit destructive-action confirmation | Replace-all wipes operative data. JS `confirm()` is the project's existing pattern (`driver-merge.html`, scoring delete buttons). A type-to-confirm input ("type DELETE to proceed") is overkill for a single-admin tool, but a labelled checkbox + confirm dialog is non-negotiable | LOW | Mirror the `DriverMergeController` pattern: form action with hidden token + JS `confirm("This will delete ALL operative data. Continue?")` |
| Atomic transaction on import | If restore fails halfway, half-wiped DB is worse than no restore. Spring `@Transactional` on the executor with explicit rollback for any `IOException`/`DataAccessException`/`ConstraintViolationException` — same pattern as `DriverSheetImportService.execute()` (Phase 70 GAP-70-01 lesson, see PROJECT.md decision row) | MEDIUM | Single `@Transactional` boundary on the import service method; uploads dir written in a staging folder and atomically renamed/replaced only after JSON commit succeeds. Failure = staging folder discarded, DB rolled back |
| Audit log entry per import (who / when / row counts) | "What did the last restore do?" is the first question after any restore. The user explicitly asked for this. Mirrors `BackupRestoreLog` patterns common in admin tools | LOW-MEDIUM | New `import_audit_log` table or simpler: append to `data/{profile}/import-audit.log` (text, one line per import). Captures `user`, `timestamp`, `bundle filename`, `wiped {table → count}`, `restored {table → count}` |
| Filename convention with timestamp | Operator will accumulate ZIP files. `ctc-backup-2026-05-09-143022.zip` is universally readable; `backup.zip` is not | LOW | `Content-Disposition: attachment; filename="ctc-backup-{ISO-instant}.zip"` |
| File size limit on upload | Spring Boot's default 1 MB/10 MB caps will reject typical backups (logos alone may be 5-50 MB). Without explicit configuration, users will hit cryptic Tomcat errors | LOW | `spring.servlet.multipart.max-file-size=200MB` + `max-request-size=200MB` in `application.yml`. Document the cap |
| Clear error messages on malformed ZIP | The user is sysadmin-savvy, but if the parser explodes on a ZIP that was edited by hand, "Internal Server Error" wastes time. Specific errors: missing `data.json`, malformed JSON, missing entity, FK reference in JSON to row that's not in JSON | LOW-MEDIUM | Specific exceptions → flash messages, never a stack trace. Reuse `GlobalExceptionHandler` pattern |
| MIME / extension validation on upload | Same hardening posture as `RaceAttachmentService` (Phase 28). `.zip` extension + content-type sniff before parsing | LOW | Reject non-ZIP uploads with a friendly message, not a parser exception |

### Differentiators (Competitive Advantage)

Features that go beyond table stakes. Each is a real candidate but needs to be weighed against the v1.10 scope.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| SHA-256 checksum file inside the ZIP | Detects tampering / corrupted download mid-transfer. Operator can `sha256sum` the file before upload | LOW | `data.json.sha256` next to `data.json`; verified before parse begins. Cheap, and the user is technically savvy enough to appreciate it |
| Diff preview ("3 seasons would be wiped, 4 would be restored, net change: +1") | More informative than raw row counts. Helps spot accidents like restoring a stale ZIP onto live data | MEDIUM | Compare current DB row counts vs. ZIP row counts per entity in the preview screen. The data is already loaded for the preview, so the cost is mostly UX |
| Selective import (per-season picker) | The user explicitly mentioned this as a future-milestone feature. Big win for "merge production data into dev" scenarios where dev has scratch data you want to keep | HIGH | Requires per-entity scoping logic and a deletion strategy that respects the Season → Phase → Group → Matchday cascade. Out of v1.10 scope per user (deferred), but worth flagging as the obvious next step |
| Background async processing with progress polling | Discourse-style: `/admin/backups` shows a progress log as the dump runs. Useful for large datasets | HIGH | For the current data volume (≤100 races, ≤2 seasons active, uploads <200 MB) the export/import is sub-second to a few seconds — sync is fine. Becomes a differentiator only at 10× scale. **Recommend defer** |
| Integrity-only "verify" mode (parse + validate, no write) | Lets the operator sanity-check a ZIP against the current schema before committing. Removes one source of restore-time anxiety | LOW-MEDIUM | Same pipeline as preview, but stops before the confirm step. Could be a checkbox on the upload form ("Validate only") |
| Compressed JSON (`data.json.gz` inside the ZIP) | Reduces ZIP size further. JSON compresses 5-10× | LOW | Marginal win — ZIP already compresses JSON. Low priority |
| Export-only mode without uploads | "I just want the data, not the 50 MB of logos" | LOW | Query parameter `?includeUploads=false`. Easy add if asked |
| Two-step delete confirmation (type-to-confirm) | Stops muscle-memory clicks on `Confirm`. GitLab uses this pattern for project deletion | LOW | Overkill for a single-admin tool. Phase out unless the user requests it |

### Anti-Features (Commonly Requested, Often Problematic)

Features that sound reasonable but fail the cost/benefit test for v1.10. Documented here so they don't drift in later.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Cloud destinations (S3, GCS, Azure Blob) | "Backups should be off-host" | Adds AWS SDK / GCS SDK dependency, IAM credential management, retry/timeout config, network egress — all for a use case the user can solve with `scp` + `cron` outside the app | Local download from admin UI; user mirrors to cloud out-of-band if desired |
| Scheduled / cron'd backups | "What if I forget to back up before a risky migration?" | Adds Quartz / `@Scheduled`, error-handling for headless failure, retention policy UI, disk-fill protection. The user explicitly wants manual button only | Document the manual flow + add a banner before risky operations later (out-of-scope reminder) |
| Multi-format support (CSV, XML, SQL dump) | "Maybe I want the data in Excel" | Triples the serialization surface. CSV can't represent the entity graph, XML duplicates JSON, SQL dump is `mysqldump`'s job. The user explicitly wants JSON-only | JSON-only; if Excel needed, write a one-off report later |
| Date-range filtered export ("only races since 2024") | Sounds like "smaller backup files" | Cuts FK paths — references to teams/seasons outside the range break referential integrity on restore. The full-or-full-per-season distinction is the right boundary | Full export only in v1.10; per-season selector deferred to a later milestone |
| Merge import (preserve existing rows, add new ones) | "Restore is too destructive" | Schema drift between source and target = inconsistent state, silent data loss on conflicts, requires per-entity merge rules. The user explicitly chose replace-all in PROJECT.md, citing this exact rationale | Replace-all only; document why merge isn't supported |
| Encryption at rest for the ZIP | "What if someone steals the file?" | Single-admin app with HTTP Basic Auth and HTTPS in front — adding ZIP encryption duplicates transport security. Key management without a UI is worse than no encryption | Trust the host filesystem + transport TLS. If the user is worried, recommend storing the ZIP inside an encrypted disk image |
| User-account / multi-tenant scoped exports | "What if we add multi-user later?" | The PROJECT.md `Out of Scope` row "Form Login / User Management — over-engineered for admin tool" already settled this. No multi-tenancy means no per-user backup scope | Single global backup. Revisit only if user model changes |
| In-app backup-history viewer with download links | "Show me my last 10 backups" | Requires backup-storage policy, retention sweeper, file-tracking table, deletion UI — significant scope for marginal value over `ls /var/ctc/backups/` | Out of v1.10. Reconsider if cron'd backups are ever added (they aren't) |
| Import progress bar with WebSocket / SSE | "Long ops need feedback" | At current data volume the import is sub-second. WebSocket plumbing would dwarf the feature itself | Block the request; if the dataset ever grows enough to need this, we'll hear about it from the user first |

---

## Cluster A — Spring Boot 4.0.6 Upgrade & Template Audit

### Table Stakes

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| `pom.xml` parent bump 4.0.5 → 4.0.6 | The reason the milestone exists. Picks up 65 bug fixes + 8 CVE patches per [Spring Boot 4.0.6 announcement](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/) (TLS hostname verification fixes for Elasticsearch/RabbitMQ/Cassandra, DevTools timing-attack fix, temp-dir ownership verification, PRNG strength improvement, security-filter-chain rule adjustments, PID file symlink-following prevention) | LOW | One-line `pom.xml` change + `./mvnw verify -Pe2e` to confirm |
| Fix the 3 known fragment-parameter ternaries | The v1.9 milestone reverted a Spring Boot bump for exactly this reason — `match-scoring-form`, `race-scoring-form`, `season-phase-form` all line 3, all `th:replace="...layout(${cond ? 'A' : 'B'}, ...)"` shape. The v1.10 milestone exists to do this properly | LOW (per template) | Move ternary into a controller-side `pageTitle` model attribute; `th:replace="...layout(${pageTitle}, ...)"`. Pattern documented in PROJECT.md milestone goal |
| Audit all ~78 templates (62 admin + 16 site) for the same incompatibility shape | "Vorsorglich" = preventive. The user got bitten once and wants to find the rest before the next reproducer | LOW-MEDIUM | One-time grep + manual review. ~80 templates is ~30 min review. Targets: `th:replace`, `th:insert`, fragment-parameter expressions with `?:` ternary or method calls on JDK classes |
| `./mvnw verify -Pe2e` green on 4.0.6 | Standard regression bar — Surefire + Failsafe + Playwright E2E all green | LOW | Same gate as every prior milestone |
| JaCoCo ≥ 82 % held | Standard coverage gate from `pom.xml` | LOW | Already at 87.02 % per v1.9 close — buffer is comfortable |

### Differentiators (Optional Hardening)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Build-time guard preventing fragment-parameter ternaries | Stops the regression class from coming back. Without a guard, the next contributor re-introduces the pattern in months | MEDIUM | **Recommended approach: simple regex grep gate via Maven `exec-maven-plugin`** running a shell/Groovy script that fails the build if `templates/**/*.html` contains a `th:(replace|insert)\s*=.*\?.*:.*` pattern at the fragment-call site. Lighter than a full Maven Enforcer custom rule (which requires a separate Maven module, see [Maven Enforcer custom rule docs](https://maven.apache.org/enforcer/enforcer-api/writing-a-custom-rule.html)) |
| Maven Enforcer custom rule | More structured / discoverable than a shell script | HIGH | Requires its own module, packaging, and rule registration. Overkill for a single-pattern check. **Recommend the lighter shell-based grep gate** |
| Pre-commit hook (e.g. `lefthook`, `pre-commit`) running the same regex | Catches the issue before CI | LOW (incremental) | Optional, complements the build-time guard. The project already has the .pre-commit pattern from other linting (CSS / inline styles per CLAUDE.md) — extending is cheap |
| New Actuator endpoints exposed to admin UI | The 4.0.6 release notes mention security-filter-chain rule adjustments — worth checking if `/actuator/health` behavior changes for the prod profile | LOW | Per [the 4.0.6 announcement](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/), no new user-facing Actuator endpoints; only internal CVE fixes. **No work expected here.** |

### Anti-Features

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| Wholesale Thymeleaf API rewrite | "While we're upgrading, let's modernize" | Thymeleaf 3.x → 4.x is a separate, much larger migration. 3.2 fragment-parameter restriction is a tiny subset | Fix only the specific incompatibility class; defer broader Thymeleaf modernization |
| Migrating away from Thymeleaf Layout Dialect | [Wim Deblauwe's blog](https://www.wimdeblauwe.com/blog/2026/02/25/migrating-away-from-thymeleaf-layout-dialect/) is a tempting rabbit hole | Out of milestone scope — the layout dialect is not what's broken | Address only if it surfaces as a 3.2 incompatibility (it doesn't, per project context) |
| Preventive bump to Spring Boot 4.1.x or beyond | "Why stop at 4.0.6?" | 4.1.x has its own breaking-change profile; this milestone is hygiene-bound to 4.0.5 → 4.0.6 | Stay on the patch line; 4.1 is a future-milestone topic |
| Auto-rewriting templates by AST transformation | "Use a Thymeleaf-aware tool to rewrite the ternaries" | The project has 3 known + at-most-a-handful-discoverable templates. Manual fix is faster and safer than tooling for ~5-10 sites | Hand-edit + tests |

---

## Feature Dependencies

```
Cluster B (Export/Import):

[Export Service: write data.json + uploads to ZipOutputStream]
    └──depends on──> [Schema Version Constant / Flyway version probe]
    └──depends on──> [Entity ordering policy (FK-respecting topological order for serialization)]

[Import Service: parse ZIP → preview → execute]
    └──depends on──> [Export Service]   (round-trip symmetry; tests use export → import → diff)
    └──depends on──> [Schema Version Constant] (refuse mismatched bundles)
    └──depends on──> [Replace-All Wipe Logic (FK-respecting reverse topological order)]

[Preview Screen]
    └──depends on──> [Import Service: parse-only mode]
    └──depends on──> [Existing preview-page convention from DriverSheetImportController / CsvImportController]

[Confirm + Execute]
    └──depends on──> [Preview Screen] (no execute without preview)
    └──depends on──> [Atomic @Transactional boundary]
    └──depends on──> [Audit Log writer]

[Audit Log writer]
    └──independent of──> everything else (can be added at any phase, but must exist before the first real import)

Cluster A (Upgrade):

[pom.xml bump 4.0.5 → 4.0.6]
    └──blocks──> [./mvnw verify -Pe2e green run]    (can't verify without bumping)

[Template Audit]
    └──independent of──> [pom.xml bump]   (audit can run before or after; suggest before, so the bump just-works)

[Build-time guard (regex grep gate)]
    └──depends on──> [Template Audit]   (can't enforce a pattern that's still violated; fix violations first)

[Cluster A] ──independent of──> [Cluster B]
```

### Dependency Notes

- **Export Service must come before Import Service.** Round-trip is the natural integration test (export → import → assert idempotent). Roadmapper: order exports first.
- **Schema Version Constant is shared infrastructure.** Both Export (writes header) and Import (reads + validates header) depend on it. It's a small data-access concern (single integer constant tied to Flyway version). Build it as Phase-1 sub-task before either service.
- **Preview Screen depends on Import Service in parse-only mode.** Reuse `DriverSheetImportService.preview()` pattern: stateless parse, return DTOs to controller, render Thymeleaf. The "preview must come after data-extraction service" dependency the consumer flagged is exactly this.
- **Audit Log writer is independent of UI.** Can be added in any phase. Recommend slotting it into the Import-Execute phase so the first real import is logged.
- **Template Audit + pom.xml bump are technically independent**, but auditing first means the bump itself produces a green test run with no detective work needed. Order: audit → fix → bump → verify.
- **Build-time guard depends on a clean tree.** Adding a regex gate while violations still exist is a permanent red build. Fix all violations first, then add the guard.
- **Cluster A and Cluster B share no code paths.** They can be implemented in parallel (different worktrees) or interleaved. No ordering constraint between clusters.

---

## MVP Definition

The user's intent is clear from PROJECT.md: ship the platform hygiene + a usable backup/restore button for v1.10, and defer everything that's "next-milestone-grade" to v1.11+.

### Launch With (v1.10)

Minimum viable product — what's required to call the milestone shipped.

**Cluster A:**
- [ ] Spring Boot 4.0.5 → 4.0.6 in `pom.xml` — picks up CVE fixes
- [ ] All known + audit-discovered fragment-parameter ternaries fixed (move to controller `pageTitle`)
- [ ] All ~78 templates audited, audit results documented
- [ ] `./mvnw verify -Pe2e` green on 4.0.6
- [ ] Build-time regex guard added after fixes (lightweight, prevents regression — high ROI)

**Cluster B:**
- [ ] Export endpoint: `GET /admin/backup/export` → streaming ZIP (`data.json` + `uploads/`)
- [ ] JSON header: `schemaVersion`, `appVersion`, `exportDate`
- [ ] Entity scope per PROJECT.md (Seasons → SeasonPhases → SeasonPhaseGroups → PhaseTeams → Teams → SeasonTeams → Drivers → SeasonDrivers → PsnAlias → Matchdays → Matches → Races → RaceLineups → RaceResults → Playoffs → PlayoffMatchups → PlayoffSeeds → RaceScoring → MatchScoring)
- [ ] Import endpoint: `POST /admin/backup/import` → preview screen → confirm screen
- [ ] Schema-version refusal on mismatch (clear error message, no silent upgrade)
- [ ] Replace-all transactional wipe + restore (single `@Transactional` boundary)
- [ ] Confirm dialog with explicit "ALL operative data will be deleted" wording
- [ ] Audit log entry per import (who, when, wiped+restored row counts per table)
- [ ] Multipart size limit raised to 200 MB and documented
- [ ] Round-trip integration test (export → import → assert no diff)

### Add After Validation (v1.11)

Features to add once the v1.10 backup feature is in production use and the operator has identified actual gaps.

- [ ] **Per-season selective export/import** — explicitly deferred by user; will become urgent the first time the operator wants to merge real production season data into dev without losing dev fixtures
- [ ] **Verify-only mode** (parse + validate, no write) — natural follow-on once preview is stable; small effort, big trust gain
- [ ] **Diff preview in numbers** ("3 seasons would be wiped, 4 restored") — incremental UX win once preview screen exists
- [ ] **SHA-256 checksum file in the ZIP** — defensive depth; cheap to add later
- [ ] **`?includeUploads=false` query parameter** — only if asked; users may want a faster JSON-only export

### Future Consideration (v2+)

Features to defer until a different operating model emerges (multi-admin, scaled data, hosted SaaS).

- [ ] **Cloud destinations (S3/GCS)** — only relevant if the app moves off self-host
- [ ] **Scheduled backups** — only relevant if there's a second operator who'd benefit from "set and forget"
- [ ] **Multi-format export (CSV/SQL)** — only relevant if a non-CTC consumer of the data emerges
- [ ] **Background async with progress polling** — only relevant at 10× current data volume
- [ ] **In-app backup history viewer** — only relevant if scheduled backups land first
- [ ] **Encryption at rest** — only relevant if multi-tenant or hosted

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Spring Boot 4.0.5 → 4.0.6 bump | HIGH (8 CVE fixes) | LOW | P1 |
| Fragment-parameter ternary fix in 3 known templates | HIGH (unblocks the bump) | LOW | P1 |
| Full template audit | MEDIUM (catches future surprises) | LOW | P1 |
| Export endpoint + ZIP packaging | HIGH (the headline feature) | MEDIUM | P1 |
| `data.json` schema header | HIGH (gate for safe import) | LOW | P1 |
| Import endpoint + preview screen | HIGH (the other half of the headline feature) | MEDIUM-HIGH | P1 |
| Schema-version refusal | HIGH (blast-radius reduction) | LOW | P1 |
| Replace-all transactional restore | HIGH (data integrity) | MEDIUM | P1 |
| Confirm dialog + destructive-action warning | HIGH (saves the user from themselves) | LOW | P1 |
| Audit log per import | MEDIUM (post-incident forensics) | LOW-MEDIUM | P1 |
| Build-time regex guard for fragment-parameter ternaries | MEDIUM (regression prevention) | LOW | P1 |
| Multipart size limit 200 MB | HIGH (without it, real imports fail) | LOW | P1 |
| Round-trip integration test | HIGH (proves the feature works end-to-end) | LOW-MEDIUM | P1 |
| Verify-only "validate this ZIP" mode | MEDIUM | LOW | P2 |
| SHA-256 checksum file | LOW-MEDIUM | LOW | P2 |
| Diff preview row-count comparison | MEDIUM | MEDIUM | P2 |
| Per-season selective export | HIGH (long-term) | HIGH | P3 (deferred to v1.11) |
| Cloud destinations / scheduled / multi-format | LOW | HIGH | P3 (anti-feature for v1.10) |
| Maven Enforcer custom rule (vs. regex grep) | LOW (over-engineered) | HIGH | P3 (anti-feature; pick regex grep) |

**Priority key:**
- **P1:** Must have for v1.10 launch
- **P2:** Should have, candidate for v1.11
- **P3:** Defer or never

---

## Competitor Feature Analysis

The user explicitly cited GitLab Project Export, GitHub Migrations API, and Discourse backup as reference points. Here's what each does and what translates to a Thymeleaf-server-rendered admin UI.

| Feature | GitLab Project Export | Discourse `/admin/backups` | GitHub Migrations API | Our v1.10 Approach |
|---------|----------------------|---------------------------|----------------------|--------------------|
| Trigger | Async background job; user gets email when done | Async background job; live progress log streams in admin UI | API-only; pure async with polling endpoint | **Sync streaming ZIP response.** Data volume is small enough that sub-second download is feasible. No async machinery. |
| File format | Tarball (`.tar.gz`) of NDJSON + repo bundle + uploads | `.tar.gz` of SQL dump + uploads | `.tar.gz` of Git bundles + JSON metadata | **ZIP** (Java-native, no extra deps) of `data.json` + `uploads/` |
| Header / version | `VERSION` file in tarball | `metadata.json` with version + plugin list | `schema.json` per archive | **JSON top-level header** (`schemaVersion`/`appVersion`/`exportDate`) |
| Restore preview | None — restore is "trust the file" | None — but restore writes to a staging DB first, then atomic-swaps | None — destination repo is created fresh, no overwrite risk | **Full preview screen with per-table wipe + restore counts** (mirrors `CsvImportController` / `DriverSheetImportController` pattern) |
| Confirmation | None for export; restore is admin-only via API | "Are you sure?" modal with site name as type-to-confirm | None | **JS confirm() with explicit "delete ALL operative data" wording** (mirrors `DriverMergeController`) |
| Atomicity | Restore creates a new project, then renames | Restore is atomic via DB swap | New repo per migration | **`@Transactional` replace-all** (one boundary, all-or-nothing) |
| Schema check | Refuses if export-version > current | Refuses if version mismatch | Version-tagged archives | **Refuse mismatched `schemaVersion` with explicit error message** |
| Audit / log | Audit events in admin log | Backup log file + UI viewer | Migration log via API | **`import_audit_log` table or append-only log file** with per-table counts |
| Cloud destinations | S3/GCS configurable | S3/local | GitHub-managed only | **Local download only** (anti-feature) |
| Selective | Per-project | Whole-instance only | Per-repo or per-org | **Whole-DB only in v1.10**, per-season deferred |

**Key observations:**
- Our scale (single admin, ≤100 races, ≤200 MB uploads) makes us **simpler than all three** competitors. They all have async/progress machinery we don't need.
- The **preview screen** is our project's own convention (CsvImportController, DriverSheetImportController, gt7-sync-preview), and it's actually a *better* UX than GitLab/Discourse, which trust the file blindly. Don't drop it for "consistency with the big tools" — keep the preview.
- The **type-to-confirm** Discourse pattern is overkill for a single-admin tool. JS `confirm()` is the right level.
- The **schema-version header** is universally adopted across all three references — strongly validates including it as table stakes.

---

## UI Patterns That Translate to Thymeleaf

Constraint from CLAUDE.md: server-rendered Bootstrap-style admin templates, no SPA framework, CSS classes only (no inline styles), reuse existing patterns from `admin.css`. Mapping each backup/restore UI element to an existing precedent in the codebase:

| UI Element | Existing Precedent | Reuse As-Is |
|------------|-------------------|-------------|
| Single export button on admin nav | "Drivers Import" button on `/admin/drivers` toolbar (Phase 54) | Same toolbar pattern with a `Backup` button group |
| Upload form with multipart file input | `gt7-sync.html` Google-Sheet-URL form, `import.html` for CSV upload | Direct copy of the form structure with `enctype="multipart/form-data"` |
| Preview table with per-bucket counts | `driver-import-preview.html` (6 bucket tables, Phase 55) | Reuse the table-per-entity pattern; one table per domain entity with row counts |
| Confirm-execute screen | `gt7-sync-preview.html` confirm step, `driver-merge.html` confirm | Same hidden-form-with-token + JS `confirm()` pattern |
| Destructive-action warning banner | `driver-merge.html` warns about FK reassignment | Reuse the `.alert-danger` styling for "this will wipe all operative data" |
| Flash-message feedback | `successMessage` / `errorMessage` flash attributes (project convention) | Standard pattern — no new infra |
| Audit log viewer (if shown in UI) | None today | New page (low priority); for v1.10 a simple text log file is sufficient |
| Progress indicator | None today (no async ops in admin UI) | Not needed at our scale; sync request is fine |

**Recommendation:** Don't invent new UI patterns. Every screen of the backup/restore feature has a 1:1 precedent in the existing admin templates. This is also a derisking signal — the v1.8 driver-import flow is the closest analogue and was implemented in 2 days.

---

## Sources

- [GitLab Project Import/Export documentation](https://docs.gitlab.com/user/project/settings/import_export/) — observe their explicit warning that project exports are not for backup; informs our schema-version-strict approach
- [GitLab Backup/Restore docs](https://docs.gitlab.com/administration/backup_restore/) — full-instance reference for replace-all semantics
- [Discourse backup admin UI thread](https://meta.discourse.org/t/manually-create-and-restore-discourse-backups/18273) — `/admin/backups` is the type-to-confirm UX reference
- [Discourse "create, download, restore" thread](https://meta.discourse.org/t/create-download-and-restore-a-backup-of-your-discourse-database/122710) — closest UX peer, confirms the single-button pattern
- [Spring Boot 4.0.6 release announcement](https://spring.io/blog/2026/04/23/spring-boot-4-0-6-available-now/) — 65 bug fixes, 8 CVEs (TLS hostname verification for Elasticsearch/RabbitMQ/Cassandra, DevTools timing-attack fix, temp-dir ownership verification, PRNG strength improvement, security-filter-chain rule adjustments, PID file symlink-following prevention)
- [Spring Boot 4.0.6 GitHub milestone #422](https://github.com/spring-projects/spring-boot/milestone/422) — full per-issue list (referenced; not enumerated in the announcement)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) — broader context for 4.x line
- [Thymeleaf 3.1: What's new and how to migrate](https://www.thymeleaf.org/doc/articles/thymeleaf31whatsnew.html) — origin of the SpEL-restriction change-class that now bites fragment-parameter ternaries
- [Thymeleaf releases page](https://www.thymeleaf.org/releasenotes.html) — current 3.1.5.RELEASE; 3.2 series ships through Spring Boot 4.x dependency management
- [Wim Deblauwe — Migrating away from Thymeleaf Layout Dialect (2026)](https://www.wimdeblauwe.com/blog/2026/02/25/migrating-away-from-thymeleaf-layout-dialect/) — anti-feature reference (out-of-scope rabbit hole)
- [Maven Enforcer — Writing a custom rule](https://maven.apache.org/enforcer/enforcer-api/writing-a-custom-rule.html) — confirms why the heavyweight option is overkill for our single-pattern check
- [Baeldung — Serve a ZIP File With Spring Boot](https://www.baeldung.com/spring-boot-requestmapping-serve-zip) — `ZipOutputStream` streaming reference for the export endpoint
- [Spring Guides — Uploading Files](https://spring.io/guides/gs/uploading-files/) — multipart upload pattern for the import endpoint
- Project's own PROJECT.md (`v1.10 Spring Boot Upgrade & Data Export/Import` section) — definitive scope and decisions

In-codebase references (existing patterns that the new feature should mirror):

- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/controller/DriverSheetImportController.java` — the closest UX analogue (preview → confirm → execute)
- `/Users/jegr/Documents/github/ctc-manager/src/main/java/org/ctc/admin/service/DriverSheetImportService.java` — `@Transactional execute()` with mutable `ExecuteResult` accumulator; same shape works for our import service
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/driver-import-preview.html` — the per-bucket table-rendering pattern
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/gt7-sync-preview.html` — confirm-step pattern with hidden form
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/driver-merge.html` — destructive-action JS confirm pattern
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/match-scoring-form.html` (line 3) — known broken fragment-parameter ternary
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/race-scoring-form.html` (line 3) — known broken fragment-parameter ternary
- `/Users/jegr/Documents/github/ctc-manager/src/main/resources/templates/admin/season-phase-form.html` (line 3) — known broken fragment-parameter ternary

---
*Feature research for: CTC Manager v1.10 — Spring Boot 4.0.6 upgrade + Admin Data Export/Import*
*Researched: 2026-05-09*
