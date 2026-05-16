# Phase 73: Backup Export — Jackson MixIns + Streaming ZIP Endpoint — Pattern Map

**Mapped:** 2026-05-11
**Files analyzed:** ~50 (24 MixIns + Module + 2 services + controller + template + 1 layout edit + 24 repo edits + ~11 tests)
**Analogs found:** 49 / 50 (one new role — Jackson MixIn — has no direct in-repo analog; canonical Jackson shape provided)

---

## File Classification

### NEW production files

| New File (absolute path) | Role | Data Flow | Closest Analog | Match Quality |
|--------------------------|------|-----------|----------------|---------------|
| `src/main/java/org/ctc/backup/serialization/CarMixIn.java` | Jackson MixIn (annotation-only) | transform | (canonical Jackson 2 shape — no in-repo analog) | no-analog (template provided) |
| `src/main/java/org/ctc/backup/serialization/TrackMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/RaceScoringMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/MatchScoringMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/DriverMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/PsnAliasMixIn.java` | Jackson MixIn (`@JsonIdentityReference` on `driver`) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/TeamMixIn.java` | Jackson MixIn (self-FK, `@JsonIdentityReference` on `parentTeam`) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/SeasonMixIn.java` | Jackson MixIn (`@ManyToMany` ID-ref collections) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/SeasonPhaseMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/SeasonPhaseGroupMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/PhaseTeamMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/SeasonTeamMixIn.java` | Jackson MixIn (self-typed `successor` FK) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/SeasonDriverMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/PlayoffMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/PlayoffRoundMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/PlayoffMatchupMixIn.java` | Jackson MixIn (self-FK `nextMatchup`) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/PlayoffSeedMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/MatchdayMixIn.java` | Jackson MixIn (suppress `getSeason` getter) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/MatchMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/RaceMixIn.java` | Jackson MixIn (ignore inverse `@OneToOne settings`, many convenience getters) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/RaceLineupMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/RaceResultMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/RaceSettingsMixIn.java` | Jackson MixIn | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/RaceAttachmentMixIn.java` | Jackson MixIn (`url` drives uploads mirror) | transform | same | no-analog |
| `src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java` | Jackson `SimpleModule` `@Component` (registers all 24 MixIns) | event-driven (Spring DI hook) | `org.ctc.backup.config.BackupObjectMapperConfig` (Phase 72 — consumes `List<Module>`) | role-match (sibling config-level component) |
| `src/main/java/org/ctc/backup/service/BackupExportService.java` | `@Service` + `@Transactional(readOnly=true)` — DB-read orchestrator | request-response (read-aggregate) | `org.ctc.domain.service.StandingsService` (closest `@Transactional(readOnly=true)` aggregator service) | role-match |
| `src/main/java/org/ctc/backup/service/BackupArchiveService.java` | `@Service` — ZIP plumbing, no DB | streaming / file-I/O | `org.ctc.admin.controller.TeamCardController.downloadAll(...)` inline ZIP (only existing ZIP writer) | partial-match (logic exists inline in a controller; will be promoted to a dedicated service) |
| `src/main/java/org/ctc/backup/BackupController.java` | `@Controller` — single GET form + single POST streaming download | request-response / streaming | `org.ctc.sitegen.SiteGeneratorController` (single GET + single POST under `/admin/generate`, also a "feature-module" controller outside `org.ctc.admin.controller`) | exact match |
| `src/main/resources/templates/admin/backup.html` | Thymeleaf page (one description paragraph + one POST button) | request-response | `src/main/resources/templates/admin/generate.html` (locked by UI-SPEC) | exact match |

### MODIFIED production files

