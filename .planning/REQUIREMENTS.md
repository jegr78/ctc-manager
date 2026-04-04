# Requirements: CTC Manager v1.1

**Defined:** 2026-04-04
**Core Value:** Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## v1.1 Requirements

Requirements for Codebase Concerns Cleanup. Each maps to roadmap phases.

### Architecture

- [ ] **ARCH-01**: Domain Services importieren keine Admin DTOs mehr — Entkopplung durch Domain-DTOs oder Controller-Konvertierung
- [ ] **ARCH-02**: Alle 5 Controller (Standings, PowerRankings, Playoff, TeamCard, CsvImport) nutzen Services statt direkte Repository-Injections
- [ ] **ARCH-03**: TemplateEditorController nutzt generischen Ansatz mit Map<String, AbstractGraphicService> statt 30+ Copy-Paste-Bloecke
- [ ] **ARCH-04**: PlayoffService in fokussierte Services aufgeteilt (Bracket-View, Seeding separiert)
- [ ] **ARCH-05**: RaceService in fokussierte Services aufgeteilt (FormData-Assembly, Calendar-Events separiert)

### Error Handling

- [ ] **ERRH-01**: Alle 60+ catch(Exception e) in Controllern durch spezifische Exception-Catches ersetzt (IOException, BusinessRuleException), unerwartete Exceptions propagieren zu GlobalExceptionHandler

### Features

- [ ] **FEAT-01**: Alltime Standings zeigt cross-season Team-Aggregation (StandingsService.calculateAlltimeStandings())
- [ ] **FEAT-02**: StandingsController enthaelt keine Business-Logik mehr — Buchholz-Berechnung und Swiss-Sorting in StandingsService

### Security

- [ ] **SECU-01**: FileStorageService.storeFromUrl() validiert Hostname — private IPs, localhost und interne Netzwerke blockiert
- [ ] **SECU-02**: FileStorageService.store() und storeImage() pruefen Path-Traversal mit normalize()+startsWith(uploadDir)

### Code Quality

- [ ] **QUAL-01**: Inline-Styles in Admin Templates durch CSS-Utility-Klassen ersetzt (Prioritaet: season-detail, race-detail; Ausnahme: Graphic-Render-Templates)
- [ ] **QUAL-02**: Unbounded findAll() in RaceService, DriverService, DriverRankingService eingegrenzt (seasonId-Parameter oder Limit)

## Future Requirements

None deferred — all concerns addressed in v1.1.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Full Pagination UI | Nur Repository/Service-Ebene, kein Template-Umbau |
| OSIV deaktivieren | Bewusst aktiviert, nur @EntityGraph als Optimierung |
| Graphic-Render-Template Styles | Standalone HTML fuer Playwright Screenshots, kein Admin-CSS |
| H2-Console / Stacktrace-Exposure Config | Konfigurationsaenderung, kein Code-Concern |
| CompletableFuture GT7 Sync | Isolierter Sync-Prozess, geringes Risiko |
| Playwright Health Check | Runtime-Dependency, separates Ops-Thema |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| ARCH-01 | — | Pending |
| ARCH-02 | — | Pending |
| ARCH-03 | — | Pending |
| ARCH-04 | — | Pending |
| ARCH-05 | — | Pending |
| ERRH-01 | — | Pending |
| FEAT-01 | — | Pending |
| FEAT-02 | — | Pending |
| SECU-01 | — | Pending |
| SECU-02 | — | Pending |
| QUAL-01 | — | Pending |
| QUAL-02 | — | Pending |

**Coverage:**

- v1.1 requirements: 12 total
- Mapped to phases: 0
- Unmapped: 12 (roadmap pending)

---
*Requirements defined: 2026-04-04*
*Last updated: 2026-04-04 after initial definition*
