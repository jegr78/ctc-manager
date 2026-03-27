# Detail/Readonly Views für Admin-UI

## Kontext

Die Admin-UI hat aktuell nur Listen- und Edit-Seiten. Es fehlen Readonly-Detail-Seiten, die einen Überblick über eine Entity mit all ihren Beziehungen geben, ohne direkt im Bearbeitungsmodus zu sein. Detail-Seiten dienen als zentraler Hub für die Navigation zwischen verknüpften Entities.

## Entscheidungen

- **Ansatz:** Eigene Detail-Templates pro Entity (kein Readonly-Modus auf bestehenden Forms)
- **Navigationsfluss:** Liste → Detail (Klick auf Name) → Edit (Button). Edit-Shortcut in der Liste bleibt erhalten.
- **Detail-Seiten:** Reine Anzeige — alle Aktionen (Add/Remove/Save) bleiben im Edit-Formular
- **Cross-Navigation:** Alle verknüpften Entities sind Links zu deren Detail-Seiten
- **Link-Styling:** Subtiler dashed Underline, wird beim Hover farbig (Akzentfarbe) — kein Standard-Link-Look

## Layout-Muster (alle Detail-Seiten)

Jede Detail-Seite folgt demselben Aufbau:

1. **Toolbar:** Back-Link zur Liste + Entity-Titel + Edit-Button + Delete-Button
2. **Stammdaten:** Key-Value Grid (Label links grau/uppercase, Wert rechts)
3. **Zugehörige Daten:** Sections mit Überschrift + Anzahl, darunter Tabelle mit verlinkten Einträgen

### CSS: Detail-Link-Klasse

```css
.detail-link {
    color: var(--text);
    text-decoration: none;
    border-bottom: 1px dashed rgba(255, 255, 255, 0.25);
    transition: color 0.15s, border-color 0.15s;
}
.detail-link:hover {
    color: var(--accent);
    border-bottom-color: var(--accent);
}
```

### CSS: Stammdaten-Grid

```css
.detail-fields {
    display: grid;
    grid-template-columns: 160px 1fr;
    gap: 12px 24px;
    margin-bottom: 32px;
}
.detail-fields .label {
    color: rgba(255, 255, 255, 0.5);
    font-size: 13px;
    text-transform: uppercase;
}
```

## Detail-Seiten pro Entity

### 1. Season Detail

**Route:** `GET /admin/seasons/{id}`
**Template:** `season-detail.html`

**Stammdaten:**
- Name
- Start Date
- End Date
- Active Status (Badge: Active/Inactive)

**Zugehörige Daten:**
- **Teams** — Tabelle: Short Name (Link → Team Detail), Name, Type (Parent/Sub Badge)
- **Matchdays** — Tabelle: #, Label (Link → Matchday Detail), Date, Races (Anzahl)
- **Playoffs** — Tabelle: Name (Link → Playoff Bracket), Teams, Rounds

### 2. Team Detail

**Route:** `GET /admin/teams/{id}`
**Template:** `team-detail.html`

**Stammdaten:**
- Name
- Short Name
- Logo (Bild oder URL, falls vorhanden)
- Parent Team (Link → Team Detail, falls Sub-Team)

**Zugehörige Daten:**
- **Sub-Teams** — Tabelle: Short Name (Link → Team Detail), Name (nur wenn Parent mit Sub-Teams)
- **Seasons** — Tabelle: Name (Link → Season Detail), in denen das Team spielt
- **Drivers** — Tabelle: PSN-ID (Link → Driver Detail), Name, Season (via SeasonDriver)

### 3. Driver Detail

**Route:** `GET /admin/drivers/{id}`
**Template:** `driver-detail.html`

**Stammdaten:**
- PSN-ID
- Name

**Zugehörige Daten:**
- **Season-Zuordnungen** — Tabelle: Season (Link → Season Detail), Team (Link → Team Detail)

### 4. Matchday Detail

