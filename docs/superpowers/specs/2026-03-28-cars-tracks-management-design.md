# Cars & Tracks Management — Design Spec

## Ziel

Autos und Strecken als eigene Stammdaten verwalten, pro Saison einen Pool zuweisen und im Race-Formular ueber Searchable Dropdowns auswaehlen. Uniqueness-Regel: Jedes Auto und jede Strecke darf pro Saison nur einmal vom Heimteam verwendet werden.

## Scope

### In Scope

1. **Sidebar-Navigation** — Umbau von horizontaler Navbar auf vertikale Sidebar mit Gruppen (League, Master Data, Tools)
2. **Car Entity + CRUD** — Stammdatenverwaltung fuer Autos
3. **Track Entity + CRUD** — Stammdatenverwaltung fuer Strecken
4. **Saison-Pool-Zuordnung** — Dual-List Transfer-Picker auf Season-Edit-Seite
5. **Race-Formular-Integration** — FK-Referenzen statt Freitext, Searchable Dropdown, Uniqueness-Validierung
6. **DB-Migration** — V1 erweitern (kein Deployment vorhanden)

### Out of Scope (spaeter)

- GT7-Webseiten-Import (Scraping/Parsing fuer initiale Befuellung)
- Bilder/Logos fuer Autos und Strecken

## Datenmodell

### Car

Entity `org.ctc.domain.model.Car`:
- `id` — UUID, Primary Key
- `manufacturer` — String, required (z.B. "Mazda", "Porsche")
- `name` — String, required (z.B. "RX-Vision GT3 Concept")
- Unique Constraint auf `(manufacturer, name)`

### Track

Entity `org.ctc.domain.model.Track`:
- `id` — UUID, Primary Key
- `name` — String, required, unique (z.B. "Tsukuba Circuit")
- `country` — String, optional (z.B. "Japan")

### Saison-Pool (Many-to-Many)

- `season_cars` — Junction-Tabelle: `season_id` (FK) + `car_id` (FK), PK auf beide
- `season_tracks` — Junction-Tabelle: `season_id` (FK) + `track_id` (FK), PK auf beide

Ein Pool gilt fuer die gesamte Saison inklusive Playoff-Phase.

### Aenderung an Race

- `track` (VARCHAR 500) entfaellt → `track_id` (UUID, FK auf `tracks`, optional)
- `car` (VARCHAR 500) entfaellt → `car_id` (UUID, FK auf `cars`, optional)

### Uniqueness-Regel

Pro Saison darf ein Heimteam jedes Car und jeden Track nur einmal verwenden. Die Regel gilt einzeln — d.h. bei 10 Heimspielen braucht das Team 10 verschiedene Autos und 10 verschiedene Strecken.

Die Regel gilt nur fuer regulaere Saison-Races und Playoff-Races innerhalb derselben Saison. Bye-Races (ohne Gegner) sind ausgenommen, da dort kein Heimrecht relevant ist.

Bei Edit eines bestehenden Races wird das aktuell zugewiesene Car/Track nicht als "bereits verwendet" gezaehlt (Selbstreferenz ausschliessen).

Validierung erfolgt serverseitig im Controller/Service (kein DB-Constraint, da der Kontext "Heimteam + Saison" sich aus der Matchday-Beziehung ergibt). Im Race-Formular werden bereits verwendete Eintraege disabled angezeigt.

## Navigation: Sidebar-Layout

### Umbau

Die horizontale Navbar wird durch eine vertikale Sidebar ersetzt. Alle Links bleiben mit einem Klick erreichbar, sind aber logisch gruppiert.

### Gruppen

**League** — Seasons, Matchdays, Races, Playoffs

**Master Data** — Teams, Drivers, Cars, Tracks

**Tools** — Standings, Import, Generate Site

### Verhalten

- Sidebar ist immer sichtbar (kein Collapse)
- Aktiver Link wird hervorgehoben (cyan Akzent + linker Border)
- Gruppenkoepfe sind nicht klickbar, nur Labels (uppercase, kleinere Schrift)
- "Generate Site" als Button hervorgehoben (wie bisher)
- Logo + "CTC Admin" oben in der Sidebar

## Admin-Seiten

### Cars (`/admin/cars`)

**Liste** (`cars.html`):
- Tabelle: Manufacturer, Name, Actions (Edit, Delete)
- Sortierbar nach Manufacturer und Name
- Suchfeld zum Filtern
- Pagination (client-seitig, wie bei Drivers)

