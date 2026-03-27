# CTC Manager — Design Spec

## Kontext

Der **Community Team Cup (CTC)** ist eine Gran Turismo Rennliga, in der Teams in Duellen (6 vs. 6 Fahrer) gegeneinander antreten. Bisher werden Teams, Fahrer und Ergebnisse über Google Spreadsheets verwaltet. Diese Lösung soll durch eine Spring Boot Admin-Anwendung abgelöst werden, die zusätzlich eine statische Webseite generieren kann, um Ergebnisse öffentlich auf GitHub Pages bereitzustellen.

---

## Architektur

**Strukturierter Monolith** — ein Spring Boot Projekt mit klarer Package-Trennung:

- `domain/` — JPA Entities, Repositories, Geschäftslogik (Scoring, Standings, Rankings)
- `admin/` — Thymeleaf-basierte Admin-UI mit CRUD für alle Entities
- `sitegen/` — Statische Seitengenerierung mit eigenen View-Models
- `dataimport/` — CSV-Import und perspektivisch KI-gestützter Bildimport

Vorteile: Einfaches Setup, klare innere Struktur, später zu Multi-Module erweiterbar.

---

## Datenmodell

### Entities

| Entity | Felder | Beschreibung |
|---|---|---|
| **Season** | id, name, startDate, endDate, active | Eine Saison (z.B. "2026") |
| **Matchday** | id, seasonId (FK), label, date, sortIndex | Ein Spieltag (z.B. "Spieltag 1", "Playoff Halbfinale", "Finale") |
| **Race** | id, matchdayId (FK), homeTeamId (FK), awayTeamId (FK), track, car | Ein Duell zwischen zwei Teams |
| **Team** | id, name, shortName, logoUrl | Ein Team (z.B. "TNR", "CLR", "P1R") |
| **Driver** | id, psnId, nickname, active | Ein Fahrer mit PSN-ID und Anzeigename |
| **SeasonDriver** | id, seasonId (FK), driverId (FK), teamId (FK) | Teamzugehörigkeit pro Saison (ermöglicht Transfers) |
| **RaceResult** | id, raceId (FK), driverId (FK), position, qualiPosition, fastestLap, pointsRace, pointsQuali, pointsFl, pointsTotal | Einzelergebnis eines Fahrers in einem Rennen |

### Beziehungen

- Season → Matchday: 1:N (variable Anzahl Spieltage pro Saison)
- Matchday → Race: 1:N (mehrere Paarungen pro Spieltag, Teams können spielfrei haben)
- Race → RaceResult: 1:N (12 Ergebnisse, 6 je Team)
- Season + Driver → SeasonDriver → Team (Fahrer können nach einer Saison das Team wechseln)
- Driver → RaceResult: 1:N

### Punktesystem

**Rennpunkte** (Position 1–12): 20, 17, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2

**Qualifying-Bonus** (Top 3): 3, 2, 1

**Schnellste Rennrunde**: 2 Punkte

**Fahrer-Gesamtpunkte**: pointsRace + pointsQuali + pointsFl

**Teamwertung pro Duell**: Summe der Fahrerpunkte je Team → Team mit mehr Punkten gewinnt

**Tabellenpunkte**: Sieg = 3, Unentschieden = 1, Niederlage = 0

**Tiebreaker**: Punkteverhältnis (Summe erzielter Matchday-Punkte vs. kassierte, analog zum Torverhältnis)

---

## Projektstruktur

```
ctc-manager/
├── pom.xml
├── src/main/java/de/ctc/
│   ├── CtcApplication.java
│   ├── domain/
│   │   ├── model/                  # JPA Entities
│   │   ├── repository/             # Spring Data JPA Repositories
│   │   └── service/                # ScoringService, StandingsService, DriverRankingService
│   ├── admin/
│   │   ├── controller/             # SeasonController, MatchdayController, TeamController, DriverController, RaceController
│   │   └── dto/                    # Form- und Display-DTOs
│   ├── sitegen/
│   │   ├── SiteGeneratorService.java
│   │   ├── SiteGeneratorController.java
│   │   └── model/                  # View-Models für die statische Seite
│   └── dataimport/
│       ├── CsvImportService.java
│       ├── CsvImportController.java
│       └── ImageImportService.java # Späteres Feature: KI-Bildimport
├── src/main/resources/
│   ├── templates/
│   │   ├── admin/                  # Admin-UI Templates (layout, seasons, matchdays, teams, drivers, races)
│   │   └── site/                   # Public-Website Templates (layout, index, standings, matchday, driver-ranking, team-profile, driver-profile, season-archive)
│   ├── static/
│   │   ├── admin/css/              # Admin Styles
│   │   └── site/                   # Public Site Assets (CSS, JS, Bilder inkl. CTC-Logo)
│   ├── application.yml
│   ├── application-dev.yml         # H2 In-Memory
│   ├── application-local.yml       # MariaDB lokal
│   └── application-prod.yml        # Cloud DB
├── src/test/java/de/ctc/
│   ├── domain/service/             # JUnit + Mockito Unit Tests
│   ├── admin/controller/           # MockMvc Integration Tests
│   ├── sitegen/                    # Generator Output Tests (Jsoup)
│   └── e2e/                        # Playwright E2E Tests
└── .github/workflows/
    ├── ci.yml                      # Build + Test (Push & PR)
    └── deploy-site.yml             # Generierung + GitHub Pages (manuell)
```

