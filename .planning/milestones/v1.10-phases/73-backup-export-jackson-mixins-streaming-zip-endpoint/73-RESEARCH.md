# Phase 73 — Backup Export — Jackson MixIns + Streaming ZIP Endpoint — Research

**Researched:** 2026-05-11
**Domain:** Spring Boot 4.0.6 / Hibernate 7 / Jackson 2.21.x — externalised serialization (MixIns) + StreamingResponseBody ZIP + CSRF-protected admin POST
**Confidence:** HIGH (all infrastructure verified against the Phase 72 artefacts already on the branch and against the live `org.ctc.domain.model` package)

<user_constraints>

## User Constraints (from upstream)

### Locked Decisions (Phase 72 CONTEXT / RESEARCH — Phase 73 inherits verbatim)

- **L-72.D-01:** Round-trip self-containment includes `Car` + `Track`. Both classes are FK-leaves and sit at the **head** of `BackupSchema.exportOrder`. Phase 73 ships `CarMixIn` + `TrackMixIn`.
- **L-72.D-02:** `FeatureSettings` is OUT of scope (the class does not exist in `org.ctc.domain.model`). Phase 73 ships NO `FeatureSettingsMixIn`.
- **L-72.D-03 amendment:** Final operative entity count is **24** (the Phase 72 `BackupSchemaTopologyIT.givenSpringContext_whenGetExportOrder_thenReturns24Entities` asserts 24). The UI-SPEC description copy currently reads "23 entities" — this RESEARCH carries an Open Question (OQ-1) recommending the copy be updated to 24 before merging.
- **L-72.D-04:** `EXPORT_ORDER` is generated at runtime from the JPA Metamodel via Kahn's algorithm; **never hand-write the list**. `BackupExportService` consumes `BackupSchema.getExportOrder()`.
- **L-72.D-06:** Package-scope filter `startsWith("org.ctc.domain.model")` is the IMPORT-08 structural exclusion mechanism. `DataImportAudit` (under `org.ctc.backup.audit`) is automatically excluded.
- **L-72.D-07:** `EntityRef(Class<?> entityClass, String tableName, String fileName)` is the locked shape. `fileName` is already `data/<kebab-case-table>.json` — Phase 73 reuses it directly.
- **L-72.D-11/P-2 amendment:** ObjectMapper isolation uses the **dual-bean shape** already shipped in `BackupObjectMapperConfig`:
  - `@Primary defaultObjectMapper()` (reconstructed via static `Jackson2ObjectMapperBuilder.json().build()` — Phase 72 Plan-03 Rule-1 deviation, see 72-03-SUMMARY).
  - `@Qualifier("backupObjectMapper") backupObjectMapper(List<Module> backupMixInModules)` — strict (`FAIL_ON_UNKNOWN_PROPERTIES=true`, `WRITE_DATES_AS_TIMESTAMPS=false`, `JavaTimeModule` registered).
- **L-72.D-12:** **MixIn injection hook is `List<Module>`.** Each Phase 73 MixIn `@Component` bean must implement `com.fasterxml.jackson.databind.Module` (typically by extending `com.fasterxml.jackson.databind.module.SimpleModule`) and self-register via `setupModule(SetupContext)` — Spring DI then injects them into `backupObjectMapper()` without touching `BackupObjectMapperConfig`. **`addMixIn(Entity.class, MixIn.class)` calls live inside each Module's `setupModule`**, not in the config class.
- **L-72.D-13/P-3:** `appVersion` comes from `@Value("${app.version}")` (Maven-filtered `@project.version@`), **NOT** from `BuildProperties`. No `pom.xml` change in Phase 73.
- **L-72.D-14:** `manifest.json` MUST be the FIRST `ZipEntry` written. This is Phase 73's discipline (the contract was documented in Phase 72; the writer enforces it).

### Phase 73 UI-SPEC Locks (from `73-UI-SPEC.md`)

- Page URL: `GET /admin/backup` → template `src/main/resources/templates/admin/backup.html`.
- Action URL: `POST /admin/backup/export` → streaming ZIP download.
- Sidebar: new top-level group **`Data`** containing one entry **`Backup`** between the existing `Tools` group (closes at `Generate Site`) and `</nav>`.
- Button: `.btn .btn-primary .btn-lg` with label `Export Backup`. **NO inline styles. NO JavaScript. NO progress UI.**
- Page H1: `Backup`. Description paragraph copy is locked (the "23 entities" wording — see OQ-1).
- Flash messages on the failure-before-stream branch:
  - Success (rare): `Backup exported successfully.`
  - Error: `Backup export failed. See application log for details.`
- All UI text **English**. No emoji. No "Are you sure?" copy.
- CSRF is auto-injected by Thymeleaf on `<form th:action method="post">` in prod/docker; disabled entirely in dev/local via `OpenSecurityConfig.csrf.disable()`.

### Claude's Discretion (this phase decides)

- The exact shape of each MixIn class (interface? abstract class? `SimpleModule` subclass? — see §MixIn Design).
- Whether `BackupExportService` fetches each entity type via stock `JpaRepository.findAll()` (with `@EntityGraph` on a dedicated repository method) or via a single `EntityManager.createQuery("FROM " + entityClass.getName(), entityClass)` loop with a `@EntityGraph` hint applied via JPA hint API.
- Whether to introduce a new `BackupArchiveService` (write-only, ZIP plumbing) separate from `BackupExportService` (DB read + dispatch), or fold both into one service. **Recommendation: split.**
- The exact ISO-Instant filename format (the colon-vs-Windows tradeoff — see Landmine §3 and §5).
- Whether the `uploads/` mirror is a literal byte-for-byte copy of `data/{profile}/uploads/` or a filtered set restricted to files referenced by `Team.logoUrl` / `SeasonTeam.logoUrl` / `Car.imageUrl` / `Track.imageUrl` / `RaceAttachment.url`. ROADMAP SC-2 explicitly says "entity-referenced files" — **the filter is mandatory**, but the implementation strategy (whitelist enumeration vs. blacklist exclusion of orphans) is open.
- The number of MixIn `@Component` beans (one per entity = 24, or one Module containing all `addMixIn(...)` calls). **Recommendation: one Module per entity** for parallelizable Wave-2 tasks and clean unit-test surface.

### Deferred Ideas (OUT OF SCOPE for Phase 73)

- Anything import-side (preview, schema-version gate, ZIP-Slip / ZipBomb defenses, multipart limits, `MaxUploadSizeExceededException` mapping) — Phase 74.
- Replace-All transaction, JPA Auditing bypass, post-commit upload-tree restore — Phase 75.
- Concurrent-import lock, read-only banner, auto-backup-before-import — Phase 76.
- `BackupRoundTripIT`, README + WIKI, JaCoCo gate, final UAT — Phase 77.
- Export audit log / "last export" timestamp / progress UI / cancel button — explicitly out per UI-SPEC and orchestrator brief.
- Per-Saison-Export selectivity — `EXPORT-FUT-01` (v1.11+).
- SHA-256 `manifest.sha256` sidecar — `EXPORT-FUT-02` (v1.11+).
- Encryption at rest — `SECU-FUT-01` (v1.11+).

</user_constraints>

<phase_requirements>

## Phase Requirements (REQUIREMENTS.md EXPORT-01..06)

| ID | Description (German source, English summary in brackets) | Research Support |
|----|----------------------------------------------------------|------------------|
| **EXPORT-01** | Admin-Sidebar-Button `Backup` → `GET /admin/backup` Form-Page with Export- und Import-Aktionen [Sidebar entry + landing page] | §Controller & Security Pattern 1 (`BackupController.showForm()` + `backup.html` per UI-SPEC) |
| **EXPORT-02** | `POST /admin/backup/export` streams ZIP via `StreamingResponseBody`, `Content-Disposition: attachment; filename=ctc-backup-{ISO-instant}.zip`, no memory buffering [Streaming download] | §Streaming ZIP Architecture (`StreamingResponseBody` + `ResponseEntity` header pattern); §Landmine L-3 (filename charset) |
| **EXPORT-03** | ZIP contains `manifest.json` (FIRST entry), per-entity JSON under `data/`, `uploads/` mirror of entity-referenced files | §Streaming ZIP Architecture (Pattern 2 + 3 + 4); §Landmine L-5 (manifest size deferred) |
| **EXPORT-04** | ~22→24 Per-Entity Jackson MixIns under `org.ctc.backup.serialization` apply `@JsonIdentityInfo` externally; entities in `org.ctc.domain.model` unchanged | §Entity Catalog (24 rows); §MixIn Design (Pattern 1, 2, 3); §Landmine L-1 (Hibernate6Module not needed in Jackson 2.x setup) |
| **EXPORT-05** | `BackupExportService` is `@Transactional(readOnly=true)`, explicit `@EntityGraph` eager-fetches, no `LazyInitializationException` during streaming | §EntityGraph Fetch Map; §Landmine L-7 (OSIV vs. StreamingResponseBody — service `@Transactional` IS the load-bearing guard) |
| **EXPORT-06** | Export endpoint CSRF-protected (token header for AJAX, form-token for POST, Spring Security 7 default in prod/docker) | §Controller & Security Pattern 2 (CSRF) + Pattern 3 (Profile-conditional auth); §Validation Architecture (Security tests) |

</phase_requirements>

## Project Constraints (from CLAUDE.md)

| Constraint | Source | Phase 73 Impact |
|-----------|--------|-----------------|
| Test coverage ≥ 82 % line coverage | "Constraints" | New ~24 MixIns + 1 service + 1 controller + 1 archive helper + 1 form template. Plan an aggressive unit-coverage strategy (reflective MixIn shape test + manifest round-trip + ZIP-layout IT). |
| Do NOT change V1 (or any V*.sql) | "Constraints" + "Do Not Modify Flyway Migrations" | Phase 73 ships ZERO Flyway changes. |
| Auth only for prod/docker; dev/local without auth | "Constraints" | `SecurityConfig` (`@Profile({"prod","docker"})`) inherits — new `/admin/backup/**` routes are caught by the existing `anyRequest().authenticated()` matcher with no further wiring. |
| OSIV stays enabled — `@EntityGraph` only for fetch optimization | "Constraints" | All per-entity fetches use `@EntityGraph(attributePaths = ...)` on dedicated repository finders — **do not** flip `FetchType.LAZY` → `EAGER` on entity classes. |
| No breaking URL changes | "Constraints" | New `/admin/backup` and `/admin/backup/export` only. No existing route renamed. |
| DTOs in controllers, not entities for POST binding | "Architectural Principles" | The POST has NO request body — there's nothing to bind. No form DTO needed for Phase 73 (Phase 74 will introduce `BackupImportForm` for the multipart upload). |
| No inline styles on buttons | "Architectural Principles" | UI-SPEC explicitly enforces `.btn .btn-primary .btn-lg` — no `style="..."` anywhere in `backup.html`. |
| English UI text | "Language" | UI-SPEC locks every visible string. |
| Lombok: `@RequiredArgsConstructor` + `@Slf4j` on services/controllers | "Lombok Usage" | `BackupController`, `BackupExportService`, `BackupArchiveService` all follow. |
| Test naming Given-When-Then | "Test Naming" | Every new test follows `givenContext_whenAction_thenExpectedResult()`. |
| Surefire = `*Test.java` (unit); Failsafe (`-Pe2e` or `verify`) = `*IT.java` | TESTING.md L8-L17 | MixIn shape tests are unit (`*Test.java`); full export ITs (`@SpringBootTest`) are `*IT.java`; E2E download interception lives in `src/test/java/org/ctc/e2e/`. |
| Communication German, code/comments English | "Language" | All JavaDoc / inline comments English. |

## Summary

Phase 73 lands the visible Backup feature on top of the wire-contract foundation Phase 72 already shipped. Three structurally independent pieces compose:

1. **MixIn-driven Jackson serialization (EXPORT-04).** Every domain entity that surfaces in the export gets a paired MixIn class under `org.ctc.backup.serialization`. Each MixIn is a Jackson `Module` `@Component` bean — Phase 72's `BackupObjectMapperConfig.backupObjectMapper(List<Module>)` picks them all up automatically. The MixIn applies `@JsonIdentityInfo` (object-by-ID references to break cycles), `@JsonManagedReference` / `@JsonBackReference` (parent-child traversal control), and `@JsonIgnore` (suppress lazy `subTeams`, `seasonDrivers`, bidirectional back-references that explode into the whole graph). **Domain entities under `org.ctc.domain.model` stay byte-identical** (success criterion 3 — verified by a grep test).

