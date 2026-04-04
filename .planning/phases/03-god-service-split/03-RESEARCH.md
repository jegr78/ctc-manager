# Phase 3: God Service Split - Research

**Researched:** 2026-04-04
**Domain:** Spring Boot Service Refactoring (Extract Class)
**Confidence:** HIGH

## Summary

RaceManagementService (694 Zeilen, 20 Dependencies, 37 Tests) ist ein klassischer God Service mit drei klar getrennten Verantwortlichkeiten: Core Race CRUD/Business-Logik, Grafik-Generierung und Datei-/Link-Verwaltung. Die Aufteilung ist ein mechanisches Refactoring mit minimalem Risiko — keine neuen Features, keine Datenbankänderungen, keine API-Änderungen.

Der einzige Consumer ist `RaceController` (241 Zeilen), der jeden Endpoint 1:1 an den Service delegiert. Der Controller-Test ist ein `@SpringBootTest` mit `@AutoConfigureMockMvc` — kein Service-Mocking, daher wird er nach dem Rewiring automatisch funktionieren sofern Spring die neuen Services korrekt verdrahtet. Die 37 Unit-Tests in `RaceManagementServiceTest` (1050 Zeilen, Mockito-basiert) müssen auf drei Testklassen aufgeteilt werden.

**Primary recommendation:** Sequenziell extrahieren: erst RaceAttachmentService (am eigenständigsten, ~80 Zeilen), dann RaceGraphicService (~100 Zeilen, DRY-Potenzial), zuletzt RaceService als verbleibenden Rumpf umbenennen. In jedem Schritt RaceManagementService als Fassade behalten und am Ende löschen.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Drei Ziel-Services: `RaceService`, `RaceGraphicService`, `RaceAttachmentService` — alle in `org.ctc.domain.service`
- **D-02:** RaceService enthaelt: getRaceListData, getRaceDetailData, getNewRaceFormData, getRaceFormData, getResultsFormData, saveRace, saveResults, quickScore, deleteRace, getUsedSelections + alle private Helpers (toForm, populateDrivers, getUsedCarIds, getUsedTrackIds)
- **D-03:** RaceGraphicService enthaelt: generateLineup, generateResults, generateSettings, generateOverlay
- **D-04:** RaceAttachmentService enthaelt: uploadAttachment, addLink, deleteAttachment, downloadAttachment, getExtension
- **D-05:** Claude's Discretion: getRaceDetailData() — Grafik-Checks in RaceService belassen oder auslagern
- **D-06:** Claude's Discretion: createOrUpdateCalendarEvent() + resolveEventDuration() — in RaceService oder eigener Service
- **D-07:** Inner Records bleiben als inner Records im jeweiligen Service
- **D-08:** RaceController injiziert alle drei neuen Services direkt (keine Fassade)
- **D-09:** RaceManagementServiceTest auf drei Testklassen aufteilen

### Claude's Discretion
- getRaceDetailData() Aufteilung (D-05): Grafik-Checks in RaceService belassen oder auslagern
- Calendar-Methode Platzierung (D-06): In RaceService oder eigener Service
- Migrations-Reihenfolge der Aufteilung (welcher Service zuerst extrahiert wird)
- Ob RaceGraphicService die Grafik-Services direkt injiziert oder ueber RaceService bekommt

### Deferred Ideas (OUT OF SCOPE)
None — discussion stayed within phase scope

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SRVC-08 | RaceManagementService in RaceService (Core CRUD), RaceGraphicService, RaceAttachmentService aufteilen | Full method-to-service mapping, dependency analysis, test mapping, and migration strategy documented below |

</phase_requirements>

## Architecture Patterns

### Current State Analysis

**RaceManagementService** (694 Zeilen, 20 injected dependencies):