| Modified File | Role | What Changes | Closest Analog (in same file) |
|---------------|------|--------------|-------------------------------|
| `src/main/resources/templates/admin/layout.html` | Layout fragment (sidebar nav) | Add new `<div class="sidebar-group">` for "Data" group with "Backup" link **between the existing Tools group (lines 64-73) and `</nav>` (line 74)** | Existing 4 sidebar groups (lines 45-73) |
| `src/main/java/org/ctc/domain/repository/CarRepository.java` | Repository | Add `@EntityGraph(attributePaths = {}) @Query("SELECT c FROM Car c") List<Car> findAllForBackup();` (empty paths — `Car` has no `@ManyToOne`) | `RaceRepository.findByMatchdayId` |
| `src/main/java/org/ctc/domain/repository/TrackRepository.java` | Repository | Add `findAllForBackup()` (empty paths) | same |
| `src/main/java/org/ctc/domain/repository/RaceScoringRepository.java` | Repository | Add `findAllForBackup()` (empty paths) | same |
| `src/main/java/org/ctc/domain/repository/MatchScoringRepository.java` | Repository | Add `findAllForBackup()` (empty paths) | same |
| `src/main/java/org/ctc/domain/repository/DriverRepository.java` | Repository | Add `findAllForBackup()` (empty paths) | same |
| `src/main/java/org/ctc/domain/repository/PsnAliasRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"driver"}` | same |
| `src/main/java/org/ctc/domain/repository/TeamRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"parentTeam"}` | same |
| `src/main/java/org/ctc/domain/repository/SeasonRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"cars","tracks"}` | same |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"season","raceScoring","matchScoring"}` | same |
| `src/main/java/org/ctc/domain/repository/SeasonPhaseGroupRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"phase"}` | same |
| `src/main/java/org/ctc/domain/repository/PhaseTeamRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"phase","team","group"}` | same |
| `src/main/java/org/ctc/domain/repository/SeasonTeamRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"season","team","successor"}` | same |
| `src/main/java/org/ctc/domain/repository/SeasonDriverRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"season","driver","team"}` (already has `{"driver","team"}` finder — extend pattern) | `SeasonDriverRepository.findBySeasonId` (already in file) |
| `src/main/java/org/ctc/domain/repository/PlayoffRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"phase"}` | `RaceRepository.findByMatchdayId` |
| `src/main/java/org/ctc/domain/repository/PlayoffRoundRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"playoff"}` | same |
| `src/main/java/org/ctc/domain/repository/PlayoffMatchupRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"round","team1","team2","winner","nextMatchup"}` | same |
| `src/main/java/org/ctc/domain/repository/PlayoffSeedRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"playoff","team"}` | same |
| `src/main/java/org/ctc/domain/repository/MatchdayRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"phase","group"}` | same |
| `src/main/java/org/ctc/domain/repository/MatchRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"matchday","homeTeam","awayTeam"}` | same |
| `src/main/java/org/ctc/domain/repository/RaceRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"matchday","match","track","car","playoffMatchup","homeTeamOverride","awayTeamOverride"}` | existing `RaceRepository.findByMatchdayId` (already in file) |
| `src/main/java/org/ctc/domain/repository/RaceLineupRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"race","driver","team"}` | `RaceRepository.findByMatchdayId` |
| `src/main/java/org/ctc/domain/repository/RaceResultRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"race","driver"}` | same |
| `src/main/java/org/ctc/domain/repository/RaceSettingsRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"race"}` | same |
| `src/main/java/org/ctc/domain/repository/RaceAttachmentRepository.java` | Repository | Add `findAllForBackup()` with `attributePaths={"race"}` | same |

**Note:** `BackupObjectMapperConfig.java` is NOT modified — Phase 72 already wired `List<Module> backupMixInModules` (verified in `BackupObjectMapperConfig.backupObjectMapper(List<Module>)`, lines 73-82). Phase 73's `BackupSerializationModule @Component` is auto-picked up.

### NEW test files

| Test File | Role | Profile | Closest Analog |
|-----------|------|---------|----------------|
| `src/test/java/org/ctc/backup/serialization/BackupSerializationModuleTest.java` | Unit (Surefire `*Test.java`) | none (`new ObjectMapper()`) | `BackupManifestSerializationTest.java` (Phase 72) |
| `src/test/java/org/ctc/backup/serialization/TeamMixInTest.java` (+ 4 more — `SeasonMixInTest`, `RaceMixInTest`, `DriverMixInTest`, `RaceAttachmentMixInTest`) | Unit | none | same |
| `src/test/java/org/ctc/backup/serialization/BackupEntityAnnotationCleanlinessIT.java` | Reflective IT (Failsafe `*IT.java`) | dev | `BackupSchemaTopologyIT.java` (Phase 72) |
| `src/test/java/org/ctc/backup/service/BackupExportServiceTest.java` | Mockito unit | none | any `*Test.java` in `src/test/java/org/ctc/domain/service/` (e.g. existing service test pattern) |
| `src/test/java/org/ctc/backup/service/BackupExportServiceIT.java` | Failsafe IT (boots Spring + H2 + DevDataSeeder fixture) | dev | `BackupObjectMapperConfigIT.java` (Phase 72) |
| `src/test/java/org/ctc/backup/service/BackupArchiveServiceTest.java` | Mockito unit (asserts manifest-first, ZIP layout) | none | sibling service tests |
| `src/test/java/org/ctc/backup/service/BackupUploadsMirrorIT.java` | Failsafe IT | dev | same |
| `src/test/java/org/ctc/backup/BackupControllerTest.java` | MockMvc unit | none | `RaceControllerTest.java` (existing MockMvc pattern in `src/test/java/org/ctc/admin/controller/`) |
| `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java` | Failsafe IT (`@Nested` prod + dev profiles, `@AutoConfigureMockMvc`, `@WithMockUser`, `.with(csrf())`) | prod + dev | **`src/test/java/org/ctc/admin/SecurityIntegrationTest.java`** (exact precedent) |
| `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` | Playwright E2E (extends `PlaywrightConfig`, `page.waitForDownload()`) | dev | `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` (closest base class consumer; **no existing download-interception test — first of its kind in this repo**) |

---

## Pattern Assignments

### 1) Jackson MixIn classes (24 new files under `org.ctc.backup.serialization`)

**Role:** transform-only annotation carrier (no logic, no I/O).

**Closest analog:** None in this repo. Canonical Jackson 2 shape verified against `pom.xml` / Phase 72 `BackupObjectMapperConfig` which uses `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2 — RESEARCH §Standard Stack).

