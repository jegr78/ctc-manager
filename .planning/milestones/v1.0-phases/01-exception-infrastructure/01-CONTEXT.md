# Phase 1: Exception Infrastructure - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Zentrale Exception-Behandlung fuer die gesamte Admin-Anwendung. Unbehandelte Exceptions (insb. fehlende Entities) werden abgefangen und der Admin sieht benutzerfreundliche Fehlermeldungen statt Whitelabel Error Pages oder Stacktraces. Bestehende Controller-Flash-Messages bleiben unangetastet.

</domain>

<decisions>
## Implementation Decisions

### Fehlerseite
- **D-01:** Fehlerseite wird im bestehenden Admin-Layout gerendert (Sidebar, Header) — kein Standalone-Template
- **D-02:** Claude's Discretion: Dev-Profil darf mehr Details zeigen (Exception-Typ, Message), Prod nur freundliche Meldung
- **D-03:** Claude's Discretion: HTTP-Statuscodes abdecken — mindestens 404 + 500, 403 vorbereiten wenn sinnvoll fuer Phase 5 (Security)

### Handler-Strategie
- **D-04:** Claude's Discretion: Neuer GlobalExceptionHandler.java oder GlobalModelAdvice erweitern — Empfehlung: eigene Klasse fuer saubere Trennung
- **D-05:** Claude's Discretion: Handler faengt nur unbehandelte Exceptions (NoSuchElement, EntityNotFound, unerwartete Fehler). Bestehende Controller-Flash-Message try-catches bleiben in Phase 1 erhalten (werden erst in spaeteren Phasen beim Controller-Refactoring angegangen)

### Exception-Typen
- **D-06:** Eigene Exception-Klassen einfuehren (nicht Standard-Java-Exceptions mit Messages)
- **D-07:** 3-5 Exception-Typen pro Concern: EntityNotFoundException, ValidationException, BusinessRuleException + ggf. weitere passend zum Domain
- **D-08:** Alle Exception-Klassen in eigenem Package (z.B. `org.ctc.domain.exception`)

### orElseThrow
- **D-09:** Alle 50+ .orElseThrow() Aufrufe mit EntityNotFoundException und aussagekraeftiger Message versehen
- **D-10:** Claude's Discretion: Message-Format (Entity-Typ + ID ist Minimum)
- **D-11:** Claude's Discretion: Exception-Typ fuer orElseThrow — EntityNotFoundException empfohlen (passt zu D-06/D-07)

### Claude's Discretion
- Error-Page Template-Design (innerhalb Admin-Layout)
- Ob GlobalExceptionHandler neue Klasse oder Erweiterung von GlobalModelAdvice
- Scope der zentral gefangenen Exceptions (Flash-Message-Patterns erhalten)
- Exaktes Message-Format fuer orElseThrow
- Ob 403-Seite schon vorbereitet wird

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Exception Handling
- `src/main/java/org/ctc/admin/controller/GlobalModelAdvice.java` — Einzige bestehende @ControllerAdvice, nur appVersion Model-Attribut
- `.planning/codebase/CONCERNS.md` — Abschnitte "No Global Exception Handler", "Unguarded .orElseThrow() Calls", "Broad Exception Catching"
- `.planning/research/ARCHITECTURE.md` — Target-Architektur fuer Exception Handling

### Templates
- `src/main/resources/templates/admin/layout.html` — Admin-Layout fuer Fehlerseite (Fragment-Pattern)

### Projekt-Richtlinien
- `CLAUDE.md` — Architektur-Prinzipien, insb. "Controller duenn halten" und "Keine Fallback-Berechnungen"

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `GlobalModelAdvice.java` — Bestehende @ControllerAdvice, kann als Referenz dienen oder erweitert werden
- `layout.html` — Admin-Layout mit Fragment-Pattern (`th:replace="~{admin/layout :: layout(...)}"`)

### Established Patterns
- POST-Redirect-GET mit Flash-Attributes fuer Erfolg/Fehler-Meldungen in Controllern
- 65 `catch(Exception e)` Bloecke die RedirectAttributes nutzen — muessen erhalten bleiben
- OSIV aktiv — Entities koennen in Templates lazy geladen werden

### Integration Points
- 50+ `.orElseThrow()` in Services und Controllern — alle ohne Message, werfen NoSuchElementException
- 17 Controller mit teils eigenen catch-Bloecken — Flash-Messages muessen weiter funktionieren
- Kein error.html Template vorhanden — Spring Boot zeigt aktuell Whitelabel Error Page

</code_context>

<specifics>
## Specific Ideas

- Fehlerseite soll sich nahtlos ins Admin-Layout einfuegen (nicht wie ein Fremdkoerper)
- Exception-Klassen sollen als Basis fuer spaeteren Controller-Refactoring und Security dienen

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 01-exception-infrastructure*
*Context gathered: 2026-04-03*
