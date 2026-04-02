# Power Rankings Grafik — Design Spec

## Kontext

Die CTC-Liga veröffentlicht nach jedem Spieltag ein Power Ranking der Teams als Social-Media-Grafik. Bisher wird diese manuell erstellt. Dieses Feature automatisiert die Generierung: Der Admin wählt eine Season Number, ordnet Teams per Drag & Drop, gibt einen Subtitle ein und lädt die fertige PNG-Grafik herunter.

## Überblick

- **Admin-Seite** unter `/admin/tools/power-rankings` (Sidebar: Tools-Sektion, unterhalb von Standings)
- **Grafik-Service** `PowerRankingsGraphicService extends AbstractGraphicService` (wie OverlayGraphicService)
- **Custom Template Support** mit neuem Tab in Template Editors
- **Grafik-Format:** 1920×1080px PNG, zwei Spalten, Matchday-Overview-Styling

## Admin-Seite

### URL & Navigation

- Route: `GET /admin/tools/power-rankings`
- Sidebar: Neuer Eintrag "Power Rankings" in Tools-Gruppe, zwischen Standings und Import
- Seitentitel: "Power Rankings"

### UI-Elemente

**1. Season Number Dropdown**
- Label: "Season Number"
- Optionen: Gruppiert nach `(year, number)` — zeigt z.B. "Season 4 (2026) — 14 Teams"
- Hinweistext darunter: "Groups seasons with same number (e.g. Group A + B)"
- Bei Auswahl: Lädt Teams aus allen Seasons mit dieser `(year, number)` Kombination per `onchange` Submit
- Query: `SeasonRepository.findByYearAndNumber(year, number)` (neuer Repository-Query)

**2. Subtitle Freitext**
- Label: "Subtitle (free text)"
- Placeholder: "e.g. Match Day 2, After Playoffs..."
- Wird als zweite Zeile im Grafik-Header verwendet

**3. Team Ranking Liste (Drag & Drop)**
- Erscheint nach Season-Auswahl
- Jede Zeile zeigt: Rang-Nummer, Drag-Handle (☰), Team-Logo (klein), Team-Name, Rating
- **Initiale Sortierung:** Nach `SeasonTeam.rating` absteigend; Teams ohne Rating ans Ende (alphabetisch nach shortName)
- **Drag & Drop:** HTML5 Drag & Drop API (kein externes JS-Framework nötig)
- Rang-Nummern aktualisieren sich automatisch nach Umordnung

**4. Download Button**
- Label: "⬇ Download PNG"
- POST auf `/admin/tools/power-rankings/download`
- Sendet: `seasonYear`, `seasonNumber`, `subtitle`, `teamIds[]` (in Ranking-Reihenfolge)
- Response: `image/png` mit `Content-Disposition: attachment; filename="Power Rankings.png"`

### Team-Filterung

Aus den gesammelten SeasonTeams aller Seasons mit gleicher `(year, number)`:
- **Einschließen:** Standalone-Teams (kein parentTeam, keine subTeams in der Saison) und Sub-Teams (`team.isSubTeam() == true`)
- **Ausschließen:** Parent-Teams, deren Sub-Teams ebenfalls in den Seasons vorhanden sind
- **Deduplizierung:** Wenn dasselbe Team in mehreren Seasons vorkommt (z.B. Gruppe A + B), nur einmal listen

### Logik zur Deduplizierung

Teams werden über `team.getId()` dedupliziert. Für SeasonTeam-Properties (Farben, Logo) wird der erste gefundene SeasonTeam-Eintrag verwendet (beliebige Season der Gruppe).

## Grafik-Template

### Layout (1920×1080px)

```
┌──────────────────────────────────────────────────────┐
│  [CTC]    Power Rankings 2026          [CTC]         │
│            Match Day 2                                │
├─────────────────────────┬────────────────────────────┤
│  1  ████ Team Name      │  8  ████ Team Name         │
│  2  ████ Team Name      │  9  ████ Team Name         │
│  3  ████ Team Name      │  10 ████ Team Name         │
│  4  ████ Team Name      │  11 ████ Team Name         │
│  5  ████ Team Name      │  12 ████ Team Name         │
│  6  ████ Team Name      │  13 ████ Team Name         │
│  7  ████ Team Name      │  14 ████ Team Name         │
└─────────────────────────┴────────────────────────────┘
```

