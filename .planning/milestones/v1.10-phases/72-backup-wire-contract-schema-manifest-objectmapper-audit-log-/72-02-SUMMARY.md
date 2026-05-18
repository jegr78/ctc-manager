---
phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log
plan: 02
subsystem: backup
tags: [v1.10, backup, manifest, wire-contract, jackson, record, jsr310]

# Dependency graph
requires:
  - phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log
    provides: "Plan 01 — BackupSchema component + EntityRef record (sibling artefact in `org.ctc.backup.schema` package)"
provides:
  - "BackupManifest Java record locking the manifest.json wire-format (schema_version int, app_version string, export_date ISO-8601 Instant, table_counts Map<String, Long>)"
  - "Per-field @JsonProperty snake_case keys — JSON shape rule lives with the record, not the mapper"
  - "BackupManifestSerializationTest (Surefire) — 4 unit-test cases proving the wire shape"
  - "Pom dependency `jackson-datatype-jsr310` — unlocks Instant ISO-8601 serialization for the Jackson 2.x ObjectMapper used by the backup module"
affects: [72-03-BackupObjectMapperConfig, 73-BackupExportService, 74-BackupImportService]

# Tech tracking
tech-stack:
  added:
    - "com.fasterxml.jackson.datatype:jackson-datatype-jsr310 (Java-Time module for Jackson 2.x — required by the backup ObjectMapper that plan 03 builds)"
  patterns:
    - "Per-field @JsonProperty for snake_case JSON keys on Java records (vs. project-wide PropertyNamingStrategies)"
    - "Wave-0 RED stub Surefire test followed by GREEN flip in a single plan — TDD discipline at the plan-level"
    - "Strict ObjectMapper constructed inline in unit tests to decouple from Spring DI graph (FAIL_ON_UNKNOWN_PROPERTIES=true, WRITE_DATES_AS_TIMESTAMPS=false, JavaTimeModule)"

key-files:
  created:
    - "src/main/java/org/ctc/backup/schema/BackupManifest.java"
    - "src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java"
  modified:
    - "pom.xml (added jackson-datatype-jsr310 dependency — Rule 3 unblock)"

key-decisions:
  - "BackupManifest is a Java record with the four locked components in the locked order; per-field @JsonProperty annotations enforce snake_case JSON keys regardless of mapper-level naming strategy"
  - "appVersion is NOT injected from BuildProperties — Phase 73's BackupExportService caller supplies it via @Value(\"${app.version}\") (RESEARCH P-3)"
  - "manifest.json first-entry write-order discipline (D-14) is deferred to Phase 73 — this plan ships only the record definition"
  - "Test constructs its ObjectMapper inline (NOT via Spring DI) so the test is independent of plan 03's BackupObjectMapperConfig"

patterns-established:
  - "Per-field @JsonProperty on record components: locks JSON shape at the record level — preferred over global PropertyNamingStrategies"
  - "Wave-0 RED stub committed before the production class: separate test commit + feat commit gives a clean RED → GREEN audit trail in git log"

requirements-completed: [SCHEMA-02]

# Metrics
duration: ~6min
completed: 2026-05-11
---

# Phase 72 Plan 02: BackupManifest Wire-Contract Record Summary

**BackupManifest Java record locking the manifest.json wire-format (schema_version int, app_version string, export_date ISO-8601 Instant, table_counts Map<String, Long>) with per-field @JsonProperty snake_case keys; backed by a 4-case Surefire unit test that exercises serialization shape, ISO-8601 date encoding, integer schema_version, and round-trip equality.**

## Performance

- **Duration:** ~6 minutes (clock time from worktree setup to final commit)
- **Started:** 2026-05-11T15:32:00Z (approx, worktree creation)
- **Completed:** 2026-05-11T15:37:27Z
- **Tasks:** 2 / 2 (both atomic)
- **Files created:** 2 (1 production, 1 test)
- **Files modified:** 1 (pom.xml — Rule 3 unblock)

## Accomplishments