**Canonical shape — copy this skeleton for each entity** (from RESEARCH §MixIn Design Pattern 1, applied to `Team`):

```java
// src/main/java/org/ctc/backup/serialization/TeamMixIn.java
package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link Team}. Phase 73 EXPORT-04.
 *
 * <p>Required because:
 * <ul>
 *   <li>{@code Team.parentTeam} is a self-FK — needs ID-only reference.</li>
 *   <li>{@code Team.subTeams} is inverse — must be ignored.</li>
 *   <li>Convenience methods (isSubTeam, hasSubTeams, getParentOrSelf) are computed.</li>
 * </ul>
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
                       "subTeams", "seasonDrivers",
                       "subTeam", "parentOrSelf"})        // "subTeam" suppresses isSubTeam()
public abstract class TeamMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getParentTeam();
}
```

**Per-entity variations** — see RESEARCH §Entity Catalog (the table at lines 171-196) for exact `@JsonIgnoreProperties` + `@JsonIdentityReference` lists for each of the 24. Highlights:

| MixIn | Special handling |
|-------|------------------|
| `CarMixIn`, `TrackMixIn`, `RaceScoringMixIn`, `MatchScoringMixIn` | Plain `@JsonIdentityInfo` only; no FKs. |
| `DriverMixIn` | Ignore `seasonDrivers`, `raceResults`, `aliases` (PsnAlias is its own top-level file). |
| `PsnAliasMixIn` | `@JsonIdentityReference(alwaysAsId=true)` on `getDriver()`. |
| `SeasonMixIn` | Ignore `phases`, `seasonDrivers`, `seasonTeams`; render `cars` + `tracks` as ID-ref collections. |
| `RaceMixIn` | Ignore `settings` (inverse `@OneToOne`), plus `results`, `attachments`, and the 6 convenience getters (`homeTeam`, `awayTeam`, `homeScore`, `awayScore`, `bye`, `allSettings`, `calendarEvent`). |
| `MatchdayMixIn` | Suppress `getSeason()` (derived, not persisted). |
| `PlayoffMatchupMixIn` | `@JsonIdentityReference` on `nextMatchup` (self-FK) plus `round`, `team1`, `team2`, `winner`. |
| `SeasonTeamMixIn` | `@JsonIdentityReference` on `successor` (self-typed FK). |

**Catch-all rule (every MixIn):** include `"hibernateLazyInitializer", "handler"` in `@JsonIgnoreProperties` — defense in depth against lazy-proxy leakage.

**Notes for the planner:**
- MixIn classes are `abstract` (Jackson does not instantiate them; abstract makes intent explicit and prevents accidental construction).
- Package layout: `org.ctc.backup.serialization` (RESEARCH §Recommended Project Structure).
- No Lombok, no Spring annotations — they are pure Jackson annotation carriers.
- The `org.ctc.domain.model.*` entities stay byte-identical (success criterion 3, enforced by `BackupEntityAnnotationCleanlinessIT`).

---

### 2) `BackupSerializationModule` — single `@Component SimpleModule`

**Role:** Spring-DI hook that registers all 24 MixIns onto `backupObjectMapper`.

**Closest analog:** `org.ctc.backup.config.BackupObjectMapperConfig` (Phase 72 — consumes `List<Module> backupMixInModules`).

**Existing wiring contract** (from `BackupObjectMapperConfig.java` lines 73-82 — DO NOT MODIFY):

```java
@Bean
@Qualifier("backupObjectMapper")
public ObjectMapper backupObjectMapper(List<Module> backupMixInModules) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.registerModule(new JavaTimeModule());
    backupMixInModules.forEach(mapper::registerModule);
    return mapper;
}
```

**Canonical shape — copy from RESEARCH §MixIn Design Pattern 2:**

```java
// src/main/java/org/ctc/backup/serialization/BackupSerializationModule.java
package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.ctc.domain.model.*;
import org.springframework.stereotype.Component;

@Component
public class BackupSerializationModule extends SimpleModule {

    public BackupSerializationModule() {
        super("BackupSerializationModule");
        setMixInAnnotation(Car.class, CarMixIn.class);
        setMixInAnnotation(Track.class, TrackMixIn.class);
        // ... all 24 — see RESEARCH lines 342-368 for the full list ...
        setMixInAnnotation(RaceAttachment.class, RaceAttachmentMixIn.class);
    }
}
```

