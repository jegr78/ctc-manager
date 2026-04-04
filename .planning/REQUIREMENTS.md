# Requirements: CTC Manager Technical Debt Cleanup

**Defined:** 2026-04-03
**Core Value:** Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## v1 Requirements

Requirements for this milestone. Each maps to roadmap phases.

### Exception Handling

- [x] **EXCP-01**: Global Exception Handler mit @ControllerAdvice faengt unbehandelte Exceptions (NoSuchElementException, EntityNotFoundException) und zeigt benutzerfreundliche Fehlerseite
- [x] **EXCP-02**: Alle 50+ .orElseThrow() Aufrufe mit aussagekraeftigen Exception-Messages (Entity-Typ + ID)

### Service Layer

- [ ] **SRVC-01**: DriverController Repository-Zugriffe (assign, delete) in DriverService extrahieren
- [ ] **SRVC-02**: TrackController Repository-Zugriffe (save, delete, image upload) in TrackService extrahieren
- [ ] **SRVC-03**: CarController Repository-Zugriffe (image upload) in CarService extrahieren
- [ ] **SRVC-04**: RaceScoringController Repository-Zugriffe in RaceScoringService extrahieren
- [ ] **SRVC-05**: MatchScoringController Repository-Zugriffe in MatchScoringService extrahieren
- [ ] **SRVC-06**: TeamController Repository-Zugriffe in TeamManagementService extrahieren
- [ ] **SRVC-07**: SeasonController 8 Repository-Injections durch Service-Aufrufe ersetzen
- [x] **SRVC-08**: RaceManagementService in RaceService (Core CRUD), RaceGraphicService, RaceAttachmentService aufteilen

### Security

- [x] **SECU-01**: Spring Security Basic Auth aktiv fuer prod und docker Profile
- [x] **SECU-02**: Dev und local Profile bleiben ohne Authentifizierung
- [x] **SECU-03**: Alle bestehenden @WebMvcTest Tests funktionieren mit Security auf Classpath
- [x] **SECU-04**: FileStorageService.storeFromUrl() validiert URL-Schema (nur https) und optional Hostname-Allowlist

### Database

- [x] **DBIX-01**: Flyway V2 Migration mit Indexes auf allen FK-Columns (races, race_results, race_lineups, matches, matchdays, season_drivers, season_teams)
- [x] **DBIX-02**: @EntityGraph Annotationen fuer haeufig traversierte Beziehungen (Match->Teams, Race->Matchday)

## v2 Requirements

Deferred to future milestone. Tracked but not in current roadmap.

### Exception Handling

- **EXCP-03**: Custom Exception Hierarchy (EntityNotFoundException, BusinessRuleException) als dedizierte Klassen
- **EXCP-04**: 65 catch(Exception e) Bloecke durch spezifische Exception-Typen ersetzen

### Service Layer

- **SRVC-09**: StandingsController Swiss-Pairing Business-Logik in StandingsService verschieben
- **SRVC-10**: TemplateEditorController 30+ identische Try-Catch durch Map-basiertes Dispatch-Pattern ersetzen

### Security

- **SECU-05**: H2-Console explizit nur in dev-Profil aktivieren (application.yml Default: disabled)
- **SECU-06**: Stacktrace-Exposure in Prod explizit deaktivieren (application-prod.yml)

### Reliability

- **RELI-01**: CompletableFuture Fehler-Propagation in GT7 Sync (failed downloads im Result Summary)
- **RELI-02**: Alltime Standings implementieren oder UI-Option deaktivieren
- **RELI-03**: Playwright Runtime-Dependency dokumentieren und Health Check hinzufuegen

### Performance

- **PERF-01**: Unbounded findAll() durch Pagination-ready Repositories ersetzen

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| OAuth2 / OIDC | Basic Auth reicht fuer Single-Admin-App |
| OSIV deaktivieren | Bewusst aktiviert, nur @EntityGraph als Optimierung |
| Flyway V1 aendern | Checksummen-geschuetzt, nur neue Migrationen |
| Pagination UI | Nur Repository-Vorbereitung, kein Template-Umbau |
| Neue Features | Erst nach Debt Cleanup |
| Form Login / User Management | Ueberdimensioniert fuer Admin-Tool |
| CSRF Protection | Kein oeffentlich zugaengliches Formular in Prod |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| EXCP-01 | Phase 1 | Complete |
| EXCP-02 | Phase 1 | Complete |
| SRVC-01 | Phase 2 | Pending |
| SRVC-02 | Phase 2 | Pending |
| SRVC-03 | Phase 2 | Pending |
| SRVC-04 | Phase 2 | Pending |
| SRVC-05 | Phase 2 | Pending |
| SRVC-06 | Phase 2 | Pending |
| SRVC-07 | Phase 2 | Pending |
| SRVC-08 | Phase 3 | Complete |
| SECU-01 | Phase 5 | Complete |
| SECU-02 | Phase 5 | Complete |
| SECU-03 | Phase 5 | Complete |
| SECU-04 | Phase 5 | Complete |
| DBIX-01 | Phase 4 | Complete |
| DBIX-02 | Phase 4 | Complete |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16
- Unmapped: 0

---
*Requirements defined: 2026-04-03*
*Last updated: 2026-04-03 after roadmap creation*
