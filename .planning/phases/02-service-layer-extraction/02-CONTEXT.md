# Phase 2: Service Layer Extraction - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Repository-Zugriffe aus allen Controllern in Service-Klassen extrahieren. Nach dieser Phase injiziert kein Controller mehr direkt ein Repository. CRUD-Operationen, Validierung und Seiteneffekte werden zentral in Services verwaltet.

</domain>

<decisions>
## Implementation Decisions

### Service-Schnitt
- **D-01:** Hybrid-Ansatz: Bestehende Services erweitern wo moeglich (TeamManagementService, DriverService, SeasonManagementService), neue Services nur wo kein Service existiert (CarService, TrackService, RaceScoringService, MatchScoringService)
- **D-02:** Claude's Discretion: TeamController Repository-Zugriffe in TeamManagementService oder aufteilen — Claude analysiert die Methoden

### Season Mega-Controller
- **D-03:** Alle 8 Repository-Injections aus SeasonController in den bestehenden SeasonManagementService migrieren (hat schon Team/Car/Track Pool-Verwaltung)
- **D-04:** Claude's Discretion: Maximale Anzahl Service-Injections pro Controller — Claude entscheidet was sinnvoll ist

### Package-Konvention
- **D-05:** Neue Services (Car, Track, RaceScoring, MatchScoring) in `org.ctc.domain.service` — konsistent mit bestehenden Services
- **D-06:** Naming: Einfache Entity-Services als `{Entity}Service` (CarService, TrackService), Management-Services behalten ihren Namen (TeamManagementService, SeasonManagementService)

### Claude's Discretion
- Migrations-Reihenfolge (welcher Controller zuerst)
- Ob TeamController in TeamManagementService allein oder aufgeteilt wird
- Maximale Service-Injections pro Controller
- Test-Strategie waehrend Migration (bestehende Tests anpassen vs. neue)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Architecture
- `.planning/codebase/CONCERNS.md` — Abschnitt "Controllers with Direct Repository Access" mit allen betroffenen Controllern und Zeilennummern
- `.planning/codebase/ARCHITECTURE.md` — Layer-Beschreibung, Package-Struktur
- `.planning/research/ARCHITECTURE.md` — Zielarchitektur und Refactoring-Reihenfolge

### Betroffene Controller (aus CONCERNS.md)
- `src/main/java/org/ctc/admin/controller/DriverController.java` — assign (93-103), delete (114-115)
- `src/main/java/org/ctc/admin/controller/TrackController.java` — image upload (61-67), save (83-88), delete (103-112)
- `src/main/java/org/ctc/admin/controller/CarController.java` — image upload (57-73)
- `src/main/java/org/ctc/admin/controller/RaceScoringController.java` — CRUD (39-87)
- `src/main/java/org/ctc/admin/controller/MatchScoringController.java` — CRUD (37-81)
- `src/main/java/org/ctc/admin/controller/TeamController.java` — CRUD (79-146)
- `src/main/java/org/ctc/admin/controller/SeasonController.java` — 8 Repositories injiziert (30-40)

### Bestehende Services (Erweiterungsziele)
- `src/main/java/org/ctc/domain/service/TeamManagementService.java` — Team-Detail, Farb/Logo-Propagation
- `src/main/java/org/ctc/domain/service/DriverService.java` — Driver-Operationen
- `src/main/java/org/ctc/domain/service/SeasonManagementService.java` — Team/Car/Track-Pool-Verwaltung

### Projekt-Richtlinien
- `CLAUDE.md` — "Controller duenn halten", "DTOs statt Entities in Controllern"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TeamManagementService` — bereits Team-Detail-Daten, Farb/Logo-Propagation, kann um TeamController CRUD erweitert werden
- `SeasonManagementService` — bereits Team/Car/Track Pool-Verwaltung, natuerliches Ziel fuer SeasonController Repository-Zugriffe
- `DriverService` — existiert bereits, kann um assign/delete erweitert werden
- Exception-Klassen aus Phase 1 (EntityNotFoundException, ValidationException) — fuer Service-Validierung nutzbar

### Established Patterns
- POST-Redirect-GET mit Flash-Attributes — Services muessen Exceptions werfen, Controller fangen und setzen Flash-Messages
- DTOs fuer Form-Binding (POST), Entities fuer GET — Pattern muss in Services beibehalten werden
- @Transactional auf Service-Methoden — bestehender Standard

### Integration Points
- 7 Controller mit direkten Repository-Zugriffe → 4 neue Services + 3 bestehende Services erweitern
- Bestehende Tests (WebMvcTest, ServiceTest) muessen nach Migration weiter gruen sein

</code_context>

<specifics>
## Specific Ideas

- SeasonManagementService ist das natuerliche Ziel fuer den SeasonController — Pool-Verwaltung ist schon dort
- Neue Services (Car, Track, RaceScoring, MatchScoring) sollen schlank starten — nur die Repository-Operationen die aktuell in Controllern liegen

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 02-service-layer-extraction*
*Context gathered: 2026-04-03*