**Notes for the planner:**
- One Module containing all 24 `setMixInAnnotation(...)` calls — NOT 24 separate modules. RESEARCH §Pattern 2 rationale: a single file makes the entity→MixIn map reviewable at a glance.
- `@Component` (not `@Configuration`) — picked up by Spring component scan; injected into `backupObjectMapper(List<Module>)` automatically.
- Must extend `com.fasterxml.jackson.databind.module.SimpleModule` so it satisfies `List<Module>` injection.

---

### 3) `BackupExportService` — `@Transactional(readOnly=true)` aggregator

**Role:** controller-detached read-aggregate service. For each `EntityRef` in `BackupSchema.getExportOrder()`, fetches rows via `*Repository.findAllForBackup()`.

**Closest analog:** `src/main/java/org/ctc/domain/service/StandingsService.java` (Phase 60+; only repo + `@Transactional(readOnly=true)`).

**Imports + class shape** (`StandingsService` lines 1-26):

```java
package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingsService {

    private final MatchRepository matchRepository;
    private final SeasonRepository seasonRepository;
    // ... constructor-injected via final fields (Lombok @RequiredArgsConstructor)

    @Transactional(readOnly = true)
    public List<TeamStanding> compute(...) { ... }
}
```

**Apply to `BackupExportService`:** copy the `@Slf4j @Service @RequiredArgsConstructor` triad. Inject `BackupSchema` + `@Qualifier("backupObjectMapper") ObjectMapper` + all 24 repositories as `final` fields. Annotate the public entry method(s) with `@Transactional(readOnly=true)`.

**Tip for ctor-injecting a qualified bean** (Spring 6+/Lombok pattern):

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupExportService {

    private final BackupSchema backupSchema;
    @Qualifier("backupObjectMapper")
    private final ObjectMapper backupObjectMapper;     // @Qualifier on the field is honored by @RequiredArgsConstructor
    private final CarRepository carRepository;
    // ... 23 more repos ...

    @Transactional(readOnly = true)
    public Map<String, Long> countRowsPerTable() { ... }

    @Transactional(readOnly = true)
    public List<?> fetchAllForBackup(Class<?> entityClass) {
        // dispatch via switch on entityClass → repository.findAllForBackup()
    }
}
```

---

### 4) `BackupArchiveService` — ZIP plumbing, manifest-first

**Role:** stateless write-only service: opens a `ZipOutputStream`, writes manifest first, then data entries, then uploads mirror.

**Closest analog:** `org.ctc.admin.controller.TeamCardController.downloadAll(...)` lines 113-137 (only existing JDK `ZipOutputStream` user in the repo).

**Existing inline-ZIP pattern from `TeamCardController.downloadAll`:**

```java
// TeamCardController.java lines 116-129
var baos = new ByteArrayOutputStream();
try (var zip = new ZipOutputStream(baos)) {
    for (var st : seasonTeams) {
        String cardPath = teamCardService.getCardPath(st);
        Path file = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve(cardPath.substring("/uploads/".length()));
        if (Files.exists(file)) {
            zip.putNextEntry(new ZipEntry(st.getTeam().getShortName() + "-card.png"));
            Files.copy(file, zip);
            zip.closeEntry();
        }
    }
}
```

**Apply to `BackupArchiveService.writeZip(OutputStream out, Instant exportDate)`:** copy the `try-with-resources ZipOutputStream`, `putNextEntry / Files.copy / closeEntry` shape — but write to the **caller-supplied `OutputStream`** (the StreamingResponseBody's `outputStream` arg) instead of a `ByteArrayOutputStream`. Use `RESEARCH §Streaming ZIP Architecture / Pattern 2` (lines 552-584) as the literal template:

```java
public void writeZip(OutputStream outputStream, Instant exportDate) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(outputStream)) {
        zip.setLevel(Deflater.DEFAULT_COMPRESSION);

        // 1. manifest.json — FIRST entry (Phase 73 success criterion 2)
        Map<String, Long> tableCounts = backupExportService.countRowsPerTable();
        BackupManifest manifest = new BackupManifest(
                BackupSchema.SCHEMA_VERSION, appVersion, exportDate, tableCounts);
        zip.putNextEntry(new ZipEntry("manifest.json"));
        backupObjectMapper.writerWithDefaultPrettyPrinter().writeValue(zip, manifest);
        zip.closeEntry();

        // 2. data/<entity>.json in EXPORT_ORDER
        for (EntityRef ref : backupSchema.getExportOrder()) {
            zip.putNextEntry(new ZipEntry(ref.fileName()));      // "data/seasons.json", etc.
            List<?> rows = backupExportService.fetchAllForBackup(ref.entityClass());
            backupObjectMapper.writeValue(zip, rows);
            zip.closeEntry();
        }

        // 3. uploads/ mirror
        for (UploadEntry upload : backupExportService.enumerateReferencedUploads()) {
            zip.putNextEntry(new ZipEntry("uploads/" + upload.relativePath()));
            Files.copy(upload.absolutePath(), zip);
            zip.closeEntry();
        }
        zip.finish();
    }
}
```

**Notes for the planner:**
- `@Slf4j @Service @RequiredArgsConstructor` triad.
- Inject `BackupExportService`, `BackupSchema`, `@Qualifier("backupObjectMapper") ObjectMapper`, and `@Value("${app.version}") String appVersion`.
- Inject `@Value("${app.upload-dir:uploads}") String uploadDir` for resolving `/uploads/...` strings to filesystem paths (same pattern as `TeamCardController.java` line 39).
- DO NOT buffer to `ByteArrayOutputStream` — write directly to the caller's `OutputStream` (true streaming, EXPORT-02).

---

### 5) `BackupController` — `@Controller` with one GET form + one POST streaming download

**Role:** thin HTTP handler. Renders `admin/backup` on GET; returns `ResponseEntity<StreamingResponseBody>` on POST.

**Closest analog:** `org.ctc.sitegen.SiteGeneratorController` (single GET + single POST under `/admin/generate`, ALSO a feature-module controller placed OUTSIDE `org.ctc.admin.controller` — exactly the layout Phase 73 follows).

**Full `SiteGeneratorController` for shape:**

```java
package org.ctc.sitegen;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/admin/generate")
@RequiredArgsConstructor
public class SiteGeneratorController {