2. **`BackupExportService` (EXPORT-05).** `@Transactional(readOnly=true)`, injects `BackupSchema`, `@Qualifier("backupObjectMapper") ObjectMapper`, and 24 repositories. For each `EntityRef` in `BackupSchema.getExportOrder()`, calls a `repository.findAllForBackup()` method annotated with `@EntityGraph(attributePaths = {...})` that loads every association the MixIns will traverse. Returns a JSON byte payload (or directly streams through Jackson's `SequenceWriter`) per entity.

3. **`BackupArchiveService` + `BackupController` (EXPORT-01..03 + EXPORT-06).** The controller has two handlers: `GET /admin/backup` (renders the template per UI-SPEC) and `POST /admin/backup/export` (returns `ResponseEntity<StreamingResponseBody>`). `BackupArchiveService.writeZip(OutputStream)` is called from inside the `StreamingResponseBody` lambda: opens a `ZipOutputStream`, writes `manifest.json` as entry #1, iterates `BackupSchema.getExportOrder()` writing `data/<entity>.json` entries in order, then mirrors entity-referenced files into `uploads/`. CSRF protection is inherited from `SecurityConfig` (prod/docker) and disabled in `OpenSecurityConfig` (dev/local). Anonymous POST on prod/docker → HTTP 401 — verified by a `@Nested @ActiveProfiles("prod")` MockMvc test mirroring `SecurityIntegrationTest`.

**Primary recommendation:** Land in three waves: (Wave 1) all 24 MixIns + their unit tests + repository `findAllForBackup()` methods + a `BackupSerializationRoundTripTest` over a hand-crafted in-memory aggregate. (Wave 2) `BackupExportService` + `BackupArchiveService` + `BackupExportServiceIT` (full Saison-2023+2024-3 fixture, asserts no LIE, asserts ZIP layout, asserts manifest-first). (Wave 3) `BackupController` + `backup.html` template + sidebar `Data` group + `BackupControllerSecurityIT` + Playwright E2E download test. Wave 1 is fully parallelizable across 24 MixIn tasks.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Sidebar navigation | Frontend (Thymeleaf) | Layout fragment | `admin/layout.html` is the single source of truth for the sidebar; the new `Data` group goes there. |
| Page rendering (`/admin/backup`) | Admin / Controller | Thymeleaf | `BackupController.showForm()` returns the view name; template handles HTML. Thin-controller pattern (CLAUDE.md). |
| POST handler + streaming | Admin / Controller | Spring MVC StreamingResponseBody | `BackupController.export()` returns `ResponseEntity<StreamingResponseBody>`. Wrapper is HTTP-concern; payload generation is delegated to `BackupArchiveService`. |
| ZIP plumbing (entries, ordering, file mirror) | Backup / Archive | `java.util.zip.ZipOutputStream` | Pure I/O orchestration. No DB access, no HTTP knowledge. |
| DB read aggregate | Backup / Export Service | JPA / Hibernate | `@Transactional(readOnly=true)`. Walks `BackupSchema.exportOrder` and calls per-entity `findAllForBackup()` (with `@EntityGraph`). |
| Per-entity JSON serialization | Backup / Serialization | Jackson 2 (`ObjectMapper` from `BackupObjectMapperConfig`) | `backupObjectMapper.writerWithDefaultPrettyPrinter().writeValue(...)` (or `SequenceWriter` for streaming). MixIns drive the byte-level shape. |
| Externalised Jackson annotations | Backup / Serialization (MixIns) | `org.ctc.backup.serialization.*Mixin` | One MixIn per entity. Entities in `org.ctc.domain.model` stay annotation-clean. |
| CSRF token rendering | Frontend (Thymeleaf) | Spring Security | `<form method="post">` auto-injects `_csrf` hidden field via Thymeleaf's Spring Security dialect. Already enabled by existing config; no Phase 73 wiring needed. |
| Anonymous-rejection on prod/docker | Security / SecurityConfig | Spring Security 7 | Existing `anyRequest().authenticated()` matcher catches `/admin/backup/**` automatically. No new matcher rule. |
| Entity-referenced upload mirror | Backup / Archive | FileStorageService (read-only) | `BackupArchiveService` enumerates upload paths from entity fields (`Team.logoUrl`, etc.), resolves to filesystem paths via `app.upload-dir`, copies entries into ZIP under `uploads/`. |

**Tier check:** Every new piece lives in the right tier. The controller does NOT load entities (delegates to service). The service does NOT write to the response stream directly (returns serialized bytes / writes through the archive helper). The archive helper does NOT call repositories (receives serialized payloads from the service). The MixIns do NOT touch I/O. This separation makes Wave-1 / Wave-2 / Wave-3 independently testable.

## Standard Stack

### Core (already on the classpath — zero new Maven coordinates)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 4.0.6 | Framework | [VERIFIED: pom.xml L8] |
| Spring MVC / `StreamingResponseBody` | 7.x (transitive) | Async write-to-OutputStream for large downloads | [CITED: docs.spring.io — StreamingResponseBody renders OUTSIDE the controller thread, freeing the request-processing thread; ideal for ZIP streaming where I/O dominates] |
| Jackson Databind (Jackson 2) | 2.21.x (managed via spring-boot-starter-parent) | `ObjectMapper`, `addMixIn(...)`, `@JsonIdentityInfo`, `SimpleModule` | [VERIFIED: pom.xml L78-L81 — `jackson-datatype-jsr310` declared in Phase 72-03; the rest is transitive via `spring-boot-starter-jackson`. Phase 72's `BackupObjectMapperConfig.backupObjectMapper(...)` uses `com.fasterxml.jackson.databind.ObjectMapper` — i.e. **Jackson 2**, not Jackson 3. Phase 73 MixIns import from `com.fasterxml.jackson.annotation.*` and extend `com.fasterxml.jackson.databind.module.SimpleModule`.] |
| `java.util.zip.ZipOutputStream` | JDK 25 stdlib | ZIP writer | [VERIFIED: existing precedent in `TeamCardController.downloadAll()` — same JDK API, lines 118-128 of TeamCardController.java] |
| JPA `@EntityGraph` | Spring Data JPA (transitive) | Per-query eager-fetch hint | [VERIFIED: 14 repositories already use `@EntityGraph` — `SeasonDriverRepository`, `RaceResultRepository`, `MatchdayRepository`, `RaceLineupRepository`, `PlayoffMatchupRepository`, `PlayoffSeedRepository`, `SeasonTeamRepository` — the pattern is well-established] |
| Spring Security 7 | (transitive via spring-boot-starter-security) | CSRF + profile-conditional auth | [VERIFIED: `SecurityConfig.java` exists, `OpenSecurityConfig.java` exists; CSRF default-on in prod/docker per Spring Security 7] |
| Lombok | 1.18.x (transitive) | `@RequiredArgsConstructor`, `@Slf4j` on service + controller | [VERIFIED: CONVENTIONS.md L58-L68] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.springframework.security.test` | 7.x (transitive) | `@WithMockUser`, `@WithAnonymousUser` for `BackupControllerSecurityIT` | Mirrors the existing `SecurityIntegrationTest` pattern (file already on disk at `src/test/java/org/ctc/admin/SecurityIntegrationTest.java`) |
| Playwright | 1.59.0 (compile scope) | E2E download-interception test | Existing `PlaywrightConfig` base class — use `page.waitForDownload()` for the ZIP download |

### Deliberately NOT Added

| Library | Reason it could be tempting | Why we REJECT it |
|---------|----------------------------|------------------|
| `jackson-datatype-hibernate6` | Eager strip of Hibernate proxies during serialization | We are doing **explicit eager fetch** via `@EntityGraph` (EXPORT-05). The proxies are unwrapped at fetch time, not at serialization time. Adding the Hibernate module would mask bugs (proxies leaking to serializer = sign of insufficient eager fetch). Reject. See Landmine L-1. |
| `jackson-datatype-jdk8` | `Optional<T>` / `OptionalInt` serialization | None of the 24 entities expose `Optional` fields. Reject. |
| Apache Commons Compress | Richer ZIP API (e.g. ZIP64 for >4 GiB) | Current dataset is far below 4 GiB; JDK `ZipOutputStream` is sufficient and is already in use by `TeamCardController`. v1.11+ may revisit if dataset grows. Reject. |
| A reactive library (WebFlux, Reactor) for streaming | "Streaming feels reactive" | The project is Spring MVC + Thymeleaf SSR. `StreamingResponseBody` is the MVC-native streaming primitive (Spring MVC's `OutputStream`-backed handler). Reject. |

**Installation:** No `pom.xml` modification needed in Phase 73.

**Version verification:**

```bash
grep -E "spring-boot-starter-parent|jackson-databind|jackson-datatype-jsr310" /Users/jegr/Documents/github/ctc-manager/pom.xml | head
```

Output (verified 2026-05-11):
- `spring-boot-starter-parent: 4.0.6` (pom.xml L8)
- `jackson-datatype-jsr310: managed by spring-boot-starter-parent (2.21.x)` (pom.xml L78-L81 — added in Phase 72 Plan 03)
- Jackson 2 databind is transitive via the Phase 72 `BackupObjectMapperConfig` import `com.fasterxml.jackson.databind.ObjectMapper`.

## Entity Catalog

The 24 entities reachable via `BackupSchema.getExportOrder()` (verified by reading `Metamodel.getEntities()` filter `org.ctc.domain.model.*` at startup; cross-checked against `ls src/main/java/org/ctc/domain/model/`). Three columns named `@ManyToOne FKs`, `@OneToMany / collection fields` and `MixIn requirement` summarise what each MixIn must do.

> Order shown is the topological order Kahn's algorithm produces (FK-leaves first → most-dependent last). The **authoritative runtime order** comes from `BackupSchema.getExportOrder()`. The illustrative order below matches the Phase-72 RESEARCH §D-03 illustrative ordering, adjusted for `PlayoffRound`.

| # | Entity | `@ManyToOne` FKs | `@OneToMany` / collection fields | MixIn requirement |
|---|--------|------------------|---------------------------------|-------------------|
| 1 | `Car` | (none) | (none) | `@JsonIdentityInfo` on `id`. Plain serialization. Has `imageUrl` — referenced by `uploads/` mirror. |
| 2 | `Track` | (none) | (none) | `@JsonIdentityInfo`. Has `imageUrl`. |
| 3 | `RaceScoring` | (none) | (none) | `@JsonIdentityInfo`. `racePoints` / `qualiPoints` are `String` CSV columns; native string. |
| 4 | `MatchScoring` | (none) | (none) | `@JsonIdentityInfo`. Plain. |
| 5 | `Driver` | (none) | `seasonDrivers`, `raceResults`, `aliases` | `@JsonIdentityInfo`. **Ignore `seasonDrivers` + `raceResults`** (back-refs that explode into the whole graph — they are owned by `SeasonDriver` / `RaceResult`). **Serialize `aliases`** as embedded children (orphanRemoval; cascading owner). |
| 6 | `PsnAlias` | `driver` | (none) | `@JsonIdentityInfo`. Render `driver` as `@JsonIdentityReference(alwaysAsId=true)` (driver already serialized at depth #5; emit only `{"driver":"<uuid>", ...}`). |
| 7 | `Team` | `parentTeam` (self-FK!) | `subTeams`, `seasonDrivers` | `@JsonIdentityInfo`. `parentTeam` → `@JsonIdentityReference(alwaysAsId=true)` (other `Team` rows are serialized in the same file; the writer emits the parent ID, the import resolves it during the second pass). **Ignore `subTeams`** (derivable from `parentTeam`). **Ignore `seasonDrivers`** (owned by `SeasonDriver`). |
| 8 | `Season` | (none) | `phases`, `seasonDrivers`, `seasonTeams`, `cars`, `tracks` | `@JsonIdentityInfo`. **`cars` + `tracks`** are `@ManyToMany` — serialize as a list of UUID references (`@JsonIdentityReference(alwaysAsId=true)` collection element). **Ignore `phases`, `seasonDrivers`, `seasonTeams`** (owned by child entities; will be re-attached during import via `season_id` FK). |
| 9 | `SeasonPhase` | `season`, `raceScoring`, `matchScoring` | `groups`, `matchdays` | `@JsonIdentityInfo`. All `@ManyToOne` → `@JsonIdentityReference`. Ignore both `@OneToMany` (owned children). |
| 10 | `SeasonPhaseGroup` | `phase` | (none) | `@JsonIdentityInfo`. `phase` as ID ref. |
| 11 | `PhaseTeam` | `phase`, `team`, `group` | (none) | `@JsonIdentityInfo`. All FKs as ID refs. |
| 12 | `SeasonTeam` | `season`, `team`, `successor` (self-typed!) | (none) | `@JsonIdentityInfo`. All FKs as ID refs. `successor` self-type works because all `SeasonTeam` rows live in the same `data/season-teams.json` (intra-file forward reference resolved on read). |
| 13 | `SeasonDriver` | `season`, `driver`, `team` | (none) | `@JsonIdentityInfo`. All FKs as ID refs. |
| 14 | `Playoff` | `phase` | `rounds`, `seeds` | `@JsonIdentityInfo`. `phase` as ID ref. Ignore `rounds` + `seeds` (owned). |
| 15 | `PlayoffRound` | `playoff` | `matchups` | `@JsonIdentityInfo`. `playoff` as ID ref. Ignore `matchups` (owned). |
| 16 | `PlayoffMatchup` | `round`, `team1`, `team2`, `winner`, `nextMatchup` (self-FK!) | `races` | `@JsonIdentityInfo`. All FKs as ID refs (including `nextMatchup` self-FK — same intra-file resolution). Ignore `races` (back-ref). |
| 17 | `PlayoffSeed` | `playoff`, `team` | (none) | `@JsonIdentityInfo`. FKs as ID refs. |
| 18 | `Matchday` | `phase`, `group` | `matches`, `races` | `@JsonIdentityInfo`. FKs as ID refs. Ignore both `@OneToMany` (back-refs). Note: `getSeason()` is a derived convenience method, **not** a JPA attribute — Jackson should skip it (`@JsonIgnore` on `getSeason()` via MixIn). |
| 19 | `Match` | `matchday`, `homeTeam`, `awayTeam` | `races` | `@JsonIdentityInfo`. FKs as ID refs. Ignore `races` back-ref. |
| 20 | `Race` | `matchday`, `match`, `track`, `car`, `playoffMatchup`, `homeTeamOverride`, `awayTeamOverride` | `settings` (`@OneToOne`), `results`, `attachments` | `@JsonIdentityInfo`. All `@ManyToOne` as ID refs. **`settings` is owning-side `@OneToOne(mappedBy="race")` from the Race side — actually the inverse side: `RaceSettings.race` is owning.** So `Race.settings` is the inverse — MixIn must **`@JsonIgnore` `Race.settings`** to avoid double-emission (RaceSettings is its own top-level entity at #22). Ignore `results` + `attachments` back-refs. Also ignore the convenience getters `getHomeTeam()`, `getAwayTeam()`, `getHomeScore()`, `getAwayScore()`, `isBye()`, `hasAllSettings()`, `hasCalendarEvent()` (computed, not persisted). |
| 21 | `RaceLineup` | `race`, `driver`, `team` | (none) | `@JsonIdentityInfo`. FKs as ID refs. |
| 22 | `RaceResult` | `race`, `driver` | (none) | `@JsonIdentityInfo`. FKs as ID refs. |
| 23 | `RaceSettings` | `race` (`@OneToOne` owning) | (none) | `@JsonIdentityInfo`. `race` as ID ref. |
| 24 | `RaceAttachment` | `race` | (none) | `@JsonIdentityInfo`. `race` as ID ref. **`url` field is the upload path** (`/uploads/races/{raceId}/...`) — drives the `uploads/` mirror enumeration. `type` is an `AttachmentType` enum — serialize as `STRING` per Phase 72's `BackupObjectMapperConfig` (default Jackson behaviour serializes enums by name). |

### Bidirectional Cycles (must be explicitly broken via MixIn)

1. **`Season ↔ SeasonPhase ↔ SeasonPhaseGroup`** — broken by ignoring `Season.phases` and `SeasonPhase.groups`; child entities reference parent by ID. Re-assembly on import is by FK.
2. **`Season ↔ SeasonTeam ↔ Team`** — broken by ignoring `Season.seasonTeams` and `Team.seasonDrivers` (no `Team.seasonTeams` field exists — but the symmetric trap is `Team` getting reached via `SeasonTeam.team` which is fine via ID ref).
3. **`Team self-FK (Team.parentTeam ↔ Team.subTeams)`** — broken by ignoring `Team.subTeams`; serialized as parent-ID-only on each row.
4. **`Driver ↔ PsnAlias`** — Driver owns aliases (cascade ALL, orphan removal). Serialize aliases inline as child collection (no separate `data/psn-aliases.json` needed — but BackupSchema topo-sort surfaces PsnAlias as a top-level entity, so we ALSO emit them in `data/psn-aliases.json`). To avoid duplicate emission, ignore `Driver.aliases` in DriverMixIn — `PsnAlias.driver` is the authoritative FK and lives in `data/psn-aliases.json`. **Recommendation: ignore `Driver.aliases` and emit only via the PsnAlias top-level file.** This is consistent with how every other parent-child pair is treated.
5. **`Race ↔ RaceSettings (@OneToOne)`** — Race side is inverse (`mappedBy="race"`), but JPA Metamodel reports both sides as `SingularAttribute<X,Y>` with `ONE_TO_ONE` persistent attribute type. Phase 72 `EntityTopoSorter.isInverseOneToOne(attr)` already filters Race side via reflection; therefore the topo-sort places `RaceSettings` AFTER `Race`. RaceMixIn must `@JsonIgnore` `settings` to avoid the inverse-side re-emission.
6. **`Race ↔ RaceResult / RaceAttachment / RaceLineup`** — all child sides. Ignore `Race.results`, `Race.attachments`, `Match.races`, `PlayoffMatchup.races`, `Matchday.races`, `Matchday.matches`, `Driver.raceResults`, `SeasonPhase.matchdays`, `Playoff.rounds`, `Playoff.seeds`, `PlayoffRound.matchups`. All these `@OneToMany` collections are back-references — child rows in the corresponding `data/<child>.json` file hold the FK.
7. **`PlayoffMatchup.nextMatchup` (self-FK on the bracket-advancement chain)** — `@JsonIdentityReference(alwaysAsId=true)`. Intra-file forward reference resolved by Jackson's `@JsonIdentityInfo` on the same MixIn.
8. **`SeasonTeam.successor` (self-FK on team-replacement chain)** — same pattern as above.

### Convenience Methods That Must Be Suppressed

Several entities expose computed getters that are NOT JPA attributes but Jackson WILL serialize by default (via JavaBean property discovery). These produce noise and risk lazy-loading explosions. MixIns must `@JsonIgnore` them:

- `Season.getDisplayLabel()`, `getTeams()`, `getMatchdays()`, `getActiveTeams()`, `getEligibleTeams()`, `buildSuccessionMap()`
- `Team.isSubTeam()`, `hasSubTeams()`, `getParentOrSelf()`
- `SeasonTeam.getEffectivePrimaryColor()`, `getEffectiveSecondaryColor()`, `getEffectiveAccentColor()`, `getEffectiveLogoUrl()`, `isReplaced()`, `getActiveSeasonTeam()`
- `Matchday.getSeason()`
- `Playoff.getSeason()`
- `Race.getHomeTeam()`, `getAwayTeam()`, `isBye()`, `getHomeScore()`, `getAwayScore()`, `hasAllSettings()`, `hasCalendarEvent()`
- `PlayoffMatchup.isComplete()`, `isReady()`
- `RaceSettings.isComplete()`
- `RaceScoring.getRacePointsArray()`, `getQualiPointsArray()`, `canParse()`, `isValid()`
- `RaceAttachment.isImage()`
- `Driver` — `getAliases()` itself stays (it IS the persistent attribute), but the addAlias/removeAlias methods are mutators (no getter shape — Jackson doesn't touch).

**Catch-all rule:** every MixIn declares `@JsonIgnoreProperties(value = {"hibernateLazyInitializer", "handler"})` to suppress the two Hibernate proxy plumbing properties that show up if any lazy reference somehow leaks past the `@EntityGraph` (defense in depth).

### Entities NOT in scope

- `BaseEntity` — `@MappedSuperclass`, not an `@Entity`. Its `createdAt` / `updatedAt` columns ARE included on every subclass (and MixIns don't need to do anything special — Jackson picks up the inherited fields via reflection).
- `PhaseType`, `PhaseLayout`, `SeasonFormat`, `AttachmentType` — enums. Already serialize as `STRING` via Jackson's default `Enum.name()` mapping (no MixIn needed).
- `DataImportAudit` — under `org.ctc.backup.audit`. Phase 72 D-06 package filter excludes it from `BackupSchema.getExportOrder()`. No MixIn.

## MixIn Design

### Recommended Project Structure

```
src/main/java/org/ctc/backup/
├── schema/                                  # Phase 72 — unchanged
├── config/                                  # Phase 72 — unchanged
├── audit/                                   # Phase 72 — unchanged
├── serialization/                           # NEW (Phase 73)
│   ├── CarMixIn.java                        # @JsonIdentityInfo on id
│   ├── TrackMixIn.java
│   ├── RaceScoringMixIn.java
│   ├── MatchScoringMixIn.java
│   ├── DriverMixIn.java                     # @JsonIgnore seasonDrivers, raceResults, aliases
│   ├── PsnAliasMixIn.java
│   ├── TeamMixIn.java                       # @JsonIgnore subTeams, seasonDrivers; parentTeam as ID ref
│   ├── SeasonMixIn.java                     # @JsonIgnore phases/seasonDrivers/seasonTeams; cars/tracks as ID-ref collections
│   ├── SeasonPhaseMixIn.java
│   ├── SeasonPhaseGroupMixIn.java
│   ├── PhaseTeamMixIn.java
│   ├── SeasonTeamMixIn.java
│   ├── SeasonDriverMixIn.java
│   ├── PlayoffMixIn.java
│   ├── PlayoffRoundMixIn.java
│   ├── PlayoffMatchupMixIn.java
│   ├── PlayoffSeedMixIn.java
│   ├── MatchdayMixIn.java
│   ├── MatchMixIn.java
│   ├── RaceMixIn.java                       # @JsonIgnore settings inverse + convenience getters
│   ├── RaceLineupMixIn.java
│   ├── RaceResultMixIn.java
│   ├── RaceSettingsMixIn.java
│   ├── RaceAttachmentMixIn.java
│   └── BackupSerializationModule.java       # SimpleModule that registers ALL 24 MixIns via setupModule
├── service/                                 # NEW (Phase 73)
│   ├── BackupExportService.java             # @Transactional(readOnly=true)
│   └── BackupArchiveService.java            # ZIP plumbing, manifest-first, uploads mirror
└── BackupController.java                    # NEW (Phase 73), under org.ctc.backup
```

**Note on `BackupController` location:** Phase 72 RESEARCH §"New package" puts everything backup-related under `org.ctc.backup.*`. The existing precedent for "feature modules with their own controller package" is `org.ctc.dataimport.CsvImportController` (lives in `org.ctc.dataimport` directly, not `org.ctc.admin.controller`). Phase 73 follows that precedent: `org.ctc.backup.BackupController`, NOT `org.ctc.admin.controller.BackupController`.

### Pattern 1: One MixIn class per entity (interface or abstract class — Jackson does not care)

```java
// src/main/java/org/ctc/backup/serialization/TeamMixIn.java
package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link Team}. Phase 73 EXPORT-04.
 *
 * <p>Required because:
 * <ul>
 *   <li>{@code Team.parentTeam} is a self-FK — needs ID-only reference to keep the JSON acyclic
 *       and let the importer resolve via second-pass FK rewire.</li>
 *   <li>{@code Team.subTeams} is the inverse of {@code parentTeam} — must be ignored or each
 *       parent-team JSON node would contain the entire subtree (causing duplicate emission and
 *       defeating the {@code @JsonIdentityInfo} object-graph compaction).</li>
 *   <li>{@code Team.seasonDrivers} is a back-reference owned by {@code SeasonDriver.team} — must
 *       be ignored; the rows live in {@code data/season-drivers.json}.</li>
 *   <li>Convenience methods ({@code isSubTeam}, {@code hasSubTeams}, {@code getParentOrSelf})
 *       are computed, not persisted — must be ignored to avoid duplicate JSON keys and lazy-init
 *       triggers.</li>
 *   <li>The {@code hibernateLazyInitializer} + {@code handler} catch-all is defense-in-depth
 *       against a lazy proxy that slipped past {@code @EntityGraph}.</li>
 * </ul>
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "subTeams", "seasonDrivers",
                       "subTeam", "parentOrSelf"})
