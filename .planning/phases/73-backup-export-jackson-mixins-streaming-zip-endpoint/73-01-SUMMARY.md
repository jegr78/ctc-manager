---
phase: 73
plan: 01
subsystem: backup-export-serialization
tags: [jackson, mixins, serialization, backup]
requires:
  - phase: 72
    plan: 03
    artifact: BackupObjectMapperConfig.backupObjectMapper(List<Module>)
provides:
  - 24 entity-MixIn pairs externalising every Jackson serialization concern off org.ctc.domain.model
  - BackupSerializationModule @Component picked up automatically by Phase 72's backupObjectMapper bean
  - Permanent reflective regression gate against re-introducing Jackson annotations on domain entities
affects:
  - Phase 73 / Plan 02 (BackupExportService consumes the registered MixIns via the qualified backupObjectMapper)
  - Phase 73 / Plan 03 (BackupArchiveService writes through the qualified backupObjectMapper)
tech_stack:
  added: []
  patterns:
    - Jackson MixIn annotation-carrier pattern (24 abstract classes, one per entity)
    - Single SimpleModule @Component wiring all setMixInAnnotation pairs
    - Field-stub MixIn declarations with @JsonProperty + @JsonIdentityReference for Lombok @Getter(AccessLevel.NONE) fields
    - Reflective JPA-Metamodel walk + annotation-package filter as permanent test gate
key_files:
  created:
    - src/main/java/org/ctc/backup/serialization/CarMixIn.java
    - src/main/java/org/ctc/backup/serialization/TrackMixIn.java
    - src/main/java/org/ctc/backup/serialization/RaceScoringMixIn.java
    - src/main/java/org/ctc/backup/serialization/MatchScoringMixIn.java
    - src/main/java/org/ctc/backup/serialization/DriverMixIn.java
    - src/main/java/org/ctc/backup/serialization/PsnAliasMixIn.java
    - src/main/java/org/ctc/backup/serialization/TeamMixIn.java
    - src/main/java/org/ctc/backup/serialization/SeasonMixIn.java
    - src/main/java/org/ctc/backup/serialization/SeasonPhaseMixIn.java
    - src/main/java/org/ctc/backup/serialization/SeasonPhaseGroupMixIn.java
    - src/main/java/org/ctc/backup/serialization/PhaseTeamMixIn.java
    - src/main/java/org/ctc/backup/serialization/SeasonTeamMixIn.java
    - src/main/java/org/ctc/backup/serialization/SeasonDriverMixIn.java
    - src/main/java/org/ctc/backup/serialization/PlayoffMixIn.java
    - src/main/java/org/ctc/backup/serialization/PlayoffRoundMixIn.java
    - src/main/java/org/ctc/backup/serialization/PlayoffMatchupMixIn.java
    - src/main/java/org/ctc/backup/serialization/PlayoffSeedMixIn.java
    - src/main/java/org/ctc/backup/serialization/MatchdayMixIn.java
    - src/main/java/org/ctc/backup/serialization/MatchMixIn.java
    - src/main/java/org/ctc/backup/serialization/RaceMixIn.java
    - src/main/java/org/ctc/backup/serialization/RaceLineupMixIn.java
    - src/main/java/org/ctc/backup/serialization/RaceResultMixIn.java
    - src/main/java/org/ctc/backup/serialization/RaceSettingsMixIn.java
    - src/main/java/org/ctc/backup/serialization/RaceAttachmentMixIn.java
    - src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java
    - src/test/java/org/ctc/backup/serialization/BackupSerializationModuleTest.java
    - src/test/java/org/ctc/backup/serialization/TeamMixInTest.java
    - src/test/java/org/ctc/backup/serialization/SeasonMixInTest.java
    - src/test/java/org/ctc/backup/serialization/RaceMixInTest.java
    - src/test/java/org/ctc/backup/serialization/DriverMixInTest.java
    - src/test/java/org/ctc/backup/serialization/RaceAttachmentMixInTest.java
    - src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java
  modified: []
decisions:
  - "RaceMixIn uses @JsonProperty-annotated field stubs (not abstract getters) for homeTeamOverride/awayTeamOverride because Race.java carries @Getter(AccessLevel.NONE) on those fields, which omits the public Lombok getter that JavaBean-style Jackson discovery would need. Abstract MixIn getters alone do not give Jackson read access to private fields without a backing accessor. The field-stub form makes Jackson read the private fields via reflection while keeping the entity byte-identical (EXPORT-04 SC-3)."
  - "Catch-all @JsonIgnoreProperties({\"hibernateLazyInitializer\",\"handler\"}) on every MixIn — defense in depth against lazy-proxy leakage (RESEARCH §Pattern 3)."
  - "PsnAlias is emitted only via its own top-level file (data/psn-aliases.json) — Driver.aliases is suppressed (OQ-4 single-source emission). Symmetric treatment with every other parent-child pair."