    private final SiteGeneratorService siteGeneratorService;

    @GetMapping
    public String showGenerate() {
        return "admin/generate";
    }

    @PostMapping
    public String generate(RedirectAttributes redirectAttributes) {
        var result = siteGeneratorService.generate();
        if (result.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", ...);
        } else {
            redirectAttributes.addFlashAttribute("successMessage", ...);
        }
        return "redirect:/admin/generate";
    }
}
```

**Apply to `BackupController` (location: `org.ctc.backup.BackupController`, NOT `org.ctc.admin.controller` — RESEARCH §Recommended Project Structure note line 274):**

```java
// src/main/java/org/ctc/backup/BackupController.java
package org.ctc.backup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.service.BackupArchiveService;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
// ...

@Slf4j
@Controller
@RequestMapping("/admin/backup")
@RequiredArgsConstructor
public class BackupController {

    private final BackupArchiveService backupArchiveService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("title", "Backup");        // UI-SPEC: active-state detection
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
                throw new UncheckedIOException(e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }

    private static String isoSafeFilename(Instant when) {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(when);
    }
}
```

**Notes for the planner:**
- `@RequestMapping("/admin/backup")` — base URL for both endpoints. CSRF + auth wiring is inherited from existing `SecurityConfig` (prod/docker) and `OpenSecurityConfig` (dev/local) — RESEARCH §Controller & Security Pattern 3.
- `Content-Disposition: attachment` (NOT inline) + `application/octet-stream` (NOT `application/zip`) — matches `TeamCardController.downloadAll()` precedent (line 133-135).
- No Form DTO needed (POST has no request body).
- Lives in `org.ctc.backup.*`, NOT `org.ctc.admin.controller.*` — feature-module precedent matches `org.ctc.sitegen.SiteGeneratorController` and `org.ctc.dataimport.CsvImportController` (lines 21-25).

---

### 6) `admin/backup.html` — Thymeleaf page (one card, one button)

**Role:** server-rendered HTML page. Single description paragraph + single POST button.

**Closest analog:** `src/main/resources/templates/admin/generate.html` (full file, 21 lines — UI-SPEC explicitly cites this as the analog).

**Full `generate.html` for shape:**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org"
      th:replace="~{admin/layout :: layout('Generate Site', ~{::section})}">
<body>
<section>
    <h1>Generate Static Website</h1>
    <div class="card">
        <p class="text-dim mb-md">
            Generates all HTML pages of the public CTC website into the output directory.
            The generated pages can then be deployed to GitHub Pages.
        </p>
        <form th:action="@{/admin/generate}" method="post">
            <button type="submit" class="btn btn-success btn-lg">
                Generate Site
            </button>
        </form>
    </div>
</section>
</body>
</html>
```

