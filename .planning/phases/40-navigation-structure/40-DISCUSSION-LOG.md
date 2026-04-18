# Phase 40: Navigation & Structure - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-16
**Phase:** 40-navigation-structure
**Areas discussed:** Subnav-Design, Active-State-Mechanik, Breadcrumb-Aufbau, Matchday-Ziel

---

## Subnav-Design

### Platzierung

| Option | Description | Selected |
|--------|-------------|----------|
| Im Layout-Fragment (empfohlen) | Subnav-Leiste direkt unter der Top-Nav in layout.html — erscheint automatisch auf allen Season-Seiten wenn Season-Kontext vorhanden ist | ✓ |
| Innerhalb jedes Templates | Subnav als HTML-Block am Anfang jeder Season-Seite. Mehr Kontrolle, aber Duplikation | |
| Claude entscheidet | Technische Entscheidung nach Template-Struktur | |

**User's choice:** Im Layout-Fragment
**Notes:** Single source of truth, konsistent, minimale Duplikation

### Visueller Stil

| Option | Description | Selected |
|--------|-------------|----------|
| Pill-Links (empfohlen) | Horizontale Leiste mit Pill-förmigen Links, ähnlich der Top-Nav. Dunkler Hintergrund, kompakt | ✓ |
| Unterstrichene Tabs | Horizontale Tabs mit Unterstreichung für aktiven Tab. Klarer, moderner Look | |
| Claude entscheidet | Visueller Stil nach Claudes Einschätzung | |

**User's choice:** Pill-Links
**Notes:** Passt zum bestehenden Dark-Theme Design

### Subnav-Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Nur Season-Unterseiten (empfohlen) | Subnav nur auf Seiten unter season/{slug}/ | |
| Index zeigt Active-Season-Subnav | Index-Seite zeigt die Subnav der aktiven Season | |
| Claude entscheidet | Abhängig davon welche Seiten Season-Kontext haben | ✓ |

**User's choice:** Claude entscheidet
**Notes:** —

---

## Active-State-Mechanik

### Visuelle Darstellung

| Option | Description | Selected |
|--------|-------------|----------|
| Accent-Farbe + Hintergrund (empfohlen) | Aktiver Link bekommt accent-Farbe (#4fc3f7) und leichten Hintergrund rgba(79,195,247,0.1) | ✓ |
| Nur Accent-Farbe | Aktiver Link nur in Accent-Farbe, kein Hintergrund | |
| Claude entscheidet | Visuelles Design passend zum Dark-Theme | |

**User's choice:** Accent-Farbe + Hintergrund
**Notes:** Konsistent mit Pill-Stil der Subnav

### Scope

| Option | Description | Selected |
|--------|-------------|----------|
| Beides — Top-Nav UND Subnav (empfohlen) | Top-Nav zeigt aktiven Bereich, Subnav zeigt aktuelle Unterseite. Vollständige Orientierung | ✓ |
| Nur Subnav | Top-Nav bleibt ohne Active-State | |
| Claude entscheidet | Abhängig von Komplexität und Nutzen | |

**User's choice:** Beides — Top-Nav UND Subnav
**Notes:** —

---

## Breadcrumb-Aufbau

### Hierarchie

| Option | Description | Selected |
|--------|-------------|----------|
| Home > Season > Page (empfohlen) | Dreistufig, kompakt, passend zur flachen Seitenstruktur | ✓ |
| Home > Archive > Season > Page | Vierstufig, zeigt Weg über Archiv | |
| Claude entscheidet | Basierend auf URL-Struktur | |

**User's choice:** Home > Season > Page
**Notes:** Kompakt, ausreichend für die flache Struktur

### Position

| Option | Description | Selected |
|--------|-------------|----------|
| Zwischen Subnav und Content (empfohlen) | Direkt unter der Subnav-Leiste, über dem Section-Title. Kleine Schrift, dezent | ✓ |
| Im Content-Bereich (innerhalb main) | Am Anfang des Content-Bereichs als erstes Element | |
| Claude entscheidet | Nach Design-Gesichtspunkten | |

**User's choice:** Zwischen Subnav und Content
**Notes:** —

### Klickbarkeit

| Option | Description | Selected |
|--------|-------------|----------|
| Alle klickbar außer letzte (empfohlen) | Home und Season sind Links, aktuelle Seite ist nur Text | ✓ |
| Alle klickbar | Auch aktuelle Seite ist ein Link (reload) | |
| Claude entscheidet | Standard-Konventionen anwenden | |

**User's choice:** Alle klickbar außer letzte
**Notes:** Home → index.html, Season → standings.html der Season

---

## Matchday-Ziel

### Link-Ziel

| Option | Description | Selected |
|--------|-------------|----------|
| Letzter Matchday (empfohlen) | Link zeigt auf neuesten Matchday. Kein neuer Seitentyp | |
| Erster Matchday | Chronologischer Einstieg bei Matchday 1 | |
| Dropdown mit allen Matchdays | Expandable Submenu für alle Matchdays. Höhere Komplexität (JS) | |
| Matchday-Indexseite erstellen | Neue Übersichtsseite matchdays.html pro Season | ✓ |

**User's choice:** Matchday-Indexseite erstellen
**Notes:** Sauberer Navigationseinstieg, eigene Seite statt Workaround

### Index-Inhalt

| Option | Description | Selected |
|--------|-------------|----------|
| Kompakte Matchday-Liste (empfohlen) | Klickbare Einträge mit Label und optionalem Datum, keine Score-Details | ✓ |
| Match-Cards mit Scores | Jeder Matchday als Card mit Match-Ergebnissen. Mehr Detail | |
| Claude entscheidet | Layout basierend auf vorhandenen Daten | |

**User's choice:** Kompakte Matchday-Liste
**Notes:** Übersichtlich, Details gehören auf die Einzelseiten

---

## Claude's Discretion

- Subnav-Scope: ob Index/Archive auch Subnav bekommen (D-04)
- Exakte CSS-Properties für Subnav und Breadcrumbs
- Mobile-Verhalten der Subnav
- Matchday-Datum-Anzeige bei null matchDate

## Deferred Ideas

None — discussion stayed within phase scope
