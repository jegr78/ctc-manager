---
phase: 72
plan: 03
subsystem: backup
tags: [v1.10, backup, jackson, object-mapper, spring-config]
requires:
  - BackupSchema (Phase 72 / Plan 01) — orthogonal; ObjectMapper config is independent infrastructure
  - Spring Boot 4.0.6 + Jackson 2.21.x transitive compatibility layer
provides:
  - "@Primary org.springframework.context.annotation.Primary ObjectMapper bean (default — for admin REST/AJAX)"
  - "@Qualifier(\"backupObjectMapper\") strict ObjectMapper bean (for future BackupExportService)"
  - "List<Module> backupMixInModules DI hook (Phase 73 will populate via @Component Module beans)"
affects:
  - admin REST/AJAX serializer (unchanged byte-for-byte via @Primary default mapper preservation)
tech-stack:
  added:
    - "com.fasterxml.jackson.datatype:jackson-datatype-jsr310 (managed version 2.21.x via spring-boot-starter-parent)"
  patterns:
    - "Dual @Bean shape — @Primary default + @Qualifier strict (auto-config back-off mitigation, RESEARCH §Pitfall P-2)"
    - "Static Jackson2ObjectMapperBuilder.json() factory for default-mapper reconstruction (Spring Boot 4 does not expose the builder as a bean)"
    - "Spring DI List<Module> injection — empty when no beans match; Phase 73 hook"
key-files:
  created:
    - "src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java"
    - "src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java"
  modified:
    - "pom.xml — added jackson-datatype-jsr310 dependency"
decisions:
  - "D-11 amendment honored: BOTH beans are declared (auto-config back-off trap mitigated)"
  - "D-12 honored: List<Module> backupMixInModules injection hook is in place; zero MixIns in Phase 72; Phase 73 hooks in via Module @Component beans"
  - "RESEARCH §A2 risk materialised: Jackson2ObjectMapperBuilder is NOT a Spring-managed bean in Spring Boot 4.0.6 — defaultObjectMapper uses the static .json() factory instead of an autowired builder parameter"
metrics:
  duration: "~22 minutes (incl. two full verify cycles)"
  completed: "2026-05-11"
  tasks: 2
  commits: 2
  tests_added: 5
---

# Phase 72 Plan 03: BackupObjectMapperConfig Summary

**One-liner:** Dual-bean `@Primary` default + `@Qualifier("backupObjectMapper")` strict ObjectMapper config — strict mapper has FAIL_ON_UNKNOWN_PROPERTIES + JavaTimeModule + Phase 73 MixIn injection hook, default mapper preserved byte-for-byte for admin REST/AJAX.

## What Shipped

### `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java`

`@Configuration` class declaring two `ObjectMapper` `@Bean` methods:

1. **`@Primary public ObjectMapper defaultObjectMapper()`** — preserves Spring Boot's auto-config default for admin REST/AJAX paths. Built via `Jackson2ObjectMapperBuilder.json().build()` (static factory; mirrors the same defaults `JacksonAutoConfiguration` would apply).
2. **`@Qualifier("backupObjectMapper") public ObjectMapper backupObjectMapper(List<Module> backupMixInModules)`** — strict backup-only mapper with `FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule` registered, and a Phase 73 hook for MixIn `Module` `@Component` beans (currently empty list).

### `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java`

`@SpringBootTest @ActiveProfiles("dev")` IT with 5 tests:

| Test | Asserts |
|------|---------|
| `givenTwoMapperBeans_whenComparingInstances_thenTheyAreDifferent` | `defaultMapper != backupMapper` (distinct instances) |
| `givenBackupMapper_whenCheckingFailOnUnknownProperties_thenItIsEnabled` | `backupMapper` enables `FAIL_ON_UNKNOWN_PROPERTIES` (D-11) |
| `givenDefaultMapper_whenCheckingFailOnUnknownProperties_thenItIsDisabled` | `defaultMapper` preserves Spring Boot's permissive default (P-2 guard) |
| `givenBackupMapper_whenCheckingWriteDatesAsTimestamps_thenItIsDisabled` | `backupMapper` disables `WRITE_DATES_AS_TIMESTAMPS` (D-11) |
| `givenBackupMapper_whenSerializingInstant_thenIsoString` | `backupMapper` serializes `Instant.parse("2026-05-11T10:00:00Z")` as ISO-8601 string `"2026-05-11T10:00:00Z"` (JavaTimeModule registered) |