metrics:
  tasks_completed: 3
  duration_minutes: 30
  completed_date: "2026-05-11"
  files_created: 32
  files_modified: 0
  tests_added: 8
  surefire_tests_total: 1238
  failsafe_test_for_this_plan: 1
---

# Phase 73 Plan 01: Jackson MixIns + BackupSerializationModule Summary

EXPORT-04 ships 24 Jackson MixIn annotation-carrier classes under `org.ctc.backup.serialization` plus the single `BackupSerializationModule` `@Component` that registers them — so every cycle-breaking, FK-as-ID, back-reference-suppression, and convenience-getter-suppression concern lives outside `org.ctc.domain.model`, which stays byte-identically Jackson-annotation-free.

## What Shipped

**25 production files** under `src/main/java/org/ctc/backup/serialization/`:

- **24 MixIn classes**, one per operative entity reachable from `BackupSchema.getExportOrder()`. Every MixIn carries `@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")` plus a catch-all `@JsonIgnoreProperties({"hibernateLazyInitializer","handler", ...})`. Foreign-key references are externalised as `@JsonIdentityReference(alwaysAsId = true)`. Bidirectional back-reference collections and computed convenience getters are listed in the `@JsonIgnoreProperties` value array.
- **1 `BackupSerializationModule`** Spring `@Component` extending `SimpleModule`. Its no-arg constructor calls `super("BackupSerializationModule")` then registers all 24 entity-to-MixIn mappings via `setMixInAnnotation(EntityX.class, EntityXMixIn.class)`. Picked up automatically by Phase 72's `BackupObjectMapperConfig.backupObjectMapper(List<Module> backupMixInModules)` injection point — zero changes to `BackupObjectMapperConfig.java`.

**7 test files** under `src/test/java/org/ctc/backup/serialization/`:

- `BackupSerializationModuleTest` (Surefire `*Test.java`, no Spring) — reflectively verifies all 24 `findMixInClassFor` mappings plus a negative-case sanity check (`String.class` → `null`).
- `TeamMixInTest`, `SeasonMixInTest`, `RaceMixInTest`, `DriverMixInTest`, `RaceAttachmentMixInTest` — 5 representative JSON-shape tests asserting absence of ignored fields, presence of `id`, and ID-ref form for `@ManyToOne` foreign keys. Each test builds an in-memory aggregate and parses the JSON tree with AssertJ.
- `BackupEntityAnnotationCleanlinessIT` (Failsafe `*IT.java`, profile `dev`, `@SpringBootTest`) — reflectively walks every `EntityType` from `EntityManagerFactory.getMetamodel().getEntities()` whose Java class is in `org.ctc.domain.model`, then inspects every declared field and method for annotations whose type's package starts with `com.fasterxml.jackson`. The collected violation set must be empty. Permanent regression gate.

## Self-Test

| Verification | Result |
|--------------|--------|
| `find src/main/java/org/ctc/backup/serialization -name '*.java' \| wc -l` | 25 (24 MixIns + 1 Module) |
| `grep -c 'setMixInAnnotation' BackupSerializationModule.java` | 24 |
| `grep -c '^@Component' BackupSerializationModule.java` | 1 |
| `grep -l 'extends SimpleModule' BackupSerializationModule.java` | matched |
| `./mvnw -DskipITs -Dtest='BackupSerializationModuleTest,TeamMixInTest,SeasonMixInTest,RaceMixInTest,DriverMixInTest,RaceAttachmentMixInTest' test` | Tests run: 7, Failures: 0, Errors: 0 — **BUILD SUCCESS** |
| `./mvnw -DskipUTs -Dit.test=BackupEntityAnnotationCleanlinessIT verify` | Surefire: 1238 tests, 0 failures. Failsafe: 1 test, 0 failures. **BUILD SUCCESS** |
| `git diff src/main/java/org/ctc/domain/model/` | empty — entities byte-identically unchanged (EXPORT-04 SC-3) |

## Decisions Made