| Concern | Methods | Lines (approx.) | Dependencies |
|---------|---------|-----------------|--------------|
| Core CRUD/Business | getRaceListData, getRaceDetailData, getNewRaceFormData, getRaceFormData, getResultsFormData, saveRace, saveResults, quickScore, deleteRace, getUsedSelections + 5 private helpers | ~450 | 12 repositories, ScoringService, GoogleCalendarService, TeamCardService |
| Graphic Generation | generateLineup, generateResults, generateSettings, generateOverlay | ~100 | RaceRepository, RaceAttachmentRepository, 4 graphic services |
| Attachment/File Ops | uploadAttachment, addLink, deleteAttachment, downloadAttachment, getExtension | ~100 | RaceRepository, RaceAttachmentRepository, FileStorageService, @Value uploadDir |
| Calendar | createOrUpdateCalendarEvent, resolveEventDuration | ~50 | RaceRepository, GoogleCalendarService |

**Consumers:** Nur `RaceController` (bestätigt via grep — kein anderer Service/Controller referenziert RaceManagementService).

### Target State: Three Services

```
org.ctc.domain.service/
├── RaceService.java              # ~450 Zeilen — Core CRUD + Calendar
├── RaceGraphicService.java       # ~100 Zeilen — Grafik-Generierung
├── RaceAttachmentService.java    # ~100 Zeilen — File/Link Operations
└── (RaceManagementService.java DELETED)
```

### Recommended Project Structure per Service

#### RaceService.java
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceService {

    // Repositories (12): race, match, matchday, season, team, driver,
    //   seasonDriver, raceLineup, car, track, seasonTeam, raceAttachment (nur für exists-check in detail)
    // Services: ScoringService, GoogleCalendarService, TeamCardService

    // Inner records: RaceListData, RaceDetailData, ResultsFormData, RaceFormData, SaveResult

    // Public: getRaceListData, getRaceDetailData, getNewRaceFormData, getRaceFormData,
    //   getResultsFormData, saveRace, saveResults, quickScore, deleteRace, getUsedSelections,
    //   createOrUpdateCalendarEvent
    // Private: toForm, populateDrivers, getUsedCarIds, getUsedTrackIds, resolveEventDuration
}
```

#### RaceGraphicService.java
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceGraphicService {

    private final RaceRepository raceRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final LineupGraphicService lineupGraphicService;
    private final ResultsGraphicService resultsGraphicService;
    private final SettingsGraphicService settingsGraphicService;
    private final OverlayGraphicService overlayGraphicService;

    // DRY: Die 4 generate-Methoden folgen identischem Pattern
    @Transactional
    public void generateLineup(UUID raceId) { ... }
    @Transactional
    public void generateResults(UUID raceId) { ... }
    @Transactional
    public void generateSettings(UUID raceId) { ... }
    @Transactional
    public void generateOverlay(UUID raceId) { ... }
}
```

#### RaceAttachmentService.java
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class RaceAttachmentService {

    private final RaceRepository raceRepository;
    private final RaceAttachmentRepository raceAttachmentRepository;
    private final FileStorageService fileStorageService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Transactional
    public String uploadAttachment(UUID raceId, MultipartFile file) { ... }
    @Transactional
    public String addLink(UUID raceId, String name, String url) { ... }
    @Transactional
    public UUID deleteAttachment(UUID attachmentId) { ... }
    public ResponseEntity<Resource> downloadAttachment(UUID attachmentId) { ... }
    // private: getExtension
}
```

### Discretion Recommendations

**D-05 (getRaceDetailData Grafik-Checks):** Empfehlung: **In RaceService belassen.** Die Grafik-Verfuegbarkeitschecks (lineupExists, resultsExist, etc.) sind reine Attachment-Queries (`race.getAttachments().stream().anyMatch(...)`) und TeamCard-Existenzprüfungen. Sie verursachen keine Abhängigkeit auf die Grafik-*Generator*-Services — nur auf TeamCardService und RaceAttachmentRepository. Eine Auslagerung würde entweder eine zusätzliche Methode in RaceGraphicService oder ein neues DTO erfordern, beides Overengineering für boolean-Flags die nur im Detail-View gebraucht werden.

**D-06 (Calendar in RaceService):** Empfehlung: **In RaceService belassen.** Nur 2 Methoden (~50 Zeilen), eine davon private. GoogleCalendarService ist bereits injiziert. Ein eigener RaceCalendarService wäre Overengineering — es gibt keine Chance auf Wiederverwendung ausserhalb des Race-Kontexts.

**Graphic-Service Injection:** Empfehlung: **RaceGraphicService injiziert die 4 Grafik-Services direkt** (LineupGraphicService, ResultsGraphicService, SettingsGraphicService, OverlayGraphicService). Kein Umweg über RaceService — das würde eine zirkuläre Abhängigkeit erzeugen oder RaceService unnötig aufblähen.

### Controller Rewiring Pattern

```java
@Controller
@RequestMapping("/admin/races")
@RequiredArgsConstructor
public class RaceController {

