# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management-Anwendung (Spring Boot 4 / Thymeleaf / MariaDB). Verwaltet Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring und Standings fuer die Community Team Cup Liga. Nach dem v1.0 Tech Debt Cleanup ist die Codebasis architektonisch sauber und produktionsbereit.

## Core Value

Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## Current Milestone: v1.1 Codebase Concerns Cleanup

**Goal:** Alle identifizierten technischen Concerns aus dem Codebase-Audit systematisch beheben — von Layer-Violations ueber Security-Hardening bis hin zu Code-Reduktion.

**Target features:**

- Layer-Violation beheben: Domain Services von Admin DTOs entkoppeln
- Residuale Repository-Zugriffe aus 5 Controllern entfernen
- TemplateEditorController von 380 Zeilen Duplikation auf generischen Ansatz refactoren
- 60+ `catch(Exception e)` Bloecke durch spezifische Exceptions ersetzen
- Alltime Standings implementieren (aktuell leere Liste)
- StandingsController Business-Logik (Buchholz/Swiss-Sorting) in Service verschieben
- Inline-Styles in Admin Templates durch CSS-Klassen ersetzen
- Grosse Service-Klassen aufteilen (PlayoffService, RaceService)
- SSRF-Schutz: Hostname-Validierung in FileStorageService.storeFromUrl()
- Path-Traversal-Check in store() und storeImage() ergaenzen
- Unbounded findAll() eingrenzen

## Current State (after v1.0)

- **Codebase:** 13.526 LOC Java (Prod) + 17.021 LOC Java (Tests), 777 Tests, 82%+ Coverage
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
- ✓ SSRF Hostname-Validierung (private IPs, localhost, link-local blockiert) — Phase 6
- ✓ Path-Traversal-Schutz in store(), storeImage(), storeFromUrl() — Phase 6
- ✓ Domain Services von Admin DTOs entkoppelt (10 Services, 0 admin.dto Imports) — Phase 7
- ✓ Residuale Repository-Zugriffe aus 5 Controllern entfernt — Phase 7
- ✓ StandingsController Business-Logik (Buchholz/Swiss-Sorting) in StandingsService verschoben — Phase 7
- ✓ catch(Exception e) in Controllern und Services durch spezifische Exceptions ersetzt — Phase 8
- ✓ Unbounded findAll() in RaceService eingegrenzt, DriverRankingService dokumentiert — Phase 8

### Active (v1.1)

- [ ] TemplateEditorController Duplikation beseitigen (generischer Ansatz)
- [ ] Alltime Standings implementieren
- [ ] Inline-Styles in Admin Templates durch CSS-Klassen ersetzen
- [ ] Grosse Service-Klassen aufteilen (PlayoffService, RaceService)

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

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-05 after Phase 8 completion*
