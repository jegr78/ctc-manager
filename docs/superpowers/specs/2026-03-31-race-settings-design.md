# Race Settings + Settings-Grafik

## Kontext

Fuer jedes Race sollen zusaetzliche Einstellungen (Runden, Reifenverschleiss, Wetter etc.) gepflegt werden koennen. Auf Basis dieser Settings wird eine Settings-Grafik generiert, die als Attachment am Race gespeichert wird — analog zu Lineup- und Results-Grafiken.

## Neue Entity: RaceSettings

Eigene Entity mit `@OneToOne`-Beziehung zu Race. Erweitert `BaseEntity` (createdAt/updatedAt). Neue DB-Tabelle `race_settings`.

Car und Track bleiben auf Race (bestehende FK-Relationen mit vielen Referenzen).

### Felder

| Feld                        | Java-Typ | DB-Typ             | Beschreibung                   |
| --------------------------- | -------- | ------------------ | ------------------------------ |
| id                          | UUID     | UUID PK            | Primaerschluessel              |
| race                        | Race     | race_id FK NOT NULL | OneToOne zu Race              |
| numberOfLaps                | Integer  | INT                | Rundenzahl                     |
| tyreWearMultiplier          | Integer  | INT                | Reifenverschleiss-Multiplikator |
| fuelConsumptionMultiplier   | Integer  | INT                | Spritverbrauchs-Multiplikator  |
| refuelingSpeed              | Integer  | INT                | Tankgeschwindigkeit            |
| initialFuel                 | String   | VARCHAR(100)       | Anfangs-Sprit                  |
| numberOfRequiredPitStops    | Integer  | INT                | Pflicht-Boxenstopps            |
| timeProgressionMultiplier   | Integer  | INT                | Zeitverlaufs-Multiplikator     |
| weather                     | String   | VARCHAR(255)       | Wetter (Preset/Custom mit Slots) |
| timeOfDay                   | String   | VARCHAR(100)       | Tageszeit                      |
| availableTyres              | String   | VARCHAR(255)       | Verfuegbare Reifentypen        |
| mandatoryTyres              | String   | VARCHAR(255)       | Pflicht-Reifentypen            |

Alle Felder nullable — muessen erst fuer Grafik-Generierung vollstaendig sein.

### Entity-Beziehung

```text
Race (1) ──── (0..1) RaceSettings
```

- `Race` erhaelt `@OneToOne(mappedBy = "race", cascade = ALL, orphanRemoval = true) RaceSettings settings`
- `RaceSettings` erhaelt `@OneToOne(fetch = LAZY) @JoinColumn(name = "race_id") Race race`
- Convenience-Methode auf Race: `hasAllSettings()` — prueft ob RaceSettings existiert und alle Felder gefuellt sind

## DB-Schema

Neue Tabelle `race_settings` direkt in `V1__initial_schema.sql` ergaenzen (Projekt noch nicht veroeffentlicht):

```sql
CREATE TABLE race_settings (
    id         BINARY(16) NOT NULL PRIMARY KEY,
    race_id    BINARY(16) NOT NULL,
    number_of_laps INT,
    tyre_wear_multiplier INT,
    fuel_consumption_multiplier INT,
    refueling_speed INT,
    initial_fuel VARCHAR(100),
    number_of_required_pit_stops INT,
    time_progression_multiplier INT,
    weather VARCHAR(255),
    time_of_day VARCHAR(100),
    available_tyres VARCHAR(255),
    mandatory_tyres VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_race_settings_race FOREIGN KEY (race_id) REFERENCES races(id),
    CONSTRAINT uq_race_settings_race UNIQUE (race_id)
);
```

## RaceForm DTO

Bestehende `RaceForm` erhaelt die 11 Settings-Felder direkt (flach, kein verschachteltes Objekt). Mapping zwischen Form und Entity in `RaceManagementService.toForm()` und `saveRace()`.

## Race Edit Form

Neue Sektion "Race Settings" im bestehenden Formular (`race-form.html`) unterhalb von Car/Track. Felder gruppiert in Zweier-Reihen:

- Row 1: Number of Laps | Number of Required Pit Stops
- Row 2: Tyre Wear Multiplier | Fuel Consumption Multiplier
- Row 3: Initial Fuel | Refueling Speed
- Row 4: Time of Day | Time Progression Multiplier
- Row 5: Weather (volle Breite)
- Row 6: Available Tyres | Mandatory Tyres

Alle Felder optional (kein `required`). Number-Inputs mit `type="number" min="0"`.

## SettingsGraphicService

Neue Klasse `org.ctc.admin.service.SettingsGraphicService` — erweitert `AbstractGraphicService`.

### Methode: `generateSettings(Race race)`