**Apply to `admin/backup.html`** — two diffs only from `generate.html` (per UI-SPEC §Layout):
1. Title arg: `'Backup'` instead of `'Generate Site'`.
2. H1 text: `Backup`.
3. Description paragraph: `"Exports the full league database (all 23 entities) plus every uploaded file into a single ZIP archive. The download starts immediately and may take a few seconds for large leagues."` (UI-SPEC — note OQ-1 may bump "23" → "24"; planner's call before merge).
4. Form action: `@{/admin/backup/export}`.
5. Button class: `btn btn-primary btn-lg` (NOT `btn-success`; UI-SPEC §Color rationale — `.btn-success` is reserved for `Generate Site`).
6. Button label: `Export Backup`.

**No inline styles, no JavaScript, no progress UI** — UI-SPEC locks.

**Verified CSS** (admin.css lines 234, 245):
```css
.btn-primary { background: #1976d2; color: var(--white); }
.btn-lg { padding: 12px 24px; font-size: 16px; }
```

---

### 7) Sidebar entry edit in `admin/layout.html`

**Role:** layout-fragment edit — add new `<div class="sidebar-group">` between the existing Tools group and `</nav>`.

**Current state — `layout.html` lines 64-73 (Tools group):**

```html
<div class="sidebar-group">
    <span class="sidebar-group-label">Tools</span>
    <a th:href="@{/admin/standings}" th:classappend="${title.contains('Standing') ? 'active' : ''}">Standings</a>
    <a th:href="@{/admin/tools/power-rankings}" th:classappend="${title.contains('Power Rankings') ? 'active' : ''}">Power Rankings</a>
    <a th:href="@{/admin/import}" th:classappend="${title.contains('Import') ? 'active' : ''}">Import</a>
    <a th:href="@{/admin/gt7-sync}" th:classappend="${title.contains('GT7') ? 'active' : ''}">GT7 Sync</a>
    <a th:href="@{/admin/tools/team-cards}" th:classappend="${title.contains('Team Cards') ? 'active' : ''}">Team Cards</a>
    <a th:href="@{/admin/tools/template-editors}" th:classappend="${title.contains('Template') ? 'active' : ''}">Template Editors</a>
    <a th:href="@{/admin/generate}" class="btn-generate">Generate Site</a>
</div>
```

**Intended diff** — INSERT this block AFTER line 73 (closing `</div>` of Tools) AND BEFORE line 74 (`</nav>`):

```html
<div class="sidebar-group">
    <span class="sidebar-group-label">Data</span>
    <a th:href="@{/admin/backup}" th:classappend="${title.contains('Backup') ? 'active' : ''}">Backup</a>
</div>
```

**Notes for the planner:**
- Pattern follows the existing 4 sidebar groups (`League`, `Master Data`, `Scoring`, `Tools`) exactly — `.sidebar-group` wrapping a `.sidebar-group-label` span and one or more `<a th:href ... th:classappend>` links.
- No CSS additions needed; admin.css line 91-98 already styles `.sidebar-group-label`.
- The active-state predicate `${title.contains('Backup') ? 'active' : ''}` works because the controller passes `model.addAttribute("title", "Backup")` and the layout receives it as the `title` arg.

---

### 8) Per-entity repository edits (~24 files) — add `findAllForBackup()`

**Role:** Spring Data JPA finder with `@EntityGraph` for eager-fetch.

**Closest analog:** `org.ctc.domain.repository.RaceRepository` (in the same file, multiple existing `@EntityGraph` patterns).

**Existing patterns from `RaceRepository.java` lines 11-31:**

```java
public interface RaceRepository extends JpaRepository<Race, UUID> {

    @EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
    List<Race> findByMatchdayId(UUID matchdayId);

    @EntityGraph(attributePaths = {"matchday", "match", "track", "car"})
    @Query("SELECT r FROM Race r WHERE r.matchday.phase.season.id = :seasonId")
    List<Race> findByMatchdaySeasonId(UUID seasonId);
    // ... 7 more methods, every association-loaded finder uses @EntityGraph
}
```

**Apply to each of the 24 repositories** — append a single method using the canonical shape from RESEARCH §EntityGraph Fetch Map Pattern (line 437-441):

```java
@EntityGraph(attributePaths = {"matchday", "match", "track", "car", "playoffMatchup",
                                "homeTeamOverride", "awayTeamOverride"})
@Query("SELECT r FROM Race r")
List<Race> findAllForBackup();
```

**Why `@Query` is mandatory** — RESEARCH line 442: explicit JPQL prevents the method-name parser from mis-interpreting `ForBackup` as a property filter.

**Per-repo `attributePaths` mapping:** see the table above under "MODIFIED production files" (each row holds the locked `attributePaths` list, sourced from RESEARCH §EntityGraph Fetch Map lines 407-432).

---

### 9) Security IT (`BackupControllerSecurityIT`)

**Role:** MockMvc IT with two `@Nested` test classes — prod profile (auth + CSRF enforced) and dev profile (open).

**Closest analog:** `src/test/java/org/ctc/admin/SecurityIntegrationTest.java` (Phase 71 — only existing profile-conditional security IT).

**Full structure from `SecurityIntegrationTest` (lines 1-69):**

```java
package org.ctc.admin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIntegrationTest {

    @Nested
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:h2:mem:sectest;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=validate",
            "spring.flyway.locations=classpath:db/migration",
            "logging.config=classpath:logback-test.xml"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("prod")
    class ProdProfileSecurityTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void givenNoCredentials_whenAccessAdmin_thenUnauthorized() throws Exception {
            mockMvc.perform(get("/admin/seasons"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser
        void givenValidCredentials_whenAccessAdmin_thenOk() throws Exception {
            mockMvc.perform(get("/admin/seasons"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("dev")
    class DevProfileSecurityTest { /* ... */ }
}
```

**Apply to `BackupControllerSecurityIT`** — copy the two `@Nested` shells, swap GET for POST, add `.with(csrf())` for the positive case:

```java
// post-request matcher: use SecurityMockMvcRequestPostProcessors.csrf()
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Test
void givenAnonymous_whenPostExport_thenUnauthorized() throws Exception {
    mockMvc.perform(post("/admin/backup/export").with(anonymous()))
            .andExpect(status().isUnauthorized());
}

@Test
@WithMockUser
void givenAuthenticatedNoCsrf_whenPostExport_thenForbidden() throws Exception {
    mockMvc.perform(post("/admin/backup/export"))
            .andExpect(status().isForbidden());
}

@Test
@WithMockUser
void givenAuthenticatedWithCsrf_whenPostExport_thenOk() throws Exception {
    mockMvc.perform(post("/admin/backup/export").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                    org.hamcrest.Matchers.matchesPattern("attachment; filename=\"?ctc-backup-\\d{8}T\\d{6}Z\\.zip\"?")));
}
```

**Notes for the planner:**
- Failsafe filename suffix `*IT.java` (NOT `*Test.java`) — required by `pom.xml` Failsafe surefire-include.
- File location: `src/test/java/org/ctc/backup/BackupControllerSecurityIT.java`.

---

### 10) Playwright E2E (`BackupExportE2ETest`)

**Role:** end-to-end browser-driven test asserting the click-through download flow.

**Closest analog (base class consumption):** `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java` (existing `PlaywrightConfig` subclass).

**No existing in-repo precedent for download interception** — search confirmed no `page.waitForDownload(...)` usage anywhere in `src/test/java`. Planner must introduce the pattern. Use the Playwright Java API.

**Base class to extend** (`PlaywrightConfig.java` lines 1-47):

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
public abstract class PlaywrightConfig {

    static Playwright playwright;
    static Browser browser;

    @LocalServerPort
    int port;

    BrowserContext context;
    Page page;

    @BeforeAll
    static void setupBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }
    // teardown, setupPage, teardownPage, url(path) — see file
}
```

**`AdminWorkflowE2ETest` showing the setup pattern (lines 9-67):**

```java
class AdminWorkflowE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() { setupPage(); }

    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void whenNavigateToSeasons_thenAllNavigationLinksAreVisible() {
        page.navigate(url("/admin/seasons"));
        assertThat(page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Backup").setExact(true))).isVisible();
        // ... assertion-style: Playwright Java assertions
    }
}
```

**Apply to `BackupExportE2ETest` — canonical download-interception pattern (Playwright Java docs):**

```java
class BackupExportE2ETest extends PlaywrightConfig {

