# CTC Manager — Gran Turismo Racing League Manager

## What This Is

Gran Turismo Racing League Management-Anwendung (Spring Boot 4 / Thymeleaf / MariaDB). Verwaltet Seasons, Matchdays, Matches, Races, Teams, Drivers, Scoring und Standings fuer die Community Team Cup Liga. Nach v1.0 Tech Debt Cleanup und v1.1 Codebase Concerns Cleanup ist die Codebasis architektonisch sauber, sicherheitsgehaertet und produktionsbereit.

## Core Value

Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## Current Milestone: v1.2 Driver Merge

**Goal:** Zwei Fahrer zusammenfuehren — Quell-Fahrer wird in Ziel-Fahrer gemergt, alle FK-Referenzen umgehaengt, PSN-ID als Alias uebernommen.

**Target features:**
- Merge-Button auf der Fahrer-Detailseite mit Ziel-Fahrer-Auswahl
- Alle FK-Referenzen umhaengen (SeasonDriver, RaceLineup, RaceResult, PsnAlias)
- PSN-ID des Quell-Fahrers als neuer Alias am Ziel-Fahrer
- Quell-Fahrer nach Merge loeschen
- Duplikat-Handling bei Unique Constraints (gleicher Fahrer bereits in Season/Race)

## Current State (after v1.1)

- **Codebase:** 13,731 LOC Java (Prod) + 18,621 LOC Java (Tests), 820 Tests, 82%+ Coverage
- **Tech Stack:** Spring Boot 4.0.5, Java 25, MariaDB 11 / H2, Thymeleaf, Playwright
- **Security:** HTTP Basic Auth (prod/docker), open (dev/local), SSRF hostname blocklist, path traversal defense, Content-Disposition header injection protection, null MIME-type safe defaults
- **Architecture:** Saubere 3-Tier (Controller → Service → Repository), keine God Services, zentrale Exception-Behandlung, domain services fully decoupled from admin DTOs
- **Database:** 36 FK-Indexes, 28 @EntityGraph-Annotationen, Flyway-managed
- **Templates:** CSS utility classes statt inline styles, TemplateManageable generic dispatch

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

### Validated (v1.1)

- ✓ SSRF Hostname-Validierung (private IPs, localhost, link-local blockiert) — Phase 6/12 (SECU-01)
- ✓ Path-Traversal-Schutz in store(), storeImage(), storeFromUrl() — Phase 6/12 (SECU-02)
- ✓ Domain Services von Admin DTOs entkoppelt (10 Services, 0 admin.dto Imports) — Phase 7/13 (ARCH-01)
- ✓ 5 Controller nutzen nur Services, keine Repositories — Phase 7/13 (ARCH-02)
- ✓ StandingsController Business-Logik (Buchholz/Swiss-Sorting) in StandingsService — Phase 7/13 (FEAT-02)
- ✓ catch(Exception e) durch spezifische Exceptions ersetzt — Phase 8/14 (ERRH-01)
- ✓ Unbounded findAll() in RaceService eingegrenzt, DriverRankingService dokumentiert — Phase 8 (QUAL-02)
- ✓ TemplateEditorController generischer Dispatch via TemplateManageable — Phase 10 (ARCH-03)
- ✓ PlayoffService aufgeteilt (BracketView + Seeding) — Phase 10 (ARCH-04)
- ✓ RaceService aufgeteilt (FormData + Calendar) — Phase 10 (ARCH-05)
- ✓ Inline-Styles in Admin Templates durch CSS-Klassen ersetzt — Phase 11 (QUAL-01)
- ✓ Alltime Standings cross-season Aggregation — Phase 9/15 (FEAT-01)

### Active

- [ ] Merge-Button auf Fahrer-Detailseite mit Ziel-Fahrer-Auswahl
- [ ] Alle FK-Referenzen (SeasonDriver, RaceLineup, RaceResult, PsnAlias) umhaengen
- [ ] PSN-ID des Quell-Fahrers als Alias am Ziel-Fahrer
- [ ] Quell-Fahrer nach Merge loeschen
- [ ] Duplikat-Handling bei Unique Constraints

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
| TemplateManageable Interface | Generischer Dispatch statt 380 Zeilen Duplikation | ✓ v1.1 |
| PlayoffService + RaceService Split | Fokussierte Services statt God Services | ✓ v1.1 |
| Domain DTO Decoupling | Primitive Parameters statt Admin DTOs in Domain Services | ✓ v1.1 |
| Recovery Phases (12-15) | Worktree file clobber erforderte Re-Implementation | ✓ v1.1 |

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
*Last updated: 2026-04-13 after Phase 29 — Mass Assignment Fix (v1.5)*