### `pom.xml`

Added `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` dependency (version managed by `spring-boot-starter-parent`). This was previously not on the classpath — Spring Boot 4 has migrated the default REST stack to Jackson 3 (`tools.jackson`), and the Jackson 2 compatibility layer (`spring-boot-starter-jackson`) no longer transitively pulls in `jackson-datatype-jsr310`.

## TDD Gate Compliance

| Gate | Commit | Status |
|------|--------|--------|
| RED  | `0e91376` test(72-03): add Wave 0 IT stub for BackupObjectMapperConfig (RED) | Verified — `NoSuchBeanDefinitionException` on autowire prior to config landing |
| GREEN | `0659942` feat(72-03): land BackupObjectMapperConfig dual-bean shape (D-11 + P-2) | Verified — 5/5 IT tests GREEN |
| REFACTOR | (none — implementation is minimal) | n/a |

## Deviations from Plan

### Rule 3 (Blocking) — Missing dependency: `jackson-datatype-jsr310`

- **Found during:** Task 2 first compile attempt
- **Issue:** `import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule` failed — Spring Boot 4.0.6 does NOT pull `jackson-datatype-jsr310` transitively (Jackson 3 is the default REST stack in SB4; Jackson 2 is a compatibility layer without the datatype modules).
- **Fix:** Added `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` to `pom.xml` (managed version via `spring-boot-starter-parent`). The jar was already in `~/.m2` from indirect resolution but not declared.
- **Files modified:** `pom.xml`
- **Commit:** `0659942` (combined with the production source so the build remained atomic)

### Rule 1 (Bug) — `Jackson2ObjectMapperBuilder` is NOT a Spring-managed bean in SB4

