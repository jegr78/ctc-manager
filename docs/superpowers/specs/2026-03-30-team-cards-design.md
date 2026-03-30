# Team Cards Design Spec

## Context

Team Cards sind Grafiken im Story-Format (1080x1920px) die bisher manuell in Canva erstellt werden. Sie zeigen pro Team: Logo, Teamfarben, Overall Rating, aktuelle Punkte und Record (W-L-D). Ziel ist es, diese Cards direkt aus der Admin-UI generieren zu koennen — basierend auf gepflegten Teamfarben, hochgeladenem Logo und saisonspezifischen Daten.

## Entscheidungen

| Thema | Entscheidung |
|-------|-------------|
| Rating-Speicherung | Neues **SeasonTeam**-Entity |
| Farben | Am **Team** als Default, am **SeasonTeam** ueberschreibbar |
| Logo | Am **Team** als Default (File Upload), am **SeasonTeam** ueberschreibbar |
| Card-Generierung | **Playwright** (HTML/CSS → PNG Screenshot) |
| Card-Layout | **Gradient Fade** (vertikaler Gradient, moderner Look) |
| Card-Format | **1080x1920px** (Story-Format) |
| UI-Einstieg | Eigene **Tool-Seite** "Team Cards" |
| Galerie | Cards werden gespeichert + Vorschau-Galerie + ZIP-Download |
| Farb-Eingabe | Color Picker + Hex-Feld |
| DB-Migration | V1 anpassen (noch nicht veroeffentlicht) |

---

## 1. Datenmodell

### 1.1 Neues Entity: SeasonTeam

Ersetzt die bestehende ManyToMany Join-Tabelle `season_teams`.

```
SeasonTeam
  - id: UUID (PK)
  - season: Season (FK, ManyToOne, NOT NULL)
  - team: Team (FK, ManyToOne, NOT NULL)
  - rating: Integer (nullable, manuell gepflegt)
  - primaryColor: String (nullable, Override)
  - secondaryColor: String (nullable, Override)
  - accentColor: String (nullable, Override)
  - logoUrl: String (nullable, Override)
  - UNIQUE(season_id, team_id)
```

**Convenience-Methoden am SeasonTeam:**
- `getEffectivePrimaryColor()` → `primaryColor != null ? primaryColor : team.getPrimaryColor()`
- `getEffectiveSecondaryColor()` → analog
- `getEffectiveAccentColor()` → analog
- `getEffectiveLogoUrl()` → analog

### 1.2 Erweiterung Team-Entity

