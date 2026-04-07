# Phase 21: English Code - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-08
**Phase:** 21-english-code
**Areas discussed:** Scope-Abgrenzung, Test-Daten Strings, HTML-Kommentare, Verifikations-Strategie

---

## Scope-Abgrenzung

| Option | Description | Selected |
|--------|-------------|----------|
| Nur gefundene Reste | Fokus auf 27 Test-Strings + 3 HTML-Kommentare + Grep-Scan | ✓ |
| Vollständiger Re-Scan | Komplettes Projekt nochmal systematisch durchgehen | |

**User's choice:** Nur gefundene Reste (Empfohlen)
**Notes:** Production Java bereits komplett Englisch. Phase 20 hat bereits gescannt.

---

## Test-Daten Strings

| Option | Description | Selected |
|--------|-------------|----------|
| Ja, zu "Matchday N" | Konsistent mit English-only Policy, Slug-Assertions mitanpassen | ✓ |
| Nein, belassen | Test-Daten simulieren Benutzereingaben, die deutsch sein könnten | |

**User's choice:** Ja, zu "Matchday N" (Empfohlen)
**Notes:** Slug-Assertion in SiteGeneratorServiceTest wird von spieltag-1.html zu matchday-1.html angepasst.

---

## HTML-Kommentare

| Option | Description | Selected |
|--------|-------------|----------|
| Ins Englische übersetzen | Konkrete Übersetzungen für alle 3 Kommentare | ✓ |
| Kommentare entfernen | HTML-Kommentare sind selten nötig | |

**User's choice:** Ins Englische übersetzen (Empfohlen)
**Notes:** None

---

## Verifikations-Strategie

| Option | Description | Selected |
|--------|-------------|----------|
| Grep-Scan mit Wortliste | Definierte Liste deutscher Wörter + Umlaut-Scan mit Allowlist | ✓ |
| Manueller Review | Claude scannt visuell | |
| Beides | Grep-Scan + manuelle Prüfung | |

**User's choice:** Grep-Scan mit Wortliste (Empfohlen)
**Notes:** None

---

## Claude's Discretion

- Exact German word list for verification grep scan
- Order of file changes
- Commit grouping strategy

## Deferred Ideas

None — discussion stayed within phase scope
