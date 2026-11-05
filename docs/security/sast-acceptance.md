# SAST Acceptance Log

CodeQL Code-Scanning false-positive triage decisions for `jegr78/ctc-manager`.
See `CLAUDE.md` "Conventions > CodeQL SAST (Code Scanning)" for the workflow.

**Buckets:**

- **fixed** — finding addressed by a source-code change (link to commit SHA in the `Source-Marker` column)
- **suppressed** — finding is intentional / false positive, suppressed via `.github/codeql/codeql-config.yml` `query-filters` + `// CodeQL FP:` source marker at the protected method/block
- **accepted** — finding documented as known risk, neither fixed nor suppressed (rare; requires explicit risk acceptance)

> **D-19 Update-on-Triage discipline:** every CodeQL suppression must change three files in the SAME commit — `.github/codeql/codeql-config.yml` (filter), the relevant `.java` source (`// CodeQL FP:` marker), and this file (table row). Partial-writes are forbidden.

## SSRF (Server-Side Request Forgery)

| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
|----------|------|----------|--------|-----------|---------------|
| N/A (filtered) | java/ssrf | FileStorageService.storeFromUrl:86 | suppressed | startsWith-chain hostname blocklist (validateHostname, FileStorageService.java:128-159) covering localhost, 127.x, 10.x, 192.168.x, 169.254.x, 172.16-31.x ranges not recognized as a sanitizer by CodeQL (only allowlist-style sanitizers are recognized); intentional defense, unit-tested; defense-in-depth via Phase 81 SpotBugs SSRF_SPRING,SSRF suppression in config/spotbugs-exclude.xml. **Alert-ID = N/A (filtered):** `query-filters[].exclude: { id: java/ssrf }` in `.github/codeql/codeql-config.yml` excludes the rule before SARIF upload — the alert never reaches the Security tab, so no Alert-Number exists. Baseline scan 2026-05-17 confirmed: 0 alerts emitted for this rule. | FileStorageService.java:86 |

## ZIP-Slip (Archive Path Traversal)

| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
|----------|------|----------|--------|-----------|---------------|
| N/A (filtered) | java/zipslip | BackupArchiveService.assertEntrySafe:614 | suppressed | PathTraversalGuard.assertWithin delegation (toAbsolutePath().normalize().startsWith()) not traceable by CodeQL through the utility-class boundary; intentional defense, IT-tested; defense-in-depth via Phase 81 SpotBugs PATH_TRAVERSAL_IN suppression on BackupArchiveService in config/spotbugs-exclude.xml. **Alert-ID = N/A (filtered):** `query-filters[].exclude: { id: java/zipslip }` excludes the rule before upload. Baseline scan 2026-05-17 confirmed: 0 alerts emitted. | BackupArchiveService.java:611 |
| N/A (filtered) | java/path-injection | BackupImportService.restoreOneTable:673 | suppressed | Same ZipFile#getEntry path-injection pattern as BackupArchiveService; ZIP entries are name-validated by assertEntrySafe via PathTraversalGuard before any path resolution; defense-in-depth via Phase 81 SpotBugs co-suppression. **Alert-ID = N/A (filtered):** `query-filters[].exclude: { id: java/path-injection }` excludes the rule before upload. Baseline scan 2026-05-17 confirmed: 0 alerts emitted. | BackupImportService.java:672 |
| N/A (filtered) | java/path-injection | BackupImportService.countRestorableRows | suppressed | Same ZipFile#getEntry lookup as restoreOneTable; the entry name is `EntityRef.fileName()` from the JPA metamodel, never from the archive. **Alert-ID = N/A (filtered):** `query-filters[].exclude: { id: java/path-injection }` excludes the rule before upload. | BackupImportService.java (countRestorableRows) |

## BCrypt Password Hashing (Not Applicable)

