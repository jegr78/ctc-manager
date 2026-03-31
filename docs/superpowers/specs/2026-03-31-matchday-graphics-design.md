# Matchday Graphics — Design Spec

## Context

Die Liga erstellt für jeden Matchday drei Grafiken (bisher manuell extern):
1. **Overview** — Übersicht aller Begegnungen mit Team-Logos, Farben, Seed-Nummern und Record
2. **Schedule** — Zeitplan mit Datum/Uhrzeit jeder Begegnung
3. **Results** — Ergebnisse mit Gesamtpunkten pro Match

Diese Grafiken sollen aus der Admin-UI heraus on-the-fly generiert und als PNG heruntergeladen werden können. Die HTML-Templates sollen über den bestehenden Template-Editor bearbeitbar sein.

## Entscheidungen

| Frage | Entscheidung |
|-------|-------------|
| UI-Ort | Matchday-Detailseite (`/admin/matchdays/{id}`) |
| Download-Flow | Button → direkter PNG-Download (kein Modal, kein ZIP) |
| Persistenz | Keine — on-the-fly generiert, temporäre Dateien gelöscht |
| Dimensionen | 1920×1080 (Landscape) für alle drei |
| Zeitzone (Schedule) | `Europe/London` (automatisch GMT/BST) |
| Record-Format | W-L-D (Wins-Losses-Draws) auf allen drei Grafiken |
| Seed-Nummer (Overview) | Aktuelle Tabellenposition aus StandingsService |
| Score (Results) | `Match.homeScore` / `Match.awayScore` |
| Template-Editor | 3 neue Tabs im bestehenden Editor (`/admin/tools/template-editors`) |

## Architektur

### Service-Hierarchie

```
AbstractGraphicService (bestehend)
├── TeamCardService
├── LineupGraphicService
├── ResultsGraphicService
├── SettingsGraphicService
└── AbstractMatchdayGraphicService (NEU)
    ├── MatchdayOverviewGraphicService (NEU)
    ├── MatchdayScheduleGraphicService (NEU)
    └── MatchdayResultsGraphicService (NEU)
```

### AbstractMatchdayGraphicService

Erbt von `AbstractGraphicService`. Stellt gemeinsame Logik für alle drei Matchday-Grafiken bereit.

**Gemeinsame Datenaufbereitung (`prepareBaseContext(Matchday)`):**
- Alle Matches des Matchday laden (Bye-Matches ausschließen)
- Pro Match: Home/Away Team mit Name, ShortName, Farben (primaryColor, secondaryColor, accentColor)
- Team-Logos als Base64 encodieren (analog zu bestehenden Services)
- Season-Record (W-L-D) pro Team via `StandingsService.calculateStandings()`
- Tabellenposition (Seed) pro Team aus Standings
- CTC-Logo + Conthrax-Font als Base64 (via `encodeClasspathResource()`)
- Matchday-Label, Season-Name, Season-Year

**Template-Management (Template Method Pattern):**
- `loadTemplate()` / `saveTemplate(String)` / `resetTemplate()` / `hasCustomTemplate()`
- Abstrakte Methoden für Subklassen: `getTemplateFileName()`, `getDefaultTemplatePath()`
- Custom-Templates unter `{uploadDir}/matchday-overview-template.html` etc.

### MatchdayOverviewGraphicService

**Zusätzliche Template-Variablen:**
- Keine — Basis-Context reicht (Seed-Nummern, Teams, Logos, Farben, Record W-L-D)

**Template:** `matchday-overview-render.html`
**Custom:** `{uploadDir}/matchday-overview-template.html`

### MatchdayScheduleGraphicService

**Zusätzliche Template-Variablen:**
- `scheduledMatches` — Liste der Matches sortiert nach dem frühesten `Race.dateTime` pro Match, mit formatiertem Datum/Uhrzeit
- Datumsformat: `"EEE, dd MMM. HH:mm z"` (z.B. "Fri, 20 Mar. 19:30 GMT") mit `ZoneId.of("Europe/London")`
- Bei Multi-Leg-Matches: das früheste Race.dateTime bestimmt Position und Anzeige
- Matches ohne `dateTime` am Ende der Liste

**Template:** `matchday-schedule-render.html`
**Custom:** `{uploadDir}/matchday-schedule-template.html`

