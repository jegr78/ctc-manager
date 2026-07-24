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
