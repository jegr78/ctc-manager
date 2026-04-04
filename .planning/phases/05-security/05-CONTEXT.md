# Phase 5: Security - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Spring Security Basic Auth fuer prod- und docker-Profile einfuehren, dev/local offen lassen, SSRF-Schutz fuer FileStorageService.storeFromUrl(), und alle bestehenden Tests gruen halten. Kein OAuth2, kein Form Login, kein User-Management.

</domain>

<decisions>
## Implementation Decisions

### Credentials-Verwaltung
- **D-01:** Username/Passwort ausschliesslich ueber Environment Variables (`SPRING_SECURITY_USER_NAME`, `SPRING_SECURITY_USER_PASSWORD`)
- **D-02:** Docker-Profil hat Default-Credentials in docker-compose.yml (z.B. admin/ctc-admin) fuer einfaches lokales Testen
- **D-03:** Prod-Profil erfordert explizite Env-Vars — Applikation startet ohne, aber alle Endpoints sind dann gesperrt (Spring Security Default-Passwort im Log)

### SecurityConfig
- **D-04:** Eigene SecurityFilterChain Bean mit profil-bedingter Konfiguration: prod/docker verlangen Basic Auth, dev/local erlauben alles
- **D-05:** Claude's Discretion: Ob eine einzelne SecurityConfig mit @ConditionalOnProfile oder zwei separate Configs (SecuredConfig + OpenConfig) — Claude analysiert was mit Spring Security 7 / Spring Boot 4 am saubersten ist
- **D-06:** Actuator Health-Endpoint (/actuator/health) bleibt ohne Auth erreichbar (Docker Healthcheck)

### Test-Strategie
- **D-07:** ~~Alle 19 WebMvcTest-Klassen erhalten @WithMockUser~~ **REVISED (Research Finding):** Alle 19 Controller-Tests nutzen `@SpringBootTest` + `@ActiveProfiles("dev")`, NICHT `@WebMvcTest`. Da das dev-Profil `OpenSecurityConfig` (permitAll) laedt, bleiben alle Tests ohne `@WithMockUser` gruen. Kein Eingriff in bestehende Tests noetig.
- **D-08:** Eigene dedizierte Security-Tests die verifizieren: 401 ohne Credentials in prod/docker, 200 mit Credentials, dev-Profil bleibt offen
- **D-09:** ~~Claude's Discretion: Ob @WithMockUser per Klasse oder als Meta-Annotation / Test-Basisklasse~~ **OBSOLETE:** Nicht mehr noetig, da @WithMockUser nur fuer neue Security-Integration-Tests verwendet wird

### SSRF-Schutz
- **D-10:** FileStorageService.storeFromUrl() validiert URL-Schema — nur `https://` erlaubt
- **D-11:** Bei nicht-HTTPS URL: IllegalArgumentException("Only HTTPS URLs allowed: " + url) + log.warn()
- **D-12:** Keine Hostname-Allowlist, kein Private-IP-Block — nur Schema-Validierung fuer SECU-04

### Fehlerseiten
- **D-13:** 401 (nicht authentifiziert): Standard Browser Basic Auth Dialog via WWW-Authenticate Header — kein custom Template
- **D-14:** 403 (Zugriff verweigert): Eigene Fehlerseite im Admin-Layout, analog zur bestehenden Error-Seite aus Phase 1
- **D-15:** Claude's Discretion: 403-Seite ueber GlobalExceptionHandler routen oder Spring Security AccessDeniedHandler konfigurieren

### Claude's Discretion
- Spring Security 7 API: PathPatternRequestMatcher vs. antMatchers, lambda DSL Syntax
- Profil-Erkennung: @Profile, @ConditionalOnProperty, oder SecurityFilterChain mit Profil-Check
- @WithMockUser Platzierung (Klasse vs. Meta-Annotation vs. Basisklasse)
- 403-Routing-Mechanismus
- Ob H2-Console Pfad explizit in Security-Config aufgenommen wird

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Security Concerns
- `.planning/codebase/CONCERNS.md` — Abschnitte "No Security Layer" (Severity: High) und "storeFromUrl Downloads Arbitrary URLs" (Severity: Medium)
- `.planning/research/ARCHITECTURE.md` — Zielarchitektur Security
- `.planning/research/PITFALLS.md` — Spring Security 7 API-Aenderungen

### Betroffene Dateien
- `src/main/java/org/ctc/domain/service/FileStorageService.java:83-93` — storeFromUrl() ohne URL-Validierung
- `src/main/resources/application-prod.yml` — Prod-Config (keine Security-Einstellungen)
- `src/main/resources/application-docker.yml` — Docker-Config (keine Security-Einstellungen)
- `src/main/resources/application-dev.yml` — Dev-Config (H2-Console enabled, Stacktrace always)
- `docker-compose.yml` — Docker-Compose fuer lokale Umgebung (Default-Credentials hier setzen)
- `docker-compose.prod.yml` — Prod Docker-Compose (Env-Vars fuer Credentials)

### Bestehende Infrastruktur (aus Phase 1)
- `src/main/java/org/ctc/admin/controller/GlobalExceptionHandler.java` — Zentraler Exception Handler (403-Routing moeglich)
- `src/main/resources/templates/admin/error.html` — Bestehende Fehlerseite im Admin-Layout

### Projekt-Richtlinien
- `CLAUDE.md` — Profile-Beschreibung, Docker-Setup, Architektur-Prinzipien
- `.planning/REQUIREMENTS.md` — SECU-01 bis SECU-04

### Tests
- 19 WebMvcTest-Dateien in `src/test/java/org/ctc/` — alle benoetigen @WithMockUser
- `src/test/java/org/ctc/domain/service/FileStorageServiceTest.java` — Bestehende Tests fuer storeFromUrl()

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalExceptionHandler` (Phase 1) — kann fuer 403-Fehlerseite erweitert werden
- `error.html` Template im Admin-Layout — Vorlage fuer 403-Seite
- `FileStorageServiceTest` — bestehende Tests fuer storeFromUrl(), SSRF-Tests hier ergaenzen

### Established Patterns
- Environment Variables fuer Prod-Konfiguration (DATABASE_URL, DATABASE_USERNAME, etc.)
- Profil-basierte Konfiguration via application-{profile}.yml
- @WebMvcTest fuer Controller-Tests mit MockMvc
- POST-Redirect-GET mit Flash-Attributes in Controllern

### Integration Points
- `pom.xml` — spring-boot-starter-security hinzufuegen
- SecurityFilterChain Bean — neue Config-Klasse
- docker-compose.yml / docker-compose.prod.yml — Env-Vars fuer Credentials
- 19 WebMvcTest-Klassen — @WithMockUser ergaenzen
- FileStorageService.storeFromUrl() — URL-Schema-Validierung einfuegen

</code_context>

<specifics>
## Specific Ideas

- Docker-Compose Default-Credentials sollen einfaches lokales Testen ermoeglichen (admin/ctc-admin o.ae.)
- 403-Seite soll sich nahtlos ins Admin-Layout einfuegen (wie die Error-Seite aus Phase 1)
- Spring Security 7 API (Spring Boot 4.x) beachten — lambda DSL, PathPatternRequestMatcher

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-security*
*Context gathered: 2026-04-04*