    private final RaceService raceService;
    private final RaceGraphicService raceGraphicService;
    private final RaceAttachmentService raceAttachmentService;

    // Endpoint → Service mapping:
    // list, detail, create, edit, results, save, saveResults, quickScore, delete, usedSelections
    //   → raceService
    // createCalendarEvent → raceService
    // generateLineup, generateResults, generateSettings, generateOverlay
    //   → raceGraphicService
    // uploadAttachment, addLink, deleteAttachment, downloadAttachment
    //   → raceAttachmentService
}
```

### Anti-Patterns to Avoid
- **Fassade-Schicht:** Keine `RaceManagementService` als Delegation-Wrapper behalten. Controller ruft die Services direkt auf (D-08).
- **Zirkuläre Dependencies:** RaceGraphicService darf RaceService nicht injizieren und umgekehrt. Beide lesen Race via RaceRepository selbständig.
- **Shared Mutable State:** Keine static fields oder Singletons für Cross-Service-Kommunikation.

## Dependency Analysis (No Circular Dependencies)

```
RaceController
  ├── RaceService           → Repositories (12), ScoringService, GoogleCalendarService, TeamCardService
  ├── RaceGraphicService    → RaceRepository, RaceAttachmentRepository, 4 Graphic Services
  └── RaceAttachmentService → RaceRepository, RaceAttachmentRepository, FileStorageService
