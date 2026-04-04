# CTC Manager — Technical Debt Cleanup

## What This Is

Systematische Bereinigung aller technischen Schulden im CTC Manager, einer Gran Turismo Racing League Management-Anwendung (Spring Boot 4 / Thymeleaf / MariaDB). Ziel ist eine saubere Codebasis als Grundlage fuer zukuenftige Feature-Entwicklung.

## Core Value

Architektur-Konsistenz: Alle Controller delegieren an Services, Exception Handling ist zentral, und die Prod-Umgebung ist abgesichert.

## Requirements

### Validated

Die bestehende Anwendung ist funktional komplett:

- ✓ Season/Matchday/Match/Race CRUD — existing
- ✓ Team/Driver/Car/Track Management — existing
- ✓ Scoring System (konfigurierbar via RaceScoring) — existing
- ✓ Standings/Rankings Berechnung — existing
- ✓ CSV Import fuer Race Results — existing
- ✓ GT7 Car/Track Sync — existing
- ✓ Playoff Bracket Management — existing
- ✓ Graphic Generation (Team Cards, Lineups, Results) — existing
- ✓ Static Site Generation — existing
- ✓ Template Editor — existing
- ✓ Zentrale Exception-Behandlung (GlobalExceptionHandler, EntityNotFoundException) — Phase 1
- ✓ Aussagekraeftige orElseThrow()-Messages in allen 21 Produktionsdateien — Phase 1
- ✓ 7 Controller ohne Repository-Injections, 4 neue + 3 erweiterte Services — Phase 2
- ✓ RaceManagementService in 3 Services aufgeteilt (RaceService, RaceGraphicService, RaceAttachmentService) — Phase 3
- ✓ 36 FK-Indexes auf allen Foreign Key Columns via Flyway V2 Migration — Phase 4
- ✓ 28 @EntityGraph-Annotationen auf Collection-returning Repository-Methoden — Phase 4
- ✓ Spring Security Basic Auth fuer prod/docker Profile (SecurityConfig + OpenSecurityConfig) — Phase 5
- ✓ SSRF-Schutz fuer FileStorageService.storeFromUrl() (nur HTTPS, IllegalArgumentException + log.warn) — Phase 5
- ✓ 403 Access Denied Seite im Admin-Layout, Docker-Credentials konfiguriert — Phase 5
- ✓ 753 Tests gruen inkl. Security Integration Tests, alle bestehenden Tests unveraendert — Phase 5

### Active

- [ ] TemplateEditorController Duplikation beseitigen
- [ ] StandingsController Business-Logik in Service verschieben
- [ ] Spezifische Exception-Typen statt catch(Exception e)
- [ ] H2-Console explizit nur in dev aktivieren
- [ ] Stacktrace-Exposure in Prod explizit deaktivieren
- [ ] CompletableFuture Fehler-Propagation in GT7 Sync
- [ ] Alltime Standings implementieren oder UI-Option deaktivieren
- [ ] Playwright Runtime-Dependency dokumentieren / Health Check
- [ ] Unbounded findAll() durch Pagination ersetzen (Vorbereitung)

### Out of Scope

- Neue Features — erst nach Debt Cleanup
- OAuth2/OIDC — Basic Auth reicht fuer Single-Admin-App
- Full Pagination UI — nur Repository-Vorbereitung, kein UI-Umbau
- OSIV deaktivieren — bewusst aktiviert, nur @EntityGraph als Optimierung
- Flyway V1 Migration aendern — nur neue Migrationen

## Context

- 17 Concerns identifiziert via Codebase-Analyse (`.planning/codebase/CONCERNS.md`)
- Severity-Verteilung: 1x High, 6x Medium, 10x Low
- Bestehende Testabdeckung: 753 Tests, 82% Line Coverage Minimum
- Architektur-Prinzipien in CLAUDE.md dokumentiert — Code soll diesen entsprechen
- App laeuft lokal ohne Auth (dev/local), Auth nur fuer prod/docker Profile
- Spring Boot 4.0.5, Java 25, MariaDB 11 / H2

## Constraints

- **Testabdeckung**: 82% Line Coverage Minimum darf nicht unterschritten werden
- **Flyway**: Bestehende V1 Migration nicht aendern, nur neue V2+ Migrationen
- **Profile**: Auth nur fuer prod/docker, dev/local bleiben ohne Auth
- **OSIV**: Bleibt aktiviert — nur @EntityGraph-Annotationen als Optimierung
- **Abwaertskompatibilitaet**: Keine Breaking Changes an bestehenden URLs/Endpoints
- **Playwright**: Bleibt Compile-Scope Dependency (Runtime-Nutzung fuer Graphics)

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Architektur-Refactoring vor Auth | Saubere Service-Schicht macht Auth-Integration einfacher | Phase 5: Validated |
| Basic Auth statt Form Login | Single-Admin-App, kein User-Management noetig | Phase 5: Complete |
| Auth nur prod/docker Profile | Dev/Local sind lokale Umgebungen ohne Netzwerk-Exposure | Phase 5: Complete |
| RaceManagementService aufteilen | 673 Zeilen, 13 Dependencies — 3 fokussierte Services extrahiert | Phase 3: Complete |
| Zwei-Profil SecurityFilterChain | @Profile-basiert statt Runtime-Check — sauberer, testbarer | Phase 5: Complete |
| CSRF disabled | Kein oeffentlich zugaengliches Formular, Single-Admin via Basic Auth | Phase 5: Complete |
| @WithMockUser nicht noetig | Alle 19 Tests nutzen @ActiveProfiles("dev") → OpenSecurityConfig permitAll | Phase 5: Validated |
| Alle 17 Concerns angehen | Komplettbereinigung vor naechstem Feature-Zyklus | — Pending |

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
*Last updated: 2026-04-04 after Phase 5 completion — all 5 phases complete, milestone v1 done*
