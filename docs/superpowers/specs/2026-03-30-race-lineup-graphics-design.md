# Race Lineup Graphics — Design Spec

## Context

Die CTC-Liga veröffentlicht vor jedem Race eine Lineup-Grafik, die beide Teams mit ihren Fahrer-Paarungen zeigt. Bisher werden diese manuell erstellt. Dieses Feature automatisiert die Generierung als 1920×1080 Full-HD-PNG, analog zur bestehenden Team Card Generierung. Zusätzlich wird eine "Template Editors"-Seite eingeführt, die den bestehenden Team Card Editor und den neuen Lineup Editor unter Tabs vereint.

## Feature-Übersicht

1. **LineupGraphicService** — Generiert Lineup-PNG per Race via Playwright (analog zu TeamCardService)
2. **Lineup-Template** — Thymeleaf HTML-Template (1920×1080, editierbar)
3. **Template Editors Seite** — Neue Seite unter Tools mit Tabs für Team Cards und Lineup Templates
4. **Generate-Button** — Prominenter Button auf der Race-Detailseite
5. **Download-Button** — Pro Attachment in der Race-Detailansicht
6. **RaceAttachment** — Generierte Grafik wird als FILE-Attachment am Race gespeichert

## 1. Lineup-Grafik Layout (1920×1080)

### Struktur

```
┌─────────────────────────────────────────────────────────┐
│  COMMUNITY      Lineups                            ⚡   │  ← Header (großzügig, ~12%)
│  TEAM CUP        MD 1                                   │
│  2026                                                    │
├─────────────────────────────────────────────────────────┤
│ ┌─────────┐                                ┌─────────┐  │
│ │         │  Driver A1        Driver B1     │         │  │
│ │  Home   │  Driver A2        Driver B2     │  Away   │  │
│ │  Team   │  Driver A3        Driver B3     │  Team   │  │  ← Main (~76%)
│ │  Card   │  Driver A4        Driver B4     │  Card   │  │
│ │  (PNG)  │  Driver A5        Driver B5     │  (PNG)  │  │
│ │         │  Driver A6        Driver B6     │         │  │
│ └─────────┘                                └─────────┘  │
├─────────────────────────────────────────────────────────┤
│    P6           Regular Season                  P13     │  ← Footer (großzügig, ~12%)
└─────────────────────────────────────────────────────────┘
```

### Header
- Links: "COMMUNITY TEAM CUP" (Conthrax, uppercase, bold, dunkelgrau/schwarz) + Jahr darunter (gleiche Farbe wie Haupttext, aus Season-Name extrahiert)
- Mitte: "Lineups" (groß, italic, Conthrax) + Matchday-Label darunter (bold)
- Rechts: CTC-Logo (Blitz-Icon, `/static/admin/img/ctc-logo-white.png`)
- Hintergrund: Hellgrau-Gradient (wie Screenshots)
- Farbgebung: Orientiert sich an den Screenshots — kein Rot, alle Texte in dunklen Tönen auf hellem Hintergrund
- Höhe: Großzügig dimensioniert (~12% der Gesamthöhe)

