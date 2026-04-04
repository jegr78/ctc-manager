# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management-Anwendung (Spring Boot 4 / Thymeleaf / MariaDB). Verwaltet Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring und Standings fuer die Community Team Cup Liga. Nach dem v1.0 Tech Debt Cleanup ist die Codebasis architektonisch sauber und produktionsbereit.

## Core Value

Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## Current State (after v1.0)

- **Codebase:** 13.526 LOC Java (Prod) + 17.021 LOC Java (Tests), 753 Tests, 82%+ Coverage
- **Tech Stack:** Spring Boot 4.0.5, Java 25, MariaDB 11 / H2, Thymeleaf, Playwright
- **Security:** HTTP Basic Auth (prod/docker), open (dev/local), SSRF-geschuetzt
- **Architecture:** Saubere 3-Tier (Controller → Service → Repository), keine God Services, zentrale Exception-Behandlung
- **Database:** 36 FK-Indexes, 28 @EntityGraph-Annotationen, Flyway-managed

## Requirements

### Validated (v1.0)

- ✓ Zentrale Exception-Behandlung (GlobalExceptionHandler, EntityNotFoundException) — Phase 1
- ✓ Aussagekraeftige orElseThrow()-Messages in allen 21 Produktionsdateien — Phase 1
- ✓ 7 Controller ohne Repository-Injections, 4 neue + 3 erweiterte Services — Phase 2
- ✓ RaceManagementService in 3 Services aufgeteilt (RaceService, RaceGraphicService, RaceAttachmentService) — Phase 3
- ✓ 36 FK-Indexes auf allen Foreign Key Columns via Flyway V2 Migration — Phase 4
- ✓ 28 @EntityGraph-Annotationen auf Collection-returning Repository-Methoden — Phase 4
- ✓ Spring Security Basic Auth fuer prod/docker Profile — Phase 5
- ✓ SSRF-Schutz fuer FileStorageService.storeFromUrl() — Phase 5

### Active (v2 candidates)

- [ ] TemplateEditorController Duplikation beseitigen (SRVC-10)
- [ ] StandingsController Business-Logik in Service verschieben (SRVC-09)
- [ ] Spezifische Exception-Typen statt catch(Exception e) (EXCP-03, EXCP-04)
- [ ] H2-Console explizit nur in dev aktivieren (SECU-05)
- [ ] Stacktrace-Exposure in Prod explizit deaktivieren (SECU-06)
- [ ] CompletableFuture Fehler-Propagation in GT7 Sync (RELI-01)
- [ ] Alltime Standings implementieren oder UI-Option deaktivieren (RELI-02)
- [ ] Playwright Runtime-Dependency dokumentieren / Health Check (RELI-03)
- [ ] Unbounded findAll() durch Pagination ersetzen (PERF-01)

### Out of Scope

- OAuth2/OIDC — Basic Auth reicht fuer Single-Admin-App
- Full Pagination UI — nur Repository-Vorbereitung, kein Template-Umbau
- OSIV deaktivieren — bewusst aktiviert, nur @EntityGraph als Optimierung
- Flyway V1 Migration aendern — Checksummen-geschuetzt
- Form Login / User Management — ueberdimensioniert fuer Admin-Tool
- CSRF Protection — kein oeffentlich zugaengliches Formular in Prod

## Constraints

- **Testabdeckung**: 82% Line Coverage Minimum
- **Flyway**: Bestehende Migrationen nicht aendern, nur neue
- **Profile**: Auth nur fuer prod/docker, dev/local bleiben ohne Auth
- **OSIV**: Bleibt aktiviert — nur @EntityGraph als Optimierung
- **Abwaertskompatibilitaet**: Keine Breaking Changes an bestehenden URLs/Endpoints
- **Playwright**: Bleibt Compile-Scope Dependency (Runtime-Nutzung fuer Graphics)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Architektur-Refactoring vor Auth | Saubere Service-Schicht macht Auth-Integration einfacher | ✓ Validated |
| Basic Auth statt Form Login | Single-Admin-App, kein User-Management noetig | ✓ v1.0 |
| Auth nur prod/docker Profile | Dev/Local ohne Netzwerk-Exposure | ✓ v1.0 |
| RaceManagementService aufteilen | 673 Zeilen, 13 Dependencies → 3 Services | ✓ v1.0 |
| Zwei-Profil SecurityFilterChain | @Profile-basiert statt Runtime-Check | ✓ v1.0 |
| CSRF disabled | Single-Admin via Basic Auth, kein Formular oeffentlich | ✓ v1.0 |
| @WithMockUser nicht noetig | Alle Tests nutzen @ActiveProfiles("dev") → permitAll | ✓ Validated |

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-04 after v1.0 milestone completion*