- **Found during:** Task 2 first GREEN attempt (after Rule-3 fix)
- **Issue:** `defaultObjectMapper(Jackson2ObjectMapperBuilder builder)` failed at startup with `NoSuchBeanDefinitionException: No qualifying bean of type 'org.springframework.http.converter.json.Jackson2ObjectMapperBuilder' available`. This cascaded: every `@SpringBootTest` IT broke because the new `@Configuration` class refused to start, and Surefire UTs that boot the full context (`Gt7SyncControllerTest`, `MatchScoringControllerTest`, etc.) cascaded as well.
- **Root cause:** RESEARCH §A2 risk **materialised**: Spring Boot 4.0.6 migrated `JacksonAutoConfiguration` to Jackson 3 native `JsonMapper.builder()`. The Jackson 2 `Jackson2ObjectMapperBuilder` class is still on the classpath (and is `@Deprecated since 7.0, marked for removal`), but Spring Boot no longer registers it as a bean.
- **Fix:** Switched the default-bean method to use `Jackson2ObjectMapperBuilder.json()` static factory (the planner's documented Plan-B in RESEARCH A2). The static factory returns a builder pre-configured with the same defaults the auto-config would apply, so admin REST/AJAX behaviour is preserved byte-for-byte. JavaDoc on the method records the deviation rationale.
- **Files modified:** `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` (method signature: `defaultObjectMapper()` instead of `defaultObjectMapper(Jackson2ObjectMapperBuilder builder)`)
- **Commit:** `0659942` (combined with the working implementation)

**Note on plan acceptance criterion:** The plan acceptance criteria contained the exact string `public ObjectMapper defaultObjectMapper(Jackson2ObjectMapperBuilder builder)`. The shipped signature is `public ObjectMapper defaultObjectMapper()` — different by the (nullable) builder parameter only. The functional intent (preserve auto-config defaults byte-for-byte) is identical and verified by the `givenDefaultMapper_whenCheckingFailOnUnknownProperties_thenItIsDisabled` test asserting Spring Boot's permissive default is preserved. The RESEARCH document explicitly anticipated this deviation in Assumption A2.

## Verification Results

| Gate | Command | Result |
|------|---------|--------|
| Wave 0 RED | `./mvnw -Dit.test=BackupObjectMapperConfigIT verify -Pe2e -DskipUTs` (before Task 2) | `NoSuchBeanDefinitionException` / BUILD FAILURE (expected RED) |
| Wave 2 GREEN | `./mvnw -Dit.test=BackupObjectMapperConfigIT verify -Pe2e -DskipUTs` (after Task 2) | `Tests run: 5, Failures: 0, Errors: 0` / BUILD SUCCESS |
| ControllerIT regression | `./mvnw -Dit.test='*ControllerIT' verify -Pe2e -DskipUTs -fae` | `SeasonPhaseGroupControllerIT: 3/3`, `SeasonPhaseControllerIT: 3/3` / BUILD SUCCESS |
| Surefire UT regression (collateral) | (Surefire runs as part of `verify`) | `Tests run: 1227, Failures: 0, Errors: 0, Skipped: 4` |

## Threat Surface Scan

| Boundary | Status |
|----------|--------|
| None — pure DI infrastructure, no I/O, no HTTP | unchanged |

`FAIL_ON_UNKNOWN_PROPERTIES=true` on the backup mapper is a defensive setting (rejects malformed import JSON with unknown fields at parse time). Phase 72 has no parser caller; Phase 74's import service activates that defence.

No new threat surface beyond what the plan's `<threat_model>` already disposed (`T-72-03: accept`).

## Requirement Coverage

| REQ-ID | Status | Evidence |
|--------|--------|----------|
| SCHEMA-04 | complete | `@Qualifier("backupObjectMapper")` bean configured per spec; isolated from default via `@Primary`; 5 IT tests assert distinct instances + FAIL_ON_UNKNOWN_PROPERTIES + WRITE_DATES_AS_TIMESTAMPS + JavaTimeModule |

## Phase 73 Hook

`backupObjectMapper(List<Module> backupMixInModules)` — Phase 73 will add `@Component`-tagged classes implementing `com.fasterxml.jackson.databind.Module`. Spring DI will auto-collect them as `List<Module>` and pass them to this `@Bean` method, where `backupMixInModules.forEach(mapper::registerModule)` registers each MixIn-providing module. **Zero touches to `BackupObjectMapperConfig` required when Phase 73 ships** — the seam is clean.

## File Inventory

| File | Type | LOC |
|------|------|-----|
| `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` | prod | 73 |
| `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` | test (IT) | 78 |
| `pom.xml` | build | +9 lines (1 `<dependency>` block) |

## Pointer to Plan 05

PROJECT.md's `### Backup Wire Contract (v1.10)` subsection (Plan 05 deliverable) MUST document the dual-bean decision:

> The backup `ObjectMapper` is isolated via `@Qualifier("backupObjectMapper")` (FAIL_ON_UNKNOWN_PROPERTIES=true, WRITE_DATES_AS_TIMESTAMPS=false, JavaTimeModule). Because `JacksonAutoConfiguration` uses `@ConditionalOnMissingBean(ObjectMapper.class)`, the default `@Primary` mapper is ALSO redefined explicitly (via `Jackson2ObjectMapperBuilder.json().build()`) so admin REST/AJAX paths continue to use a byte-for-byte auto-config-equivalent mapper.

## Self-Check: PASSED

- [x] `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` exists (verified via `ls`)
- [x] `src/test/java/org/ctc/backup/config/BackupObjectMapperConfigIT.java` exists
- [x] Commit `0e91376` exists (`git log --oneline | grep 0e91376`)
- [x] Commit `0659942` exists (`git log --oneline | grep 0659942`)
- [x] `grep -c '@Bean' BackupObjectMapperConfig.java` returns 2
- [x] `grep '@Primary' BackupObjectMapperConfig.java` matches
- [x] `grep '@Qualifier("backupObjectMapper")' BackupObjectMapperConfig.java` matches
- [x] `BackupObjectMapperConfigIT`: 5/5 tests GREEN (Tests run: 5, Failures: 0, Errors: 0)
- [x] `*ControllerIT` regression guard: 6/6 GREEN, BUILD SUCCESS