public abstract class TeamMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getParentTeam();
}
```

### Pattern 2: One `SimpleModule` `@Component` that registers every MixIn (single Spring-DI hook)

```java
// src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java
package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.ctc.domain.model.*;
import org.springframework.stereotype.Component;

/**
 * Phase 73 EXPORT-04 — the single Jackson {@code Module} that wires every per-entity MixIn
 * onto the {@code backupObjectMapper} bean. Picked up automatically by Phase 72's
 * {@code BackupObjectMapperConfig.backupObjectMapper(List&lt;Module&gt; backupMixInModules)}
 * via Spring DI — zero config-class changes.
 *
 * <p>The module is implemented as a single {@code @Component} (instead of 24 separate
 * {@code Module} beans) because every MixIn is a sibling annotation-carrier with no
 * cross-MixIn state — keeping them in one file makes the entity→MixIn mapping reviewable
 * at a glance.
 */
@Component
public class BackupSerializationModule extends SimpleModule {

    public BackupSerializationModule() {
        super("BackupSerializationModule");
        setMixInAnnotation(Car.class, CarMixIn.class);
        setMixInAnnotation(Track.class, TrackMixIn.class);
        setMixInAnnotation(RaceScoring.class, RaceScoringMixIn.class);
        setMixInAnnotation(MatchScoring.class, MatchScoringMixIn.class);
        setMixInAnnotation(Driver.class, DriverMixIn.class);
        setMixInAnnotation(PsnAlias.class, PsnAliasMixIn.class);
        setMixInAnnotation(Team.class, TeamMixIn.class);
        setMixInAnnotation(Season.class, SeasonMixIn.class);
        setMixInAnnotation(SeasonPhase.class, SeasonPhaseMixIn.class);
        setMixInAnnotation(SeasonPhaseGroup.class, SeasonPhaseGroupMixIn.class);
        setMixInAnnotation(PhaseTeam.class, PhaseTeamMixIn.class);
        setMixInAnnotation(SeasonTeam.class, SeasonTeamMixIn.class);
        setMixInAnnotation(SeasonDriver.class, SeasonDriverMixIn.class);
        setMixInAnnotation(Playoff.class, PlayoffMixIn.class);
        setMixInAnnotation(PlayoffRound.class, PlayoffRoundMixIn.class);
        setMixInAnnotation(PlayoffMatchup.class, PlayoffMatchupMixIn.class);
        setMixInAnnotation(PlayoffSeed.class, PlayoffSeedMixIn.class);
        setMixInAnnotation(Matchday.class, MatchdayMixIn.class);
        setMixInAnnotation(Match.class, MatchMixIn.class);
        setMixInAnnotation(Race.class, RaceMixIn.class);
        setMixInAnnotation(RaceLineup.class, RaceLineupMixIn.class);
        setMixInAnnotation(RaceResult.class, RaceResultMixIn.class);
        setMixInAnnotation(RaceSettings.class, RaceSettingsMixIn.class);
        setMixInAnnotation(RaceAttachment.class, RaceAttachmentMixIn.class);
    }
}
```

**Why one Module instead of 24:** the Phase 72 DI hook is `List<Module>`. Either shape works — but a single Module:
- gives a one-stop file to read the full entity→MixIn mapping;
- avoids 24 boilerplate `@Component` Module wrappers that would each contain a single `setMixInAnnotation(...)` call;
- preserves Jackson's `Module` registration semantics (each `addModule` call replaces existing mappings for the same type only — order of registration doesn't matter because there's only one module).

**Trade-off rejected:** "one Module per MixIn so Wave-1 tasks can be parallelized" — superficially attractive but creates 24 trivial Module classes whose only content is `setMixInAnnotation(Foo.class, FooMixIn.class)`. The 24 MixIn class files can be parallelized **without** wrapping each in a Module — the central `BackupSerializationModule` is the LAST file written in Wave 1 (depends on every MixIn class compiling), but every individual MixIn file is independent.

### Pattern 3: Hibernate-proxy interaction

[CITED: jackson docs — `jackson-datatype-hibernate6` exists, but our setup does NOT need it because the `@EntityGraph` eager-fetch (EXPORT-05) materializes every traversed association BEFORE Jackson sees the object.] The catch-all `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` on every MixIn is defense-in-depth. If a lazy proxy ever does slip through (e.g. a forgotten `@EntityGraph` attribute path), the export still emits well-formed JSON instead of throwing `LazyInitializationException` mid-stream and corrupting the ZIP. The Failsafe IT `BackupExportServiceIT.givenSaison2023Fixture_whenExport_thenNoLazyInitInLogs` (see Validation Architecture) is the authoritative guard for the eager-fetch correctness.

### Pattern 4: Date/Time fields

`BaseEntity.createdAt` and `updatedAt` are `LocalDateTime` (NOT `Instant`). The Phase 72 `BackupObjectMapperConfig.backupObjectMapper(...)` registers `JavaTimeModule` and sets `WRITE_DATES_AS_TIMESTAMPS=false` — `LocalDateTime` serializes as ISO-8601 string `"2026-05-11T17:42:30.123"` (no timezone suffix; this is the contract). `BackupManifest.exportDate` is `Instant` → serializes as `"2026-05-11T17:42:30.123Z"` (UTC `Z` suffix). Both formats are preserved on round-trip via `JavaTimeModule.deserialize(...)`.

### Pattern 5: Byte/blob/transient fields

- **`byte[]` fields:** none of the 24 entities expose a `byte[]` column (images are stored as filesystem files via `Team.logoUrl` / `Car.imageUrl` etc., NOT as `@Lob byte[]`). No special MixIn handling needed.
- **`@Transient` fields:** none declared on the 24 entities. JPA's default exclusion would apply if any existed.
- **`@CreatedDate` / `@LastModifiedDate`:** populated by `AuditingEntityListener` on persist/update; serialized like any other `LocalDateTime` field. The MixIn does NOT need to suppress these — Phase 75's IMPORT-05 `JdbcTemplate.batchUpdate` will bypass the listener so the round-tripped values survive.

## EntityGraph Fetch Map

The `BackupExportService` must avoid `LazyInitializationException` (success criterion 4). Because `StreamingResponseBody` writes the response OUTSIDE the controller method (after the request-processing thread returns), OSIV is **NOT** load-bearing — see Landmine L-7. The load-bearing guard is the explicit `@Transactional(readOnly=true)` on the service method PLUS per-repository `@EntityGraph` annotations.

**Strategy:** for each entity in `BackupSchema.getExportOrder()`, define a dedicated repository method `findAllForBackup()` annotated with `@EntityGraph(attributePaths = {<every association the MixIn keeps>})`. The MixIn drops all collection back-references via `@JsonIgnore` — that means the `@EntityGraph` only needs to fetch the `@ManyToOne` references that the MixIn renders as ID-only (Jackson reads the `getId()` of each).

Reading the `getId()` of a Hibernate lazy proxy **does not trigger initialization** — the proxy holds the ID natively. **So in principle, the `@EntityGraph` could be empty for entities whose MixIns render all FKs as ID refs.** However:

- This relies on every MixIn correctly applying `@JsonIdentityReference(alwaysAsId=true)` to every `@ManyToOne` AND on every collection being `@JsonIgnore`d. One missed annotation → LIE in production.
- Defensive eager-fetch protects against MixIn bugs.
- The 24 repositories already use `@EntityGraph` for their existing query methods (verified: 14 of them) — adding a `findAllForBackup()` per-repository is mechanical and idiomatic.

**Recommendation:** add `findAllForBackup()` to every repository, with `@EntityGraph` listing **every** `@ManyToOne` association the entity exposes (even if the MixIn turns it into an ID ref — defense in depth). Collections (`@OneToMany`) are NOT included in `attributePaths` because the MixIns ignore them.

| Entity | `@EntityGraph(attributePaths = ...)` for `findAllForBackup()` | Rationale |
|--------|-------------------------------------------------------------|-----------|
| `Car` | `{}` (or no annotation — `findAll()` is fine) | No `@ManyToOne` |
| `Track` | `{}` | No `@ManyToOne` |
| `RaceScoring` | `{}` | No `@ManyToOne` |
| `MatchScoring` | `{}` | No `@ManyToOne` |
| `Driver` | `{}` | No `@ManyToOne` (only `@OneToMany` which MixIn ignores) |
| `PsnAlias` | `{"driver"}` | `driver` ID needed |
| `Team` | `{"parentTeam"}` | `parentTeam` ID needed (self-FK) |
| `Season` | `{"cars", "tracks"}` | Two `@ManyToMany` — needs the collections **fetched** because Jackson serialises them as ID-collections (each element's `getId()` must be readable; collection itself must be initialised). |
| `SeasonPhase` | `{"season", "raceScoring", "matchScoring"}` | Three `@ManyToOne` |
| `SeasonPhaseGroup` | `{"phase"}` | One `@ManyToOne` |
| `PhaseTeam` | `{"phase", "team", "group"}` | Three `@ManyToOne` (`group` is nullable) |
| `SeasonTeam` | `{"season", "team", "successor"}` | Three `@ManyToOne` (`successor` self-typed) |
| `SeasonDriver` | `{"season", "driver", "team"}` | Three `@ManyToOne` |
| `Playoff` | `{"phase"}` | One `@ManyToOne` |
| `PlayoffRound` | `{"playoff"}` | One `@ManyToOne` |
| `PlayoffMatchup` | `{"round", "team1", "team2", "winner", "nextMatchup"}` | Five `@ManyToOne` (`nextMatchup` self-FK) |
| `PlayoffSeed` | `{"playoff", "team"}` | Two `@ManyToOne` |
| `Matchday` | `{"phase", "group"}` | Two `@ManyToOne` (`group` nullable) |
| `Match` | `{"matchday", "homeTeam", "awayTeam"}` | Three `@ManyToOne` |
| `Race` | `{"matchday", "match", "track", "car", "playoffMatchup", "homeTeamOverride", "awayTeamOverride"}` | Seven `@ManyToOne`. Critical: `settings` is INVERSE `@OneToOne` and is IGNORED by `RaceMixIn` — not in fetch graph. |
| `RaceLineup` | `{"race", "driver", "team"}` | Three `@ManyToOne` |
| `RaceResult` | `{"race", "driver"}` | Two `@ManyToOne` |
| `RaceSettings` | `{"race"}` | One owning `@OneToOne` (treated like ManyToOne by JPA) |
| `RaceAttachment` | `{"race"}` | One `@ManyToOne` |

### Per-entity repository method signature

```java
// Example: TeamRepository
@EntityGraph(attributePaths = {"parentTeam"})
@Query("SELECT t FROM Team t")
List<Team> findAllForBackup();
```

The `@Query` is explicit (not derived from method name) so the method-name parser doesn't try to interpret `ForBackup` as a property filter. The `attributePaths` list is the same as the table above.

**Memory & streaming trade-off:** `List<Team> findAllForBackup()` materialises all rows in heap. For the current dataset sizes (largest table is `race_results` with O(driver_count × race_count) ~ thousands of rows) this is well under 100 MB. **For large leagues**, an alternative is `Stream<Team> findAllForBackup()` with Hibernate's `@QueryHints({@QueryHint(name = HINT_FETCH_SIZE, value = "100")})` — but this introduces complications (the stream must be consumed inside the `@Transactional` scope, complicating the StreamingResponseBody hand-off). **Phase 73 ships the `List<T>` shape.** The memory budget will be re-evaluated in v1.11 if a real liga ships >50 MB datasets — `EXPORT-FUT-02` already captures async/chunked export.

### N+1 hotspots

- **`Race`** is the deepest aggregate (seven ManyToOne refs). The `@EntityGraph` collapses it into a single LEFT JOIN per fetch — no N+1 risk.
- **`Season`** with `cars` + `tracks` ManyToMany — Hibernate executes 2 separate SELECTs (Season + the `season_cars`/`season_tracks` join tables); the `@EntityGraph` ensures this is a `FetchType.EAGER` join, not lazy iteration.
- **`Team.parentTeam` self-FK** — single LEFT JOIN, no recursion (the join only returns the immediate parent; if the parent has a grandparent, Jackson serialises the parent as ID-only via `@JsonIdentityReference`, never traversing further).

## Streaming ZIP Architecture

### System Diagram

```
HTTP POST /admin/backup/export
   |
   v