Neue Felder:
- `primaryColor: String` (#hex, z.B. "#e53935")
- `secondaryColor: String` (#hex)
- `accentColor: String` (#hex)

Bestehendes Feld:
- `logoUrl`: bleibt, aber Upload statt Textfeld

### 1.3 Season-Entity Anpassung

Die bestehende `@ManyToMany teams`-Beziehung wird durch `@OneToMany seasonTeams` ersetzt. Alle Stellen die `season.getTeams()` nutzen muessen auf `season.getSeasonTeams()` umgestellt werden.

### 1.4 Schema-Aenderung (V1__initial_schema.sql)

Da V1 noch nicht veroeffentlicht ist, wird das Schema direkt in `V1__initial_schema.sql` angepasst:

- `teams`-Tabelle: Spalten `primary_color VARCHAR(7)`, `secondary_color VARCHAR(7)`, `accent_color VARCHAR(7)` hinzufuegen
- `season_teams`-Tabelle: Von reiner Join-Tabelle (nur season_id + team_id) zu vollwertigem Entity mit eigener `id UUID`, `rating INTEGER`, `primary_color`, `secondary_color`, `accent_color`, `logo_url VARCHAR(500)`, plus Foreign Keys und Unique Constraint

---

## 2. Team-Formular Erweiterung

### 2.1 Farb-Eingabe

Neue Card-Section "Brand Colors" im Team-Formular mit 3 Farbfeldern:
- Jeweils: `<input type="color">` + `<input type="text" pattern="#[0-9a-fA-F]{6}">` nebeneinander
- Color Picker und Hex-Feld synchronisieren sich per JavaScript
- Labels: "Primary Color", "Secondary Color", "Accent Color"

### 2.2 Logo-Upload

Bestehendes `logoUrl`-Textfeld ersetzen durch File-Upload:
- Gleicher Pattern wie Car/Track Image Upload (FileStorageService)
- Speicherung unter `/uploads/teams/{teamId}/`
- Bildvorschau wenn Logo vorhanden
- Akzeptierte Formate: PNG, JPEG, WebP, GIF

### 2.3 Validierung

- Farbwerte: Hex-Format `#RRGGBB` (7 Zeichen)
- Logo: Max 10MB (bestehende FileStorageService-Validierung)

---

## 3. SeasonTeam-Verwaltung

### 3.1 Bestehende Season-Teams-Verwaltung anpassen

Die bestehende Zuordnung von Teams zu Seasons muss auf SeasonTeam umgestellt werden. Wo aktuell Teams per ManyToMany hinzugefuegt/entfernt werden, wird stattdessen ein SeasonTeam-Record erstellt/geloescht.

### 3.2 Rating und Overrides pflegen

Auf der Season-Detail-Seite (bestehende Seite unter `/admin/seasons/{id}`):
- Tabelle aller SeasonTeams mit Spalten: Team, Rating, Farb-Overrides
- Rating: Inline-Eingabefeld (Integer)
- Farb-Overrides: Optional, nur sichtbar wenn aufgeklappt (Accordion oder Modal)
- Logo-Override: Optional, File-Upload

---

## 4. Team Cards Tool-Seite

### 4.1 Route und Navigation

- **Route:** `/admin/tools/team-cards`
- **Sidebar:** Unter "Tools", nach "Generate Site"
- **Controller:** `TeamCardController`

### 4.2 Seitenaufbau

- **Saison-Dropdown:** Oben, Default = aktive Saison
- **Team-Grid:** Alle Teams der gewaehlten Saison als Kacheln
- Pro Kachel:
  - Thumbnail der generierten Card (oder Placeholder)
  - Team-Name, Short-Name, Rating
  - "Generate" Button (einzeln)
  - "Download PNG" Button (nur wenn Card existiert)
- **Toolbar:**
  - "Generate All" Button (Batch)
  - "Download All as ZIP" Button

### 4.3 Endpunkte

| Method | URL | Zweck |
|--------|-----|-------|
| GET | `/admin/tools/team-cards` | Seite mit Saison-Dropdown |
| GET | `/admin/tools/team-cards?seasonId={id}` | Seite fuer bestimmte Saison |
| POST | `/admin/tools/team-cards/generate/{seasonTeamId}` | Einzelne Card generieren |
| POST | `/admin/tools/team-cards/generate-all?seasonId={id}` | Alle Cards einer Saison |
| GET | `/admin/tools/team-cards/download/{seasonTeamId}` | Einzel-Download PNG |
| GET | `/admin/tools/team-cards/download-all?seasonId={id}` | ZIP-Download aller Cards |

---

## 5. Card-Template & Rendering

### 5.1 Template

**Datei:** `templates/admin/team-card.html`

Eigenstaendige HTML-Seite (kein Admin-Layout-Fragment). Wird nur fuer Playwright-Screenshot gerendert.

**Design — Gradient Fade:**
- Hintergrund: Vertikaler Gradient von `secondaryColor` (oben, semi-transparent) nach dunkel (#111)
- Rating-Kreis: Oben zentriert, Border in `accentColor`, Zahl in Conthrax-Font
- Logo: Zentriert im mittleren Bereich
- Team-Name: Unter dem Logo, weiss, Conthrax-Font
- Sub-Team-Bezeichnung: Unter Team-Name, in `accentColor`
- Stats-Bereich unten:
  - "POINTS" Label in `accentColor`, Wert in weiss gross
  - "RECORD" Label in `accentColor`, W-L-D in weiss

### 5.2 TeamCardService

**Package:** `de.ctc.admin.service`

**Methoden:**
- `generateCard(SeasonTeam seasonTeam)` → String (PNG-Pfad)
- `generateAllCards(Season season)` → List<String> (PNG-Pfade)
- `getCardPath(SeasonTeam seasonTeam)` → String (erwarteter Pfad, auch wenn nicht generiert)

**Ablauf:**
1. Daten sammeln: SeasonTeam (Rating, effektive Farben/Logo), StandingsService (Points, W-L-D)
2. Thymeleaf-Template rendern → temporaere HTML-Datei
   - Bilder (Logo, Fonts) als base64 inline oder file:// Referenzen
3. Playwright headless Chromium:
   - `page.setViewportSize(1080, 1920)`
   - `page.navigate("file:///tmp/team-card-{id}.html")`
   - `page.screenshot(path)` → PNG
4. PNG via FileStorageService speichern unter `/uploads/team-cards/{seasonId}/{teamShortName}.png`
5. Temporaere HTML-Datei aufraeumen

### 5.3 Playwright Runtime-Dependency

- Playwright von `<scope>test</scope>` auf `<scope>compile</scope>` aendern (oder zusaetzliche compile-Dependency)
- Docker: Chromium-Installation im Dockerfile ergaenzen
- Browser wird pro Generierung gestartet oder als Singleton im Service gehalten (Performance-Tradeoff)

---

## 6. Datenquellen fuer Card-Inhalte

| Card-Element | Datenquelle |
|-------------|-------------|
| Rating (Zahl) | `SeasonTeam.rating` |
| Logo | `SeasonTeam.getEffectiveLogoUrl()` |
| Primary Color | `SeasonTeam.getEffectivePrimaryColor()` |
| Secondary Color | `SeasonTeam.getEffectiveSecondaryColor()` |
| Accent Color | `SeasonTeam.getEffectiveAccentColor()` |
| Team-Name | `Team.name` |
| Sub-Team-Bezeichnung | Suffix aus `Team.shortName` wenn Sub-Team (z.B. "A", "1") |
| Points | `StandingsService.calculateStandings(season)` → `TeamStanding.points` |
| Record (W-L-D) | `TeamStanding.wins` - `TeamStanding.losses` - `TeamStanding.draws` |

---

## 7. Betroffene Dateien

### Neue Dateien
- `SeasonTeam.java` — JPA Entity
- `SeasonTeamRepository.java` — Spring Data Repository
- `TeamCardService.java` — Card-Generierung (Playwright)
- `TeamCardController.java` — Admin Controller fuer Tool-Seite
- `team-cards.html` — Thymeleaf Template fuer Tool-Seite
- `team-card.html` — Thymeleaf Template fuer Card-Rendering (Playwright)

### Zu aendernde Dateien
- `V1__initial_schema.sql` — Schema erweitern (Farben an teams, season_teams als Entity)
- `Team.java` — Farbfelder hinzufuegen
- `Season.java` — ManyToMany → OneToMany SeasonTeam
- `team-form.html` — Farb-Picker + Logo-Upload
- `TeamController.java` — Logo-Upload Endpunkt, Farb-Felder
- `layout.html` — Sidebar-Eintrag "Team Cards" unter Tools
- `TestDataService.java` — Farben + SeasonTeam-Erstellung fuer Testdaten
- `StandingsService.java` — ggf. Anpassung wenn season.getTeams() → season.getSeasonTeams()
- `ScoringService.java` — ggf. Anpassung analog
- `SiteGeneratorService.java` — ggf. Anpassung analog
- `pom.xml` — Playwright Scope aendern
- `Dockerfile` — Chromium installieren
- `docker-compose.yml` — ggf. Volume fuer team-cards

---

## 8. Verifikation

### Unit Tests
- `SeasonTeam.getEffective*()` Methoden (Fallback-Logik)
- `TeamCardService` — Template-Rendering, Dateipfad-Logik

### Integration Tests
- SeasonTeam CRUD (Repository-Tests)
- TeamCardController Endpunkte
- Farb-Validierung

### E2E Tests (Playwright)
- Team-Formular: Farben setzen + Logo hochladen
- Team Cards Seite: Card generieren, Vorschau sehen, Download
- SeasonTeam: Rating pflegen

### Manuelle Verifikation
- Dev-Server starten (`dev,demo` Profil)
- Team mit Farben + Logo anlegen
- Team Cards Seite oeffnen, Cards generieren
- Visuell pruefen: Gradient, Farben, Logo-Positionierung, Schrift
- PNG herunterladen und Qualitaet pruefen (1080x1920, keine Artefakte)
- ZIP-Download testen