**Formular** (`car-form.html`, `/admin/cars/new`, `/admin/cars/{id}/edit`):
- Manufacturer (Text, required, Placeholder "e.g. Mazda")
- Name (Text, required, Placeholder "e.g. RX-Vision GT3 Concept")
- Standard form-row Layout (2 Spalten)

**Loeschen**: Nur moeglich wenn Car in keinem Saison-Pool und keinem Race referenziert. Sonst Fehlermeldung mit Hinweis wo es verwendet wird.

### Tracks (`/admin/tracks`)

**Liste** (`tracks.html`):
- Tabelle: Name, Country, Actions
- Sortierbar, Suchfeld, Pagination

**Formular** (`track-form.html`, `/admin/tracks/new`, `/admin/tracks/{id}/edit`):
- Name (Text, required)
- Country (Text, optional, Placeholder "e.g. Japan")

**Loeschen**: Gleiche Schutzlogik wie bei Cars.

## Saison-Pool-Zuordnung

### Platzierung

Auf der Season-Edit-Seite (`/admin/seasons/{id}/edit`), unterhalb der bestehenden Team-Zuordnung. Zwei neue Sektionen:

1. **"Car Pool"** — Dual-List Transfer-Picker
2. **"Track Pool"** — Dual-List Transfer-Picker

Nur sichtbar bei bestehenden Seasons (nicht bei "New Season").

### Dual-List Transfer-Picker

Zwei Listen nebeneinander:

- **Links: "Available"** — Alle Cars/Tracks die noch nicht zugewiesen sind. Mit Suchfeld zum Filtern. Bei Cars zusaetzlich Hersteller-Filter (Dropdown).
- **Rechts: "Assigned"** — Aktuell zugewiesene Cars/Tracks. Mit Filter-Feld.
- **Mitte: Buttons** — `→` (hinzufuegen), `←` (entfernen), `All →` (alle hinzufuegen)

Aenderungen werden per POST gespeichert (eigene Endpoints, nicht Teil des Season-Formulars).

### Endpoints

- `POST /admin/seasons/{id}/cars/add` — Car IDs zum Pool hinzufuegen
- `POST /admin/seasons/{id}/cars/remove` — Car IDs aus Pool entfernen
- `POST /admin/seasons/{id}/tracks/add` — Track IDs hinzufuegen
- `POST /admin/seasons/{id}/tracks/remove` — Track IDs entfernen

## Race-Formular-Integration

### Searchable Dropdown (Autocomplete)

Die bisherigen Text-Inputs fuer Track und Car werden durch Searchable Dropdowns ersetzt:

- Textfeld mit Autocomplete-Dropdown
- Tippen filtert die Liste sofort (ueber Manufacturer + Name bei Cars, ueber Name bei Tracks)
- Dropdown zeigt Eintraege im Format `"Manufacturer — Name"` (Cars) bzw. `"Name"` (Tracks)
- Bereits vom Heimteam in dieser Saison verwendete Eintraege werden ausgegraut und sind nicht waehlbar
- Auswahl setzt einen Hidden-Input mit der UUID

### Info-Box

Unterhalb der Car/Track-Felder eine Info-Box:
- Ueberschrift: "Already used by {TeamShortName} this season"
- Liste der bereits verwendeten Autos und Strecken mit Spieltag-Referenz
- Nur sichtbar wenn es bereits verwendete Eintraege gibt

### Einschraenkung auf Saison-Pool

Die Auswahl ist strikt auf den Saison-Pool beschraenkt. Wenn kein Pool zugewiesen ist, sind die Felder leer (keine Auswahl moeglich). Hinweistext: "No cars/tracks assigned to this season yet."

### Serverseitige Validierung

Im `RaceController.save()`:
1. Pruefen ob Car/Track im Saison-Pool ist
2. Pruefen ob Heimteam dieses Car/Track in dieser Saison noch nicht verwendet hat (andere Races mit gleichem Heimteam + Saison + Car/Track)
3. Bei Verstoss: Zurueck zum Formular mit Fehlermeldung

### Dynamisches Nachladen

Wenn im Race-Formular das Heimteam gewechselt wird, muss die "already used" Info aktualisiert werden. Dies geschieht per JavaScript: Bei Aenderung des Home-Team-Selects wird ein JSON-Endpoint aufgerufen der die bereits verwendeten Car/Track-IDs fuer dieses Team in dieser Saison liefert.

Endpoint: `GET /admin/races/used-selections?seasonId={id}&homeTeamId={id}` → JSON mit `usedCarIds[]` und `usedTrackIds[]`

## DB-Migration

Da kein Deployment existiert, wird V1 direkt erweitert:

### Neue Tabellen in V1

```sql
CREATE TABLE cars (
    id UUID PRIMARY KEY,
    manufacturer VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    CONSTRAINT uq_car UNIQUE (manufacturer, name)
);

CREATE TABLE tracks (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    country VARCHAR(100)
);

CREATE TABLE season_cars (
    season_id UUID NOT NULL,
    car_id UUID NOT NULL,
    PRIMARY KEY (season_id, car_id),
    CONSTRAINT fk_sc_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    CONSTRAINT fk_sc_car FOREIGN KEY (car_id) REFERENCES cars(id) ON DELETE CASCADE
);

CREATE TABLE season_tracks (
    season_id UUID NOT NULL,
    track_id UUID NOT NULL,
    PRIMARY KEY (season_id, track_id),
    CONSTRAINT fk_st_season FOREIGN KEY (season_id) REFERENCES seasons(id) ON DELETE CASCADE,
    CONSTRAINT fk_st_track FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);
```

### Aenderung an races-Tabelle in V1

```sql
-- Ersetze:
--   track VARCHAR(500),
--   car VARCHAR(500),
-- Durch:
    track_id UUID,
    car_id UUID,
    CONSTRAINT fk_race_track FOREIGN KEY (track_id) REFERENCES tracks(id),
    CONSTRAINT fk_race_car FOREIGN KEY (car_id) REFERENCES cars(id),
```

## Betroffene Dateien

### Neu

| Datei | Beschreibung |
|-------|-------------|
| `domain/model/Car.java` | Entity |
| `domain/model/Track.java` | Entity |
| `domain/repository/CarRepository.java` | Repository |
| `domain/repository/TrackRepository.java` | Repository |
| `admin/controller/CarController.java` | CRUD Controller |
| `admin/controller/TrackController.java` | CRUD Controller |
| `admin/dto/CarForm.java` | Form DTO |
| `admin/dto/TrackForm.java` | Form DTO |
| `templates/admin/cars.html` | Liste |
| `templates/admin/car-form.html` | Formular |
| `templates/admin/tracks.html` | Liste |
| `templates/admin/track-form.html` | Formular |
| `static/admin/js/searchable-dropdown.js` | Autocomplete-Komponente |

### Geaendert

| Datei | Aenderung |
|-------|-----------|
| `db/migration/V1__initial_schema.sql` | cars, tracks, season_cars, season_tracks Tabellen; Race FK-Umbau |
| `domain/model/Race.java` | track/car String → Car/Track Entity-Referenzen |
| `domain/model/Season.java` | cars/tracks Many-to-Many Listen |
| `admin/controller/RaceController.java` | Searchable Dropdown Daten, Validierung, JSON-Endpoint |
| `admin/controller/SeasonController.java` | Pool-Zuordnung Endpoints |
| `admin/dto/RaceForm.java` | trackId/carId statt track/car String |
| `templates/admin/layout.html` | Navbar → Sidebar-Layout |
| `templates/admin/race-form.html` | Searchable Dropdowns statt Text-Inputs |
| `templates/admin/season-form.html` | Dual-List Sektionen fuer Car/Track Pool |
| `static/admin/css/admin.css` | Sidebar-Styles, Searchable-Dropdown-Styles |
| `sitegen/SiteGeneratorService.java` | Car/Track Entity statt String lesen |
| `templates/site/driver-profile.html` | Track-Anzeige anpassen |

### Tests

| Datei | Beschreibung |
|-------|-------------|
| `test/.../CarControllerTest.java` | NEU — CRUD Tests |
| `test/.../TrackControllerTest.java` | NEU — CRUD Tests |
| `test/.../RaceControllerTest.java` | Anpassen — Car/Track FK statt String |
| `test/.../StandingsServiceTest.java` | Anpassen falls Track/Car referenziert |
| `test/.../SiteGeneratorServiceTest.java` | Anpassen — Car/Track Entities in setUp |
| `test/.../AdminWorkflowE2ETest.java` | Erweitern — Cars/Tracks Navigation, CRUD |

## Implementierungsreihenfolge

1. Sidebar-Layout (layout.html + CSS umbau)
2. DB-Migration (V1 erweitern)
3. Car + Track Entities und Repositories
4. Car + Track Admin CRUD (Controller, Templates)
5. Season-Pool-Zuordnung (Dual-List auf Season-Edit)
6. Race-Formular-Integration (Searchable Dropdown + Validierung)
7. SiteGen-Anpassung (Car/Track Entity statt String)
8. Tests anpassen und erweitern