BackupController.export(HttpServletResponse response)
   |  -- inside controller thread:
   |     - Build BackupExportContext (Instant.now() + ISO-filename derivation)
   |     - Set Content-Disposition + Content-Type headers
   |     - Return ResponseEntity.ok().body(StreamingResponseBody lambda)
   |  -- StreamingResponseBody runs OUTSIDE the controller thread, on the MVC
   |     async worker thread (still inside the same HTTP request):
   v
StreamingResponseBody.writeTo(OutputStream out)
   |
   v
BackupArchiveService.writeZip(out, context)
   |
   +---> 1. Pre-flush: BackupExportService.exportAggregate()
   |     - @Transactional(readOnly=true) — wraps the ENTIRE write loop
   |     - returns a BackupAggregate(manifest, Map<String,byte[]> perEntityJson, List<UploadFile>)
   |     - OR: streams per-entity via SequenceWriter — see "Streaming vs preload" below
   |
   v
ZipOutputStream zipOut = new ZipOutputStream(out)
   |
   +---> 2. zipOut.putNextEntry(new ZipEntry("manifest.json"))
   |     backupObjectMapper.writeValue(zipOut, manifest)
   |     zipOut.closeEntry()
   |
   +---> 3. For each EntityRef ref in BackupSchema.getExportOrder():
   |       zipOut.putNextEntry(new ZipEntry(ref.fileName()))           // e.g. "data/seasons.json"
   |       backupObjectMapper.writeValue(zipOut, entitiesOfType)
   |       zipOut.closeEntry()
   |
   +---> 4. For each uploadPath in computeReferencedUploads(aggregate):
   |       zipOut.putNextEntry(new ZipEntry("uploads/" + relativePath))
   |       Files.copy(filesystemPath, zipOut)
   |       zipOut.closeEntry()
   |
   +---> 5. zipOut.finish() + zipOut.close()
```

### Pattern 1: Controller signature

```java
// src/main/java/org/ctc/backup/BackupController.java
@Controller
@RequestMapping("/admin/backup")
@RequiredArgsConstructor
@Slf4j
public class BackupController {