```

Shared dependencies: `RaceRepository` und `RaceAttachmentRepository` werden von allen drei Services genutzt. Das ist korrekt — Spring Beans sind Singletons, geteilte Repository-Injection ist Standard-Pattern.

**Kein Zirkel:** Keiner der drei Services injiziert einen der anderen.

## Test Mapping

### Current: RaceManagementServiceTest (1050 Zeilen, 37 Tests)

| Test Section | Count | Target Test Class |
|-------------|-------|-------------------|
| getRaceListData | 3 | RaceServiceTest |
| saveRace (new, edit, pool validation, settings) | 6 | RaceServiceTest |
| saveResults | 2 | RaceServiceTest |
| quickScore | 1 | RaceServiceTest |
| getRaceDetailData (incl. settings flags) | 3 | RaceServiceTest |
| getNewRaceFormData | 2 | RaceServiceTest |
| getRaceFormData | 1 | RaceServiceTest |
| getResultsFormData | 1 | RaceServiceTest |
| getUsedSelections | 1 | RaceServiceTest |
| deleteRace | 1 | RaceServiceTest |
| createOrUpdateCalendarEvent | 6 | RaceServiceTest |
| **RaceServiceTest subtotal** | **27** | |
| generateLineup | 1 | RaceGraphicServiceTest |
| generateResults (incl. failure) | 2 | RaceGraphicServiceTest |
| generateSettings (incl. failure) | 2 | RaceGraphicServiceTest |
| **RaceGraphicServiceTest subtotal** | **5** | |
| addLink (valid, invalid) | 2 | RaceAttachmentServiceTest |
| deleteAttachment (file, link) | 2 | RaceAttachmentServiceTest |
| uploadAttachment | 1 | RaceAttachmentServiceTest |
| downloadAttachment | 1 | RaceAttachmentServiceTest |
| **RaceAttachmentServiceTest subtotal** | **6** | |
| **TOTAL** | **38** | *(1 test possibly uncounted in section headers)* |

### Test Infrastructure Notes
- Alle Tests nutzen `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`
- Helper-Methoden am Ende der Datei (createRaceWithScore, etc.) müssen in jede Testklasse kopiert oder als TestHelper extrahiert werden
- `RaceControllerTest` (678 Zeilen, `@SpringBootTest`) braucht **keine Änderung** — er nutzt keine Service-Mocks, sondern echte Spring-Beans via `@AutoConfigureMockMvc`

## Transactional Integrity

Kritische Transaktionen die nicht gebrochen werden dürfen:

| Method | Transactional? | Critical Because |
|--------|---------------|-----------------|
| `saveResults` | `@Transactional` | Results speichern + `aggregateMatchScores()` muss atomar sein |
| `saveRace` | `@Transactional` | Race + Match + Validierung atomar |
| `generateXxx` | `@Transactional` | Race laden + Grafik generieren + Attachment speichern atomar |
| `uploadAttachment` | `@Transactional` | File store + DB record atomar |
| `deleteAttachment` | `@Transactional` | File delete + DB delete atomar |
| `createOrUpdateCalendarEvent` | `@Transactional` | Race update mit eventId atomar |

**Kein Risiko:** Jede `@Transactional`-Methode wandert komplett in einen Service. Keine Transaktion wird auf mehrere Services aufgeteilt.

## DRY-Potenzial in RaceGraphicService

Die vier `generateXxx()`-Methoden folgen exakt demselben Pattern:

```java
@Transactional
public void generateXxx(UUID raceId) {
    var race = raceRepository.findById(raceId)
            .orElseThrow(() -> new EntityNotFoundException("Race", raceId));
    try {
        String url = xxxGraphicService.generateXxx(race);
        String attachmentName = race.getMatchday().getLabel() + "-"
                + race.getHomeTeam().getShortName() + "-" + race.getAwayTeam().getShortName() + "-Xxx";
        var attachment = new RaceAttachment(race, AttachmentType.FILE, attachmentName, url);
        raceAttachmentRepository.save(attachment);
    } catch (IOException e) {
        log.error("Xxx generation failed for race {}", raceId, e);
        throw new RuntimeException("Generation failed: " + e.getMessage(), e);
    }
}
```

**Option (Claude's Discretion):** Private Helper-Methode extrahieren:
```java
private void generateAndSaveGraphic(UUID raceId, String suffix,
        GraphicGenerator generator) { ... }