### MatchdayResultsGraphicService

**Zusätzliche Template-Variablen:**
- `matchResults` — Liste der Matches mit `homeScore` / `awayScore`
- Sortierung nach `Race.dateTime`

**Template:** `matchday-results-render.html`
**Custom:** `{uploadDir}/matchday-results-template.html`

## Datenmodell für Templates

Alle drei Templates erhalten einen gemeinsamen Context. Die konkreten Services ergänzen grafik-spezifische Daten.

### MatchdayGraphicData (DTO für Template-Context)

```java
record MatchdayGraphicData(
    String matchdayLabel,       // "Match Day 3"
    String seasonName,          // "Community Team Cup 2026"
    String seasonYear,          // "2026"
    String ctcLogoBase64,
    String fontBase64,
    List<MatchGraphicRow> matches
)

record MatchGraphicRow(
    String homeTeamName,        // "Community League Racing 1"
    String homeTeamShortName,   // "CLR1"
    String homeLogoBase64,
    String homePrimaryColor,
    String homeSecondaryColor,
    String homeAccentColor,
    int homeSeed,               // Tabellenposition
    String homeRecord,          // "2-1-0" (W-L-D)
    String awayTeamName,
    String awayTeamShortName,
    String awayLogoBase64,
    String awayPrimaryColor,
    String awaySecondaryColor,
    String awayAccentColor,
    int awaySeed,
    String awayRecord,
    // Schedule-spezifisch:
    String scheduledDateTime,   // "Fri, 20 Mar. 19:30 GMT" (null wenn kein Datum)
    // Results-spezifisch:
    Integer homeScore,          // Match.homeScore (null wenn kein Ergebnis)
    Integer awayScore           // Match.awayScore (null wenn kein Ergebnis)
)
```

## Admin-UI: Matchday-Detailseite

### Neuer "Graphics"-Bereich

Position: Unterhalb der bestehenden Matches-Liste auf `/admin/matchdays/{id}`.

**3 Download-Buttons:**
- **Overview** — `POST /admin/matchdays/{id}/download-overview`
- **Schedule** — `POST /admin/matchdays/{id}/download-schedule`
- **Results** — `POST /admin/matchdays/{id}/download-results`

**Validierung & Button-Status:**

| Grafik | Bedingung aktiv | Bedingung disabled/Warnung |
|--------|----------------|---------------------------|
| Overview | ≥1 Match ohne Bye | Keine Matches |
| Schedule | ≥1 Match mit Race.dateTime | Warnung wenn nicht alle Matches Datum haben |
| Results | ≥1 Match mit homeScore/awayScore | Keine Ergebnisse vorhanden |

**Hinweistext:** "Graphics are generated on-the-fly and downloaded as PNG (1920×1080)."

### Controller-Endpoints

Neue Methoden im `MatchdayController`:

```
POST /admin/matchdays/{id}/download-overview  → ResponseEntity<byte[]> (PNG)
POST /admin/matchdays/{id}/download-schedule  → ResponseEntity<byte[]> (PNG)
POST /admin/matchdays/{id}/download-results   → ResponseEntity<byte[]> (PNG)
```

Response: `Content-Type: image/png`, `Content-Disposition: attachment; filename="matchday-3-overview.png"`.

Datenaufbereitung in einem neuen `MatchdayGraphicOrchestrationService` (analog zu `RaceManagementService`), der Standings berechnet, Daten sammelt und den passenden Graphic-Service aufruft.

## Template-Editor: 3 neue Tabs

Integration in den bestehenden Template-Editor (`/admin/tools/template-editors`).

**Neue Tabs:**
- Matchday Overview
- Matchday Schedule
- Matchday Results

**Neue Endpoints im `TemplateEditorController`:**
```
POST /admin/tools/template-editors/matchday-overview/save
POST /admin/tools/template-editors/matchday-overview/reset
POST /admin/tools/template-editors/matchday-schedule/save
POST /admin/tools/template-editors/matchday-schedule/reset
POST /admin/tools/template-editors/matchday-results/save
POST /admin/tools/template-editors/matchday-results/reset
```