---

## Spring Profiles

| Profil | Datenbank | Verwendung |
|---|---|---|
| `dev` | H2 In-Memory | Entwicklung, Tests, CI-Pipeline |
| `local` | MariaDB lokal | Produktiver lokaler Betrieb |
| `prod` | Cloud DB (konfigurierbar) | Perspektivisches Cloud-Deployment |

---

## Admin-UI

Thymeleaf-basierte serverseitige Oberfläche mit Navigation:

**Saisons** → **Spieltage** → **Teams** → **Fahrer** → **Rennen** → **Seite generieren**

Funktionen:
- CRUD für alle Entities (Saisons, Spieltage, Teams, Fahrer, Rennen)
- Ergebniseingabe pro Rennen: Position, Quali-Position, Fastest-Lap-Checkbox für jeden der 12 Fahrer
- Automatische Punkteberechnung beim Speichern
- Tabellen- und Fahrerranking-Vorschau
- Button "Seite generieren" triggert den SiteGeneratorService
- CSV-Import: Upload-Formular für historische Ergebnisse mit Auto-Anlage von Fahrern/SeasonDriver

---

## Statische Webseite

### Design

- **Dark Theme**: Schwarz (#0a0a0a), Weiß (#ffffff), Grau-Abstufungen
- **CTC-Logo**: Originales Blitz-im-Kreis-Logo (SVG/PNG), keine Nachbildungen
- **Schrift**: Modern, technisch, Racing-Ästhetik (kantige Sans-Serif)
- **Layout**: Clean, datenorientiert, Tabellen und Duell-Karten

### Seiten

| Seite | URL | Inhalt |
|---|---|---|
| Startseite | index.html | Tabelle + letzter Spieltag |
| Teamtabelle | standings.html | Vollständige Tabelle (S/U/N/PV/Pkt) |
| Spieltag-Detail | matchday/{label}.html | Alle Paarungen + Einzelergebnisse |
| Fahrerwertung | driver-ranking.html | Individuelle Fahrer-Rangliste über die Saison |
| Teamprofil | team/{shortName}.html | Roster, Ergebnisse, Statistiken |
| Fahrerprofil | driver/{psnId}.html | Rennhistorie, Punkte, Teams |
| Saisonarchiv | archive/{season}.html | Endstand und Ergebnisse vergangener Saisons |

### Generierung

Der `SiteGeneratorService` nutzt `SpringTemplateEngine` programmatisch:
1. Baut für jede Seite einen Thymeleaf-Context mit den nötigen Daten (über eigene View-Models)
2. Rendert das Template zu einem HTML-String
3. Schreibt die HTML-Datei in ein Output-Verzeichnis
4. Kopiert statische Assets (CSS, JS, Bilder) in den Output

Trigger: Admin-Button oder REST-Endpoint (`POST /admin/generate`)

---

## Datenimport

Es existieren 4+ historische Saisons, die importiert werden müssen. Die Daten liegen in zwei Formaten vor:

### Datenquellen

1. **Google Spreadsheets** — enthalten die Detaildaten (PSN-ID, Position, Quali-Position, Fastest Lap pro Fahrer)
2. **Grafiken** — Settings-Grafiken (Auto, Strecke, Renneinstellungen) und Scorecards (Gesamtpunkte pro Fahrer)

### Phase 1: CSV-Import (Kernfunktion)

CSV-Upload über die Admin-UI mit folgender Logik:

- **PSN-ID als primärer Schlüssel** für Fahrer
- **Mehrstufige Fahrer-Suche mit Fuzzy-Logik**:
  1. Exakter Match auf PSN-ID → sofort zugeordnet
  2. Case-insensitive Match auf PSN-ID → "AHR_Hills_93" findet "ahr_hills_93"
  3. Fuzzy Match (Levenshtein-Distanz) auf PSN-ID und Nickname → "AHR_Hils_93" findet "AHR_Hills_93"
  4. Kein Match → Treffer-Vorschläge zur manuellen Bestätigung oder Neu-Anlage
- **Schwellenwert für Fuzzy Match**: Nur Treffer mit ausreichend hoher Ähnlichkeit vorschlagen (z.B. ≥ 80%), um falsche Zuordnungen zu vermeiden
- **Import-Vorschau**: Alle Fuzzy-Matches werden vor dem Speichern zur Bestätigung angezeigt; exakte Matches werden automatisch zugeordnet
- **Auto-Anlage neuer Fahrer**: Wird bestätigt, dass kein bestehender Fahrer passt → Fahrer wird automatisch angelegt (PSN-ID als vorläufiger Nickname, nachträglich pflegbar)
- **Auto-Anlage SeasonDriver**: Existiert die Zuordnung Fahrer ↔ Team ↔ Saison noch nicht → wird automatisch erstellt
- **Erwartetes CSV-Format**: Orientiert sich am bestehenden Spreadsheet-Format (Team, PSN-ID, Position, Quali, FL)
- Saison, Spieltag, Strecke und Auto können als Metadaten beim Upload angegeben werden

### Phase 2: KI-gestützter Bildimport (späteres Feature)

- Upload von Settings- und Scorecard-Grafiken über die Admin-UI
- KI-Vision-API (z.B. Claude) extrahiert strukturierte Daten aus den Bildern
- Erkannte Daten werden zur Bestätigung angezeigt bevor sie importiert werden
- Gleiche Auto-Anlage-Logik für Fahrer und SeasonDriver wie beim CSV-Import

---

## CI/CD

### ci.yml (Push & Pull Request)

1. Checkout + JDK 25 Setup
2. `mvn verify` — Kompilieren, Unit Tests, Integration Tests
3. Playwright E2E Tests (mit H2-Profil)
4. Test-Report als Artifact

### deploy-site.yml (Manuell via workflow_dispatch)

1. Checkout + JDK 25 Setup
2. `mvn package -Pgenerate` — App starten, Seiten generieren
3. Generierte HTML-Dateien als Artifact
4. Deploy auf GitHub Pages

---

## Teststrategie

| Ebene | Werkzeug | Was wird getestet? |
|---|---|---|
| Unit Tests | JUnit 5 + Mockito | ScoringService, StandingsService, DriverRankingService — Geschäftslogik isoliert |
| Integration Tests | Spring MockMvc + H2 | Admin CRUD-Controller, Ergebniseingabe mit automatischer Punkteberechnung |
| Generator Tests | JUnit 5 + Jsoup | Alle HTML-Dateien erzeugt, korrekte Daten, funktionierende Links |
| E2E Tests | Playwright (Java) | Kompletter Admin-Workflow: Saison anlegen → Ergebnis eingeben → Seite generieren |

---

## Tech Stack

| Komponente | Technologie |
|---|---|
| Sprache | Java 25 |
| Framework | Spring Boot 4.x |
| Build | Maven |
| Admin-UI | Thymeleaf |
| Datenbank | MariaDB (local/prod), H2 (dev) |
| DB-Migration | Flyway |
| Unit Tests | JUnit 5, Mockito |
| E2E Tests | Playwright (Java) |
| HTML-Validierung | Jsoup |
| CI/CD | GitHub Actions |
| Hosting | GitHub Pages (statische Seite) |

---

## Verifizierung

Um die Änderungen end-to-end zu testen:

1. **Dev-Profil starten**: `mvn spring-boot:run -Dspring-boot.run.profiles=dev` — App mit H2 starten
2. **Admin-UI testen**: Saison anlegen → Spieltag erstellen → Teams/Fahrer zuweisen → Rennergebnis eingeben
3. **Punkteberechnung prüfen**: Ergebnis speichern, Punkte sollten automatisch berechnet werden
4. **Tabelle prüfen**: Teamtabelle und Fahrerwertung in der Admin-Vorschau korrekt
5. **Seite generieren**: "Seite generieren"-Button klicken, Output-Verzeichnis prüfen
6. **Statische Seite lokal anschauen**: Generierte HTML-Dateien im Browser öffnen
7. **Tests ausführen**: `mvn verify` — alle Unit, Integration und Generator Tests grün
8. **E2E Tests**: `mvn verify -Pe2e` — Playwright Tests gegen laufende App
9. **Local-Profil**: `mvn spring-boot:run -Dspring-boot.run.profiles=local` — mit MariaDB testen