- `BackupManifest` Java record landed with EXACTLY the four locked components in the locked order (`int schemaVersion`, `String appVersion`, `Instant exportDate`, `Map<String, Long> tableCounts`).
- Per-field `@JsonProperty("snake_case_name")` annotations lock the JSON keys at the record level — the rule lives with the record, not the mapper.
- `BackupManifestSerializationTest` (Surefire, pure JUnit 5, no Spring context) green with 4 cases proving:
  1. JSON has snake_case keys and no camelCase leakage.
  2. `export_date` is an ISO-8601 string (NOT a millis timestamp).
  3. `schema_version` is a JSON integer (NOT a string).
  4. Round-trip serialization → deserialization yields an equal record.
- TDD discipline observed: separate RED commit (`99fe270`, test-only, compile-fail) followed by GREEN commit (`15f738b`, record landing). Git log preserves the cycle.

## Task Commits

Each task was committed atomically:

1. **Task 1: Wave-0 BackupManifestSerializationTest stub (RED)** — `99fe270` (`test`)
   - Test class with 4 `@Test` methods + inline strict ObjectMapper.
   - Bundled the `jackson-datatype-jsr310` dependency addition (Rule 3 unblock — see Deviations below).
   - Verified RED state: `cannot find symbol: class BackupManifest` in compile log.
2. **Task 2: BackupManifest record (GREEN)** — `15f738b` (`feat`)
   - Pure Java record with per-field `@JsonProperty` snake_case keys + English JavaDoc.
   - All 4 tests turn GREEN; `./mvnw -Dtest=BackupManifestSerializationTest test` exits 0.

## Files Created/Modified

- `src/main/java/org/ctc/backup/schema/BackupManifest.java` — Wire-format record. 4 record components, per-field `@JsonProperty`, English JavaDoc explaining field semantics, D-14 deferral, and the no-BuildProperties decision (P-3).
- `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` — Surefire unit test. Inline strict ObjectMapper, 4 given/when/then test methods following CLAUDE.md naming convention.
- `pom.xml` — Added `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` dependency. Required because Spring Boot 4 ships Jackson 3 (`tools.jackson.*`) for its primary auto-configured mapper, but the backup module deliberately uses the Jackson 2.x `com.fasterxml.jackson.databind.ObjectMapper` (transitively present via flyway-core 11.x). That mapper does not register `JavaTimeModule` by default. Comment in the pom block documents the rationale.

## Decisions Made

- **Per-field `@JsonProperty` over global `PropertyNamingStrategies.SNAKE_CASE`** — keeps the JSON-shape rule co-located with the record; the strict `backupObjectMapper` (plan 03) stays free of project-wide naming rules and can be evolved independently.
- **Inline strict ObjectMapper in the unit test** — decouples this plan from plan 03's `BackupObjectMapperConfig`. The test runs in the Surefire phase (`*Test.java`), boots no Spring context, and finishes in ~0.34s.
- **No `BuildProperties` / `spring-boot-maven-plugin:build-info` wiring** — honoured RESEARCH §Pitfall P-3. CTC already exposes `app.version` via Maven resource-filtering (`@project.version@` → `application.yml` → `@Value("${app.version}")` in `GlobalModelAdvice`). Phase 73's `BackupExportService` will use the same mechanism when constructing `BackupManifest` instances.
- **First-entry ZIP write-order (D-14) deferred** — Phase 72 ships only the record. Phase 73's `BackupExportService` owns the `ZipOutputStream.putNextEntry(new ZipEntry("manifest.json"))` discipline. Documented in JavaDoc on the record itself.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added `jackson-datatype-jsr310` dependency to pom.xml**

