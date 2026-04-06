# Requirements: CTC Manager v1.1

**Defined:** 2026-04-04
**Core Value:** Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## v1.1 Requirements

Requirements for Codebase Concerns Cleanup. Each maps to roadmap phases.

### Architecture

- [ ] **ARCH-01**: Domain Services importieren keine Admin DTOs mehr -- Entkopplung durch Domain-DTOs oder Controller-Konvertierung
- [ ] **ARCH-02**: Alle 5 Controller (Standings, PowerRankings, Playoff, TeamCard, CsvImport) nutzen Services statt direkte Repository-Injections
- [x] **ARCH-03**: TemplateEditorController nutzt generischen Ansatz mit Map<String, AbstractGraphicService> statt 30+ Copy-Paste-Bloecke
- [x] **ARCH-04**: PlayoffService in fokussierte Services aufgeteilt (Bracket-View, Seeding separiert)
- [x] **ARCH-05**: RaceService in fokussierte Services aufgeteilt (FormData-Assembly, Calendar-Events separiert)

### Error Handling

- [ ] **ERRH-01**: Alle 60+ catch(Exception e) in Controllern durch spezifische Exception-Catches ersetzt (IOException, BusinessRuleException), unerwartete Exceptions propagieren zu GlobalExceptionHandler

### Features

- [ ] **FEAT-01**: Alltime Standings zeigt cross-season Team-Aggregation (StandingsService.calculateAlltimeStandings())
- [ ] **FEAT-02**: StandingsController enthaelt keine Business-Logik mehr -- Buchholz-Berechnung und Swiss-Sorting in StandingsService

### Security

- [ ] **SECU-01**: FileStorageService.storeFromUrl() validiert Hostname -- private IPs, localhost und interne Netzwerke blockiert
- [ ] **SECU-02**: FileStorageService.store() und storeImage() pruefen Path-Traversal mit normalize()+startsWith(uploadDir)

### Code Quality

- [x] **QUAL-01**: Inline-Styles in Admin Templates durch CSS-Utility-Klassen ersetzt (Prioritaet: season-detail, race-detail; Ausnahme: Graphic-Render-Templates)
- [ ] **QUAL-02**: Unbounded findAll() in RaceService, DriverService, DriverRankingService eingegrenzt (seasonId-Parameter oder Limit)

## Future Requirements

None deferred -- all concerns addressed in v1.1.

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
| SECU-01 | Phase 12 (was 6) | Pending |
| SECU-02 | Phase 12 (was 6) | Pending |
| ARCH-01 | Phase 13 (was 7) | Pending |
| ARCH-02 | Phase 13 (was 7) | Pending |
| FEAT-02 | Phase 13 (was 7) | Pending |
| ERRH-01 | Phase 14 (was 8) | Pending |
| QUAL-02 | Phase 8 | Complete |
| FEAT-01 | Phase 15 (was 9) | Pending |
| ARCH-03 | Phase 10 | Complete |
| ARCH-04 | Phase 10 | Complete |
| ARCH-05 | Phase 10 | Complete |
| QUAL-01 | Phase 11 | Complete |

**Coverage:**

- v1.1 requirements: 12 total
- Mapped to phases: 12
- Unmapped: 0

---
*Requirements defined: 2026-04-04*
*Last updated: 2026-04-06 after gap closure phase creation*
