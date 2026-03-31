# Season: Strukturierte Felder (year, number, description)

## Context

Season hat bisher nur ein `name`-Feld, aus dem Grafik-Generatoren per Regex das Jahr extrahieren (`extractYear()`). Das ist fragil und erfordert ein bestimmtes Namens-Pattern. Durch strukturierte Felder können Grafik-Generatoren und UI-Komponenten die Informationen direkt aus der Season lesen, ohne Parse-Logik.

Zusätzlich ist `name` aktuell UNIQUE, was verhindert, dass mehrere Seasons denselben Namen tragen (z.B. "Regular Season"). Der UNIQUE-Constraint wird entfernt und `findByName()` durch ID-basierte Lookups ersetzt.

## Datenmodell

### Neue Felder auf `Season`

| Feld | Typ | DB-Spalte | Constraint | Beispiel |
|------|-----|-----------|-----------|----------|
| `year` | `int` | `year INT NOT NULL` | Pflicht | `2026` |
| `number` | `int` | `number INT NOT NULL` | Pflicht | `4` |
| `description` | `String` | `description VARCHAR(255)` | Optional | `"Group A"` |

### Änderung an `name`

- **UNIQUE-Constraint entfernen** — `name` bleibt NOT NULL, aber nicht mehr UNIQUE
- Mehrere Seasons dürfen denselben Namen tragen (z.B. "Regular Season")

### Schema-Änderung

Direkt in `V1__initial_schema.sql` anpassen (Schema noch nicht veröffentlicht):

```sql
CREATE TABLE seasons (
    ...
    name VARCHAR(255) NOT NULL,          -- UNIQUE entfernt!
    year INT NOT NULL,
    number INT NOT NULL,
    description VARCHAR(255),
    ...
);
```

### Convenience-Methode `getDisplayLabel()` auf Season

```java
public String getDisplayLabel() {
    return year + " | #" + number + " | " + name;
}
```

Beispiele:
- `"2026 | #4 | CTC Season 4"`
- `"2025 | #3 | Season 3 - Group A"`

Wird verwendet in allen **Dropdowns, Filter-Selects, Auswahllisten und Übersichtstabellen** im Admin-UI.

## Betroffene Komponenten

### 1. Entity — `Season.java`

- Neue Felder: `year` (int, `@Column(nullable = false)`), `number` (int, `@Column(nullable = false)`), `description` (String)
- **UNIQUE von `name` entfernen** (`@Column(nullable = false)` ohne `unique = true`)
- Neue Methode: `getDisplayLabel()`
- Konstruktor erweitern: `Season(String name, int year, int number)`

### 2. DTO — `SeasonForm.java`

- Neue Felder: `year` (int), `number` (int), `description` (String)

### 3. Controller — `SeasonController.java`

- Season↔SeasonForm Mapping um neue Felder erweitern

### 4. Season-Formular Template

- Eingabefelder für Year (Zahl), Number (Zahl), Description (Text, optional)

### 5. Admin-Templates — `season.name` → `season.displayLabel` in Auswahllisten

Überall wo Seasons in Auswahlen/Listen/Filtern angezeigt werden:

| Template | Stelle | Änderung |
|----------|--------|----------|
| `seasons.html` | Übersichtsliste (Name-Spalte) | `season.displayLabel` |
| `race-form.html` | Matchday-Dropdown | `md.season.displayLabel` |
| `matchdays.html` | Season-Spalte | `md.season.displayLabel` |
| `driver-detail.html` | Season Assignments | `sd.season.displayLabel` |
| `team-detail.html` | Season-Header | `group.season.displayLabel` / `s.displayLabel` |
| `race-detail.html` | Season-Link | `race.matchday.season.displayLabel` |
| `matchday-form.html` | Season-Anzeige | `matchday.season.displayLabel` |

**Site-Templates** (`site/`-Ordner) zeigen weiterhin `season.name` als Freitext.

### 6. Grafik-Generatoren

**ResultsGraphicService** + **LineupGraphicService**: `season.getYear()` statt `extractYear(season.getName())`:

```java
// Vorher
ctx.setVariable("seasonYear", extractYear(season.getName()));

// Nachher
ctx.setVariable("seasonYear", String.valueOf(season.getYear()));
```

**AbstractGraphicService**: `extractYear()` Methode und `YEAR_PATTERN` entfernen.

### 7. SiteGeneratorService

`slugify(season.getDisplayLabel())` statt `slugify(season.getName())`:

```java
// Vorher: "season-4-2026"
slugify(season.getName())

// Nachher: "2026-4-ctc-season-4"
slugify(season.getDisplayLabel())
```

### 8. findByName() → findById() Refactoring

`SeasonRepository.findByName()` wird entfernt. Alle Stellen, die bisher per Season-Name suchen, verwenden künftig die Season-ID:

| Stelle | Vorher | Nachher |
|--------|--------|---------|
| `CsvImportController` | `@RequestParam String seasonName` | `@RequestParam UUID seasonId` |
| `CsvImportService.executeImport()` | `findByName(metadata.seasonName())` | `findById(metadata.seasonId())` |
| `CsvImportService.ImportMetadata` | `seasonName` (String) | `seasonId` (UUID) |
| `MatchdayService.getMatchdaysBySeason()` | `findByName(seasonName)` | `findById(seasonId)` |
| `MatchdayService.createInline()` | `findByName(seasonName)` | `findById(seasonId)` |
| `MatchdayController` | `seasonName` Parameter | `seasonId` Parameter |
| `SeasonRepository` | `findByName()` entfernen | — |

Die Import-Formulare im UI übergeben bereits die Season-ID oder können einfach darauf umgestellt werden.

### 9. TestDataService

Season-Erstellung um year/number/description erweitern:

```java
// Vorher
createSeason("Season 4 - 2026", scorings);

// Nachher
createSeason("Season 4 - 2026", 2026, 4, null, scorings);
```

Analog für alle Test-Seasons (Season 1-4, Test-Seasons).

## Nicht betroffen

- **Logging** — weiterhin `season.getName()`
- **Site-Templates** — weiterhin `season.name`

## Verifikation

1. `./mvnw verify` — alle Unit- und Integrationstests grün
2. Dev-Server starten (`dev` Profil) → Admin-UI:
   - Season erstellen mit year/number/description → prüfen ob gespeichert
   - Season-Liste zeigt DisplayLabel
   - Alle Dropdowns (Race-Form, Matchday-Liste) zeigen DisplayLabel
   - CSV-Import funktioniert mit Season-ID statt Name
3. Grafik-Generierung testen (Lineup + Results) → seasonYear korrekt
4. SiteGenerator → Verzeichnisstruktur mit neuem Slug-Format prüfen