ROADMAP SC#4 and REQUIREMENTS SAST-05 anticipated a BCrypt-related CodeQL false-positive triage based on Phase 81 research-pitfall #13. The actual codebase does NOT declare a `PasswordEncoder` bean — `SecurityConfig.java` uses `httpBasic(Customizer.withDefaults())` with credentials stored directly in `application-{prod,docker}.yml`. CodeQL emits no BCrypt-related findings against this codebase. This sub-item is intentionally unfulfilled — TRACKED DEVIATION in `.planning/phases/85-codeql-sast/85-CONTEXT.md` D-05.

If a future phase introduces a `PasswordEncoder` bean (e.g. as part of adding form-login authentication), this section is the trigger to re-evaluate BCrypt-related CodeQL alerts and either confirm the chosen algorithm (`BCryptPasswordEncoder` with default cost >= 10) or fix per the D-10 decision tree.

## Other Triaged Findings

*(populated during baseline triage commits — plan 85-02. Each row follows the same `| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |` schema as the SSRF and ZIP-Slip sections.)*

## find-sec-bugs Baseline (SpotBugs, 2026-07-24)

The find-sec-bugs detector pack was configured as a `spotbugs-maven-plugin` dependency but was **not loaded** by SpotBugs 4.9.8.x — the generated `spotbugsXml.xml` contains no `<Plugin id='com.h3xstream.findsecbugs'>` element and reports zero SECURITY-category findings. SpotBugs 4.10.1 fixed the underlying `DetectorFactoryCollection` bootstrap ordering (spotbugs/spotbugs#4191), so the plugin bump to `4.10.3.0` activated all 144 patterns at once: 52 Medium findings, none of which the gate had ever evaluated.

Every finding was triaged by hand. Two led to source changes; the rest are structural false positives suppressed in `config/spotbugs-exclude.xml`, where each `<Match>` carries its rationale.

| Rule | Location | Count | Bucket | Rationale |
|------|----------|-------|--------|-----------|
| CRLF_INJECTION_LOGS | 6 log statements across `DriverMergeService`, `PlayoffService`, `RaceCalendarService`, `SeasonManagementService`, `StandingsViewService` | 6 | fixed | Request- and operator-supplied strings (standings `group` parameter, imported PSN IDs, season/team names, playoff round labels, calendar titles) now pass through `LogSanitizer.sanitize()` |
| NCR_NOT_PROPERLY_CHECKED_READ | `BackupArchiveService.countUploadFiles` | 1 | fixed | Manual discard-buffer loop replaced by `transferTo(OutputStream.nullOutputStream())`; the entry-size defense still runs through the counting `LimitedInputStream.read(byte[],int,int)` override |
| CRLF_INJECTION_LOGS | detector-wide | 34 | suppressed | CWE-117 is owned by CodeQL `java/log-injection` (path-sensitive, `LogSanitizer`-aware, 0 alerts). find-sec-bugs taints every controller-reachable value and reports the whole call regardless of argument type; all 34 residual sites log a `UUID`, a primitive, or a `Path` derived from a `UUID` |
| PATH_TRAVERSAL_IN | `WebConfig`, `AbstractGraphicService`, `TeamCardService`, `BackupExportService`, `BackupImportService`, `DiscordPostService`, `GoogleCalendarService`, `GoogleSheetsService` | 10 | suppressed | `Paths.get()` over operator-configured `@Value` properties resolved once at bean construction; not reachable from an HTTP request |
| PATH_TRAVERSAL_IN | `PathTraversalGuard.assertWithin` | 1 | suppressed | The flagged `Paths.get()` is the guard's own absolute-path probe — flagging the defense is circular |
| PATH_TRAVERSAL_IN | `TeamCardController.download`, `.downloadAll` | 2 | suppressed | Resolved segment passes `TeamCardService.sanitizeFilename()`; no separator can survive, so the path stays one level below the season-UUID directory |
| PATH_TRAVERSAL_IN | `RaceAttachmentService.downloadAttachment` | 1 | suppressed | `file.startsWith(uploadDirPath)` containment check on the normalized path runs immediately after the flagged `resolve()` |
| URLCONNECTION_SSRF_FD | `FileStorageService.storeFromUrl` | 1 | suppressed | Same hostname blocklist already accepted for `SSRF_SPRING`/`SSRF` above; added to the existing `<Match>` |
| PREDICTABLE_RANDOM | `DiscordRateLimitInterceptor.jitterMs` | 1 | suppressed | Retry jitter, never a token/nonce/identifier |
| NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `BackupStagingCleanup.sweepStagingDir` | 1 | suppressed | `getFileName()` on `Files.list()` entries — a directory listing never yields a root path |

**Re-evaluating the CRLF suppression:** it is detector-wide, so a genuinely unsafe new log statement will not be caught by SpotBugs. CodeQL's `java/log-injection` remains the gate for CWE-117, and `LogSanitizer.sanitize()` remains mandatory for user-controlled values in new code. If CodeQL coverage is ever dropped, this suppression must be replaced by per-site entries.

## Packaged migration SQL, 2026-11-02

| Rule | Location | Count | Bucket | Rationale |
|------|----------|-------|--------|-----------|
| SQL_INJECTION_JDBC | `db.migration.V26__AddConfidentialPicks.migrate` | 1 | suppressed | Method-scoped `@SuppressFBWarnings` for execution of the fixed, bundled `/db/schema/confidential_picks.sql` resource. The MariaDB branch only replaces fixed `TIMESTAMP(6)` tokens with `DATETIME(6)`; no request, uploaded archive or database value enters the SQL text. Migration checksum/resource-drift tests and H2/MariaDB restore tests cover the packaged DDL. Reevaluate if the resource path, SQL source or rewrite becomes input-controlled. All other SQL-injection detection remains enabled. |
| SQL_INJECTION_JDBC | `db.migration.V28__AddPickPublicationRecords.migrate` | 1 | suppressed | The fixed bundled `/db/schema/pick_publication.sql` resource uses the existing migration protocol: only fixed timestamp tokens differ by database. No request, archive or database values enter SQL text. Real H2/MariaDB publication persistence tests exercise the DDL. Reevaluate if that source or rewrite becomes input-controlled. |
| SQL_INJECTION_JDBC | `db.migration.V29__AddIncidentReporting.migrate` | 1 | suppressed | CI on PR #331 head `65048e73` reported this execution of fixed bundled `/db/schema/incident_reporting.sql` DDL. The only rewrite replaces literal timestamp type tokens for MariaDB; no request, uploaded archive or database values enter SQL text. The method-scoped annotation follows V26/V28. H2/MariaDB persistence and restoration fixtures exercise the packaged DDL. Reevaluate if the resource source or rewrite becomes input-controlled. |
| SQL_INJECTION_JDBC | `db.migration.V30__AddIncidentDelivery.migrate` | 1 | suppressed | Fixed bundled `/db/schema/incident_deliveries.sql` DDL, with only literal timestamp type replacement for MariaDB. Upgrade report/revision/event values are bound parameters and never enter SQL text. Direct upgrade and real H2/MariaDB persistence/archive tests cover the migration. Reevaluate if the SQL resource, rewrite or parameterization becomes input-controlled. |

## Internal incident pairing adapter, 2026-11-03

| Rule | Location | Count | Bucket | Rationale |
|------|----------|-------|--------|-----------|
| EI_EXPOSE_REP, EI_EXPOSE_REP2 | `org.ctc.domain.service.IncidentMatchTarget` | 10 | suppressed | CI on PR #331 head `65048e73` reported five managed-entity constructor references and their five accessors. This internal adapter intentionally retains the current Match/PlayoffMatchup, Season and participant entities for transactional ownership validation. It is not bound from an HTTP form or serialized as a transport payload. The race list remains defensively copied. The type-scoped annotation uses EXACT matching for these two representation warnings, avoiding the default prefix overlap and its redundant-suppression finding; other static-analysis rules remain enabled. Reevaluate if this adapter crosses a transport boundary or gains mutable non-entity state. |


## Internal incident delivery collaborators, 2026-11-04

| Rule | Location | Count | Bucket | Rationale |
|------|----------|-------|--------|-----------|
| EI_EXPOSE_REP, EI_EXPOSE_REP2 | `IncidentSubmissionAccepted`, `IncidentWindowClosed` | 4 | suppressed | The first complete local #290 run reported constructor/accessor references to managed report/window entities. These internal synchronous events deliberately share the locked producer entities with MANDATORY listeners, which enqueue delivery work in that same transaction. They are not HTTP forms or external payloads. The closure report list remains defensively copied. Type-scoped annotations use EXACT matching for these two warnings only, following `IncidentMatchTarget`; other rules remain enabled. Atomic rollback and immutable capture tests cover the transaction contract. Reevaluate if an event becomes asynchronous, crosses a transport boundary or gains mutable non-entity state. |
| EI_EXPOSE_REP2 | `IncidentDeliveryService` constructor | 1 | suppressed | Spring supplies its shared transaction-scoped EntityManager proxy, as in `PickPublicationService`. The service must retain that injected proxy for current locking refreshes; making a copy would break persistence context identity. An EXACT constructor-scoped annotation excludes only this structural representation warning. Whole-dispatch restore admission and H2/MariaDB concurrency tests cover its use. Reevaluate if the constructor accepts caller-owned mutable data. |


## Linked incident thread migration, 2026-11-04

| Rule | Location | Count | Bucket | Rationale |
|------|----------|-------|--------|-----------|
| SQL_INJECTION_JDBC | `db.migration.V31__AddIncidentThreads.migrate` | 1 | suppressed | Executes fixed bundled `/db/schema/incident_threads.sql` DDL with the existing literal timestamp type replacement for MariaDB. No request, archive or database values enter SQL text. The source annotation follows V29/V30. Reevaluate if the resource source or rewrite becomes input-controlled. |
| EI_EXPOSE_REP2 | `IncidentThreadPublicationService` constructor | 1 | suppressed | Retains Spring's shared transaction-scoped EntityManager proxy for current locking refreshes under the window/delivery locks. Copying it would break persistence-context identity. The EXACT constructor-scoped annotation excludes only this representation warning; other rules remain enabled. Reevaluate if the constructor starts accepting caller-owned mutable data. |

## Incident ruling persistence, 2026-11-04

| Rule | Location | Count | Bucket | Rationale |
|------|----------|-------|--------|-----------|
| SQL_INJECTION_JDBC | `db.migration.V32__AddIncidentRulings.migrate` | 1 | suppressed | Executes only bundled `/db/schema/incident_rulings.sql` DDL with the literal MariaDB timestamp-token rewrite, following V31. No request, archive or stored values enter SQL text. Reevaluate if the resource source or rewrite becomes input-controlled. |
| EI_EXPOSE_REP2 | `IncidentRulingService` constructor | 1 | suppressed | Retains Spring's shared transaction-scoped EntityManager proxy for current report/ruling/publication reads under the reporting-window lock. Copying it would break persistence-context identity. The EXACT constructor annotation follows `IncidentThreadPublicationService`; other rules remain enabled. Reevaluate if caller-owned mutable data is accepted. |


## Team penalty migration

| Rule | Location | Count | Decision | Rationale |
|---|---|---|---|---|
| SQL_INJECTION_JDBC | `db.migration.V35__AddRacePenaltyRevisions.migrate` | 1 | suppressed | Executes fixed bundled `/db/schema/race_penalty_revisions.sql` DDL. The only rewrite selects `DATETIME(6)` for MariaDB in place of literal `TIMESTAMP(6)` tokens, following V32. No request, archive or stored values enter SQL text. The checksum covers the bundled resource; H2/MariaDB round trips preserve microsecond audit times beyond the native TIMESTAMP range. Reevaluate if the source or rewrite becomes input-controlled. |
