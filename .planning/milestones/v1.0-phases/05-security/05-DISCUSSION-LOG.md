# Phase 5: Security - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-04
**Phase:** 05-security
**Areas discussed:** Credentials-Verwaltung, MockMvc-Test-Strategie, SSRF-Schutz Umfang, Fehlerseiten (401/403)

---

## Credentials-Verwaltung

| Option | Description | Selected |
|--------|-------------|----------|
| Environment Variables | spring.security.user.name / .password via Env-Vars. Kein Passwort im Code. | :heavy_check_mark: |
| application.yml pro Profil | Fest in application-prod.yml und application-docker.yml. Einfacher, aber Credentials im Repo. | |
| Hybrid | Default-Werte in application.yml, ueberschreibbar durch Env-Vars. | |

**User's choice:** Environment Variables
**Notes:** Keine Credentials im Code/Repo.

### Follow-up: Docker-Defaults

| Option | Description | Selected |
|--------|-------------|----------|
| Docker-Defaults erlauben | docker-compose.yml setzt Default-Werte (z.B. admin/ctc-admin). Einfacher fuer lokales Testen. | :heavy_check_mark: |
| Keine Defaults | Auch Docker erfordert explizite .env Konfiguration. | |

**User's choice:** Docker-Defaults erlauben
**Notes:** Einfachheit fuer lokales Testen priorisiert.

---

## MockMvc-Test-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| @WithMockUser global | Eigene Test-Basisklasse oder @WithMockUser auf jeder WebMvcTest-Klasse. Minimal-invasiv. | :heavy_check_mark: |
| Security in Tests deaktivieren | AutoConfigureMockMvc(addFilters=false) oder Security-Autoconfiguration excluden. | |
| Eigene Test-SecurityConfig | Separate SecurityConfig fuer Test-Profil die alles erlaubt. | |

**User's choice:** @WithMockUser global
**Notes:** Minimal-invasiver Ansatz bevorzugt.

### Follow-up: Dedizierte Security-Tests

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, eigene SecurityConfig-Tests | Tests die 401/200/dev-offen verifizieren. Deckt Success Criteria direkt ab. | :heavy_check_mark: |
| Nur bestehende Tests gruen halten | Keine neuen Security-Tests. | |

**User's choice:** Ja, eigene SecurityConfig-Tests
**Notes:** Direkte Abdeckung der Success Criteria 1-3.

---

## SSRF-Schutz Umfang

| Option | Description | Selected |
|--------|-------------|----------|
| Nur HTTPS-Schema | URL muss mit https:// beginnen. Blockiert file://, http://, ftp://. | :heavy_check_mark: |
| HTTPS + Hostname-Allowlist | Zusaetzlich nur bekannte Hosts erlauben. Mehr Wartungsaufwand. | |
| HTTPS + Private-IP-Block | HTTPS-Schema + DNS-Aufloesung pruefen gegen private IPs. | |

**User's choice:** Nur HTTPS-Schema
**Notes:** Einfachste Loesung die SECU-04 erfuellt.

### Follow-up: Fehlerbehandlung bei Ablehnung

| Option | Description | Selected |
|--------|-------------|----------|
| IllegalArgumentException + Warn-Log | Konsistent mit bestehendem Validierungsmuster. | :heavy_check_mark: |
| Eigene SecurityException + Warn-Log | Neue Exception-Klasse. Sauberer, aber mehr Code. | |

**User's choice:** IllegalArgumentException + Warn-Log
**Notes:** Konsistenz mit bestehendem Pattern.

---

## Fehlerseiten (401/403)

### 401-Handling

| Option | Description | Selected |
|--------|-------------|----------|
| Browser Basic Auth Dialog | Spring Security Default: WWW-Authenticate Header. Kein custom Template noetig. | :heavy_check_mark: |
| Custom 401-Seite | Eigene Fehlerseite im Admin-Layout. Mehr Aufwand, schoener. | |

**User's choice:** Browser Basic Auth Dialog
**Notes:** Passt zu Single-Admin-App, kein Mehraufwand.

### 403-Seite

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, im Admin-Layout | 403-Template analog zur bestehenden Error-Seite. Phase 1 hatte das vorbereitet. | :heavy_check_mark: |
| Nein, nicht noetig | Bei Basic Auth mit einem User gibt es keinen 403-Fall. | |
| Claude's Discretion | Claude entscheidet ob sinnvoll. | |

**User's choice:** Ja, im Admin-Layout
**Notes:** Konsistent mit Error-Seite aus Phase 1.

---

## Claude's Discretion

- Spring Security 7 API Syntax (lambda DSL, PathPatternRequestMatcher)
- Profil-Erkennung Mechanismus (eine vs. zwei SecurityConfig Klassen)
- @WithMockUser Platzierung (Klasse vs. Meta-Annotation vs. Basisklasse)
- 403-Routing (GlobalExceptionHandler vs. AccessDeniedHandler)
- H2-Console Pfad in Security-Config

## Deferred Ideas

None — discussion stayed within phase scope