### Header
- **Zeile 1:** "Power Rankings {year}" — Conthrax 44px, weight 900, letter-spacing 3px
- **Zeile 2:** Subtitle aus Freitext-Eingabe — Conthrax 28px, weight 700, color #ccc
- **CTC-Logos:** Links und rechts, 80×80px, fest eingebettet aus Classpath

### Team-Einträge
- **Styling identisch zum Matchday Overview Template:**
  - Gradient: `linear-gradient(90deg, color-mix(in srgb, [primaryColor] 70%, black) 0%, #222 100%)`
  - Logo: 64×64px Kreis mit `rgba(255,255,255,0.15)` Hintergrund, 6px Padding
  - Team-Name: Conthrax 24px, weight 700, weiß mit text-shadow
  - Rang-Nummer: 28px, weight 700, color #999
- **Zwei Spalten:** Teams werden hälftig aufgeteilt (ceil für links bei ungerader Anzahl)
- **Zeilenhöhe:** Dynamisch basierend auf Teamanzahl, max 100px, min 60px

### Template-Variablen

```
data.title          — "Power Rankings 2026"
data.subtitle       — Freitext (z.B. "Match Day 2")
data.ctcLogoBase64  — CTC Logo als data-URI
data.fontBase64     — Conthrax Font als data-URI
data.teams          — Liste von PowerRankingEntry:
  entry.rank            — Rang (1-basiert)
  entry.teamName        — Vollständiger Teamname
  entry.teamShortName   — Kurzname
  entry.logoBase64      — Team-Logo als data-URI
  entry.primaryColor    — Effektive Primärfarbe (hex)
  entry.secondaryColor  — Effektive Sekundärfarbe (hex)
  entry.accentColor     — Effektive Akzentfarbe (hex)
data.leftColumn     — Teams für linke Spalte
data.rightColumn    — Teams für rechte Spalte
```

## Service-Architektur

### PowerRankingsGraphicService

```
extends AbstractGraphicService
```

**Abhängigkeiten:**
- `TemplateEngine` — Thymeleaf Rendering
- `SeasonRepository` — Seasons laden
- `SeasonTeamRepository` — SeasonTeam-Daten (Farben, Logos, Ratings)

**Methoden:**
- `byte[] generateRankings(int year, int number, String subtitle, List<UUID> teamIds)` — Hauptmethode
- `List<RankedTeamData> loadTeamsForSeasonGroup(int year, int number)` — Teams laden, filtern, initial sortieren
- Template-Management: `loadTemplate()`, `loadDefaultTemplate()`, `saveTemplate()`, `resetTemplate()`, `hasCustomTemplate()` — analog zu OverlayGraphicService

**Datenfluss:**
1. Alle Seasons mit `(year, number)` laden
2. SeasonTeams aller dieser Seasons sammeln
3. Team-Filterung anwenden (Standalone + Sub-Teams, keine Parent-Teams mit Sub-Teams)
4. Deduplizieren nach `team.getId()`
5. Für die Grafik-Generierung: `teamIds`-Reihenfolge als Ranking verwenden
6. Für jeden Team-Eintrag: effective colors/logo aus SeasonTeam auflösen, Logo als Base64 kodieren
7. Teams in zwei Spalten aufteilen, Thymeleaf-Context befüllen, HTML rendern, Screenshot erstellen

### DTO

```java
public record PowerRankingsGraphicData(
    String title,
    String subtitle,
    String ctcLogoBase64,
    String fontBase64,
    List<PowerRankingEntry> teams,
    List<PowerRankingEntry> leftColumn,
    List<PowerRankingEntry> rightColumn
) {
    public record PowerRankingEntry(
        int rank,
        String teamName,
        String teamShortName,
        String logoBase64,
        String primaryColor,
        String secondaryColor,
        String accentColor
    ) {}
}
```