### Main
- Hintergrund: Schwarz (#111), clean ohne diagonale Linien
- Links: Home Team Card PNG, skaliert, weißer Rahmen mit leicht abgerundeten Ecken (border-radius ~6px)
- Rechts: Away Team Card PNG, gleiche Darstellung
- Mitte: Fahrer-Paarungen (dynamisch aus RaceLineup), Fahrernamen (PSN-IDs) in weiß, bold
- Paarungen: Home-Fahrer linksbündig, Away-Fahrer rechtsbündig, vertikal gleichmäßig verteilt
- Overflow: Fahrernamen dürfen nicht umbrechen — bei zu langen Namen mit Ellipsis (`text-overflow: ellipsis`) abschneiden

### Footer
- Links: "P{position}" Home-Team (große Schrift, Conthrax)
- Mitte: Season-Name (z.B. "Regular Season", italic)
- Rechts: "P{position}" Away-Team
- Hintergrund: Hellgrau (wie Header)
- Höhe: Großzügig dimensioniert (~12% der Gesamthöhe)

## 2. Template-Variablen

| Variable | Typ | Beschreibung |
|----------|-----|-------------|
| `seasonYear` | String | Jahr extrahiert aus Season-Name (z.B. "2026") |
| `matchdayName` | String | Matchday-Label (z.B. "MD 1") |
| `seasonName` | String | Season-Name für Footer (z.B. "Regular Season") |
| `homeCardBase64` | String | Home Team Card PNG als data URI |
| `awayCardBase64` | String | Away Team Card PNG als data URI |
| `homePosition` | Integer | Tabellenposition Home-Team aus Standings |
| `awayPosition` | Integer | Tabellenposition Away-Team aus Standings |
| `pairings` | List | Liste von `{homeDriver, awayDriver}` (PSN-IDs als Strings) |
| `ctcLogoBase64` | String | CTC-Logo als data URI |
| `fontBase64` | String | Conthrax-Font als data URI |

### Pairings-Logik

Die Fahrer-Paarungen werden direkt aus dem `RaceLineup` abgeleitet:
- Home-Fahrer: `RaceLineup`-Einträge, deren `team` dem Home-Team (oder Sub-Teams) des Matches entspricht
- Away-Fahrer: Analog für Away-Team
- Paarung: Index-basiert (1. Home-Fahrer vs 1. Away-Fahrer, etc.)
- Dynamische Anzahl: So viele Paarungen wie Lineup-Einträge vorhanden

## 3. LineupGraphicService

Neuer Service analog zu `TeamCardService`:

**Datei:** `org.ctc.admin.service.LineupGraphicService`

### Ablauf

1. **Daten laden:**
   - Race → Match → homeTeam, awayTeam
   - RaceLineup für das Race (getrennt nach Home/Away Team)
   - StandingsService → Positionen beider Teams (nur die Season des Races, ermittelt über `race.matchday.season`)
   - Season → Name, Jahr
   - Team Cards → PNGs als Base64 aus `/uploads/team-cards/{seasonId}/{shortName}.png`
2. **Validierung:**
   - RaceLineup muss existieren (mind. 1 Eintrag)
   - Team Cards für beide Teams müssen existieren
3. **Template rendern:** Thymeleaf → HTML mit allen Variablen → Temp-File
4. **Screenshot:** Playwright mit Viewport 1920×1080 → PNG
5. **Speichern:**
   - PNG über `FileStorageService.store()` unter `/uploads/races/{raceId}/` ablegen
   - `RaceAttachment` (Typ FILE, Name "Lineup") am Race anlegen
6. **Cleanup:** Temp-File löschen

### Template-Verwaltung

- Default-Template: `templates/admin/lineup-render.html` (Classpath)
- Custom-Template: `{uploadDir}/lineup-template.html`
- Methoden: `loadTemplate()`, `saveTemplate()`, `resetTemplate()`, `hasCustomTemplate()`

## 4. Template Editors Seite

**Route:** `/admin/tools/template-editors`

### Navigation

- Neuer Menüpunkt "Template Editors" unter Tools im Sidebar (`layout.html`)
- Bisheriger "Team Cards"-Eintrag bleibt unverändert

### Aufbau

- **Tabs:** "Team Cards" | "Lineups" (CSS-Tabs, kein JS nötig — Query-Parameter `?tab=lineup`)
- **Pro Tab:** Identischer Editor-Aufbau wie bisheriger Team Card Template Editor:
  - Links: Textarea mit Template-Code (HTML + Thymeleaf)
  - Rechts: Variablen-Referenz und Tips
  - Buttons: Save Template, Reset to Default
  - Badge: Custom/Default Status

### Controller

**Neuer Controller:** `TemplateEditorController` unter `/admin/tools/template-editors`

- `GET /` — Zeigt Editor-Seite, Tab per Query-Param
- `POST /team-cards/save` — Speichert Team Card Template (delegiert an `TeamCardService`)
- `POST /team-cards/reset` — Reset Team Card Template
- `POST /lineup/save` — Speichert Lineup Template (delegiert an `LineupGraphicService`)
- `POST /lineup/reset` — Reset Lineup Template

### Migration der Template-Endpoints

Die bestehenden Template-Endpoints im `TeamCardController` (`/template`, `/template/save`, `/template/reset`) werden in den neuen `TemplateEditorController` verschoben. Der `TeamCardController` verliert die Template-Verwaltung.

## 5. Generate-Button auf Race-Detailseite

### Platzierung

Prominenter Button im oberen Bereich der Race-Detailseite (`race-detail.html`), oberhalb des Score-Banners:

```html
<!-- Generate Lineup Button (prominent, analog zu Generate All bei Team Cards) -->
<form action="/admin/races/{id}/generate-lineup" method="post">
    <button class="btn btn-primary">Generate Lineup</button>
</form>
```

### Vorbedingungen (Controller prüft)

- Race hat ein Match (nicht standalone)
- RaceLineup existiert (mind. 1 Eintrag)
- Team Cards für beide Teams existieren

Bei fehlenden Vorbedingungen: Button deaktiviert mit Tooltip/Hinweis.

### Endpoint

`POST /admin/races/{id}/generate-lineup` im `RaceController`:
- Ruft `LineupGraphicService.generateLineup(race)` auf
- Redirect zurück zur Race-Detailseite
- Success/Error Flash-Message

## 6. Download-Button pro Attachment

In der Attachment-Liste auf der Race-Detailseite (`race-detail.html`) wird für jedes FILE-Attachment ein Download-Button ergänzt:

```html
<a th:href="@{/admin/races/attachments/{aid}/download(aid=${att.id})}"
   class="btn btn-secondary btn-sm">Download</a>
```

### Endpoint

`GET /admin/races/attachments/{attachmentId}/download` im `RaceController`:
- Liest die Datei über `FileStorageService`
- Setzt `Content-Disposition: attachment` Header
- Gibt die Datei als Download zurück

## 7. Kritische Dateien

### Zu erstellen
- `org.ctc.admin.service.LineupGraphicService` — Generierungslogik
- `org.ctc.admin.controller.TemplateEditorController` — Template Editor Seite
- `templates/admin/lineup-render.html` — Default Lineup-Template
- `templates/admin/template-editors.html` — Template Editors Seite mit Tabs

### Zu modifizieren
- `templates/admin/layout.html` — Neuer Menüpunkt "Template Editors"
- `templates/admin/race-detail.html` — Generate Lineup Button + Download-Button pro Attachment
- `RaceController.java` — Generate-Lineup Endpoint + Attachment-Download Endpoint
- `TeamCardController.java` — Template-Endpoints entfernen (nach Migration)

### Bestehender Code (wiederverwenden)
- `TeamCardService.java` — Pattern für Playwright-Rendering, Template-Verwaltung, Base64-Encoding
- `FileStorageService.java` — Datei-Speicherung unter `/uploads/races/{raceId}/`
- `StandingsService.java` — Team-Positionen berechnen
- `RaceAttachment.java` — Attachment-Entity (unverändert nutzbar)
- `RaceLineupRepository.java` — `findByRaceId()` für Lineup-Daten

## 8. Verifikation

1. **Unit Tests:** `LineupGraphicServiceTest` — Template-Rendering, Variablen-Setup, Validierung
2. **Integration Test:** Lineup generieren für ein Race mit Lineup-Daten, prüfen dass RaceAttachment angelegt wird
3. **Visuell:** Dev-Server starten (`dev,demo` Profil), Race mit Lineup anlegen, `playwright-cli` zur Inspektion der generierten Grafik und der Admin-Seiten (Template Editors, Race-Detail mit Button)
4. **Template Editor:** Beide Tabs testen (Team Cards + Lineup), Save/Reset/Custom-Badge prüfen
5. **Download:** Attachment-Download in der Race-Detailansicht testen