```
Dies ist optional — die 4 Methoden sind kurz genug dass Duplizierung akzeptabel ist. Empfehlung: DRY anwenden, da das Pattern exakt identisch ist.

## Common Pitfalls

### Pitfall 1: Import-Collisions bei Klassennamen
**What goes wrong:** `RaceGraphicService` existiert im Package `org.ctc.domain.service`, während die bestehenden Graphic Services in `org.ctc.admin.service` liegen. Kein Name-Konflikt, aber die import-Statements müssen korrekt sein.
**How to avoid:** Beim Erstellen von RaceGraphicService darauf achten, dass die Imports der Admin-Graphic-Services vollqualifiziert richtig sind.

### Pitfall 2: @Value-Injection bei @RequiredArgsConstructor
**What goes wrong:** `@Value("${app.upload-dir:uploads}")` in RaceAttachmentService funktioniert nicht mit `@RequiredArgsConstructor` wenn das Feld nicht `final` ist.
**How to avoid:** Das Feld als non-final belassen und Spring die Injection per Field-Injection übernehmen lassen (aktuelles Pattern in RaceManagementService). Alternativ: Constructor-Parameter mit `@Value`.

### Pitfall 3: Test Helper-Methoden vergessen
**What goes wrong:** Die Helper-Methoden am Ende von RaceManagementServiceTest (createRaceWithScore, etc.) werden beim Split vergessen.
**How to avoid:** Alle Helper-Methoden identifizieren und in jede neue Testklasse kopieren die sie braucht. Prüfen welche Tests welche Helpers nutzen.

### Pitfall 4: RaceManagementService-Referenzen in Imports
**What goes wrong:** Nach dem Löschen von RaceManagementService brechen Imports in RaceController und RaceControllerTest.
**How to avoid:** Sicherstellen dass RaceController auf die neuen Services umgestellt ist bevor RaceManagementService gelöscht wird. RaceControllerTest nutzt keine explizite Service-Referenz (Integration-Test).

## Migration Strategy

**Empfohlene Reihenfolge (Claude's Discretion):**

1. **RaceAttachmentService extrahieren** — Am eigenständigsten, keine Abhängigkeiten auf andere Race-Services. 5 Methoden, 6 Tests.
2. **RaceGraphicService extrahieren** — 4 Methoden mit DRY-Potenzial, 5 Tests.
3. **RaceManagementService → RaceService umbenennen** — Verbleibende Methoden, Records, und private Helpers. 27 Tests.
4. **RaceController rewiren** — Alle drei Services injizieren, Endpoints umleiten.
5. **RaceManagementService löschen** — Cleanup.
6. **Tests verifizieren** — `./mvnw verify` muss grün sein.

**Sicherheitsnetz:** Nach jedem Schritt `./mvnw verify` laufen lassen. Aktueller Stand: 744 Tests, alle grün.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito (Unit), Spring Boot Test (Integration) |
| Config file | `pom.xml` (Maven Surefire/Failsafe) |
| Quick run command | `./mvnw test -pl . -Dtest=RaceServiceTest,RaceGraphicServiceTest,RaceAttachmentServiceTest` |
| Full suite command | `./mvnw verify` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SRVC-08a | RaceService enthält Core CRUD | unit | `./mvnw test -Dtest=RaceServiceTest` | Wave 0 |
| SRVC-08b | RaceGraphicService enthält Grafik-Generierung | unit | `./mvnw test -Dtest=RaceGraphicServiceTest` | Wave 0 |
| SRVC-08c | RaceAttachmentService enthält File-Ops | unit | `./mvnw test -Dtest=RaceAttachmentServiceTest` | Wave 0 |
| SRVC-08d | Keine zirkulären Dependencies | compile | `./mvnw compile` | N/A |
| SRVC-08e | Alle Race-Workflows funktionieren | integration | `./mvnw test -Dtest=RaceControllerTest` | Exists (678 lines) |
| SRVC-08f | Transaktionale Integrität | integration | `./mvnw test -Dtest=RaceControllerTest` | Exists |

### Sampling Rate
- **Per task commit:** `./mvnw test -Dtest=RaceServiceTest,RaceGraphicServiceTest,RaceAttachmentServiceTest,RaceControllerTest`
- **Per wave merge:** `./mvnw verify`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `RaceServiceTest.java` — entsteht aus Split von RaceManagementServiceTest (27 Tests)
- [ ] `RaceGraphicServiceTest.java` — entsteht aus Split von RaceManagementServiceTest (5 Tests)
- [ ] `RaceAttachmentServiceTest.java` — entsteht aus Split von RaceManagementServiceTest (6 Tests)

None of these require new test infrastructure — they reuse existing Mockito patterns.

## Sources

### Primary (HIGH confidence)
- `src/main/java/org/ctc/domain/service/RaceManagementService.java` — 694 Zeilen, vollständig analysiert
- `src/main/java/org/ctc/admin/controller/RaceController.java` — 241 Zeilen, einziger Consumer
- `src/test/java/org/ctc/domain/service/RaceManagementServiceTest.java` — 1050 Zeilen, 37 Tests
- `src/test/java/org/ctc/admin/controller/RaceControllerTest.java` — 678 Zeilen, @SpringBootTest
- Codebase grep: Keine weiteren Referenzen auf RaceManagementService ausserhalb Controller + Test

### Secondary (MEDIUM confidence)
- None needed — rein internes Refactoring ohne externe Abhängigkeiten

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring Boot Service-Refactoring, bekanntes Pattern
- Architecture: HIGH — Method-to-Service-Mapping vollständig verifiziert durch Source-Code-Analyse
- Pitfalls: HIGH — Basiert auf konkreter Code-Analyse, nicht auf generischen Annahmen

**Research date:** 2026-04-04
**Valid until:** 2026-05-04 (stable — rein internes Refactoring)
