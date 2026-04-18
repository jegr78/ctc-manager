# Phase 20: English Messages - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-07
**Phase:** 20-English Messages
**Areas discussed:** Verifikationsstrategie, Scope-Grenze, Allowlist / Ausnahmen

---

## Verifikationsstrategie

| Option | Description | Selected |
|--------|-------------|----------|
| Grep-Scan (Empfohlen) | Systematischer Grep uber alle Dateien im Projekt. Sucht nach deutschen Wortern und Umlauten in String-Literals. | |
| Manuelles Code-Review | Jede Datei einzeln durchgehen. Grundlich aber zeitaufwandig. | |
| Beides kombiniert | Grep als erste Prufung, dann manuelles Review der Treffer. | |

**User's choice:** Grep uber alle Dateien (not just .java — all file types in the project)
**Notes:** User explicitly expanded scope beyond src/main to entire project

---

## Scope-Grenze

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Messages (I18N-01/02) | Log-Messages, Exception-Messages, Flash-Messages only | |
| Messages + alle Strings | Also deutsche String-Konstanten, Labels, Display-Texte | |
| Alles Deutsch in allen Dateien | Jeder deutsche Text uberall — auch Kommentare, Javadoc, Variablennamen, Configs | ✓ |

**User's choice:** Alles Deutsch in allen Dateien
**Notes:** Effectively merges Phase 20 and Phase 21 scope. All I18N requirements (01-05) addressed in this phase.

---

## Allowlist / Ausnahmen

| Option | Description | Selected |
|--------|-------------|----------|
| Nur echte Eigennamen (Empfohlen) | GT7-Daten (Nurburgring, etc.), Umlaut-Handling-Code. Alles andere wird Englisch. | ✓ |
| Eigennamen + Fachbegriffe | Zusatzlich deutsche Fachbegriffe die im GT7/Racing-Kontext gelaufig sind | |
| Keine Ausnahmen | Alles wird Englisch, auch Eigennamen wo moglich | |

**User's choice:** Nur echte Eigennamen
**Notes:** GT7 track names, umlaut-handling code patterns stay as-is. Everything else converted to English.

---

## Claude's Discretion

- File processing order and commit grouping
- Edge case handling for ambiguous translations
- No permanent guard tests — one-time verification only

## Deferred Ideas

None — discussion stayed within phase scope