Jeder Tab zeigt:
- Textarea mit aktuellem Template (Custom oder Default)
- Tabelle der verfügbaren Template-Variablen
- Tips mit Dimensionen und Hinweisen
- Save/Reset-Buttons
- Status-Badge: "Custom" oder "Default"

## HTML-Template Layout

Alle drei Templates: 1920×1080, schwarzer Hintergrund, Conthrax-Font.

### Overview (`matchday-overview-render.html`)

```
┌──────────────────────────────────────────┐
│  ⚡   Match Day 3 / 2026           ⚡   │  Header
├──────────────────────────────────────────┤
│ Seed│Logo Team (W-L-D) ▓▓│VS│▓▓ Team (W-L-D) Logo│Seed │  × 7 Rows
│  2  │CLR1              ▓▓│VS│▓▓              TNR-A│  1  │
│  3  │CLR2              ▓▓│VS│▓▓              AHR1 │  4  │
│ ... │                    │  │                     │ ... │
└──────────────────────────────────────────┘
```

Pro Zeile: Seed | Logo + Teamname + Record (W-L-D) mit Farbgradient | VS | Teamname + Record (W-L-D) + Logo mit Farbgradient | Seed

### Schedule (`matchday-schedule-render.html`)

```
┌──────────────────────────────────────────┐
│  ⚡  Match Day 2 Schedule            ⚡  │  Header
│      Community Team Cup 2026             │
├──────────────────────────────────────────┤
│ Wed, 18 Mar. 19:00 GMT │Logo TNR-B (0-1-0)│VS│(0-1-0) TNR-C Logo│
│ Fri, 20 Mar. 19:30 GMT │Logo DTR  (0-1-0) │VS│(0-1-0) MRL   Logo│
│ ...                                      │
└──────────────────────────────────────────┘
```

Sortiert nach Datum/Uhrzeit. Matches ohne Datum am Ende.

### Results (`matchday-results-render.html`)

```
┌──────────────────────────────────────────────┐
│  ⚡  Match Day 2 Results                 ⚡  │  Header
│      Community Team Cup 2026                 │
├──────────────────────────────────────────────┤
│ Logo TNR-B (0-1-0)│VS│(0-1-0) TNR-C Logo│ 54 : 62 │
│ Logo DTR  (0-1-0) │VS│(0-1-0) MRL   Logo│ 66 : 50 │
│ ...                                      │         │
└──────────────────────────────────────────────┘
```

Sortiert nach Datum/Uhrzeit. Score rechts als prominente Zahl.

## Bestehende Dateien die geändert werden

| Datei | Änderung |
|-------|---------|
| `MatchdayController.java` | 3 neue Download-Endpoints + Model-Attribute für Button-Status |
| `matchday-detail.html` | Neuer "Graphics"-Bereich mit Download-Buttons |
| `TemplateEditorController.java` | 6 neue Endpoints (save/reset × 3 Templates) + Model-Daten |
| `template-editors.html` | 3 neue Tabs |

## Neue Dateien

| Datei | Beschreibung |
|-------|-------------|
| `AbstractMatchdayGraphicService.java` | Basis-Service mit gemeinsamer Datenaufbereitung |
| `MatchdayOverviewGraphicService.java` | Overview-Grafik |
| `MatchdayScheduleGraphicService.java` | Schedule-Grafik |
| `MatchdayResultsGraphicService.java` | Results-Grafik |
| `MatchdayGraphicData.java` | DTOs (MatchdayGraphicData, MatchGraphicRow) |
| `matchday-overview-render.html` | Default-Template Overview |
| `matchday-schedule-render.html` | Default-Template Schedule |
| `matchday-results-render.html` | Default-Template Results |

## Verifikation

1. **Unit Tests:** AbstractMatchdayGraphicService (Datenaufbereitung, Record-Berechnung, Sortierung)
2. **Unit Tests:** Jeder konkrete Service (Template-Variablen, Validierung)
3. **Integration Tests:** Controller-Endpoints (Download-Response, Content-Type, Validierung)
4. **Manuell:** Dev-Server starten (`./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`), Matchday-Detailseite öffnen, alle 3 Grafiken herunterladen und visuell prüfen
5. **Manuell:** Template-Editor → Templates bearbeiten → Grafik neu generieren → Änderung sichtbar
6. **Playwright-CLI:** Visuell prüfen dass Grafiken korrekt aussehen
