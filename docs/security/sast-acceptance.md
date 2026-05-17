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
| TBD-baseline | java/ssrf | FileStorageService.storeFromUrl:86 | suppressed | startsWith-chain hostname blocklist (validateHostname, FileStorageService.java:128-159) covering localhost, 127.x, 10.x, 192.168.x, 169.254.x, 172.16-31.x ranges not recognized as a sanitizer by CodeQL (only allowlist-style sanitizers are recognized); intentional defense, unit-tested; defense-in-depth via Phase 81 SpotBugs SSRF_SPRING,SSRF suppression in config/spotbugs-exclude.xml | FileStorageService.java:85 |

## ZIP-Slip (Archive Path Traversal)

| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |
|----------|------|----------|--------|-----------|---------------|
| TBD-baseline | java/zipslip | BackupArchiveService.assertEntrySafe:608 | suppressed | PathTraversalGuard.assertWithin delegation (toAbsolutePath().normalize().startsWith()) not traceable by CodeQL through the utility-class boundary; intentional defense, IT-tested; defense-in-depth via Phase 81 SpotBugs PATH_TRAVERSAL_IN suppression on BackupArchiveService in config/spotbugs-exclude.xml | BackupArchiveService.java:610 |
| TBD-baseline | java/path-injection | BackupImportService.restoreOneTable:672 | suppressed | Same ZipFile#getEntry path-injection pattern as BackupArchiveService; ZIP entries are name-validated by assertEntrySafe via PathTraversalGuard before any path resolution; defense-in-depth via Phase 81 SpotBugs co-suppression | BackupImportService.java:671 |

## BCrypt Password Hashing (Not Applicable)

ROADMAP SC#4 and REQUIREMENTS SAST-05 anticipated a BCrypt-related CodeQL false-positive triage based on Phase 81 research-pitfall #13. The actual codebase does NOT declare a `PasswordEncoder` bean — `SecurityConfig.java` uses `httpBasic(Customizer.withDefaults())` with credentials stored directly in `application-{prod,docker}.yml`. CodeQL emits no BCrypt-related findings against this codebase. This sub-item is intentionally unfulfilled — TRACKED DEVIATION in `.planning/phases/85-codeql-sast/85-CONTEXT.md` D-05.

If a future phase introduces a `PasswordEncoder` bean (e.g. as part of adding form-login authentication), this section is the trigger to re-evaluate BCrypt-related CodeQL alerts and either confirm the chosen algorithm (`BCryptPasswordEncoder` with default cost >= 10) or fix per the D-10 decision tree.

## Other Triaged Findings

*(populated during baseline triage commits — plan 85-02. Each row follows the same `| Alert-ID | Rule | Location | Bucket | Rationale | Source-Marker |` schema as the SSRF and ZIP-Slip sections.)*