    @BeforeEach
    void setUp() { setupPage(); }
    @AfterEach
    void tearDown() { teardownPage(); }

    @Test
    void givenAdminUI_whenClickBackupSidebarThenExport_thenZipDownloads() {
        // given
        page.navigate(url("/admin"));
        page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Backup").setExact(true)).click();
        assertThat(page).hasURL(java.util.regex.Pattern.compile(".*/admin/backup$"));

        // when: intercept download
        Download download = page.waitForDownload(() ->
                page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Export Backup")).click());

        // then
        assertThat(download.suggestedFilename()).matches("ctc-backup-\\d{8}T\\d{6}Z\\.zip");
    }
}
```

**Notes for the planner:**
- File location: `src/test/java/org/ctc/e2e/BackupExportE2ETest.java` — mirrors `AdminWorkflowE2ETest` location.
- Runs under `-Pe2e` Failsafe profile only.
- Optional: save the download via `download.path()` and open with `new ZipInputStream(...)` to assert `manifest.json` is `entries[0]` end-to-end.

---

## Shared Patterns

### A. Constructor injection (CLAUDE.md "Lombok Usage")
**Source:** every existing `@Service` / `@Controller` in the repo (e.g. `SiteGeneratorController.java`, `StandingsService.java`).
**Apply to:** `BackupController`, `BackupExportService`, `BackupArchiveService`.

```java
@Slf4j
@Service                    // or @Controller / @Component
@RequiredArgsConstructor
public class XYZ {
    private final FooRepository fooRepository;
    private final BarService barService;
    // No explicit constructor — Lombok generates the all-final-fields ctor.
}
```

### B. `@Transactional(readOnly = true)` on read paths (CONVENTIONS.md L284)
**Source:** `StandingsService.compute(...)` line 40.
**Apply to:** every public `BackupExportService` method that touches repositories.

### C. Flash-message pattern (CONVENTIONS.md L186-207)
**Source:** `SiteGeneratorController.generate(...)` lines 25-37.
**Apply to:** any failure-before-stream branch on `BackupController.export()`. UI-SPEC strings:
- success: `"Backup exported successfully."`
- error: `"Backup export failed. See application log for details."`

### D. `@Slf4j` parameterized logging (CONVENTIONS.md L253-265)
**Source:** every existing service/controller.
**Apply to:** `BackupArchiveService`, `BackupExportService`, `BackupController`.

```java
log.info("Backup export started: schemaVersion={}, entityCount={}", SCHEMA_VERSION, exportOrder.size());
log.error("Backup export I/O failure mid-stream", e);     // throwable as last arg, no {}
```

### E. Lombok import ordering (CONVENTIONS.md L270-280)
1. `org.ctc.*` — own project
2. `jakarta.*`
3. `lombok.*`
4. `org.springframework.*`
5. `java.*`

### F. Test naming Given-When-Then (CLAUDE.md "Test Naming")
**Source:** every test in `src/test/java/org/ctc/backup/**` from Phase 72 (e.g. `BackupManifestSerializationTest.givenSampleManifest_whenSerializeThroughBackupMapper_thenJsonHasSnakeCaseKeys`).
**Apply to:** all new Phase 73 tests. Body has `// given` / `// when` / `// then` comments.

### G. CSS classes only — no inline styles (CLAUDE.md "No Inline Styles on Buttons")
**Source:** every `admin/*.html` template; specifically `admin/generate.html` line 13 (`class="btn btn-success btn-lg"`).
**Apply to:** `admin/backup.html` button — `class="btn btn-primary btn-lg"`, no `style="..."` attribute anywhere on the page.

### H. Filename pattern for new files (STRUCTURE.md L237-247)
- Templates: kebab-case (`backup.html`) — already standard.
- Java: PascalCase singular (`BackupController`, `BackupArchiveService`, `TeamMixIn`).
- Tests: `*Test.java` (Surefire) vs `*IT.java` (Failsafe) — split is load-bearing for `./mvnw verify`.

### I. CSRF + auth wiring is INHERITED, not configured
**Source:** RESEARCH §Controller & Security Pattern 2-3, verified against `SecurityConfig.java` `.anyRequest().authenticated()` matcher.
**Apply to:** Phase 73 ships ZERO security config changes. The new `/admin/backup/**` routes are caught automatically.

---

## No Analog Found

| File / Role | Why No Analog | Substitute Source |
|-------------|---------------|-------------------|
| **Jackson MixIn classes (24 files)** | No prior in-repo use of `@JsonIdentityInfo` / `@JsonIgnoreProperties` / `@JsonIdentityReference` MixIns — this is the first Jackson-MixIn pattern in CTC. | RESEARCH §MixIn Design Pattern 1 (canonical Jackson 2 shape, full code excerpt for `TeamMixIn`); Phase 72 RESEARCH §A1 cites the Jackson docs verbatim. |
| **Playwright `page.waitForDownload(...)` interception** | No existing download-interception E2E test in `src/test/java/org/ctc/e2e/`. `RaceControllerTest.java` line 495 mentions an attachment named "Download Test" but only as a string assertion — not a real download flow. | RESEARCH §Testing Strategy Layer 4 + Playwright Java API canonical pattern (sketched in section 10 above). |
| **`StreamingResponseBody` lambda** | `TeamCardController.downloadAll()` uses synchronous `ByteArrayOutputStream` + `ResponseEntity<byte[]>` — different shape. No existing `StreamingResponseBody` consumer in the repo. | RESEARCH §Streaming ZIP Architecture Pattern 1 (full controller method literal). |

For these three roles, the planner relies on RESEARCH.md's canonical shapes rather than copying from a sibling file.

---

## Metadata

**Analog search scope:**
- `src/main/java/org/ctc/admin/controller/`
- `src/main/java/org/ctc/admin/service/`
- `src/main/java/org/ctc/sitegen/`
- `src/main/java/org/ctc/dataimport/`
- `src/main/java/org/ctc/backup/` (Phase 72 artefacts)
- `src/main/java/org/ctc/domain/service/`
- `src/main/java/org/ctc/domain/repository/`
- `src/main/java/org/ctc/domain/model/` (entity shapes only)
- `src/main/resources/templates/admin/`
- `src/main/resources/static/admin/css/admin.css` (verified `.btn-primary` + `.btn-lg`)
- `src/test/java/org/ctc/admin/SecurityIntegrationTest.java`
- `src/test/java/org/ctc/e2e/PlaywrightConfig.java`
- `src/test/java/org/ctc/e2e/AdminWorkflowE2ETest.java`
- `src/test/java/org/ctc/backup/**` (Phase 72 test precedents)

**Files scanned:** ~80 (entities, repos, controllers, services, templates, tests).
**Pattern extraction date:** 2026-05-11.

---

## PATTERN MAPPING COMPLETE

Phase 73 file inventory (~50 files) fully classified; 1 new role (Jackson MixIn) has no in-repo analog and is covered by RESEARCH canonical shape; all other roles map to concrete excerpts from `SiteGeneratorController`, `generate.html`, `layout.html`, `TeamCardController.downloadAll`, `StandingsService`, `RaceRepository`, `SecurityIntegrationTest`, `PlaywrightConfig`, `BackupObjectMapperConfig`, and `BackupManifestSerializationTest`.
