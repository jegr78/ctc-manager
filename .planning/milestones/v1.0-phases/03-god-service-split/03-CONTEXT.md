# Phase 3: God Service Split - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

RaceManagementService (694 Zeilen, 20 Dependencies) in drei fokussierte Services aufteilen: RaceService (Core CRUD + Business-Logik), RaceGraphicService (Grafik-Generierung), RaceAttachmentService (Datei-/Link-Operationen). Keine neuen Features, nur Strukturverbesserung.

</domain>

<decisions>
## Implementation Decisions

### Service-Aufteilung
- **D-01:** Drei Ziel-Services: `RaceService`, `RaceGraphicService`, `RaceAttachmentService` — alle in `org.ctc.domain.service`
- **D-02:** RaceService enthaelt: getRaceListData, getRaceDetailData, getNewRaceFormData, getRaceFormData, getResultsFormData, saveRace, saveResults, quickScore, deleteRace, getUsedSelections + alle private Helpers (toForm, populateDrivers, getUsedCarIds, getUsedTrackIds)
- **D-03:** RaceGraphicService enthaelt: generateLineup, generateResults, generateSettings, generateOverlay
- **D-04:** RaceAttachmentService enthaelt: uploadAttachment, addLink, deleteAttachment, downloadAttachment, getExtension

### Detail-Methode
- **D-05:** Claude's Discretion: getRaceDetailData() mischt Core-Daten mit Grafik-Verfuegbarkeitschecks. Claude entscheidet ob die Methode komplett in RaceService bleibt oder die Grafik-Checks ausgelagert werden. Abwaegung: Einfachheit vs. saubere Trennung.

### Calendar-Integration
- **D-06:** Claude's Discretion: createOrUpdateCalendarEvent() + resolveEventDuration() — Claude entscheidet ob in RaceService belassen (pragmatisch, eine Methode) oder eigener Service (sauberer). Kein Overengineering fuer eine Methode.

### Record-Typen
- **D-07:** Inner Records bleiben als inner Records im jeweiligen Service (nicht in eigene Dateien auslagern). RaceListData, RaceDetailData, ResultsFormData, RaceFormData, SaveResult wandern mit ihren Methoden in RaceService.

### Controller-Rewiring
- **D-08:** RaceController injiziert alle drei neuen Services direkt (RaceService + RaceGraphicService + RaceAttachmentService). Keine Fassade — jeder Endpoint ruft den zustaendigen Service.

### Test-Aufteilung
- **D-09:** RaceManagementServiceTest (1050 Zeilen) wird auf drei Testklassen aufgeteilt: RaceServiceTest, RaceGraphicServiceTest, RaceAttachmentServiceTest — analog zur Service-Aufteilung.

### Claude's Discretion
- getRaceDetailData() Aufteilung (D-05): Grafik-Checks in RaceService belassen oder auslagern
- Calendar-Methode Platzierung (D-06): In RaceService oder eigener Service
- Migrations-Reihenfolge der Aufteilung (welcher Service zuerst extrahiert wird)
- Ob RaceGraphicService die Grafik-Services direkt injiziert oder ueber RaceService bekommt

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### God Service (Hauptziel)
- `src/main/java/org/ctc/domain/service/RaceManagementService.java` — 694 Zeilen, 20 Dependencies, aufzuteilender Service
- `src/test/java/org/ctc/domain/service/RaceManagementServiceTest.java` — 1050 Zeilen Tests, muessen aufgeteilt werden

### Controller (Rewiring-Ziel)
- `src/main/java/org/ctc/admin/controller/RaceController.java` — 241 Zeilen, injiziert aktuell nur RaceManagementService

### Grafik-Services (Dependencies von RaceGraphicService)
- `src/main/java/org/ctc/admin/service/LineupGraphicService.java`
- `src/main/java/org/ctc/admin/service/ResultsGraphicService.java`
- `src/main/java/org/ctc/admin/service/SettingsGraphicService.java`
- `src/main/java/org/ctc/admin/service/OverlayGraphicService.java`
- `src/main/java/org/ctc/admin/service/TeamCardService.java`

### Architektur & Konventionen
- `.planning/codebase/CONCERNS.md` — God Service Concern-Beschreibung
- `.planning/codebase/ARCHITECTURE.md` — Layer-Beschreibung
- `CLAUDE.md` — "Controller duenn halten", Service-Naming-Konventionen

### Phase 2 Context (Vorarbeit)
- `.planning/phases/02-service-layer-extraction/02-CONTEXT.md` — Service-Schnitt-Konventionen (D-05, D-06)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- Exception-Klassen aus Phase 1 (`EntityNotFoundException`) — bereits in RaceManagementService verwendet
- `FileStorageService` — wird von RaceAttachmentService injiziert (store, delete)
- `ScoringService` — wird von RaceService injiziert (calculatePoints, aggregateMatchScores, isDriverInTeam)
- `GoogleCalendarService` — Calendar-Integration

### Established Patterns
- Inner Records als Return-Typen (RaceListData, RaceDetailData etc.) — bewaehrtes Pattern aus Phase 2
- @Transactional auf Service-Methoden — Standard im Projekt
- POST-Redirect-GET mit Flash-Attributes — Controller-Pattern bleibt
- Grafik-Generierung: Race laden → Graphic-Service aufrufen → Attachment speichern (4x identisches Pattern)

### Integration Points
- `RaceController` — einziger direkter Consumer, muss auf 3 Service-Injections umgestellt werden
- `CsvImportService` — nutzt `RaceManagementService` NICHT direkt (hat eigene Import-Logik)
- Keine anderen Controller/Services referenzieren RaceManagementService

</code_context>

<specifics>
## Specific Ideas

- Die vier generateXxx()-Methoden folgen einem identischen Pattern (Race laden, Grafik generieren, Attachment speichern) — RaceGraphicService kann das DRY machen
- RaceAttachmentService wird sehr schlank (~80 Zeilen) — das ist OK, klare Verantwortlichkeit ist wichtiger als Groesse

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-god-service-split*
*Context gathered: 2026-04-04*