**Route:** `GET /admin/matchdays/{id}`
**Template:** `matchday-detail.html`

**Stammdaten:**
- Label
- Sort Index
- Date
- Season (Link → Season Detail)

**Zugehörige Daten:**
- **Races** — Tabelle: Home Team (Link → Team Detail) vs Away Team (Link → Team Detail), Track, Score (falls Ergebnisse vorhanden)
- **Lineup** — Gruppiert nach Parent-Team: Fahrer (Link → Driver Detail) → Sub-Team (Link → Team Detail)

### 5. Race Detail

**Route:** `GET /admin/races/{id}`
**Template:** `race-detail.html`

**Stammdaten:**
- Matchday (Link → Matchday Detail)
- Home Team (Link → Team Detail)
- Away Team (Link → Team Detail)
- Track
- Car
- Playoff Matchup (Link → Matchup Detail, falls Playoff-Race)

**Zugehörige Daten:**
- **Team Scores** — Zusammenfassung: Home Total vs Away Total (mit Win/Loss/Draw Styling)
- **Results** — Tabelle: Position, Driver (Link → Driver Detail), Team, Quali Position, Fastest Lap, Points Total — gruppiert nach Home/Away Team

### 6. Playoff (bestehende Seiten erweitern)

**Keine neuen Templates nötig.** Ergänzungen an bestehenden Seiten:

- **playoff-bracket.html:** Team-Namen in Matchups als Links → Team Detail
- **playoff-matchup.html:** Team-Namen als Links → Team Detail, Leg-Races als Links → Race Detail

## Änderungen an bestehenden Seiten

### Listen-Templates

Alle 5 Listen-Seiten (seasons, teams, drivers, matchdays, races) werden angepasst:

- Name/Label-Spalte wird zum Link auf die Detail-Seite (`detail-link` Klasse)
- Edit-Button in der Actions-Spalte bleibt als Shortcut
- Delete-Button bleibt

### Controller-Änderungen

Jeder der 5 Haupt-Controller (Season, Team, Driver, Matchday, Race) bekommt eine neue `GET /{id}` Methode, die das Detail-Template mit der Entity und ihren Beziehungen befüllt.

Der PlayoffController bekommt keine neue Route — die bestehende Bracket-Seite wird um Links ergänzt.

## Neue Dateien

| Datei | Beschreibung |
|-------|-------------|
| `templates/admin/season-detail.html` | Season Detail-Seite |
| `templates/admin/team-detail.html` | Team Detail-Seite |
| `templates/admin/driver-detail.html` | Driver Detail-Seite |
| `templates/admin/matchday-detail.html` | Matchday Detail-Seite |
| `templates/admin/race-detail.html` | Race Detail-Seite |

## Geänderte Dateien

| Datei | Änderung |
|-------|----------|
| `admin.css` | Detail-Link, Detail-Fields, Detail-Section Klassen |
| `templates/admin/seasons.html` | Name als Link zu Detail |
| `templates/admin/teams.html` | Name als Link zu Detail |
| `templates/admin/drivers.html` | PSN-ID als Link zu Detail |
| `templates/admin/matchdays.html` | Label als Link zu Detail |
| `templates/admin/races.html` | Home vs Away als Link zu Detail (z.B. "CLR 1 vs TNR A") |
| `templates/admin/playoff-bracket.html` | Team-Namen als Links |
| `templates/admin/playoff-matchup.html` | Team-/Race-Links ergänzen |
| `SeasonController.java` | `GET /{id}` Detail-Methode |
| `TeamController.java` | `GET /{id}` Detail-Methode |
| `DriverController.java` | `GET /{id}` Detail-Methode |
| `MatchdayController.java` | `GET /{id}` Detail-Methode |
| `RaceController.java` | `GET /{id}` Detail-Methode |

## Tests

- Integration-Tests pro Controller: `GET /{id}` gibt 200 + korrektes View-Name + Model-Attribute zurück
- Bestehende Tests bleiben unverändert (keine Breaking Changes)
