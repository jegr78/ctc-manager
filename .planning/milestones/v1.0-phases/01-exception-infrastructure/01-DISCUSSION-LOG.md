# Phase 1: Exception Infrastructure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-03
**Phase:** 01-exception-infrastructure
**Areas discussed:** Fehlerseite, Handler-Strategie, Exception-Typen, orElseThrow

---

## Fehlerseite

| Option | Description | Selected |
|--------|-------------|----------|
| Admin-Layout | Fehlerseite im bestehenden Admin-Layout (Sidebar, Header) mit freundlicher Meldung | ✓ |
| Standalone | Eigene schlichte Seite ohne Admin-Layout (wie klassische 404/500 Seiten) | |
| You decide | Claude entscheidet die beste Variante | |

**User's choice:** Admin-Layout
**Notes:** Fehlerseite soll sich nahtlos einfuegen

| Option | Description | Selected |
|--------|-------------|----------|
| Dev: Details + Prod: kurz | Dev zeigt Exception-Typ + Message + Stacktrace, Prod nur freundliche Meldung | |
| Immer kurz | Immer nur freundliche Meldung, nie technische Details | |
| You decide | Claude entscheidet | ✓ |

**User's choice:** You decide

| Option | Description | Selected |
|--------|-------------|----------|
| 404 + 500 | Eigene Seiten fuer Not Found und Internal Server Error | |
| 404 + 500 + 403 | Zusaetzlich Forbidden fuer spaetere Security-Integration | |
| You decide | Claude entscheidet basierend auf Phase-5-Vorbereitung | ✓ |

**User's choice:** You decide

---

## Handler-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Neue Klasse | Eigene GlobalExceptionHandler.java mit @ControllerAdvice | |
| Erweitern | GlobalModelAdvice um @ExceptionHandler-Methoden ergaenzen | |
| You decide | Claude entscheidet | ✓ |

**User's choice:** You decide

| Option | Description | Selected |
|--------|-------------|----------|
| Nur unbehandelte | Handler faengt nur NoSuchElementException und unerwartete Fehler | |
| Alles zentral | Handler faengt alle Exception-Typen, Controller try-catches werden entfernt | |
| You decide | Claude entscheidet | ✓ |

**User's choice:** You decide

---

## Exception-Typen

| Option | Description | Selected |
|--------|-------------|----------|
| Eigene Klassen | EntityNotFoundException extends RuntimeException | ✓ |
| Standard + Message | NoSuchElementException mit beschreibender Message | |
| You decide | Claude entscheidet | |

**User's choice:** Eigene Klassen

| Option | Description | Selected |
|--------|-------------|----------|
| Minimal (1-2) | Nur EntityNotFoundException + generische AppException | |
| Pro Concern (3-5) | EntityNotFoundException, ValidationException, BusinessRuleException, etc. | ✓ |
| You decide | Claude entscheidet passend zum Scope | |

**User's choice:** Pro Concern (3-5)

---

## orElseThrow

| Option | Description | Selected |
|--------|-------------|----------|
| Entity + ID | z.B. 'Season not found: 550e8400...' | |
| Entity + ID + Kontext | z.B. 'Season not found: 550e8400... (requested by RaceController.save)' | |
| You decide | Claude entscheidet das beste Format | ✓ |

**User's choice:** You decide

| Option | Description | Selected |
|--------|-------------|----------|
| EntityNotFoundException | Eigene Exception (passt zu Eigene Klassen Entscheidung) | |
| You decide | Claude entscheidet | ✓ |

**User's choice:** You decide

---

## Claude's Discretion

- Error-Page Template-Design (innerhalb Admin-Layout)
- Dev vs Prod Detail-Level auf Fehlerseite
- HTTP-Statuscodes (404, 500, ggf. 403)
- GlobalExceptionHandler als eigene Klasse vs Erweiterung
- Scope der zentral gefangenen Exceptions
- orElseThrow Message-Format
- orElseThrow Exception-Typ

## Deferred Ideas

None — discussion stayed within phase scope
