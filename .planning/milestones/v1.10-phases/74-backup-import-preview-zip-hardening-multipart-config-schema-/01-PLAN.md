---
id: "01"
title: "Multipart limits + staging-dir config"
wave: 1
depends_on: []
requirements: [SECU-03]
files_modified:
  - src/main/resources/application.yml
autonomous: true
---

## Objective

Raise the multipart upload limits to 100 MB on both the Spring servlet layer and the underlying Tomcat connector (D-13), and introduce the `app.backup.staging-dir` property with a profile-aware default (D-15), so that the Wave-2 services (`BackupImportService`, `BackupStagingCleanup`) and the Wave-2 advice (`BackupUploadExceptionHandler`) have a single, well-formed YAML source from which to resolve their `@Value`-injected configuration. Phase 74 ships only the YAML change in this plan — the `MaxUploadSizeExceededException` mapping (Plan 06), the staging-file write path (Plan 05), and the startup-sweep listener (Plan 07) consume these keys downstream. No per-profile override is added (D-13: backup limits are identical across `dev` / `local` / `docker` / `prod`); the profile-aware staging path is provided by `${spring.profiles.active}` interpolation inside `application.yml` itself — see Notes below for the RESEARCH OQ#1 multi-profile (`dev,demo`) comma-path resolution.

## Tasks