    private final BackupExportService backupExportService;
    private final BackupArchiveService backupArchiveService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("title", "Backup");        // per UI-SPEC active-state detection
        return "admin/backup";
    }

    @PostMapping("/export")
    public ResponseEntity<StreamingResponseBody> export() {
        var now = Instant.now();
        var filename = "ctc-backup-" + isoSafeFilename(now) + ".zip";

        StreamingResponseBody body = outputStream -> {
            try {
                backupArchiveService.writeZip(outputStream, now);
            } catch (IOException e) {
                log.error("Backup export I/O failure mid-stream", e);
                // Response is already committed; cannot redirect. Log + truncate.
                throw new UncheckedIOException(e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
```

**Notes:**
- The controller does NOT take a Form DTO — there is no request body to bind. The CSRF token is verified by Spring Security's `CsrfFilter` BEFORE the controller method is invoked (rejection → HTTP 403 long before any export work begins).
- `ContentDisposition.attachment().filename(...)` correctly RFC-5987-encodes the filename for non-ASCII characters (defensive — our ISO-Instant only contains `[0-9-:T.Z]`, so no special encoding kicks in).
- The `Content-Type` is `application/octet-stream`, not `application/zip`. Reason: forcing the browser to download as a file regardless of MIME-handler heuristics. `application/zip` would let the browser try to open the ZIP inline on some platforms. (Cross-check: `TeamCardController.downloadAll()` line 132 uses the same `APPLICATION_OCTET_STREAM`.)
- If the export fails BEFORE the first byte is flushed: rethrowing from the lambda escapes the `StreamingResponseBody` machinery and Spring renders the framework default error page. **UI-SPEC accepts this** ("the orchestrator brief lists progress / cancel UI as out of scope; mid-stream errors truncate the ZIP and log the stack trace").

### Pattern 2: Manifest-FIRST enforcement

```java
// src/main/java/org/ctc/backup/service/BackupArchiveService.java
public void writeZip(OutputStream outputStream, Instant exportDate) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
        zip.setLevel(Deflater.DEFAULT_COMPRESSION);

        // ---- ENTRY #1: manifest.json (success criterion: FIRST entry) ----
        Map<String, Long> tableCounts = backupExportService.countRowsPerTable();
        BackupManifest manifest = new BackupManifest(
                BackupSchema.SCHEMA_VERSION,
                appVersion,                                  // @Value("${app.version}")
                exportDate,
                tableCounts);
        zip.putNextEntry(new ZipEntry("manifest.json"));
        backupObjectMapper.writerWithDefaultPrettyPrinter().writeValue(zip, manifest);
        zip.closeEntry();

        // ---- ENTRIES #2..N+1: data/<entity>.json in EXPORT_ORDER ----
        for (EntityRef ref : backupSchema.getExportOrder()) {
            zip.putNextEntry(new ZipEntry(ref.fileName()));
            List<?> rows = backupExportService.fetchAllForBackup(ref.entityClass());
            backupObjectMapper.writeValue(zip, rows);
            zip.closeEntry();
        }

        // ---- ENTRIES #N+2..M: uploads/ mirror ----
        for (UploadEntry upload : backupExportService.enumerateReferencedUploads()) {
            zip.putNextEntry(new ZipEntry("uploads/" + upload.relativePath()));
            Files.copy(upload.absolutePath(), zip);
            zip.closeEntry();
        }

        zip.finish();
    }
}
```

- **Manifest-first determinism:** the manifest is the FIRST `putNextEntry` call after `new ZipOutputStream(...)`. ZIP-spec entry order matches insertion order — there is no `setEntryOrder()` API; the writer's call sequence IS the order.
- **`countRowsPerTable()` semantics:** runs BEFORE the per-entity loop so the manifest reflects the **same** transactional view that the entity loop will see (both run inside the same `@Transactional(readOnly=true)` boundary on the service). This is the "count is correct" guarantee Phase 77's `BackupRoundTripIT` relies on.
- **`writerWithDefaultPrettyPrinter()` for `manifest.json` only:** human-readable for support / debugging. The per-entity files use the default (single-line) writer because (a) they're large and (b) machine-only consumers.

### Pattern 3: Per-entity filename derivation

`EntityRef.fileName()` is already `data/<kebab-case-table>.json` (locked in Phase 72 Plan-01). No derivation needed at write time — just use `ref.fileName()` directly. Concrete examples (verified against `@Table(name=...)` of each entity):

| Entity | `@Table(name=...)` | `EntityRef.fileName()` |
|--------|--------------------|------------------------|
| Season | `seasons` | `data/seasons.json` |
| SeasonPhase | `season_phases` | `data/season-phases.json` |
| SeasonPhaseGroup | `season_phase_groups` | `data/season-phase-groups.json` |
| RaceResult | `race_results` | `data/race-results.json` |
| RaceScoring | `race_scorings` | `data/race-scorings.json` |
| PlayoffMatchup | `playoff_matchups` | `data/playoff-matchups.json` |
| PsnAlias | `psn_aliases` | `data/psn-aliases.json` |

ROADMAP SC-2 examples (`data/seasons.json`, `data/race-results.json`) match this scheme exactly.

### Pattern 4: Uploads/ mirror enumeration

Entity fields holding upload-directory references:

| Entity | Field | Path shape stored | Mapped filesystem path |
|--------|-------|-------------------|------------------------|
| `Team` | `logoUrl` | `/uploads/teams/<uuid>/<file>` (nullable; some teams have no logo) | `{app.upload-dir}/teams/<uuid>/<file>` |
| `SeasonTeam` | `logoUrl` | `/uploads/teams/<uuid>/<file>` (per-season override; nullable) | same |
| `Car` | `imageUrl` | `/uploads/cars/<uuid>/<file>` (set by GT7 sync; nullable) | `{app.upload-dir}/cars/<uuid>/<file>` |
| `Track` | `imageUrl` | `/uploads/tracks/<uuid>/<file>` (nullable) | `{app.upload-dir}/tracks/<uuid>/<file>` |
| `RaceAttachment` | `url` | `/uploads/races/<uuid>/<file>` (only when `type=FILE`; `LINK` entries hold external URLs and skip the mirror) | `{app.upload-dir}/races/<uuid>/<file>` |

Verified by grep of `org.ctc.domain.model.*` for `logoUrl|imageUrl|/uploads/` and the existing `FileStorageService.store(...)` paths.

**Enumeration algorithm:**

```java
public List<UploadEntry> enumerateReferencedUploads() {
    Set<String> uniqueRelativePaths = new LinkedHashSet<>();   // preserves insertion order, dedupes
    teamRepository.findAllForBackup().forEach(t -> addIfPresent(uniqueRelativePaths, t.getLogoUrl()));
    seasonTeamRepository.findAllForBackup().forEach(st -> addIfPresent(uniqueRelativePaths, st.getLogoUrl()));
    carRepository.findAllForBackup().forEach(c -> addIfPresent(uniqueRelativePaths, c.getImageUrl()));
    trackRepository.findAllForBackup().forEach(t -> addIfPresent(uniqueRelativePaths, t.getImageUrl()));
    raceAttachmentRepository.findAllForBackup().stream()
        .filter(a -> a.getType() == AttachmentType.FILE)
        .forEach(a -> addIfPresent(uniqueRelativePaths, a.getUrl()));
    return uniqueRelativePaths.stream()
        .map(rel -> toUploadEntry(rel))
        .filter(entry -> Files.exists(entry.absolutePath()))    // skip orphans
        .toList();
}

private void addIfPresent(Set<String> set, String url) {
    if (url != null && url.startsWith("/uploads/")) {
        set.add(url.substring("/uploads/".length()));
    }
}
```

**Notes:**
- `Files.exists` check is mandatory — a `Team.logoUrl` may point to a path that was deleted on disk but never NULL'd in the DB (UI-bug or manual operation). The export silently skips broken references rather than failing the entire export. Logged as `log.warn(...)` for traceability.
- `LinkedHashSet` deduplicates: a `SeasonTeam` may inherit `Team.logoUrl` without an override → both `Team` and `SeasonTeam` rows point to the same file → only one ZIP entry.
- `{app.upload-dir}` resolves to `data/dev/uploads`, `data/local/uploads`, or `/app/uploads` depending on profile (`application-dev.yml`, `application-local.yml`, `application-docker.yml`). The export is **profile-aware** by virtue of `@Value("${app.upload-dir}")` — no special handling needed.

### Pattern 5: Content-Disposition + ISO-Instant filename

```java
private static String isoSafeFilename(Instant when) {
    // Choice: drop colons + millis so the filename is filesystem-safe on Windows + macOS.
    //   Default ISO_INSTANT: "2026-05-11T17:42:30.123Z" — colons are illegal in NTFS, refused by File Explorer.
    //   Compact basic form:  "20260511T174230Z" — universally safe; sortable; round-trip-parseable.
    return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(when);
}
```

**Why drop the colons:** NTFS forbids `:` in filenames; the Windows download dialog will silently rewrite or reject the download. macOS and Linux tolerate colons but break on `:` in shell autocompletion. The compact basic ISO-8601 form `YYYYMMDDTHHMMSSZ` is universally safe and is what `Files.createTempFile`-style tooling uses.

ROADMAP SC-1 wording is `ctc-backup-<ISO-instant>.zip` — "ISO instant" is a family, not a specific format. The compact basic form is still an ISO-8601 instant (ISO 8601 section 4.1.2.4 "Representations with reduced accuracy").

**Recommendation in plan acceptance criteria:** "filename matches regex `^ctc-backup-\d{8}T\d{6}Z\.zip$`".

### Pattern 6: Memory safety — does `ObjectMapper.writeValue(OutputStream, ...)` stream or buffer?

[CITED: jackson docs — `ObjectMapper.writeValue(OutputStream out, Object value)` writes through a `JsonGenerator` backed by the `OutputStream`; the generator flushes incrementally and does NOT buffer the entire serialized payload in memory.] So serializing a `List<Race>` of 5000 entries does NOT allocate a 5000-element-worth byte array on the JVM heap — it writes directly into the ZIP stream as JSON is generated.

**However:** the **`List<Race> rows`** in the service method DOES live in heap. The fetch loads all rows, then serializes them. For a current liga (a few hundred races, a few thousand results), this is well under 100 MB. For the dev fixture (Saison 2023 + 2024-3), well under 10 MB.

**Alternative streaming shape (deferred):** Jackson's `SequenceWriter` (`mapper.writer().writeValues(out)`) emits a JSON array element-by-element while consuming a `Stream<T>` source. This requires:
- Repository method returning `Stream<T>` instead of `List<T>`.
- The `Stream` must be consumed inside the `@Transactional` scope — but `StreamingResponseBody` runs AFTER the controller returns, so the `@Transactional` boundary must be on the `StreamingResponseBody` lambda or on a service method called from within it.

**Phase 73 ships the simpler `List<T>` shape.** A future v1.11 phase can swap in `SequenceWriter` if memory measurement proves it necessary (`EXPORT-FUT-02` placeholder).

## Controller & Security

### Pattern 1: Controller layout

`BackupController` is a single class with two handlers (`@GetMapping`, `@PostMapping("/export")`). The Phase 74 import endpoints (`POST /admin/backup/import-preview`, `POST /admin/backup/import-execute`) will be added to the **same controller class** in Phase 74. There is no Phase-73 reason to split.

The closest existing precedent is `SiteGeneratorController` (single GET + single POST, identical Thymeleaf-`th:action` form pattern). Phase 73 mirrors its shape.

### Pattern 2: CSRF wiring

[VERIFIED: `admin/generate.html` line 12: `<form th:action="@{/admin/generate}" method="post">` with no explicit `<input type="hidden" name="_csrf"...>` — Spring Security's Thymeleaf integration auto-injects the token on every POST form rendered by Thymeleaf when `CsrfFilter` is active. The same pattern works for `<form th:action="@{/admin/backup/export}" method="post">`.]

[VERIFIED: `SecurityConfig.java` (prod/docker) does NOT call `.csrf(...)` at all → CSRF is ON by default in Spring Security 7.] [VERIFIED: `OpenSecurityConfig.java` (dev/local) calls `.csrf(csrf -> csrf.disable())` → CSRF is OFF for dev/local.]

Phase 73 needs **zero CSRF wiring** — the existing config catches the new endpoint automatically. The MockMvc CSRF test in Phase 73 (see Validation Architecture) verifies this end-to-end.

### Pattern 3: Profile-conditional auth

The existing `SecurityConfig.securityFilterChain(...)` line 21 is `.anyRequest().authenticated()`. This catches `/admin/backup` and `/admin/backup/export` automatically. No new `requestMatchers(...)` rule is needed.

`/actuator/health` remains the only `permitAll()` exception.

The `OpenSecurityConfig.anyRequest().permitAll()` lets dev/local through without authentication — Phase 73 export is freely accessible during development, mirroring every other admin endpoint.

### Pattern 4: Error handling

Spring's `StreamingResponseBody` runs AFTER the request-processing thread has returned the `ResponseEntity` and the response headers have been flushed. This has two implications:

1. **Errors BEFORE the first byte of the body** (e.g. `BackupSchema.getExportOrder()` returns null, repository injection fails, `ObjectMapper` not on the classpath) — these manifest as exceptions in `BackupController.export()` BEFORE the `ResponseEntity` is constructed. Standard `GlobalExceptionHandler` flow applies: render `admin/error.html` with a 500 status. UI-SPEC's "error flash" copy ("Backup export failed. See application log for details.") is only used if the controller catches and redirects with a flash — but for a fire-and-forget download, an error page is more accurate than a redirect.

2. **Errors AFTER the first byte** (e.g. mid-stream filesystem read failure, `LazyInitializationException` from a missed `@EntityGraph` attribute, write-side `IOException`) — the response is already committed; we cannot redirect, cannot set a flash, cannot show an error page. The browser sees a truncated ZIP. Phase 73 logs `log.error("Backup export I/O failure mid-stream", e)` and `throw new UncheckedIOException(e)` — the latter aborts the `StreamingResponseBody` cleanly. **This is acceptable per UI-SPEC.**

**Recommendation:** the controller method itself catches **only** `RuntimeException` from the synchronous pre-body phase (e.g. building the manifest, counting rows). Anything from inside the `StreamingResponseBody` lambda is rethrown as `UncheckedIOException` and logged. No flash-message attempt is made post-commit (UI-SPEC).

```java
@PostMapping("/export")
public ResponseEntity<StreamingResponseBody> export(RedirectAttributes redirectAttributes) {
    var now = Instant.now();
    var filename = "ctc-backup-" + isoSafeFilename(now) + ".zip";

    // Pre-body sanity check (failures here surface as a normal HTTP error, NOT a truncated ZIP).
    if (backupSchema.getExportOrder().isEmpty()) {
        log.error("Backup export aborted: EXPORT_ORDER is empty");
        redirectAttributes.addFlashAttribute("errorMessage",
                "Backup export failed. See application log for details.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }

    StreamingResponseBody body = outputStream -> {
        try {
            backupArchiveService.writeZip(outputStream, now);
        } catch (IOException e) {
            log.error("Backup export I/O failure mid-stream", e);
            throw new UncheckedIOException(e);
        }
    };

    return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(filename).build().toString())
            .body(body);
}
```

## Testing Strategy

### Layer 1 — Unit (Surefire, `*Test.java`)

| Test | Asserts | Coverage |
|------|---------|----------|
| `BackupSerializationModuleTest` | One Spring-context-free unit test that constructs `BackupSerializationModule`, registers it on a fresh `ObjectMapper`, and asserts `mapper.findMixInClassFor(Team.class) == TeamMixIn.class` for all 24 entities. Reflective loop over an expected `Map<Class<?>, Class<?>>` constant. | All 24 MixIns + the Module wiring. |
| `TeamMixInTest` (and one per "interesting" MixIn — those with `@JsonIdentityReference`, `@JsonIgnore`, or `@JsonIgnoreProperties` beyond the catch-all) | Build a sample `Team` instance manually (no Spring, no DB), serialize via a `new ObjectMapper().registerModule(new BackupSerializationModule())`, parse the result with Jackson's tree API, assert the JSON shape (presence of `id`, absence of `subTeams`, `parentTeam` as a string UUID, etc.). | Per-MixIn correctness. Suggest one test per "behavioural class" of MixIn: `TeamMixInTest`, `SeasonMixInTest`, `RaceMixInTest`, `DriverMixInTest`, `RaceAttachmentMixInTest` (~5 tests, not 24). The remaining 19 trivial MixIns are covered by `BackupSerializationModuleTest`. |
| `BackupArchiveServiceTest` | Mockito-driven: mock `BackupSchema`, `backupObjectMapper`, repositories. Call `writeZip(byteArrayOutputStream, fixedInstant)`. Open the bytes back with `ZipInputStream`, assert (a) `manifest.json` is the first entry, (b) the entries appear in the order returned by `getExportOrder()`, (c) `uploads/...` entries come after `data/...` entries. | ZIP-layout correctness, manifest-first invariant. |
| `BackupExportServiceTest` | Mockito-driven: stub each repository's `findAllForBackup()` with hand-built entities. Assert the service walks all entities and produces JSON of the expected shape. | Service orchestration logic. |

### Layer 2 — Integration (Failsafe `*IT.java`, `@SpringBootTest @ActiveProfiles("dev")`)

| Test | Asserts | Coverage |
|------|---------|----------|
| `BackupExportServiceIT.givenDevFixture_whenExport_thenZipMatchesContract` | Boots full Spring context with H2 + dev-data-seed (`DevDataSeeder` provides Saison 2023 + 2024-3 fixture). Calls `backupArchiveService.writeZip(...)`. Opens the resulting bytes with `ZipInputStream`. Asserts: (1) `manifest.json` exists and is `entries[0]`; (2) for every `EntityRef` in `BackupSchema.getExportOrder()`, a `data/<entity>.json` entry exists at the expected position; (3) `manifest.schema_version` equals `BackupSchema.SCHEMA_VERSION`; (4) `manifest.table_counts` matches the actual DB row counts via `repository.count()`. | EXPORT-02, EXPORT-03, full integration of MixIns + ObjectMapper + ZIP plumbing. |
| `BackupExportServiceIT.givenSaison2023Fixture_whenExport_thenNoLazyInitInLogs` | Boots `@ActiveProfiles("dev")` with `DevDataSeeder`. Wires a custom `Logger` appender into `org.hibernate` and `org.ctc.backup`. Calls export. Asserts the captured log records contain NO `LazyInitializationException` and NO `could not initialize proxy` messages. | EXPORT-05 — no LIE during streamed export. |
| `BackupExportServiceIT.givenExportRoundTrip_whenDeserializeManifest_thenAllFieldsPopulated` | Calls export. Reads back `manifest.json` from the ZIP via `backupObjectMapper.readValue(...)`. Asserts every field is non-null and `tableCounts.keySet()` equals the snake_case names of all 24 entities. | Manifest serialization round-trip. |
| `BackupEntityAnnotationCleanlinessIT` | One reflective test that walks every class in `org.ctc.domain.model.*`, asserts NO `@JsonIdentityInfo`, `@JsonIgnore`, `@JsonProperty`, `@JsonManagedReference`, `@JsonBackReference`, or any `com.fasterxml.jackson.annotation.*` annotation is present on any field or method of any class. | EXPORT-04 success criterion 3 — "entities byte-identically unchanged". |
| `BackupUploadsMirrorIT` | Seeds an in-test `Team` + `Car` + `RaceAttachment` with `logoUrl` / `imageUrl` / `url` pointing at temp-filesystem files (created in `@BeforeEach`). Calls export. Asserts the ZIP contains the corresponding `uploads/teams/<uuid>/<file>`, `uploads/cars/<uuid>/<file>`, `uploads/races/<uuid>/<file>` entries with byte-identical content. | EXPORT-03 (uploads/ mirror part). |

### Layer 3 — Security IT (Failsafe `*IT.java`)

| Test | Asserts | Coverage |
|------|---------|----------|
| `BackupControllerSecurityIT.ProdProfile.givenAnonymous_whenPostExport_thenUnauthorized` | `@Nested @ActiveProfiles("prod") @SpringBootTest @AutoConfigureMockMvc`. `mockMvc.perform(post("/admin/backup/export").with(anonymous()))` → HTTP 401. Mirrors `SecurityIntegrationTest.ProdProfileSecurityTest.givenNoCredentials_whenAccessAdmin_thenUnauthorized`. | EXPORT-06 — anonymous rejection on prod/docker. |
| `BackupControllerSecurityIT.ProdProfile.givenAuthenticatedNoCsrf_whenPostExport_thenForbidden` | `@WithMockUser`, `mockMvc.perform(post("/admin/backup/export"))` WITHOUT `.with(csrf())` → HTTP 403. | EXPORT-06 — CSRF token mandatory. |
| `BackupControllerSecurityIT.ProdProfile.givenAuthenticatedWithCsrf_whenPostExport_thenOk` | `@WithMockUser`, `mockMvc.perform(post("/admin/backup/export").with(csrf()))` → HTTP 200 + `Content-Disposition` header asserted. | Positive auth + CSRF case. |
| `BackupControllerSecurityIT.DevProfile.givenAnonymous_whenPostExport_thenOk` | `@Nested @ActiveProfiles("dev")`. No auth, no CSRF token. → HTTP 200. | Dev profile parity. |

### Layer 4 — E2E (Playwright, `-Pe2e` Failsafe)

| Test | Asserts | Coverage |
|------|---------|----------|
| `BackupExportE2ETest.givenAdminUI_whenClickBackupSidebarThenExport_thenZipDownloads` | Extends `PlaywrightConfig` (`@ActiveProfiles("dev")`, random port). Navigates `/admin`, clicks the new `Backup` sidebar link, asserts URL is `/admin/backup`, asserts the `Export Backup` button is visible, intercepts the download via `page.waitForDownload(() -> page.click("button:has-text('Export Backup')"))`. Asserts `download.suggestedFilename()` matches `ctc-backup-\d{8}T\d{6}Z\.zip`. Optionally saves the download and verifies it opens as a valid ZIP via `new ZipInputStream(...)` and finds `manifest.json` as first entry. | EXPORT-01, EXPORT-02 end-to-end. |

### Coverage Budget

Phase 72's last `./mvnw verify` (per 72-VERIFICATION.md and the recent commits) shows the project at ~87% line coverage with 1227+ tests. Phase 73 adds ~24 MixIns (each ~10 LOC, mostly annotations) + 1 controller (~50 LOC) + 1 service (~100 LOC) + 1 archive helper (~100 LOC) ≈ **~500 new LOC of production code**. Realistic coverage from the test plan above:

- 24 MixIns: covered via `BackupSerializationModuleTest` (reflective) + 5 hand-rolled MixIn tests + every IT that exercises the export → ~95%+ line coverage on the package.
- `BackupArchiveService` + `BackupExportService`: covered by `BackupExportServiceIT` end-to-end → ~90%+.
- `BackupController`: covered by `BackupControllerSecurityIT` GETs and POSTs in both profiles → ~90%+.

**Net effect:** project coverage should remain ≥ 85% (well above the 82% gate). No JaCoCo `<exclude>` additions are needed for Phase 73 — the MixIns and services are fully testable (no Playwright runtime dependency like the graphic services).

**Defensive note:** the `StreamingResponseBody` lambda body itself is a closure; JaCoCo can struggle with closure coverage in some JDK / agent combinations. If coverage flickers below threshold post-merge, the Phase-73 plan should preemptively exclude the lambda via `<exclude>org/ctc/backup/BackupController$*</exclude>` rather than try to coerce coverage via a contrived test.

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + AssertJ + Mockito + Spring Boot Test + Playwright 1.59.0 (compile scope, runs only on `-Pe2e`) |
| Config files | `pom.xml` (Surefire/Failsafe split lines 184-194 / 218-248), `src/test/resources/logback-test.xml` |
| Quick run command (per-task commit) | `./mvnw -DskipITs -Dtest='Backup*' test` (unit tests only) |
| Full IT run (per-wave merge) | `./mvnw -Dit.test='Backup*' verify -DskipUTs` |
| Phase gate (final) | `./mvnw verify -Pe2e` — full unit + IT + E2E suite |
| Coverage report | `target/site/jacoco/index.html` after `./mvnw verify` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | Expected Test File |
|--------|----------|-----------|-------------------|--------------------|
| EXPORT-01 | Sidebar entry `Backup` exists in `Data` group; `/admin/backup` renders | E2E | `./mvnw -Dtest=BackupExportE2ETest verify -Pe2e` | `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` (Wave 0 — new file) |
| EXPORT-01 | `GET /admin/backup` returns 200 + view `admin/backup` | IT (MockMvc) | `./mvnw -Dit.test=BackupControllerSecurityIT verify -DskipUTs` | `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` (Wave 0 — new) |
| EXPORT-02 | `POST /admin/backup/export` returns `Content-Disposition: attachment; filename=ctc-backup-<basicISO>.zip` and a non-empty body | IT | same as above | same |
| EXPORT-02 | The body is a streamed `StreamingResponseBody` (no full-dataset buffering — verified by injecting a delay-stream and checking the response status returns before the body completes) | unit (Mockito) | `./mvnw -Dtest=BackupControllerTest test` | `src/test/java/org/ctc/backup/BackupControllerTest.java` (Wave 0 — new) |
| EXPORT-03 | `manifest.json` is `entries[0]` of the ZIP | IT | `./mvnw -Dit.test=BackupExportServiceIT verify -DskipUTs` | `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` (Wave 0 — new) |
| EXPORT-03 | Every `data/<entity>.json` exists in the ZIP, one per `EntityRef` in `BackupSchema.getExportOrder()` | IT | same as above | same |
| EXPORT-03 | `uploads/` mirror contains only entity-referenced files | IT | `./mvnw -Dit.test=BackupUploadsMirrorIT verify -DskipUTs` | `src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java` (Wave 0 — new) |
| EXPORT-04 | Every entity in `BackupSchema.getExportOrder()` has a MixIn registered on `backupObjectMapper` | unit | `./mvnw -Dtest=BackupSerializationModuleTest test` | `src/test/java/org/ctc/backup/serialization/BackupSerializationModuleTest.java` (Wave 0 — new) |
| EXPORT-04 | `org.ctc.domain.model.*` carries no Jackson annotations | IT (reflective) | `./mvnw -Dit.test=BackupEntityAnnotationCleanlinessIT verify -DskipUTs` | `src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java` (Wave 0 — new) |
| EXPORT-04 | Per-MixIn JSON shape: presence of `id`, absence of ignored fields, ID-references for ManyToOne | unit | `./mvnw -Dtest='TeamMixInTest,SeasonMixInTest,RaceMixInTest,DriverMixInTest,RaceAttachmentMixInTest' test` | 5 new `*MixInTest.java` files (Wave 0 — new) |
| EXPORT-05 | `BackupExportService` is `@Transactional(readOnly=true)` | unit (reflective) | `./mvnw -Dtest=BackupExportServiceTest test` | `src/test/java/org/ctc/backup/service/BackupExportServiceTest.java` (Wave 0 — new) |
| EXPORT-05 | Saison-2023 + 2024-3 fixture exports without LIE in logs | IT | `./mvnw -Dit.test=BackupExportServiceIT#givenSaison2023Fixture_whenExport_thenNoLazyInitInLogs verify -DskipUTs` | `BackupExportServiceIT.java` |
| EXPORT-06 | Anonymous POST on prod profile → 401 | IT (`@WithAnonymousUser` + `@ActiveProfiles("prod")`) | `./mvnw -Dit.test=BackupControllerSecurityIT verify -DskipUTs` | `BackupControllerSecurityIT.java` |
| EXPORT-06 | Authenticated POST without CSRF on prod profile → 403 | IT | same | same |
| EXPORT-06 | Authenticated POST with CSRF on prod profile → 200 | IT | same | same |
| EXPORT-06 | Dev profile permits anonymous POST → 200 | IT | same | same |

### Sampling Rate

- **Per task commit:** `./mvnw -DskipITs test` (~30s — unit tests only) — fast feedback loop on every wave-1 MixIn commit.
- **Per wave merge:** `./mvnw verify` (Surefire + Failsafe without `-Pe2e`) — ~2-3 minutes, validates ITs.
- **Phase gate (before `/gsd-verify-work`):** `./mvnw verify -Pe2e` — full unit + IT + E2E suite (~5-7 minutes).
- **JaCoCo gate:** the `jacoco-maven-plugin` `check` goal in `pom.xml` enforces ≥82% line coverage at `verify` phase; the plan acceptance criterion repeats this explicitly.

### ROADMAP Success Criterion → Verification

| SC # | Criterion | Verification |
|------|-----------|--------------|
| SC-1 | Admin clicks `Backup` sidebar → `/admin/backup`; clicking `Export Backup` triggers `POST /admin/backup/export` streaming a ZIP with `Content-Disposition: attachment; filename=ctc-backup-<ISO-instant>.zip` | `BackupExportE2ETest` (E2E Playwright) — full click-through; `BackupControllerSecurityIT.givenAuthenticatedWithCsrf_whenPostExport_thenOk` asserts the Content-Disposition header regex. |
| SC-2 | ZIP contains `manifest.json` as FIRST entry, per-entity JSON under `data/`, `uploads/` mirror | `BackupExportServiceIT.givenDevFixture_whenExport_thenZipMatchesContract` + `BackupUploadsMirrorIT` |
| SC-3 | `org.ctc.domain.model` byte-identically unchanged | `BackupEntityAnnotationCleanlinessIT` (reflective grep test); also a `git diff` check during `/gsd-verify-work` |
| SC-4 | Saison 2023 + 2024-3 dev fixture exports without LIE in logs | `BackupExportServiceIT.givenSaison2023Fixture_whenExport_thenNoLazyInitInLogs` (logback capture) |
| SC-5 | Anonymous `POST /admin/backup/export` rejected on prod/docker; CSRF required + verified | `BackupControllerSecurityIT.ProdProfile.*` (3 tests: anonymous→401, no-csrf→403, with-csrf→200) |

### Negative Tests (extra to the above)

- `BackupControllerSecurityIT.ProdProfile.givenWrongCsrfToken_whenPostExport_thenForbidden` — explicit mismatched CSRF token → 403.
- `BackupExportServiceIT.givenEmptyDatabase_whenExport_thenZipStillValid` — boot context with a fresh (no DevDataSeeder) H2; assert export still produces a valid ZIP with all `data/*.json` files containing `[]` and `manifest.table_counts` all `0`.

### Acceptance Signals (greppable)

The plan acceptance criteria should require executor commits to surface these exact strings, so `/gsd-verify-work` can grep them mechanically:

- `Tests run: \d+, Failures: 0, Errors: 0, Skipped: 0` (per Failsafe report)
- `BUILD SUCCESS` (from `./mvnw verify -Pe2e`)
- `Line coverage: 8[2-9]\.\d+%` (JaCoCo report HTML)
- `manifest\.json.*at index 0` (custom assertion message in `BackupExportServiceIT`)

### Wave 0 Gaps (test infrastructure not yet on disk)

- [ ] `src/test/java/org/ctc/backup/serialization/BackupSerializationModuleTest.java` — covers EXPORT-04 (Module wiring)
- [ ] `src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java` — covers EXPORT-04 (entity-cleanliness reflective gate)
- [ ] `src/test/java/org/ctc/backup/serialization/{Team,Season,Race,Driver,RaceAttachment}MixInTest.java` — 5 representative MixIn shape tests
- [ ] `src/test/java/org/ctc/backup/service/BackupExportServiceTest.java` — unit (Mockito) for service orchestration
- [ ] `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` — full Spring-context ZIP-layout + LIE absence
- [ ] `src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java` — uploads mirror correctness
- [ ] `src/test/java/org/ctc/backup/service/BackupArchiveServiceTest.java` — ZIP plumbing unit
- [ ] `src/test/java/org/ctc/backup/BackupControllerTest.java` — unit (Mockito) for controller wiring
- [ ] `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` — `@Nested` prod/dev profile security tests
- [ ] `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` — Playwright click-through download interception
- [ ] No new shared fixtures needed — reuse `TestHelper.createFullSeasonFixture(...)` and `DevDataSeeder` output.
- [ ] No new framework install needed — JUnit/Mockito/Playwright already configured.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (export endpoint must require auth on prod/docker) | Spring Security 7 `httpBasic()` (existing in `SecurityConfig`) |
| V3 Session Management | yes (CSRF token is session-bound) | Spring Security 7 default `CsrfFilter` with `CookieCsrfTokenRepository` (default behavior; verified inherited via `SecurityConfig` not calling `.csrf(...)`) |
| V4 Access Control | yes (`/admin/**` is admin-only on prod/docker) | `anyRequest().authenticated()` in `SecurityConfig` |
| V5 Input Validation | partial — Phase 73's POST has no body (only a CSRF token). Phase 74 introduces the multipart import where this matters. | n/a for Phase 73 |
| V6 Cryptography | no — export is not encrypted (per `Out of Scope` decision row in REQUIREMENTS.md "Encryption at Rest"). | Future: `SECU-FUT-01` (passworded ZIP). |
| V8 Data Protection | partial — the export contains PSN IDs (public IDs per REQUIREMENTS.md "Out of Scope" rationale), driver nicknames, team data. No personal-identifiable-information (no email addresses, no real names beyond PSN IDs) is in the model. | Document in plan-acceptance: the ZIP contains league operational data, no PII. |
| V12 File and Resources | yes — the export reads filesystem files from `data/{profile}/uploads/`. Path traversal mitigated by Phase 72 `app.upload-dir` resolution. | Re-use FileStorageService path-normalization patterns. |
| V13 API and Web Service | yes — the streamed download is a single endpoint with a CSRF-protected POST. | Validated by `BackupControllerSecurityIT`. |

### Known Threat Patterns for Spring Boot 4 + Thymeleaf + Spring Security 7

| Pattern | STRIDE | Standard Mitigation | Phase 73 Status |
|---------|--------|---------------------|-----------------|
| CSRF attack on POST (cross-origin forged export trigger) | Tampering | Spring Security 7 `CsrfFilter` enabled by default in prod/docker; Thymeleaf auto-injects `_csrf` on `th:action` POST forms | Verified inherited; tested by `BackupControllerSecurityIT.givenAuthenticatedNoCsrf_whenPostExport_thenForbidden`. |
| Anonymous access to admin endpoints | Elevation of Privilege | `SecurityConfig.anyRequest().authenticated()` catches `/admin/backup/**` | Verified inherited; tested by `BackupControllerSecurityIT.givenAnonymous_whenPostExport_thenUnauthorized`. |
| Sensitive data exposure via download | Information Disclosure | Auth gate above; `Content-Disposition: attachment` discourages browser inline rendering | Plan-accepted as low-risk per the "Out of Scope: Encryption at Rest" decision. |
| Path traversal on uploads enumeration | Tampering | Uploads enumeration is **read-side** (no user input) — paths come from entity fields. Each path is validated `startsWith(app.upload-dir)` before `Files.copy(...)` | Plan acceptance criterion: `BackupArchiveService` must check `Path.normalize().startsWith(uploadRoot)` before adding any `uploads/` entry. |
| ZIP-Slip on EXPORT (writing-side; less common than reading-side) | Tampering | ZipEntry names are derived from `EntityRef.fileName()` (a `data/<kebab>.json` string with no user input) + `uploads/<relativePath>` where `<relativePath>` is a sub-path of `app.upload-dir`. Defensive check: assert no `..` segment is in any `relativePath` before adding | Plan acceptance criterion: `BackupArchiveService` asserts `!entryName.contains("..")` before each `putNextEntry()` call. |
| Resource exhaustion on export (slow client, large dataset, holds open connection) | Denial of Service | Streaming response avoids server-side memory exhaustion; if a malicious client holds the response, only one MVC async thread is consumed | Plan-accepted; v1.11+ may add an export-attempt rate-limit (`SECU-FUT-02`). |

## Landmines & Open Decisions

### L-1: Hibernate6Module is NOT needed — adding it would mask bugs

[CITED: jackson-datatype-hibernate6 docs — the module's `FORCE_LAZY_LOADING` feature triggers initialization of every proxy at serialization time, and the `REPLACE_PERSISTENT_COLLECTIONS` feature replaces Hibernate's `PersistentBag` etc. with plain `ArrayList`.] In our setup, the `@EntityGraph` already materializes every traversed association, AND the MixIns annotate `@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})` as catch-all. Adding `Hibernate6Module` would mask `@EntityGraph` gaps — a missed attribute path would silently trigger lazy init via the module instead of throwing LIE at the right place. **Reject** adding the dependency.

### L-2: Jackson 2 vs Jackson 3 — Phase 72 already locked Jackson 2

[VERIFIED: Phase 72 Plan-03 RESEARCH §A2 + 72-03-SUMMARY "Rule 1 (Bug)" — Spring Boot 4 ships Jackson 3 as the *default* REST stack (`tools.jackson`) but the `BackupObjectMapperConfig.backupObjectMapper(...)` deliberately uses `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2). The compatibility layer is on the classpath transitively via `flyway-core` + `spring-boot-starter-jackson`. `jackson-datatype-jsr310` was added explicitly in Phase 72 Plan-03 for `JavaTimeModule`.] All Phase 73 MixIn imports use `com.fasterxml.jackson.annotation.*` (Jackson 2). **No change** required.

### L-3: Filename charset — drop colons for Windows safety

ISO-8601 extended form `2026-05-11T17:42:30.123Z` contains colons that NTFS rejects. Use the **basic form** `20260511T174230Z` instead. See §Streaming ZIP Architecture Pattern 5 for the recommended `DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")`. The plan acceptance criterion should be the regex `^ctc-backup-\d{8}T\d{6}Z\.zip$`.

### L-4: ZIP byte-determinism across exports — accept non-determinism

Two exports of the same database state at different `Instant`s produce different ZIPs because:
- `manifest.exportDate` differs.
- `ZipEntry.setTime(...)` defaults to `System.currentTimeMillis()` on each entry write.

The `BackupRoundTripIT` in Phase 77 uses SHA-256 hashes of **selected entity JSON** within the ZIP (NOT of the whole ZIP), avoiding this non-determinism. Phase 73 does not need to address this; documenting the constraint here is sufficient for Phase 77's planner.

### L-5: Memory footprint of `tableCounts` — count first, serialize second

The manifest's `tableCounts` must be computed BEFORE the per-entity loop starts writing (because the manifest is entry #1). Using `repository.count()` 24 times costs 24 SELECT COUNT queries — cheap and accurate (transactional consistency guaranteed by `@Transactional(readOnly=true)`). Do NOT defer counts until after the entity loop runs and try to fix up the manifest — ZIP entries cannot be rewritten after `closeEntry()`.

### L-6: `Race.settings` inverse-side `@OneToOne` trap

`Race.settings` is declared `@OneToOne(mappedBy="race", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)`. JPA Metamodel reports this as a `SingularAttribute<Race, RaceSettings>` with `ONE_TO_ONE` type — looks identical to the owning side. Phase 72's `EntityTopoSorter.isInverseOneToOne()` filters it out via reflection (verified in `EntityTopoSorter.java` lines 102-109). **`RaceMixIn` must explicitly `@JsonIgnore` the `settings` field** — even though `@EntityGraph` doesn't fetch it, Jackson would still try to read the getter and get `null` (best case) or a Hibernate proxy (worst case). The single-line `@JsonIgnore` on the `Race.settings` getter property in `RaceMixIn` is the fix.

### L-7: OSIV is NOT load-bearing for `StreamingResponseBody` — `@Transactional` is

OSIV (`spring.jpa.open-in-view=true`) keeps the Hibernate session open until the HTTP request completes. For Thymeleaf templates this is sufficient because rendering happens INSIDE the controller-thread request. For `StreamingResponseBody`, however, the actual byte-writing happens on an MVC async worker thread AFTER the controller method returns. Whether OSIV's session is still open at that point depends on Spring's `OpenEntityManagerInViewInterceptor` semantics — and even if it is, the **transactional read-only context is NOT propagated** to the async thread; calling `findAllForBackup()` from inside the lambda would execute outside any transaction.

**Mitigation:** `BackupExportService.fetchAllForBackup(entityClass)` is `@Transactional(readOnly=true)`. Each call to it from inside the `StreamingResponseBody` lambda opens its own transactional scope, fetches, and returns. The transaction is short-lived but explicit. **OSIV is NOT relied on.** This pattern is documented in plan acceptance criterion: "`BackupExportService` method-level `@Transactional(readOnly=true)` annotation is mandatory; service-class-level annotation is acceptable."

### L-8: Spring Security 7 CSRF differences from 5/6 — verified no test-pattern changes needed

[VERIFIED via SecurityIntegrationTest.java] — the existing `@WithMockUser` + `.with(csrf())` pattern works under Spring Security 7 unchanged. The CSRF token repository defaults to `XorCsrfTokenRequestAttributeHandler` in SS7 (was `CsrfTokenRequestAttributeHandler` in SS6) but this is transparent to MockMvc. Test patterns copied from older code work as-is.

### L-9: Profile detection for `data/{profile}/uploads/`

`app.upload-dir` resolves at startup to the profile-specific value (`data/dev/uploads` for dev, `data/local/uploads` for local, `/app/uploads` for docker). Phase 73 reads from `@Value("${app.upload-dir}")` — automatically profile-aware. ROADMAP SC-2 wording "`data/{profile}/uploads/`" is a Phase 73 *description* of the source path, not a literal token to substitute at runtime; the runtime path comes from the property.

### L-10: Streaming + transactional boundary failure mode

If `BackupExportService.fetchAllForBackup(entityClass)` throws (e.g. JPA query failure, OOM during result materialisation), the exception bubbles out of the `StreamingResponseBody` lambda. If the throw happens AFTER the manifest has been flushed to the ZIP, the resulting ZIP has a truncated/malformed entry at the failure point. **The plan must document this acceptance:** "mid-stream failures produce a truncated ZIP; the executor logs `log.error(...)`; the user retries the export manually." This is consistent with UI-SPEC ("the orchestrator brief lists progress / cancel UI as out of scope").

## Open Questions

### OQ-1 (UI copy): "23 entities" vs "24 entities" in description paragraph

UI-SPEC line 134 reads:
> "Exports the full league database (all 23 entities) plus every uploaded file into a single ZIP archive."

Phase 72's `BackupSchemaTopologyIT` asserts `getExportOrder().size() == 24`. The UI-SPEC carries a note (line 287): "if Phase 72's runtime topo-sort delivers 24 by ship time, update the description copy to 24 before merging Phase 73." The runtime topo-sort DID deliver 24. The plan must change `23` → `24` in `backup.html`.

**Recommended default:** Update the description copy to `"Exports the full league database (all 24 entities) plus every uploaded file into a single ZIP archive. The download starts immediately and may take a few seconds for large leagues."`. Plan acceptance criterion: the rendered `/admin/backup` page contains the substring `(all 24 entities)`.

### OQ-2 (architecture): One Module vs many Modules for MixIn registration

Two viable shapes:
- **One `BackupSerializationModule` `@Component` extending `SimpleModule`** that calls `setMixInAnnotation(...)` 24 times. — Recommended (see Pattern 2).
- **24 separate `@Component` Module classes**, each registering exactly one MixIn. — Verbose, allows per-MixIn parallelization at the wave level, but creates 24 trivial Module files.

**Recommended default:** one Module. Plan acceptance criterion: `src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` exists and contains exactly 24 `setMixInAnnotation(...)` calls.

### OQ-3 (architecture): Service split — one service or two?

- Option A: `BackupExportService` only — DB reads + serialization + ZIP writing all in one service.
- Option B (recommended): `BackupExportService` (DB reads + per-entity serialization to `byte[]` / `Stream`) + `BackupArchiveService` (ZIP plumbing + uploads enumeration).

**Recommended default:** Option B. Single-responsibility split makes the unit tests cleaner (`BackupArchiveServiceTest` mocks `BackupExportService`; `BackupExportServiceTest` mocks the repositories). Plan acceptance criterion: both classes exist under `org.ctc.backup.service`.

### OQ-4 (data): `Driver.aliases` inline-vs-top-level emission

Phase 72's topo-sort places `PsnAlias` as a top-level entity in `EXPORT_ORDER` (alongside `Driver`). Two ways to round-trip alias data:
- Emit aliases inline as nested children of `Driver` AND emit them again as top-level rows in `data/psn-aliases.json` — DUPLICATE; would require `DriverMixIn` to keep `aliases` AND `PsnAliasMixIn` to render normally. Import code would have to de-duplicate.
- Emit aliases ONLY as top-level rows in `data/psn-aliases.json` — `DriverMixIn` ignores `aliases`. Import attaches via FK.

**Recommended default:** option 2 (top-level only). Symmetric with every other parent-child relationship. Plan acceptance criterion: `DriverMixIn` contains `@JsonIgnoreProperties({..., "aliases"})` AND `data/drivers.json` JSON shape has no `"aliases"` key.

### OQ-5 (operational): Single big POST handler vs Wave-split planning

Phase 73 has clear wave separation:
- Wave 1: 24 MixIns + `BackupSerializationModule` + 24 `findAllForBackup()` repository methods + ~6 unit tests
- Wave 2: `BackupExportService` + `BackupArchiveService` + ITs (BackupExportServiceIT, BackupUploadsMirrorIT, BackupEntityAnnotationCleanlinessIT)
- Wave 3: `BackupController` + `backup.html` + sidebar update + `BackupControllerSecurityIT` + Playwright E2E

Wave 1 tasks are independently mergeable (each MixIn file is independent of every other). Wave 2 depends on Wave 1's Module. Wave 3 depends on Wave 2's services. **Recommended default:** plan 5-6 plans across these 3 waves.

## Assumptions Log

| # | Claim | Section | Source | Risk if Wrong |
|---|-------|---------|--------|---------------|
| A1 | Jackson 2 (not Jackson 3) is the right import root for MixIns | MixIn Design | [VERIFIED: 72-03 SUMMARY Rule-1 deviation; `BackupObjectMapperConfig.backupObjectMapper(...)` returns `com.fasterxml.jackson.databind.ObjectMapper`] | LOW — Phase 72 already shipped on Jackson 2; we're following the locked decision. |
| A2 | `List<Module>` injection point in Phase-72 `backupObjectMapper(...)` picks up `@Component`-annotated `SimpleModule` instances automatically | MixIn Design Pattern 2 | [CITED: Spring DI behaviour — any `@Component` bean whose type is assignable to `Module` is collected into `List<Module>` parameter] + [VERIFIED: BackupObjectMapperConfig.java line 75 — exact signature `backupObjectMapper(List<Module> backupMixInModules)`] | LOW |
| A3 | Reading `getId()` of a Hibernate lazy proxy does NOT trigger initialization | EntityGraph Fetch Map | [CITED: Hibernate user guide — "the proxy already holds the ID; calling `getId()` is the only method that does NOT trigger initialization"] | LOW — well-documented Hibernate behaviour. |
| A4 | `EntityRef.fileName()` Phase 72 derivation produces exactly `data/<kebab>.json` matching ROADMAP SC-2 examples | Streaming ZIP Architecture Pattern 3 | [VERIFIED: EntityRef.java line 36 — `"data/" + table.replace('_', '-') + ".json"`] | LOW |
| A5 | `StreamingResponseBody` lambda runs on an MVC async thread but does NOT propagate OSIV's transactional context | Landmine L-7 | [CITED: Spring docs — `StreamingResponseBody` is "called inside the request lifecycle but its body executes on a separate thread"; transactional context is thread-local and does NOT auto-propagate] | MEDIUM — mitigation is explicit `@Transactional(readOnly=true)` on each service method. If the executor mis-implements this, the IT's `givenSaison2023Fixture_whenExport_thenNoLazyInitInLogs` will catch it (because `LazyInitializationException` is the failure mode of a missed transaction). |
| A6 | The 5 representative MixIn unit tests + 1 reflective module test give >95% MixIn coverage | Testing Strategy | [ASSUMED — based on JaCoCo's branch-and-line-coverage semantics on annotation-only classes (each annotation is a single bytecode insn; one positive test covers it).] | MEDIUM — if JaCoCo flags coverage as low post-merge, add the remaining 19 MixIn tests (mechanical, ~30 min). |
| A7 | Anonymous POST to `/admin/backup/export` on prod profile returns HTTP 401, not 403 | Controller & Security Pattern 3 | [VERIFIED: SecurityIntegrationTest.ProdProfileSecurityTest.givenNoCredentials_whenAccessAdmin_thenUnauthorized asserts `status().isUnauthorized()` (401) on `GET /admin/seasons`] | LOW — same security filter chain catches the new endpoint identically. |
| A8 | The download interception via Playwright `page.waitForDownload(...)` works against the new endpoint | Testing Strategy Layer 4 | [CITED: Playwright Java docs — `Page.waitForDownload(Runnable trigger)` returns a `Download` object after `Content-Disposition: attachment` is detected] | LOW — established pattern in Playwright; will be smoke-tested in the E2E. |

**Mitigation:** every `[ASSUMED]` row above is converted to `[VERIFIED]` when the corresponding test passes. The plan's acceptance criteria therefore double as the verification of the assumptions.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 25 (Temurin) | Compilation + runtime | ✓ | `25` (per CLAUDE.md "Runtime") | — |
| Maven 3.9.14 via `./mvnw` | Build + tests | ✓ | wrapper-managed | — |
| Spring Boot 4.0.6 | Application framework | ✓ | 4.0.6 (pom.xml L8) | — |
| Jackson Databind (Jackson 2) | MixIns + ObjectMapper | ✓ | 2.21.x (transitive) | — |
| Jackson JSR310 (`jackson-datatype-jsr310`) | `JavaTimeModule` for `Instant`/`LocalDateTime` | ✓ | 2.21.x (pom.xml L78-L81) | — |
| Spring Security 7 | CSRF + auth | ✓ | transitive 4.0.6 | — |
| Spring Data JPA `@EntityGraph` | Per-repository eager fetch | ✓ | transitive 4.0.6 | — |
| Playwright 1.59.0 | E2E download test | ✓ | compile-scope dep | — (E2E test runs only on `-Pe2e`) |
| H2 in-memory DB | dev profile + ITs | ✓ | 2.x transitive | — |
| MariaDB | local/docker/prod | ✓ (local optional) | external | dev profile suffices for Phase 73 ITs |
| `DevDataSeeder` Saison 2023 + 2024-3 fixture | `BackupExportServiceIT.givenSaison2023Fixture_whenExport_thenNoLazyInitInLogs` | ✓ | already on disk (referenced in CLAUDE.md `dev,demo` profile) | — |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none.

## Sources

### Primary (HIGH confidence — Phase 72 artefacts on disk in this branch)

- `src/main/java/org/ctc/backup/schema/BackupSchema.java` — confirmed `@Component`, `@PostConstruct` topology, `SCHEMA_VERSION = 1`, `getExportOrder()` returning `List<EntityRef>`.
- `src/main/java/org/ctc/backup/schema/EntityRef.java` — confirmed record shape `(Class<?>, String tableName, String fileName)`, `data/<kebab>.json` derivation.
- `src/main/java/org/ctc/backup/schema/BackupManifest.java` — confirmed record with `@JsonProperty` snake_case keys.
- `src/main/java/org/ctc/backup/schema/EntityTopoSorter.java` — confirmed Kahn impl + inverse-`@OneToOne` skip.
- `src/main/java/org/ctc/backup/config/BackupObjectMapperConfig.java` — confirmed dual-bean shape; `List<Module> backupMixInModules` parameter on line 75.
- `src/main/java/org/ctc/backup/audit/DataImportAudit.java` — confirmed inert entity (Phase 75 will write).
- `src/main/java/org/ctc/domain/model/*.java` — read all 24 entity classes (Car, Track, Season, SeasonPhase, SeasonPhaseGroup, Team, PhaseTeam, SeasonTeam, Driver, SeasonDriver, PsnAlias, RaceScoring, MatchScoring, RaceSettings, Matchday, Match, Race, RaceLineup, RaceResult, RaceAttachment, Playoff, PlayoffRound, PlayoffMatchup, PlayoffSeed).
- `src/main/java/org/ctc/admin/SecurityConfig.java` + `OpenSecurityConfig.java` — confirmed profile-conditional shape + CSRF defaults.
- `src/main/java/org/ctc/sitegen/SiteGeneratorController.java` — closest live precedent for Phase 73's controller.
- `src/main/resources/templates/admin/generate.html` — closest live template precedent.
- `src/main/resources/templates/admin/layout.html` — sidebar fragment authority.
- `src/main/java/org/ctc/admin/controller/TeamCardController.java` lines 113-137 — precedent for `ZipOutputStream` + `Content-Disposition` admin download pattern.
- `src/test/java/org/ctc/admin/SecurityIntegrationTest.java` — pattern source for `BackupControllerSecurityIT`.
- `.planning/phases/73-backup-export-jackson-mixins-streaming-zip-endpoint/73-UI-SPEC.md` — UI lock.
- `.planning/phases/72-*` — full Phase 72 context (CONTEXT, RESEARCH, 5 Plans, 5 Summaries, VERIFICATION).
- `.planning/REQUIREMENTS.md` lines 31-37 — EXPORT-01..06 verbatim source.
- `.planning/ROADMAP.md` lines 195-213 — Phase 73 goal + 5 success criteria.

### Secondary (MEDIUM confidence — cited)

- Spring Framework reference docs — `StreamingResponseBody` semantics (MVC async; body runs on async worker thread).
- Jackson 2 docs — `@JsonIdentityInfo`, `setMixInAnnotation`, `SimpleModule.setupModule`, `ObjectMapper.writeValue(OutputStream, ...)` streaming behaviour.
- Hibernate user guide — lazy proxy `getId()` does NOT trigger initialization.

### Tertiary (LOW confidence — none)

No claims in this RESEARCH depend on a single unverified web source. All architectural patterns are verified against the existing CTC codebase or against the Phase 72 artefacts.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every library is on the classpath, verified against `pom.xml` and Phase 72's already-shipped `BackupObjectMapperConfig`.
- Entity catalog: HIGH — read every `org.ctc.domain.model.*` class in this session.
- MixIn design: HIGH — pattern verified against Jackson 2 docs and against Phase 72's `List<Module>` injection point.
- EntityGraph fetch map: HIGH — derived directly from the entity catalog's `@ManyToOne` analysis.
- Streaming ZIP architecture: HIGH — `TeamCardController.downloadAll()` (already on disk) is a direct precedent for `ZipOutputStream` + `Content-Disposition`; `StreamingResponseBody` is well-documented in Spring.
- Controller & Security: HIGH — `SecurityIntegrationTest.java` (already on disk) is the test-pattern precedent; `SiteGeneratorController` is the controller-pattern precedent.
- Testing strategy: MEDIUM-HIGH — coverage budget assumptions are reasonable but not measured; will be verified by JaCoCo report on the first `./mvnw verify`.
- Pitfalls/landmines: MEDIUM — based on Spring/Jackson/Hibernate documented behaviour; the OSIV-vs-StreamingResponseBody interaction is the most subtle and is mitigated by explicit `@Transactional(readOnly=true)`.

**Research date:** 2026-05-11
**Valid until:** 2026-06-11 (1 month — stable Phase 72 baseline; no Spring Boot or Jackson upgrades pending).