1. Validierung: Race hat Match/PlayoffMatchup, Car, Track, und alle Settings gefuellt
2. Home/Away Teams und Season ermitteln
3. Team Cards als Base64 codieren (Pattern von LineupGraphicService)
4. Standings-Positionen berechnen
5. Thymeleaf Context befuellen:
   - Header/Footer: `seasonYear`, `matchdayName`, `seasonName`, `homePosition`, `awayPosition`
   - Assets: `ctcLogoBase64`, `fontBase64`, `homeCardBase64`, `awayCardBase64`
   - Settings: `carName`, `trackName`, `numberOfLaps`, `tyreWearMultiplier`, `fuelConsumptionMultiplier`, `refuelingSpeed`, `initialFuel`, `numberOfRequiredPitStops`, `timeProgressionMultiplier`, `weather`, `timeOfDay`, `availableTyres`, `mandatoryTyres`
6. Template rendern (Custom oder Default)
7. Playwright-Screenshot (1920x1080)
8. Speichern als `/uploads/races/{raceId}/settings.png`

### Custom-Template-Support

Gleiche Pattern wie LineupGraphicService:

- `loadTemplate()`, `loadDefaultTemplate()`, `saveTemplate(String)`, `resetTemplate()`, `hasCustomTemplate()`
- Custom-Template-Datei: `{uploadDir}/settings-template.html`

## Settings-Render Template

`templates/admin/settings-render.html` — 1920x1080px, gleiche Grundstruktur wie Lineup/Results:

### Header (grauer Gradient)

- Links: "COMMUNITY TEAM CUP" + Season Year
- Mitte: "Settings" (gross) + Matchday Label (z.B. "MD 1")
- Rechts: CTC Logo

### Main (dunkler Hintergrund #111111)

- Links (18%): Home Team Card
- Mitte: Settings als gelabelte Gruppen
  - **CAR**: {carName}
  - **TRACK**: {trackName} ({numberOfLaps} Laps)
  - **TYRE / FUEL / MANDATORY PIT STOPS**: {tyreWear}x / {fuelConsumption}x / {pitStops}
  - **INITIAL FUEL / REFUEL SPEED**: {initialFuel} / {refuelingSpeed} l/s
  - **TIME OF DAY / PROGRESSION**: {timeOfDay} / {timeProgression}x
  - **WEATHER**: {weather}
  - **AVAILABLE TYRES / MANDATORY TYRES**: {availableTyres} / {mandatoryTyres}
- Rechts (18%): Away Team Card

Labels: Klein, uppercase, gedaempfte Farbe (wie in Screenshots)
Werte: Groesser, Conthrax-Font, helle Farbe

### Footer (heller Gradient)

- Links: P{homePosition}
- Mitte: Season Name (z.B. "Regular Season")
- Rechts: P{awayPosition}

## Race Detail — Button

Neuer "Generate Settings Graphic"-Button neben Lineup und Results in `race-detail.html`.

### Preconditions

- `canGenerateSettings` = `race.hasAllSettings() && race.getCar() != null && race.getTrack() != null && hasHomeCard && hasAwayCard && !settingsGraphicExists`
- Disabled-Hinweise: "Settings incomplete." oder "Team cards missing."

### Attachment

- Name: `{matchdayLabel}-{homeShort}-{awayShort}-Settings`
- URL: `/uploads/races/{raceId}/settings.png`
- Typ: `FILE`

## RaceDetailData Record

Drei neue Felder:

- `boolean canGenerateSettings`
- `boolean settingsMissing`
- `boolean settingsExist`

## RaceController

- Neuer Endpoint: `@PostMapping("/{id}/generate-settings")`
- Detail-Methode: drei neue Model-Attribute (`canGenerateSettings`, `settingsMissing`, `settingsExist`)

## Template-Editor

Neuer "Settings"-Tab in `template-editors.html` (neben Team Cards und Lineup).

### TemplateEditorController

- `SettingsGraphicService` injizieren
- `index()`: Settings-Template und `settingsIsCustom`-Flag laden
- `@PostMapping("/settings/save")` und `@PostMapping("/settings/reset")`

## Betroffene Dateien

### Neu

- `org.ctc.domain.model.RaceSettings` — Neue Entity
- `org.ctc.domain.repository.RaceSettingsRepository` — Spring Data Repository
- `org.ctc.admin.service.SettingsGraphicService` — Grafik-Service
- `templates/admin/settings-render.html` — Render-Template

### Modifiziert

- `V1__initial_schema.sql` — `race_settings` Tabelle ergaenzen
- `Race.java` — `@OneToOne RaceSettings settings` + `hasAllSettings()`
- `RaceForm.java` — 11 Settings-Felder
- `RaceManagementService.java` — `RaceDetailData` erweitern, `generateSettings()`, Form-Mapping
- `RaceController.java` — Endpoint + Model-Attribute
- `race-form.html` — Settings-Sektion
- `race-detail.html` — Settings-Button
- `TemplateEditorController.java` — Settings-Tab
- `template-editors.html` — Settings-Tab
- `TestDataService.java` — Test-Races mit Settings versehen

## Verifikation

1. `./mvnw verify` — Tests muessen durchlaufen
2. Dev-Server starten, Race anlegen, Settings eintragen, Grafik generieren
3. Playwright-CLI: Generierte Grafik visuell pruefen
4. Template-Editor: Custom-Template speichern, generieren, Reset testen