<task id="74-01-01">
  <title>Raise multipart + Tomcat upload limits to 100 MB and add `app.backup.staging-dir` to `application.yml`</title>

  <action>
  Edit `src/main/resources/application.yml` to apply the four D-13 multipart/Tomcat keys verbatim and the D-15 staging-dir key under the existing top-level `app:` block. Treat each change as a minimal, surgical edit — preserve every other key (e.g. `app.upload-dir`, `app.version`, `spring.application.name`, `spring.jpa.*`, `spring.flyway.*`, `logging.level.*`, `server.port`, `google.*`, `ctc.site.*`, `management.endpoints.*`) byte-identically; do not reformat unrelated lines.

  Specific identifiers and values:

  1. Replace the existing `spring.servlet.multipart.max-file-size: 10MB` line with `spring.servlet.multipart.max-file-size: 100MB` (key path: `spring.servlet.multipart.max-file-size`, exact value: `100MB`).
  2. Replace the existing `spring.servlet.multipart.max-request-size: 10MB` line with `spring.servlet.multipart.max-request-size: 100MB` (key path: `spring.servlet.multipart.max-request-size`, exact value: `100MB`).
  3. Add a new `server.tomcat.max-http-form-post-size` key with the integer-bytes value `104857600` (= 100 × 1024 × 1024). Place it under the existing top-level `server:` block (which currently only holds `server.port: 8080`) by introducing a nested `tomcat:` map. The Tomcat connector silently caps any Spring multipart limit it does not match, so this key is the authoritative ceiling and must be set in lockstep with the Spring keys.
  4. Add a new `server.tomcat.max-swallow-size` key with the integer-bytes value `104857600` under the same `server.tomcat:` map. This prevents Tomcat from prematurely closing the connection when an oversize request body is already being streamed (`MaxUploadSizeExceededException` must reach Plan 06's advice).
  5. Add a new `app.backup.staging-dir` key with the literal string value `data/${spring.profiles.active}/backup-staging` under the existing top-level `app:` block (which currently holds `app.upload-dir` and `app.version`). Use Spring property-placeholder syntax — the `${spring.profiles.active}` token interpolates at runtime; do not hard-code any profile name. The key is intentionally a single line under `app.backup` (i.e. `app.backup.staging-dir: data/${spring.profiles.active}/backup-staging`) so that future `app.backup.*` keys (Phase 75+) can extend the same namespace.

  Do NOT add per-profile overrides in `application-dev.yml`, `application-local.yml`, `application-docker.yml`, or `application-prod.yml`. D-13 requires a single source. The four profile YAMLs already inherit `spring.servlet.multipart.*` and `app.*` from `application.yml`; the `${spring.profiles.active}` interpolation handles the profile-aware path without extra config (the staging-dir default resolves to `data/dev/backup-staging`, `data/local/backup-staging`, `data/docker/backup-staging`, `data/prod/backup-staging`, or `data/dev,demo/backup-staging` for multi-profile `dev,demo` runs — see Notes for the comma-path rationale).

  YAML structural rules: two-space indent (matches the existing file convention); preserve the trailing newline; keys remain `kebab-case` (matches the existing `app.upload-dir`); the multipart block keeps the `spring:` → `servlet:` → `multipart:` nesting it already has — only the two values change. The Tomcat block introduces `server:` → `tomcat:` → `max-http-form-post-size` / `max-swallow-size` as a peer of the existing `server.port`. The `app.backup` block introduces `app:` → `backup:` → `staging-dir` as a peer of `app.upload-dir` and `app.version`.

  Do not modify `app.upload-dir` (`data/dev/uploads`) — it serves the existing `FileStorageService` and is unrelated to backup staging. Do not modify `app.version` (`@project.version@` Maven filtering token).

  Per D-13 / SECU-03 (REQUIREMENTS.md L54), no other configuration source (e.g. `application-prod.yml` env-var overrides) is permitted to set these four multipart/Tomcat keys. If a later phase needs profile-specific multipart limits, that change reopens this plan; for Phase 74 the four keys are single-sourced.
  </action>

  <read_first>
    - `src/main/resources/application.yml` (the file being modified — current keys: `app.upload-dir`, `app.version`, `spring.application.name`, `spring.servlet.multipart.max-file-size: 10MB`, `spring.servlet.multipart.max-request-size: 10MB`, `spring.jpa.open-in-view: true`, `spring.flyway.locations: classpath:db/migration`, `logging.level.org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor: ERROR`, `server.port: 8080`, `google.sheets.credentials-path: google-credentials.json`, `google.calendar.id: ${GOOGLE_CALENDAR_ID:}`, `ctc.site.*`, `management.endpoints.web.exposure.include: health`).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-CONTEXT.md` (D-13 verbatim block lines 51-63 — the four target multipart/Tomcat keys; D-15 lines 67-68 — the staging-dir property and default; D-17 line 70 — the startup-sweep that consumes the staging dir; D-18 line 71 — the stateless re-parse pattern that consumes the staging dir).
    - `.planning/phases/74-backup-import-preview-zip-hardening-multipart-config-schema-/74-PATTERNS.md` §"`src/main/resources/application.yml` — extension" — analog block + adaptation rule per D-13.
    - `.planning/REQUIREMENTS.md` §SECU-03 (line 54) — acceptance criteria: the four keys with these exact values; §IMPORT-01 (line 41) — references `app.backup.staging-dir` as configurable.
    - `src/main/resources/application-dev.yml`, `src/main/resources/application-local.yml`, `src/main/resources/application-docker.yml`, `src/main/resources/application-prod.yml` (verify no profile-override is required — they do not currently set `spring.servlet.multipart.*` or `server.tomcat.*` or `app.backup.*`).
  </read_first>

  <acceptance_criteria>
    1. `src/main/resources/application.yml` contains the key path `spring.servlet.multipart.max-file-size` with value `100MB` (and no other value for this key).
    2. `src/main/resources/application.yml` contains the key path `spring.servlet.multipart.max-request-size` with value `100MB` (and no other value for this key).
    3. `src/main/resources/application.yml` contains the key path `server.tomcat.max-http-form-post-size` with the integer value `104857600` (matching D-13's exact byte count: 100 × 1024 × 1024).
    4. `src/main/resources/application.yml` contains the key path `server.tomcat.max-swallow-size` with the integer value `104857600`.
    5. `src/main/resources/application.yml` contains the key path `app.backup.staging-dir` with the literal string value `data/${spring.profiles.active}/backup-staging` (Spring placeholder syntax preserved — the `${...}` is part of the value).
    6. The string `10MB` no longer appears anywhere in `src/main/resources/application.yml` (the old 10 MB limits are fully replaced).
    7. All other keys in `src/main/resources/application.yml` are byte-identical to the pre-edit state — `app.upload-dir: data/dev/uploads`, `app.version: @project.version@`, `spring.application.name: ctc-manager`, `spring.jpa.open-in-view: true`, `spring.jpa.properties.hibernate.format_sql: true`, `spring.flyway.enabled: true`, `spring.flyway.locations: classpath:db/migration`, `logging.level.org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor: ERROR`, `server.port: 8080`, `google.sheets.credentials-path: google-credentials.json`, `google.calendar.id: ${GOOGLE_CALENDAR_ID:}`, the full `ctc.site.*` tree, `management.endpoints.web.exposure.include: health` — all preserved verbatim.
    8. None of `application-dev.yml`, `application-local.yml`, `application-docker.yml`, `application-prod.yml` was modified by this task (D-13 single-source rule).
    9. YAML is well-formed (parseable by SnakeYAML — Spring Boot's loader). Verifiable by the smoke test below.
    10. The four new keys live in the canonical Spring locations so that `@Value("${app.backup.staging-dir:...}")` (Plan 05/07) and the implicit `MultipartProperties` / `TomcatConnectorCustomizer` binding (Spring Boot autoconfig) pick them up without further wiring.
  </acceptance_criteria>

  <automated>./mvnw -q test -Dtest='ApplicationContextSmokeTest'</automated>

  <verify_notes>
    The smoke verification command runs the existing `ApplicationContextSmokeTest` (or equivalent context-loading test under `src/test/java/`) — if that class loads the Spring context with the new keys without throwing `BeanCreationException` / `ConfigurationPropertiesBindingException` / `YAMLException`, the YAML is well-formed and the keys bind to their respective property classes (`MultipartProperties`, Tomcat connector properties). The smoke test does NOT exercise an actual oversize upload — that is Plan 06's `BackupImportMultipartLimitIT` job. If `ApplicationContextSmokeTest` does not exist by name, substitute with `./mvnw -q -Dtest='*SmokeTest' test` or fall back to `./mvnw -q -DskipTests=false -Dtest='*' compile test-compile` followed by `./mvnw -q -Dtest='BackupControllerIT#givenAuthenticatedAdmin_whenGetBackup_thenViewRendersWithLockedUiSpecStrings' test` — any test that loads the full `@SpringBootTest` context proves the YAML parses.
  </verify_notes>

  <done>The four D-13 multipart/Tomcat keys and the D-15 staging-dir key are present in `src/main/resources/application.yml` with the exact values specified above; the previous 10 MB Spring limits are removed; no profile-specific YAML was changed; the Spring context loads under the `dev` test profile without exception.</done>
</task>

## Verification

### must_haves

**Truths:**
- The active Spring context resolves `spring.servlet.multipart.max-file-size` to 100 MB. (Consumed by Spring's `StandardServletMultipartResolver`; without this, Plan 06's `BackupImportMultipartLimitIT` cannot prove that a 101 MB upload triggers `MaxUploadSizeExceededException`.)
- The active Spring context resolves `spring.servlet.multipart.max-request-size` to 100 MB. (Spring rejects the whole request before Tomcat sees it once this is set.)
- The active Spring context resolves `server.tomcat.max-http-form-post-size` to 104857600 bytes. (Without this, Tomcat caps the request body at its own default (typically 2 MB) before Spring's multipart limit is even consulted — the Spring limit becomes a no-op.)
- The active Spring context resolves `server.tomcat.max-swallow-size` to 104857600 bytes. (Allows Tomcat to keep the connection alive while reading the oversize body so that `MaxUploadSizeExceededException` reaches Plan 06's advice and renders a Flash redirect instead of a connection reset.)
- The active Spring context resolves `app.backup.staging-dir` to a profile-suffixed path of the form `data/{profile}/backup-staging`. (Plan 05's `BackupImportService` and Plan 07's `BackupStagingCleanup` both `@Value`-inject this property; they cannot start without it.)
- No 10 MB upload limit remains anywhere in the active configuration. (Existing pre-Phase-74 multipart use cases — `FileStorageService` for race attachments, CSV import — never required >10 MB; raising the global limit to 100 MB is safe per D-13's "no other multipart use case in the app needs more than 100 MB" rationale.)
- The four profile YAMLs (`application-dev.yml`, `application-local.yml`, `application-docker.yml`, `application-prod.yml`) inherit the new keys via Spring's profile-merge without per-profile overrides.

**Artifacts:**
- `src/main/resources/application.yml` — modified file, contains the five new/raised keys with the exact values per Acceptance Criteria 1-5.

**Test Classes:**
- `ApplicationContextSmokeTest` (or any existing `@SpringBootTest` context-loader test) — exercises Acceptance Criterion 9 (YAML is well-formed and binds to Spring's property classes). Owning Wave: 1. Test is the existing smoke harness; this plan does NOT add a new test class. Per the planning context, "One IT belongs in Plan 06 (`BackupImportMultipartLimitIT`) — Plan 01 ships only the YAML."

## Notes

### RESEARCH OQ#1 — Multi-profile (`dev,demo`) staging path resolution

D-15 specifies the staging-dir default as `data/${spring.profiles.active}/backup-staging`. RESEARCH §OQ#1 raised the question: when Spring is started with two active profiles (e.g. `--spring.profiles.active=dev,demo`, the documented `dev,demo` demo-data profile), the `${spring.profiles.active}` placeholder interpolates to the literal comma-joined string `dev,demo`, producing the path `data/dev,demo/backup-staging`.

**Planner choice — accept the literal comma-path (option (a)).** Rationale:

1. **POSIX-compliant.** A comma is a perfectly valid character in a POSIX filename. macOS, Linux (the supported `docker`/`prod` runtime), and any Linux-based CI runner handle `data/dev,demo/backup-staging` without quoting issues.
2. **Windows is irrelevant.** The application's supported runtime targets are `dev` (Mac/Linux dev machines), `local` (Mac/Linux + local MariaDB), `docker` (Linux container), `prod` (Linux container). The only Windows touchpoint is the off-chance a developer runs `dev,demo` on Windows; the staging dir is admin-only, used 1-2 times per week, and the comma is a non-issue on NTFS (NTFS accepts commas in filenames).
3. **No Java-side resolution needed in Phase 74.** Plan 07's `BackupStagingCleanup` and Plan 05's `BackupImportService` both consume `${spring.profiles.active}`-interpolated paths via plain `@Value` injection and use them with `java.nio.file.Paths.get(...)` — which makes no special interpretation of commas. There is no path-splitting or active-profile-listing code in Phase 74.
4. **The alternative (option (b) — Java-side resolution that picks the first active profile or joins with a different separator) would add code and complexity for a near-zero benefit.** Comma-paths are a known POSIX-fine pattern and are not user-visible (admins never type the staging path; the app writes there).
5. **If a future v1.11 phase ever wants a different multi-profile rule** (e.g. always take the first profile, or use an underscore), it adds a `BackupStagingProperties` `@ConfigurationProperties` class and overrides the default in Java — a clean v1.11 extension that this phase does NOT preempt.

This decision is documented here (Plan 01 owns the YAML) so that the executor of Plan 07 (`BackupStagingCleanup`) and Plan 05 (`BackupImportService`) does NOT add comma-path-aware splitting logic on their own — the path is treated as opaque.

### Why a sibling `server.tomcat:` block (not `server.tomcat-extra:` or a `@Bean WebServerFactoryCustomizer`)

Spring Boot autoconfig binds `server.tomcat.*` to the `TomcatConnectorCustomizer` family directly (`org.springframework.boot.autoconfigure.web.ServerProperties$Tomcat`); the two keys `max-http-form-post-size` and `max-swallow-size` are standard properties on that class. No `@Bean` customizer is needed. The cost is two YAML lines, vs. a programmatic alternative that would add a `@Configuration` class and a `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` bean — both unnecessary and harder to audit. The YAML route also keeps the four multipart/Tomcat keys colocated in one file (D-13 single-source).

### Why `app.backup.staging-dir` and not `ctc.backup.staging-dir`

The existing top-level keys split into `app.*` (runtime-mutable settings — `upload-dir`, `version`) and `ctc.*` (compile-time-shaped business config — site-generation links, channel URLs). The staging dir is a runtime path (admin-only, profile-aware, may differ between dev/local/docker/prod), so `app.backup.staging-dir` is the consistent namespace. Plans 05/07/08 will all `@Value("${app.backup.staging-dir:...}")`-inject from this single key.

## PLAN COMPLETE 01