### RankedTeamData (Admin-Seite DTO)

```java
public record RankedTeamData(
    UUID teamId,
    String teamName,
    String teamShortName,
    String logoUrl,
    String primaryColor,
    Integer rating
) {}
```

## Controller

### PowerRankingsController

**Route:** `/admin/tools/power-rankings`

**Endpoints:**

| Method | Path | Beschreibung |
|--------|------|-------------|
| `GET /admin/tools/power-rankings` | Seite anzeigen, optional mit `?year=&number=` |
| `POST /admin/tools/power-rankings/download` | PNG generieren und als Download senden |

**GET — Seite laden:**
1. Alle Seasons laden, nach `(year, number)` gruppieren, als Dropdown-Optionen bereitstellen
2. Wenn `year` + `number` Parameter vorhanden: Teams laden, filtern, nach Rating sortieren
3. Model-Attribute: `seasonGroups`, `selectedYear`, `selectedNumber`, `teams`

**POST — Download:**
1. Parameter: `year` (int), `number` (int), `subtitle` (String), `teamIds` (List<UUID>)
2. `powerRankingsGraphicService.generateRankings(year, number, subtitle, teamIds)` aufrufen
3. Response: `ResponseEntity<byte[]>` mit Content-Type `image/png`

## Template Editor Integration

### Neuer Tab "Power Rankings"

- Tab-ID: `power-rankings`
- Position: Nach "Stream Overlay" (letzter Tab)
- Template-Dateiname: `power-rankings-template.html`
- Default-Template: `admin/power-rankings-render`
- Preview-Type im TemplatePreviewService: `"power-rankings"`

### Änderungen am TemplateEditorController

- `PowerRankingsGraphicService` als neue Dependency injecten
- Template laden/speichern/reset analog zu den anderen Tabs
- Endpoints: `POST .../power-rankings/save`, `POST .../power-rankings/reset`

### TemplatePreviewService

- Neuen Case `"power-rankings"` hinzufügen
- Preview mit Dummy-Daten (z.B. 6 Teams mit Beispiel-Farben)

## Sidebar-Änderung

In `layout.html` — neuer Link in der Tools-Gruppe:

```html
<a th:href="@{/admin/tools/power-rankings}" 
   th:classappend="${title.contains('Power Rankings') ? 'active' : ''}">Power Rankings</a>
```

Position: zwischen Standings und Import.

## Repository-Änderung

Neuer Query in `SeasonRepository`:

```java
List<Season> findByYearAndNumber(int year, int number);
```

## Dateien

### Neu erstellen
- `src/main/java/org/ctc/admin/service/PowerRankingsGraphicService.java`
- `src/main/java/org/ctc/admin/controller/PowerRankingsController.java`
- `src/main/java/org/ctc/admin/dto/PowerRankingsGraphicData.java`
- `src/main/java/org/ctc/admin/dto/RankedTeamData.java`
- `src/main/resources/templates/admin/power-rankings.html` (Admin-Seite)
- `src/main/resources/templates/admin/power-rankings-render.html` (Grafik-Template)

### Ändern
- `src/main/java/org/ctc/domain/repository/SeasonRepository.java` — neuer Query
- `src/main/resources/templates/admin/layout.html` — Sidebar-Link
- `src/main/java/org/ctc/admin/controller/TemplateEditorController.java` — neuer Tab
- `src/main/resources/templates/admin/template-editors.html` — neuer Tab
- `src/main/java/org/ctc/admin/service/TemplatePreviewService.java` — Preview-Support

## Verifikation

1. **Unit Tests:** PowerRankingsGraphicService — Team-Filterung (Parent/Sub/Standalone), Sortierung, Spaltenaufteilung
2. **Integration Tests:** Controller-Tests — Season-Auswahl, Download-Endpoint
3. **E2E Tests:** Playwright — Seite laden, Season wählen, Teams anzeigen, Drag & Drop, Download
4. **Visuelle Prüfung:** `playwright-cli` — Admin-Seite und generierte Grafik inspizieren
5. **Template Editor:** Power-Rankings-Tab laden, Preview, Save, Reset testen
