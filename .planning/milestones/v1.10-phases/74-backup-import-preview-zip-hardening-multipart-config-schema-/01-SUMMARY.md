---
phase: "74"
plan: "01"
subsystem: "config"
tags: [multipart, upload-limits, tomcat, backup, yaml]
dependency_graph:
  requires: []
  provides: [app.backup.staging-dir, spring.servlet.multipart limits 100MB, server.tomcat limits 100MB]
  affects: [Plan 05 BackupImportService, Plan 06 BackupUploadExceptionHandler, Plan 07 BackupStagingCleanup]
tech_stack:
  added: []
  patterns: [Spring Boot application.yml single-source config]
key_files:
  created: []
  modified:
    - src/main/resources/application.yml
decisions:
  - "Accept literal comma-path for dev,demo profile: data/dev,demo/backup-staging (POSIX-valid, no Java workaround needed)"
  - "Tomcat keys set via YAML server.tomcat.* (no @Bean WebServerFactoryCustomizer needed — Spring Boot autoconfig binds directly)"
  - "app.backup.staging-dir namespace chosen over ctc.backup.* because staging-dir is runtime-mutable (profile-aware path)"
metrics:
  duration: "~3 min"
  completed: "2026-05-12"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 1
---

# Phase 74 Plan 01: Multipart limits + staging-dir config Summary

Raised Spring servlet multipart upload limits and Tomcat connector ceiling to 100 MB, and introduced `app.backup.staging-dir` with profile-aware placeholder — all in a single surgical edit to `application.yml`.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 74-01-01 | Raise multipart + Tomcat upload limits to 100 MB; add `app.backup.staging-dir` | 4a42d4f | src/main/resources/application.yml |

## Changes Made

### src/main/resources/application.yml

Five targeted changes, all other keys preserved byte-identically:

1. `spring.servlet.multipart.max-file-size`: `10MB` → `100MB`
2. `spring.servlet.multipart.max-request-size`: `10MB` → `100MB`
3. Added `server.tomcat.max-http-form-post-size: 104857600` (100 MiB in bytes)
4. Added `server.tomcat.max-swallow-size: 104857600` (100 MiB in bytes)
5. Added `app.backup.staging-dir: data/${spring.profiles.active}/backup-staging`

No per-profile YAML files were modified (D-13 single-source rule). All four profile files (`application-dev.yml`, `application-local.yml`, `application-docker.yml`, `application-prod.yml`) inherit the new keys via Spring's profile-merge.

## Verification

Smoke test `TemplateRenderingSmokeIT` (65 tests): **BUILD SUCCESS** — Spring context loads without `BeanCreationException`, `ConfigurationPropertiesBindingException`, or `YAMLException`. New keys bind correctly to `MultipartProperties` and Tomcat server properties via Spring Boot autoconfig.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

- [x] `src/main/resources/application.yml` exists and contains all 5 new/updated keys
- [x] Commit `4a42d4f` present in git log
- [x] `10MB` string no longer present in `application.yml`
- [x] No profile-specific YAMLs modified
- [x] Smoke test: 65/65 passed, BUILD SUCCESS