- **Found during:** Task 1 (Wave-0 RED verification — `./mvnw test-compile`)
- **Issue:** The plan-specified test imports `com.fasterxml.jackson.datatype.jsr310.JavaTimeModule`, but that package is not on the project classpath. Spring Boot 4 ships Jackson 3 (`tools.jackson.*`) for its primary auto-configured `ObjectMapper`, while the backup module uses the Jackson 2.x `com.fasterxml.jackson.databind.ObjectMapper` (transitively present via flyway-core 11.x → `com.fasterxml.jackson.core:jackson-databind:2.21.2`). That Jackson 2.x mapper does NOT auto-register `JavaTimeModule`. Without the dependency, `test-compile` failed with `package com.fasterxml.jackson.datatype.jsr310 does not exist` — not the intended `cannot find symbol BackupManifest` RED state.
- **Fix:** Added `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` to `pom.xml` (version managed by spring-boot-starter-parent → 2.21.1). Annotated the block with an English comment explaining why the dependency is needed despite Spring Boot 4 already shipping Jackson 3.
- **Files modified:** `pom.xml` (1 dependency block + comment, ~10 lines).
- **Verification:** Re-ran `./mvnw test-compile` — RED state now correctly shows `cannot find symbol: class BackupManifest` (4 occurrences in compile log), confirming the dependency is no longer a confounder. After Task 2, `./mvnw -Dtest=BackupManifestSerializationTest test` exits 0.
- **Committed in:** `99fe270` (part of Task 1 commit — bundled with the test stub since the dependency is what allows the test to even *reach* the intended RED state).

**Note on scope:** The plan's `<files_modified>` frontmatter lists only the two `.java` files. `pom.xml` is technically outside the plan's stated scope. Bundling the dependency add with Task 1 was the cleanest path: (a) it is a true Rule 3 blocker (test cannot compile without it), (b) plan 03's `BackupObjectMapperConfig` will have the *identical* dependency need (same `JavaTimeModule` registration), so adding it now unblocks both plans without duplicating work, and (c) the commit message and this SUMMARY make the deviation fully traceable. See the **NEEDS_CONTEXT for Orchestrator** section below for the orchestrator hand-off.

---

**Total deviations:** 1 auto-fixed (1 blocking dependency)
**Impact on plan:** No scope creep. The dependency is the minimum-viable unblock for both this plan and plan 03; the change is localized to a single pom.xml dependency block with an explanatory comment.

## NEEDS_CONTEXT for Orchestrator

`pom.xml` was modified inside this worktree as a Rule 3 unblock. The orchestrator should be aware of this when merging waves so plan 03's `BackupObjectMapperConfig` (which uses the same `JavaTimeModule` import) does not also try to add the same dependency. Merging order: any wave that lands plan 03 after this plan inherits the dependency for free.

## Issues Encountered

- Initial `./mvnw test-compile` after writing the test failed with a package-not-found error rather than the intended `cannot find symbol BackupManifest` symbol-not-found error. Root cause: `jackson-datatype-jsr310` was not on the classpath. Resolved by the Rule 3 deviation documented above. After the fix, the RED state was confirmed cleanly with `grep -qE "cannot find symbol.*BackupManifest|cannot find symbol: class BackupManifest" /tmp/72-02-wave0.log` returning hits.

## Self-Check: PASSED

- File `src/main/java/org/ctc/backup/schema/BackupManifest.java` — FOUND
- File `src/test/java/org/ctc/backup/schema/BackupManifestSerializationTest.java` — FOUND
- Commit `99fe270` (test stub + jsr310 dep) — FOUND in `git log`
- Commit `15f738b` (BackupManifest record) — FOUND in `git log`
- `./mvnw -Dtest=BackupManifestSerializationTest test` exit code 0 — confirmed BUILD SUCCESS
- `grep -c '@JsonProperty' src/main/java/org/ctc/backup/schema/BackupManifest.java` ≥ 4 — returns 5 (import line + 4 annotations)
- `grep -c 'schema_version\|app_version\|export_date\|table_counts' src/main/java/org/ctc/backup/schema/BackupManifest.java` ≥ 4 — returns 4

## Next Phase Readiness

- Plan 03 (`BackupObjectMapperConfig`) can land immediately — the `JavaTimeModule` dependency it needs is now on the classpath.
- Phase 73's future `BackupExportService` can serialize `BackupManifest` instances directly through plan 03's `@Qualifier("backupObjectMapper")` `ObjectMapper`; the wire shape proven by this plan's 4 unit tests is the canonical contract.
- No coverage concerns: the plan added ~40 LOC of production code (the record) and ~100 LOC of test code (4 cases) — record components are exercised by 4 distinct test methods.

---
*Phase: 72-backup-wire-contract-schema-manifest-objectmapper-audit-log-*
*Plan: 02*
*Completed: 2026-05-11*
