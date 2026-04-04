# Roadmap: CTC Manager Technical Debt Cleanup

## Overview

Systematische Bereinigung der technischen Schulden in strenger Abhaengigkeitsreihenfolge: Zuerst Exception-Infrastruktur als Fundament, dann Service-Layer-Extraktion aus Controllern, dann die komplexe RaceManagementService-Aufteilung, dann unabhaengige DB-Optimierungen, und zuletzt Security als isolierte Phase (weil sie alle 221 MockMvc-Tests bricht und nicht mit anderen Aenderungen vermischt werden darf).

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Exception Infrastructure** - GlobalExceptionHandler und aussagekraeftige Exception-Messages als Fundament
- [ ] **Phase 2: Service Layer Extraction** - Controller-Repository-Zugriffe in bestehende und neue Services verschieben
- [ ] **Phase 3: God Service Split** - RaceManagementService in drei fokussierte Services aufteilen
- [ ] **Phase 4: Database Optimization** - FK-Indexes und EntityGraph-Annotationen fuer Query-Performance
- [ ] **Phase 5: Security** - Spring Security Basic Auth fuer prod/docker mit SSRF-Schutz

## Phase Details

### Phase 1: Exception Infrastructure
**Goal**: Unbehandelte Exceptions werden zentral abgefangen und der Admin sieht benutzerfreundliche Fehlermeldungen statt Stacktraces
**Depends on**: Nothing (first phase)
**Requirements**: EXCP-01, EXCP-02
**Success Criteria** (what must be TRUE):
  1. Eine fehlende Entity (z.B. geloeschtes Team per URL aufrufen) zeigt eine lesbare Fehlerseite statt Whitelabel Error
  2. Alle .orElseThrow()-Aufrufe im Code enthalten Entity-Typ und ID in der Exception-Message
  3. Bestehende Controller-Flash-Messages (z.B. nach Form-Submits) funktionieren weiterhin ungestoert
**Plans**: 2 plans

Plans:
- [x] 01-01-PLAN.md — Exception classes, GlobalExceptionHandler, admin error template + tests
- [x] 01-02-PLAN.md — Migrate all 136 orElseThrow() calls to EntityNotFoundException across 21 files

### Phase 2: Service Layer Extraction
**Goal**: Alle Controller delegieren Repository-Zugriffe an Services — kein Controller injiziert mehr direkt ein Repository
**Depends on**: Phase 1
**Requirements**: SRVC-01, SRVC-02, SRVC-03, SRVC-04, SRVC-05, SRVC-06, SRVC-07
**Success Criteria** (what must be TRUE):
  1. DriverController, TrackController, CarController, RaceScoringController, MatchScoringController und TeamController enthalten keine @Autowired Repository-Felder mehr
  2. SeasonController hat maximal 3 Service-Injections statt 8 Repository-Injections
  3. Alle CRUD-Operationen (assign, delete, save, upload) funktionieren identisch wie vor dem Refactoring
  4. Test-Coverage bleibt ueber 82% (neue Services haben eigene Unit-Tests)
**Plans**: 4 plans

Plans:
- [x] 02-01-PLAN.md — RaceScoringService + MatchScoringService: new services for simplest scoring controllers
- [x] 02-02-PLAN.md — TrackService + CarService: new services with image upload handling
- [x] 02-03-PLAN.md — DriverService + TeamManagementService: extend existing services for remaining controllers
- [x] 02-04-PLAN.md — SeasonController consolidation: 7 repository injections into SeasonManagementService

### Phase 3: God Service Split
**Goal**: RaceManagementService (673 Zeilen, 13 Dependencies) ist in drei fokussierte Services mit klaren Verantwortlichkeiten aufgeteilt
**Depends on**: Phase 2
**Requirements**: SRVC-08
**Success Criteria** (what must be TRUE):
  1. RaceService enthaelt nur Core-CRUD (create, update, delete Race), RaceGraphicService nur Grafik-Generierung, RaceAttachmentService nur Datei-Operationen
  2. Keine zirkulaeren Dependencies zwischen den drei neuen Services
  3. Transaktionale Integritaet: Race-Ergebnis-Import (Results + Score-Aggregation) laeuft weiterhin in einer Transaktion
  4. Alle bestehenden Race-bezogenen Workflows (CRUD, CSV-Import, Grafik-Generierung) funktionieren identisch
**Plans**: TBD

Plans:
- [ ] 03-01: TBD

### Phase 4: Database Optimization
**Goal**: FK-Columns haben Indexes und haeufig traversierte Beziehungen nutzen EntityGraph fuer optimierte Queries
**Depends on**: Phase 2
**Requirements**: DBIX-01, DBIX-02
**Success Criteria** (what must be TRUE):
  1. Flyway V2 Migration laeuft fehlerfrei auf H2 (Tests) und MariaDB (Docker/Prod)
  2. Match-Listen und Race-Abfragen laden zugehoerige Teams/Matchdays ohne N+1 Queries (verifizierbar via Hibernate SQL-Log)
**Plans**: TBD

Plans:
- [ ] 04-01: TBD

### Phase 5: Security
**Goal**: Prod- und Docker-Umgebungen sind mit HTTP Basic Auth abgesichert, Dev/Local bleiben offen
**Depends on**: Phase 2
**Requirements**: SECU-01, SECU-02, SECU-03, SECU-04
**Success Criteria** (what must be TRUE):
  1. Zugriff auf beliebige Admin-URLs in prod/docker ohne Credentials liefert HTTP 401
  2. Zugriff auf Admin-URLs in dev/local funktioniert ohne Login wie bisher
  3. Alle bestehenden MockMvc-Tests (221+) sind gruen mit Security auf dem Classpath
  4. FileStorageService.storeFromUrl() lehnt non-HTTPS URLs ab und loggt den Versuch
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5
Note: Phase 4 depends on Phase 2 (not Phase 3), so 4 and 3 have no mutual dependency.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Exception Infrastructure | 2/2 | Complete | 2026-04-03 |
| 2. Service Layer Extraction | 4/4 | Complete | 2026-04-04 |
| 3. God Service Split | 0/0 | Not started | - |
| 4. Database Optimization | 0/0 | Not started | - |
| 5. Security | 0/0 | Not started | - |