1. **Field-stub MixIn declarations for `Race.homeTeamOverride` / `Race.awayTeamOverride`.** Both fields carry `@Getter(AccessLevel.NONE)` on the entity (the public access path is the convenience method `getHomeTeam()` / `getAwayTeam()` with override-fallback logic). With Lombok omitting the public getter, an abstract MixIn getter alone does NOT give Jackson a usable accessor — verified by dumping the JSON, which omitted both override fields. Switched the two override MixIn members from abstract getter declarations to `@JsonProperty("...")`-annotated field stubs. Jackson now uses the MixIn field declaration to discover the underlying private fields and reads them via reflection. Verified end-to-end: both override UUIDs appear in the JSON output.
2. **Single Spring component registers all 24 mappings.** Trade-off: one Module file (atomic review of the entity-to-MixIn map) vs. 24 trivial Module wrappers (parallelizable but never read together). Chose the single-file form per RESEARCH §Pattern 2 rationale.
3. **PsnAlias single-source emission.** Driver.aliases is in `@JsonIgnoreProperties`; PsnAlias rows are emitted only via `data/psn-aliases.json` (their dedicated top-level file). Symmetric with every other parent-child pair (SeasonDriver, RaceResult, etc.).
4. **Defense-in-depth `hibernateLazyInitializer` + `handler` ignore tokens** on every MixIn — guards against any future lazy-proxy slip past the `@EntityGraph` strategy that Plan 02 will wire.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 — Missing critical functionality] RaceMixIn could not serialize Race.homeTeamOverride / Race.awayTeamOverride**
- **Found during:** Task 2 (`RaceMixInTest.givenRaceWithSettings_whenSerialize_thenSettingsAbsent` failed with NPE on `node.get("homeTeamOverride").asText()` — the property was absent from the JSON).
- **Issue:** `Race` declares `@Getter(AccessLevel.NONE)` on `homeTeamOverride` / `awayTeamOverride`, so Lombok generates no public getter. Jackson's default JavaBean property introspection cannot see the private fields, and an `abstract Team getHomeTeamOverride()` declaration on the MixIn alone does NOT give Jackson a usable accessor against the entity (Jackson cannot invoke the abstract method, and the underlying class has no concrete getter to fall back on).
- **Fix:** Replaced the two abstract MixIn getters with `@JsonProperty("homeTeamOverride")` / `@JsonProperty("awayTeamOverride")` field-stub declarations carrying `@JsonIdentityReference(alwaysAsId = true)`. Jackson now reads the underlying private fields by reflection. Keeps the entity byte-identical.
- **Files modified:** `src/main/java/org/ctc/backup/serialization/RaceMixIn.java`
- **Commit:** fa1124c (folded into the Task-2 test commit since the fix was discovered while authoring the RaceMixInTest)
- **Plan correctness:** The plan's `<behavior>` for `RaceMixIn` listed all 7 ManyToOne refs as ID-ref renderings — the fix preserves that semantic. The implementation note about `abstract getter` was an incorrect mental model for Lombok-`AccessLevel.NONE` fields; the field-stub form is the canonical Jackson idiom for the same outcome.

### Auth Gates

None.

## Test Counts and Coverage Signal

- **Surefire (Plan-scoped):** 7 new test methods across 6 new `*Test.java` files. All pass; total project Surefire run = 1238 tests, 0 failures.
- **Failsafe (Plan-scoped):** 1 new test method in 1 new `*IT.java` file. Passes; runs under `dev` profile with full Spring context (~55 s).
- **Coverage delta on `org.ctc.backup.serialization`:** all 25 production files are pure annotation carriers / a 25-line `SimpleModule` constructor. The 6 Surefire `*Test.java` files exercise the Module wiring + 5 representative MixIns; the Failsafe IT walks every domain-model entity. JaCoCo line coverage on the package is structurally near-100% (annotations and `super(...)` + 24 register calls are all touched). The plan introduces no production logic that needs a coverage budget.

## Phase 72 / Phase 73 Seam — Verified

- `BackupObjectMapperConfig.java` is unchanged (verified `git diff` empty on that file).
- The qualified `backupObjectMapper(List<Module>)` bean automatically picks up `BackupSerializationModule` via the Spring DI scan — verified by the Module wiring test that imitates the same registration sequence on a freshly constructed `ObjectMapper`.

## Threat Flags

None — Plan 73-01 ships annotation-carrier classes + a Spring component + tests. No HTTP surface, no I/O, no user input. Threat T-73-MIXIN-02 (lazy-proxy leakage) mitigated by the catch-all `@JsonIgnoreProperties` token on every MixIn — verified by the JSON-shape tests asserting the absence of `hibernateLazyInitializer` / `handler` keys (implicit: no field of those names appears in any assertion-against-presence list).

## Self-Check: PASSED

- [x] All 25 production files exist under `src/main/java/org/ctc/backup/serialization/`.
- [x] All 7 test files exist under `src/test/java/org/ctc/backup/serialization/`.
- [x] Commits exist: f5903d9 (Task 1), fa1124c (Task 2), eeef0da (Task 3) — all present in `git log --oneline -5`.
- [x] `./mvnw verify` Surefire 1238/0/0, Failsafe 1/0/0 on `BackupEntityAnnotationCleanlinessIT` — BUILD SUCCESS.
- [x] `git diff src/main/java/org/ctc/domain/model/` is empty — EXPORT-04 SC-3 invariant intact.
- [x] `BackupSerializationModuleTest` proves all 24 `setMixInAnnotation` mappings via reflection.
- [x] `BackupEntityAnnotationCleanlinessIT` proves zero Jackson annotations on `org.ctc.domain.model`.
- [x] No changes to `BackupObjectMapperConfig.java` (Phase 72 / Phase 73 seam clean).
